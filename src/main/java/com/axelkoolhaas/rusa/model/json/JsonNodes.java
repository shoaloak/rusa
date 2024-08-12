package com.axelkoolhaas.rusa.model.json;

import com.axelkoolhaas.rusa.model.CompositeNode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor
public class JsonNodes {
    @Getter
    private final List<JsonNode> classes = new LinkedList<>();

    public JsonNode findOrCreateNode(CompositeNode cn) {
        Optional<JsonNode> optionalTarget = this.getClasses()
                .stream()
                .filter(t -> t.getType().equals(cn.getType()) && t.getName().equals(cn.getInternalPath()))
                .findFirst();

        JsonNode jsonNode;
        if (optionalTarget.isEmpty()) {
            jsonNode = new JsonNode(cn.getType(), cn.getInternalPath());
            this.getClasses().add(jsonNode);
        } else {
            jsonNode = optionalTarget.get();
        }

//        return optionalTarget.orElseGet(() -> new Target(cn.getType(), cn.getInternalPath()));
        return jsonNode;
    }
}
