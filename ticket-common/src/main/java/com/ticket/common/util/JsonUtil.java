package com.ticket.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具（基于 Jackson 单例），避免在多处重复 new ObjectMapper。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
