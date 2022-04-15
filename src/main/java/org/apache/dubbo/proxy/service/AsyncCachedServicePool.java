package org.apache.dubbo.proxy.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.proxy.config.ConfigCenter;
import org.apache.dubbo.proxy.entity.ServiceConfig;
import org.apache.dubbo.proxy.utils.TimeoutFuture;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p> Description:
 * <p>  服务缓存池
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
@Component
public class AsyncCachedServicePool implements AsyncServicePool {

    private final ConfigCenter configCenter;

    /**
     * mapping.services 通过配置配的服务，服务启动直接初始化好
     */
    private Map<ServiceConfig, ReferenceConfig<GenericService>> mappingService;

    /**
     * mapping.services 之外的服务使用缓存，避免服务太多占用大量资源
     */
    private Cache<ServiceConfig, ReferenceConfig<GenericService>> serviceCache;

    private ThreadPoolExecutor serviceReferenceExecutor;

    @Autowired
    public AsyncCachedServicePool(ConfigCenter configCenter) {
        this.configCenter = configCenter;
    }

    @PostConstruct
    public void init() {
        serviceReferenceExecutor = new ThreadPoolExecutor(1,
                10, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new NamedThreadFactory("service-reference"));
        serviceCache = CacheBuilder
                .newBuilder()
                .expireAfterAccess(60 * 60, TimeUnit.SECONDS)
                .concurrencyLevel(Runtime.getRuntime().availableProcessors() * 2)
                .maximumSize(1000)
                .removalListener(new RemovalListener<ServiceConfig, ReferenceConfig<GenericService>>() {
                    /**
                     * ReferenceConfig保持了到服务端的链接，是个很重的对象，要注意销毁
                     * @param notification
                     */
                    @Override
                    public void onRemoval(RemovalNotification<ServiceConfig, ReferenceConfig<GenericService>> notification) {
                        notification.getValue().destroy();
                    }
                })
                .build();

        mappingService = new HashMap<>();
        Map<String, ServiceConfig> serviceMapping = configCenter.getServiceMapping();
        for (ServiceConfig serviceConfig : serviceMapping.values()) {
            ReferenceConfig<GenericService> referenceConfig = initReference(serviceConfig);
            mappingService.put(serviceConfig, referenceConfig);
        }
    }

    @PreDestroy
    public void destroy() {
        for (ReferenceConfig<GenericService> services : mappingService.values()) {
            services.destroy();
        }

        serviceCache.invalidateAll();
    }

    @Override
    public CompletableFuture<GenericService> getAsync(ServiceConfig coordinator) {
        ReferenceConfig<GenericService> service = mappingService.get(coordinator);
        if (service != null) {
            return CompletableFuture.completedFuture(service.get());
        }

        service = serviceCache.getIfPresent(coordinator);
        if (service != null) {
            return CompletableFuture.completedFuture(service.get());
        } else {
            return asyncLoad(coordinator);
        }
    }

    private CompletableFuture<GenericService> asyncLoad(ServiceConfig coordinator) {
        final TimeoutFuture<GenericService> future = new TimeoutFuture<>(configCenter.getProxyConfig().getConnectionTimeOut());
        serviceReferenceExecutor.execute(() -> {
            try {
                ReferenceConfig<GenericService> service = serviceCache.get(coordinator, new Callable<ReferenceConfig<GenericService>>() {
                    @Override
                    public ReferenceConfig<GenericService> call() throws Exception {
                        ReferenceConfig<GenericService> reference = initReference(coordinator);
                        reference.get();
                        return reference;
                    }
                });
                future.complete(service.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("创建服务失败：" + coordinator, e);
            }

        });
        return future;
    }

    private ReferenceConfig<GenericService> initReference(ServiceConfig coordinator) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setApplication(configCenter.getApplicationConfig());
        reference.setGroup(coordinator.getGroup());
        reference.setVersion(coordinator.getVersion());
        reference.setInterface(coordinator.getInterfaceName());
        reference.setGeneric("true");
        reference.setAsync(true);
        reference.setCheck(false);
        reference.setRetries(coordinator.getRetries());
        reference.setTimeout(coordinator.getTimeout());
        reference.setTag(coordinator.getTag());
        reference.setActives(coordinator.getActives());
        reference.setProtocol(coordinator.getProtocol());
        reference.setCluster(coordinator.getCluster());
        reference.setOwner(coordinator.getOwner());
        reference.setUrl(coordinator.getUrl());
        reference.setLoadbalance(coordinator.getLoadbalance());
        reference.setConnections(coordinator.getConnections());
        return reference;
    }
}
