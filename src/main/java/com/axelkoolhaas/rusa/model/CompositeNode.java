package com.axelkoolhaas.rusa.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A method node together with its class node.
 */
@Builder
public class CompositeNode {
    // Fields
    @Builder.Default
    private List<CompositeNode> callers = new ArrayList<>(); // parents
    @Builder.Default
    private List<CompositeNode> callees = new ArrayList<>(); // children
    @Getter
    private ExtendedClassNode owner;
    @Getter
    private MethodNode method;
    @Getter
    private boolean root;
    @Getter
    @Setter
    private Integer distance; // null = unreachable

    // Getters and setters
    public String getInternalPath() {
        if (this.owner==null) {
            return "root";
        }

        return owner.name.replace('/', '.');
    }

    public String getType() {
        if (this.owner==null) {
            return "root";
        }

        String typeName = this.owner.name.substring(this.owner.name.lastIndexOf('/') + 1);
        if (this.owner.isInterface()) {
            typeName = "(I)" + typeName;
        }

        return typeName;
    }

    public List<CompositeNode> getCallers() {
        return Collections.unmodifiableList(this.callers);
    }

    public void addCaller(CompositeNode caller) {
        this.callers.add(caller);
    }

    public List<CompositeNode> getCallees() {
        return Collections.unmodifiableList(this.callees);
    }

    public void addCallee(CompositeNode callee) {
        this.callees.add(callee);
    }

    public void addCallees(Collection<CompositeNode> callees) {
        this.callees.addAll(callees);
    }

    // Methods
    public String print() {
        if (this.owner==null) {
            return "root";
        }

        return this.owner.name + "::" + this.method.name;
    }

    public boolean isInterface() {
        if (this.owner == null) {
            return false;
        }
        return this.owner.isInterface();
    }

    public String printAbbrev() {
        if (this.owner==null) {
            return "root";
        }

        String typeName = this.owner.name.substring(this.owner.name.lastIndexOf('/') + 1);
        if (this.owner.isInterface()) {
            typeName = "(I)" + typeName;
        }

        return typeName + "::" + this.method.name;
    }
}
