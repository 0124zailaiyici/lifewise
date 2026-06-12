package com.lifewise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "scene")
public class ScenePromptProperties {

    private String baseRule = "";

    private String followUpReturn = "";

    private Map<String, String> schemas = new HashMap<>();

    private Map<String, String> followUp = new HashMap<>();

    public String getBaseRule() {
        return baseRule;
    }

    public void setBaseRule(String baseRule) {
        this.baseRule = baseRule;
    }

    public String getFollowUpReturn() {
        return followUpReturn;
    }

    public void setFollowUpReturn(String followUpReturn) {
        this.followUpReturn = followUpReturn;
    }

    public Map<String, String> getSchemas() {
        return schemas;
    }

    public void setSchemas(Map<String, String> schemas) {
        this.schemas = schemas;
    }

    public String getSchema(String scene) {
        return schemas.getOrDefault(scene != null ? scene : "default", schemas.get("default"));
    }

    public Map<String, String> getFollowUp() {
        return followUp;
    }

    public void setFollowUp(Map<String, String> followUp) {
        this.followUp = followUp;
    }

    public String getFollowUpPrompt(String scene) {
        return followUp.getOrDefault(scene != null ? scene : "default", followUp.get("default"));
    }
}
