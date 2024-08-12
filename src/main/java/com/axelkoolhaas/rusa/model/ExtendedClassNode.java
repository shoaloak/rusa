package com.axelkoolhaas.rusa.model;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class ExtendedClassNode extends ClassNode {

    public ExtendedClassNode(int api) {
        super(api);
    }

    public boolean isInterface() {
        return (this.access & Opcodes.ACC_INTERFACE) != 0;
    }

    public boolean isClass() {
        return (this.access & Opcodes.ACC_INTERFACE) == 0;
    }
}
