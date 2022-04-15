package org.apache.dubbo.proxy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MethodConfig {
    private String methodName;
    private Map<String, String> attachments;
    private Object[] paramValues;
    private String[] paramTypes;
}
