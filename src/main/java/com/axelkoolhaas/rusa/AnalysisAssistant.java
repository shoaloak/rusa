package com.axelkoolhaas.rusa;

import org.objectweb.asm.tree.*;

import java.util.ListIterator;
import java.util.Optional;

public class AnalysisAssistant {

    // Used to avoid instrumenting ourselves.
    public static final String PACKAGE_PREFIX = "com.axelkoolhaas.rusa";

    // Evade Spring NullPointerException when parsing
    public static final String SPRING_WEB_MVC = "org/springdoc/webmvc";

    public static final String foo = null;



    /** get line number based on LineNumbersTable Attribute
     * Do note that LineNumberTable attributes need not be one-to-one with source lines.
     */
    public static Optional<LineNumberNode> findLineNumber(InsnList insnList, AbstractInsnNode insnNode) {
        int idx = insnList.indexOf(insnNode);

        // Get index of labels and insnNode within method
        ListIterator<AbstractInsnNode> insnIt = insnList.iterator(idx);
        while (insnIt.hasPrevious()) {
            AbstractInsnNode node = insnIt.previous();

            if (node.getType() ==  AbstractInsnNode.LINE) {
                return Optional.of((LineNumberNode) node);
            }
        }

        return Optional.empty();
    }
}
