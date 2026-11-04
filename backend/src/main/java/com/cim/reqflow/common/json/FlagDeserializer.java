package com.cim.reqflow.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 0/1 标志位反序列化器。
 * 前端 Switch 组件提交布尔值、表格回显提交数字，两种形态都要能落到 Integer 字段上，
 * 统一在这里收敛，避免每个 Controller 各写一遍类型判断。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class FlagDeserializer extends JsonDeserializer<Integer> {

    /**
     * 把布尔、数字、数字字符串统一转成 0/1；null 与无法识别的值返回 null，交由业务层取默认值。
     *
     * @param parser  JSON 解析器
     * @param context 反序列化上下文
     * @return 1 / 0 / null
     * @throws IOException 读取 JSON 失败
     */
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        String text = parser.getValueAsString();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return Integer.valueOf(1);
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Integer.valueOf(0);
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
