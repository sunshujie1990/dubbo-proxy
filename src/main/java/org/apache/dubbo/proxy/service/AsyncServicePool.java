package org.apache.dubbo.proxy.service;

import org.apache.dubbo.proxy.entity.ServiceConfig;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.concurrent.CompletableFuture;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
public interface AsyncServicePool {
    /**
     * 异步非阻塞获取一个service
     * @param coordinator
     * @return
     */
    CompletableFuture<GenericService> getAsync(ServiceConfig coordinator);
}


