package com.axelkoolhaas.rusa.model.json;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class JsonDTO {
    @Getter
    private final String clazz;
    @Getter
    private final String method;
    @Getter
    private final int distance;
    private final List<String> state; // Future: Input2State correspondence, now unused

    public JsonDTO(String clazz, String method, int distance, List<String> state) {
        this.clazz = clazz;
        this.method = method;
        this.distance = distance;
        this.state = state;
    }

    public List<String> getState() {
        return Collections.unmodifiableList(this.state);
    }

    /* JSON example
    {
        "class":"com/axelkoolhaas/...",
        "method":"findArticle",
        "distance": 1,
        "state": ["param1", "param2", "..."]
    }
     */
}
