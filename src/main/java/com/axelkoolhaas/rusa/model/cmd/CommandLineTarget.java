package com.axelkoolhaas.rusa.model.cmd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class CommandLineTarget {
    @Getter
    String className;
    @Getter
    String internalClassName;
    @Getter
    String methodName;
//    @Getter
//        Long lineNumber;
}
