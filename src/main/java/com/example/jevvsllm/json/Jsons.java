package com.example.jevvsllm.json;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Jsons {

    private final JsonMapper jsonMapper;

    public Jsons(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public JsonNode readTree(String json) {
        return jsonMapper.readTree(json);
    }

    public String write(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    public byte[] writeBytes(Object value) {
        return jsonMapper.writeValueAsBytes(value);
    }

    public <T> T read(String json, Class<T> type) {
        return jsonMapper.readValue(json, type);
    }

    public Object toJava(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                map.put(entry.getKey(), toJava(entry.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode child : node) {
                list.add(toJava(child));
            }
            return list;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asString();
    }
}
