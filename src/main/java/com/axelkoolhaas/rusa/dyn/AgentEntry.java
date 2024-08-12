package com.axelkoolhaas.rusa.dyn;

import java.lang.instrument.Instrumentation;

public class AgentEntry {

    public static void agentmain(String arg, Instrumentation inst) {
        System.out.println("This functionality is currently not implemented.");
        System.out.println("Please use java -javaagent");
    }
}
