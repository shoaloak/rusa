package com.axelkoolhaas.rusa.dyn.coverage;

import java.util.Map;

public interface CodeCoverageStore {

    abstract void increment(String type, String methodName);
    abstract Map<String, Integer> getCoverage();
    abstract void reset();
}