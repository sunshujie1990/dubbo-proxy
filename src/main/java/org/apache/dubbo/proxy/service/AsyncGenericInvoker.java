package org.apache.dubbo.proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.proxy.entity.MethodConfig;
import org.apache.dubbo.proxy.entity.ServiceDefinition;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class AsyncGenericInvoker {


    private final AsyncServicePool asyncServicePool;

    @Autowired
    public AsyncGenericInvoker(AsyncServicePool asyncServicePool) {
        this.asyncServicePool = asyncServicePool;
    }

    public CompletableFuture<Object> genericCall(ServiceDefinition serviceDefinition) {
        CompletableFuture<GenericService> serviceFuture = asyncServicePool.getAsync(serviceDefinition.getServiceConfig());
        MethodConfig methodConfig = serviceDefinition.getMethodConfig();
        return serviceFuture.thenCompose(svc -> {
            log.info("dubbo generic invoking: {}", serviceDefinition);
            RpcContext.getContext().setAttachments(methodConfig.getAttachments());
            svc.$invoke(methodConfig.getMethodName(),
                    methodConfig.getParamTypes(), methodConfig.getParamValues());
            return RpcContext.getContext().getCompletableFuture();
        });
    }

}
