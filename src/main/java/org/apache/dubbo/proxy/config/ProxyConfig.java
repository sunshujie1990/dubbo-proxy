package org.apache.dubbo.proxy.config;

import lombok.Data;

/**
 * <p> Description:
 * <p>
 * <p>
 * @author sunshujie 2022/4/18
 */
@Data
public class ProxyConfig {
    private Integer port;
    private String bind;
    /**
     *严格模式，只有mapping.service可以访问
      */
    private Boolean strict;

    /**
     * 链接上游服务超时时间
     */
    private Long connectionTimeOut;
}
