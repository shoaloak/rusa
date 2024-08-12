package com.axelkoolhaas.rusa.model.json;

import com.axelkoolhaas.rusa.model.CompositeNode;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

//@RequiredArgsConstructor
public class JsonMethod {
//    @NonNull
    @Getter
    private final String name;
//    @NonNull
    @Getter
    private final Integer distance;
//    @Getter
//    private final Integer hits; // TODO graph overlay. Update: not sure what I meant here?

    public JsonMethod(String name, Integer distance) {
        this.name = name;
        this.distance = distance;
//        this.hits = hits;
    }

    @Getter
    private final List<JsonCallee> calls = new LinkedList<>();


    public void addCallees(CompositeNode parent) {
        parent.getCallees()
                .forEach(cn ->
                        this.calls.add(new JsonCallee(cn.getInternalPath(), cn.getMethod().name))
                );
    }

}
