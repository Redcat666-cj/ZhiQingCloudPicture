package com.zhiqin.zhiqinpicturebackend.collaboration.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 协同编辑消息的统一序列化工具。
 *
 * <p>重点：Long（尤其是雪花 id）必须序列化成字符串。
 * 前端 JS 的 Number 只有 53 位精度，19 位的雪花 id 直接当数字传过去会丢精度
 * （2107004585664872449 → 2107004585664872500），前后端再按 id 比较就会对不上。</p>
 *
 * <p>所以广播、错误回执、状态同步都必须走同一个 ObjectMapper，
 * 不能有的地方用 Jackson、有的地方用 hutool（hutool 默认把 Long 输出成数字）。</p>
 */
public final class PictureEditMessageJson {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper.registerModule(module);
        OBJECT_MAPPER = mapper;
    }

    private PictureEditMessageJson() {
    }

    public static String toJson(Object message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("协同编辑消息序列化失败", e);
        }
    }
}
