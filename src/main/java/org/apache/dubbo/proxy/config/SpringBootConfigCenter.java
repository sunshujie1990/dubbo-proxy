package org.apache.dubbo.proxy.config;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.proxy.entity.ServiceConfig;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
@Component
public class SpringBootConfigCenter implements ConfigCenter, ApplicationContextAware {

    private ApplicationConfig applicationConfig;
    private Map<String, ServiceConfig> serviceMapping;
    private ProxyConfig proxyConfig;
    private Binder binder;
    private static final String MAPPING_PREFIX = "mapping.services";
    private static final String PROXY_PREFIX = "proxy";
    private static final String APP_PREFIX = "application";

    @Override
    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    @Override
    public ApplicationConfig getApplicationConfig() {
        return applicationConfig;
    }

    @Override
    public Map<String, ServiceConfig> getServiceMapping() {
        return serviceMapping;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Environment environment = applicationContext.getEnvironment();
        this.binder = Binder.get(environment);
        makeApplicationConfig();
        makeServiceMapping();
        makeProxyConfig();
    }

    private void makeApplicationConfig() {
        ApplicationConfig applicationConfig =
                binder.bind(APP_PREFIX, Bindable.of(ApplicationConfig.class))
                        .get();
        this.applicationConfig = applicationConfig;
    }

    private void makeServiceMapping() {
        Map<String, ServiceConfig> serviceCoordinatorMap =
                binder.bind(MAPPING_PREFIX, Bindable.mapOf(String.class, ServiceConfig.class))
                .get();
        this.serviceMapping = serviceCoordinatorMap;
    }

    private void makeProxyConfig() {
        ProxyConfig proxyConfig =
                binder.bind(PROXY_PREFIX, Bindable.of(ProxyConfig.class))
                        .get();
        this.proxyConfig = proxyConfig;
    }
}
