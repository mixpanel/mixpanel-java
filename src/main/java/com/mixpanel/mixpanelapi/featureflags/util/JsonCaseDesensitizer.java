package com.mixpanel.mixpanelapi.featureflags.util;

import java.util.Map;

public class JsonCaseDesensitizer {
    public static Object lowercaseLeafNodes(Object object) {
        if (object == null) {
            return null;
        }
        else if (object instanceof String){
            return ((String) object).toLowerCase();
        } else if (object instanceof org.json.JSONObject) {
            org.json.JSONObject jsonObject = (org.json.JSONObject) object;
            org.json.JSONObject result = new org.json.JSONObject();
            for (String key : jsonObject.keySet()) {
                result.put(key, lowercaseLeafNodes(jsonObject.get(key)));
            }
            return result;
        } else if (object instanceof org.json.JSONArray) {
            org.json.JSONArray jsonArray = (org.json.JSONArray) object;
            org.json.JSONArray result = new org.json.JSONArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.put(lowercaseLeafNodes(jsonArray.get(i)));
            }
            return result;
        } else {
            return object;
        }
    }
    public static Object lowercaseAllNodes(Object object) {
        if (object == null) {
            return null;
        }
        else if (object instanceof String){
            return ((String) object).toLowerCase();
        } else if (object instanceof Map) {
            // lowercase keys and values in map
            Map<?, ?> map = (Map<?, ?>) object;
            Map<Object, Object> result = new java.util.HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object lowerKey = entry.getKey() instanceof String
                    ? ((String) entry.getKey()).toLowerCase()
                    : entry.getKey();
                result.put(lowerKey, lowercaseAllNodes(entry.getValue()));
            }
            return result;
        } 
        // else if (object instanceof org.json.JSONObject) {
        //     org.json.JSONObject jsonObject = (org.json.JSONObject) object;
        //     org.json.JSONObject result = new org.json.JSONObject();
        //     for (String key : jsonObject.keySet()) {
        //         result.put(((String) key).toLowerCase(), lowercaseAllNodes(jsonObject.get(key)));
        //     }
        //     return result;
        // } else if (object instanceof org.json.JSONArray) {
        //     org.json.JSONArray jsonArray = (org.json.JSONArray) object;
        //     org.json.JSONArray result = new org.json.JSONArray();
        //     for (int i = 0; i < jsonArray.length(); i++) {
        //         result.put(lowercaseAllNodes(jsonArray.get(i)));
        //     }
        //     return result;
        // }
         else {
            return object;
        }
    }
}
