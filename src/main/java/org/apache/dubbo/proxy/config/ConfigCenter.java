package org.apache.dubbo.proxy.config;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.proxy.entity.ServiceConfig;

import java.util.Map;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
public interface ConfigCenter {

    /**
     * 获取代理基本配置信息
     * @return
     */
    ProxyConfig getProxyConfig();

    /**
     * 获取dubboproxy的dubbo application配置
     * @return
     */
    ApplicationConfig getApplicationConfig();

    /**
     * 获取服务映射信息
     * @return
     */
    Map<String, ServiceConfig> getServiceMapping();
}
