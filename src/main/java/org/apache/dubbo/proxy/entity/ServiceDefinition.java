package org.apache.dubbo.proxy.entity;

import lombok.Data;

@Data
public class ServiceDefinition {
    private ServiceConfig serviceConfig;
    private MethodConfig methodConfig;
}
