package com.mixpanel.mixpanelapi.featureflags.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.json.JSONObject;

import io.github.jamsesso.jsonlogic.JsonLogic;

/**
 * Wrapper for third-party library to evaluate JsonLogic DML rules.
 */
public class JsonLogicEngine {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JsonLogicEngine.class.getName());

    private static final JsonLogic jsonLogic = new JsonLogic();

    public static boolean evaluate(JSONObject rule, Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> lowercasedData = (Map<String, Object>) JsonCaseDesensitizer.lowercaseAllNodes(data);
        try {
            String ruleJson = JsonCaseDesensitizer.lowercaseLeafNodes(rule).toString();
            logger.log(Level.FINE, () -> "Evaluating JsonLogic rule: " + ruleJson + " with data: " + lowercasedData.toString());
            Object result = jsonLogic.apply(ruleJson, lowercasedData);
            return JsonLogic.truthy(result);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating runtime rule", e);
            return false;
        }
    } 
}
