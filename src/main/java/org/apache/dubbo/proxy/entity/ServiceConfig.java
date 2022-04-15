package org.apache.dubbo.proxy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p> Description:
 * <p>
 * <p>
 * @author sunshujie 2022/4/14
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceConfig {
    private String interfaceName;
    private String group;
    private String version;
    private Integer timeout;
    private Integer retries;
    private String tag;
    private Integer actives;
    private String protocol;
    private String cluster;
    private String owner;
    private String url;
    private String loadbalance;
    private Integer connections;
}
