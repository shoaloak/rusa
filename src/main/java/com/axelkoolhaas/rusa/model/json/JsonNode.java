package com.axelkoolhaas.rusa.model.json;

import com.axelkoolhaas.rusa.model.CompositeNode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.LinkedList;
import java.util.Optional;

@RequiredArgsConstructor
public class JsonNode {
    @NonNull
    @Getter
    private String type; // class | interface
    @NonNull
    @Getter
    private String name; // full name

    @Getter
    private final List<JsonMethod> methods = new LinkedList<>();

    public JsonMethod findOrCreateMethod(CompositeNode compositeNode) {
        MethodNode mn = compositeNode.getMethod();
        Optional<JsonMethod> optionalMethod = this.getMethods()
                .stream()
                .filter(m -> m.getName().equals(mn.name))
                .findFirst();

        JsonMethod method;
        if (optionalMethod.isEmpty()) {
            method = new JsonMethod(mn.name,
                    compositeNode.getDistance() == null ? null : compositeNode.getDistance());
            this.getMethods().add(method);
        } else {
            method = optionalMethod.get();
        }

        return method;
    }
}
