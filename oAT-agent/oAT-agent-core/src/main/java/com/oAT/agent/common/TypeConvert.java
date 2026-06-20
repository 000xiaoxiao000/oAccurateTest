package com.oAT.agent.common;

import java.util.*;

public class TypeConvert {

    // int[] 转 ArrayList<Integer>
    public static ArrayList<Integer> toArrayList(int[] array) {
        if (array == null || array.length == 0) {
            return new ArrayList(0);
        }
        ArrayList<Integer> list = new ArrayList(array.length);
        for (int value : array) {
            list.add(value);
        }
        return list;
    }

    // int[] 转 List<Integer>
    public static List<Integer> toList(int[] array) {
        if (array == null || array.length == 0) return Collections.emptyList();
        List<Integer> list = new ArrayList(array.length);
        for (int value : array) {
            list.add(value);
        }
        return list;
    }

    //String 转 ArrayList<Integer>
    public static ArrayList<Integer> parseToIntList(String str) {
        ArrayList<Integer> result = new ArrayList();
        if (str == null || str.length() < 2) {
            return result;
        }

        // 手动解析而不是使用split
        int start = 1; // 跳过第一个'['
        int end = str.length() - 1; // 跳过最后一个']'

        while (start < end) {
            int comma = str.indexOf(',', start);
            if (comma < 0 || comma > end) {
                comma = end;
            }

            try {
                String numStr = str.substring(start, comma).trim();
                if (!numStr.isEmpty()) {
                    result.add(Integer.parseInt(numStr));
                }
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }

            start = comma + 1;
        }

        return result;
    }

    //String 转 Map<String, List<String>>
    public static Map<String, List<String>> convertStringToMap(String input) {
        Map<String, List<String>> map = new HashMap();
        if (input == null || !input.startsWith("{") || !input.endsWith("}")) {
            return map;
        }
        // Remove curly braces
        input = input.substring(1, input.length() - 1);
        // Split key-value pairs using regex to handle commas within values
        String[] pairs = input.split("(?<=]),");
        for (String pair : pairs) {
            // Check if the pair contains '='
            int equalIndex = pair.indexOf('=');
            if (equalIndex == -1) {
                continue; // Skip invalid pairs
            }

            // Split key and value
            String key = pair.substring(0, equalIndex).trim();
            String values = pair.substring(equalIndex + 1).trim();

            // Remove square brackets and split values
            values = values.substring(1, values.length() - 1);
            String[] valueArray = values.split(",");

            // Convert a value array to List<String>
            List<String> valueList = new ArrayList<String>(valueArray.length);
            for (String v : valueArray) {
                valueList.add(v.trim());
            }
            // Add a key-value pair to map
            map.put(key, valueList);
        }
        return map;
    }
}
