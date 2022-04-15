package org.apache.dubbo.proxy.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String writeValueAsString(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] writeValueAsBytes(Object object) {
        try {
            return MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String jsonString, Class<T> type) {
        try {
            if (StringUtils.isEmpty(jsonString)) {
                return null;
            }
            return MAPPER.readValue(jsonString, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(byte[] jsonBytes, Class<T> type) {
        try {
            if (StringUtils.isEmpty(jsonBytes)) {
                return null;
            }
            return MAPPER.readValue(jsonBytes, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
