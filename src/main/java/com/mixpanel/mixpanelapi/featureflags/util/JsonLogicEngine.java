package com.mixpanel.mixpanelapi.featureflags.util;

import java.util.Map;
import java.util.logging.Level;

import org.json.JSONObject;

import io.github.jamsesso.jsonlogic.JsonLogic;

public class JsonLogicEngine {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JsonLogicEngine.class.getName());

    public static boolean evaluate(JSONObject rule, Map<String, Object> data) {
        if (data == null) {
            data = Map.of();
        }
        data = (Map<String, Object>) JsonCaseDesensitizer.lowercaseAllNodes(data);
        JsonLogic jsonLogic = new JsonLogic();
        try {
            String ruleJson = JsonCaseDesensitizer.lowercaseLeafNodes(rule).toString();
            Object result = jsonLogic.apply(ruleJson, data);
            return JsonLogic.truthy(result);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating runtime rule", e);
            return false;
        }
    } 
}
