package org.apache.dubbo.proxy.entity;

import lombok.Data;
import org.apache.dubbo.config.ReferenceConfig;

import java.util.Map;

@Data
public class ServiceMapping {
    private Map<String, ReferenceConfig> mapping;
}
