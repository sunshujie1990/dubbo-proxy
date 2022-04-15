/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.support;

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_RETRIES;
import static org.apache.dubbo.common.constants.CommonConstants.RETRIES_KEY;

/**
 * When invoke fails, log the initial error and retry other invokers (retry n times, which means at most n different invokers will be invoked)
 * Note that retry causes latency.
 * <p>
 *
 *     原版通过for循环实现重试是不支持异步的，https://github.com/apache/dubbo/issues/6965，搜到一个issue，
 *     这么明显的bug竟然没人修复。 ClusterInvoker整个接口设计对于异步都是有问题的，
 *     先临时修复FailoverClusterInvoker
 * <a href="http://en.wikipedia.org/wiki/Failover">Failover</a>
 */
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(FailoverClusterInvoker.class);

    public FailoverClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyInvokers = invokers;
        checkInvokers(copyInvokers, invocation);
        String methodName = RpcUtils.getMethodName(invocation);
        int len = calculateInvokeTimes(methodName);
        // retry loop.
        CompletableFuture<AppResponse> future = new CompletableFuture<>();
        AsyncRpcResult asyncRpcResult = new AsyncRpcResult(future, invocation);

        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyInvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        RetryHelper retryHelper = new RetryHelper(len, invocation, loadbalance, copyInvokers, methodName, invoked, providers);
        retryHelper.future = future;
        doRequest(retryHelper);
        return asyncRpcResult;
    }

    private void doRequest(RetryHelper retryHelper) {
        //Reselect before retry to avoid a change of candidate `invokers`.
        //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
        if (retryHelper.tried > 0) {
            checkWhetherDestroyed();
            List<Invoker<T>> copyInvokers = list(retryHelper.invocation);
            // check again
            checkInvokers(copyInvokers, retryHelper.invocation);
            logger.warn("Failed to invoke the method "
                    + retryHelper.methodName + " in the service " + getInterface().getName()
                    + ". Tried " + retryHelper.tried + " times of the providers " + retryHelper.providers
                    + " (" + retryHelper.providers.size() + "/" + retryHelper.copyInvokers.size()
                    + ") from the registry " + directory.getUrl().getAddress()
                    + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                    + Version.getVersion() + ". Last error is: "
                    + retryHelper.le.getMessage());
        }

        retryHelper.tried++;

        Invoker<T> invoker = select(retryHelper.loadbalance, retryHelper.invocation, retryHelper.copyInvokers, retryHelper.invoked);
        RpcContext.getContext().setInvokers((List) retryHelper.invoked);
        retryHelper.invoked.add(invoker);

        Result result = invoker.invoke(retryHelper.invocation);
        // org.apache.dubbo.rpc.protocol.AbstractInvoker.invoke 中设置了一个future，
        // 这会导致async模式重试不生效，所以在invoke之后再重新设置一遍
        RpcContext.getContext().setFuture(new FutureAdapter<>(retryHelper.future));
        result.whenCompleteWithContext((r, t) -> {
            if (t != null) {
                if (t instanceof RpcException) {
                    RpcException e = (RpcException) t;
                    if (e.isBiz()) { // biz exception.
                        throw e;
                    }
                    retryHelper.le = e;
                } else {
                    retryHelper.le = new RpcException(t.getMessage(), t);
                }
                retryHelper.providers.add(invoker.getUrl().getAddress());
                retryHelper.retrying = true;

                if (retryHelper.tried < retryHelper.total) {
                    doRequest(retryHelper);
                } else {
                    RpcException rpcException = new RpcException(retryHelper.le.getCode(), "Failed to invoke the method "
                            + retryHelper.methodName + " in the service " + getInterface().getName()
                            + ". Tried " + retryHelper.tried + " times of the providers " + retryHelper.providers
                            + " (" + retryHelper.providers.size() + "/" + retryHelper.copyInvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                            + Version.getVersion() + ". Last error is: "
                            + retryHelper.le.getMessage(), retryHelper.le.getCause() != null ? retryHelper.le.getCause() : retryHelper.le);
                    retryHelper.future.completeExceptionally(rpcException);
                }

            } else {
                if (retryHelper.le != null && logger.isWarnEnabled()) {
                    logger.warn("Although retry the method " + retryHelper.methodName
                            + " in the service " + getInterface().getName()
                            + " was successful by the provider " + invoker.getUrl().getAddress()
                            + ", but there have been failed providers " + retryHelper.providers
                            + " (" + retryHelper.providers.size() + "/" + retryHelper.copyInvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost()
                            + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                            + retryHelper.le.getMessage(), retryHelper.le);
                }
                retryHelper.future.complete(r);
            }
        });


    }

    private int calculateInvokeTimes(String methodName) {
        int len = getUrl().getMethodParameter(methodName, RETRIES_KEY, DEFAULT_RETRIES) + 1;
        RpcContext rpcContext = RpcContext.getContext();
        Object retry = rpcContext.getObjectAttachment(RETRIES_KEY);
        if (null != retry && retry instanceof Number) {
            len = ((Number) retry).intValue() + 1;
            rpcContext.removeAttachment(RETRIES_KEY);
        }
        if (len <= 0) {
            len = 1;
        }

        return len;
    }


    /**
     * 重试是一个调用完成再开始调用，通过CompletableFuture构成串行调用链路
     * ，所以RetryHelper是线程安全的
     * @param <T>
     */
    private static class RetryHelper<T> {
        int tried;
        int total;
        boolean retrying;
        RpcException le;
        Invocation invocation;
        LoadBalance loadbalance;
        List<Invoker<T>> copyInvokers;
        String methodName;
        List<Invoker<T>> invoked;
        Set<String> providers;
        CompletableFuture<AppResponse> future;

        public RetryHelper(int total,
                           Invocation invocation, LoadBalance loadbalance,
                           List<Invoker<T>> copyInvokers, String methodName,
                           List<Invoker<T>> invoked, Set<String> providers) {
            this.tried = 0;
            this.total = total;
            this.retrying = false;
            this.invocation = invocation;
            this.loadbalance = loadbalance;
            this.copyInvokers = copyInvokers;
            this.methodName = methodName;
            this.invoked = invoked;
            this.providers = providers;
        }

    }

}
