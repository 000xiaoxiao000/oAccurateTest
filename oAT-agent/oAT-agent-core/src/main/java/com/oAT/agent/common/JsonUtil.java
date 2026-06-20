package com.oAT.agent.common;

import com.oAT.agent.common.json.JsonObject;
import com.oAT.agent.common.json.JsonReader;
import com.oAT.agent.common.json.JsonWriter;

import java.util.HashMap;
import java.util.Map;

public class JsonUtil {
    public static String toJson(Object obj) {
        Map<String, Object> item = new HashMap();
        item.put("TYPE", false); // 生成@type属性
        item.put(JsonWriter.SKIP_NULL_FIELDS, true);
        return JsonWriter.objectToJson(obj, item);
    }

    /**
     * jsonText 必须由 json-io 工具类生成 并且包含@type 属性
     *
     * @param tClass
     * @param jsonText
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T toObject(String jsonText, Class<T> tClass) {
        Object obj = JsonReader.jsonToJava(jsonText);
        if (obj instanceof JsonObject && !Map.class.isAssignableFrom(tClass)) {
            ((Map) obj).put("@type", tClass.getName());
            return (T) JsonReader.jsonToJava(toJson(obj));
        }
        return (T) obj;
    }
}
