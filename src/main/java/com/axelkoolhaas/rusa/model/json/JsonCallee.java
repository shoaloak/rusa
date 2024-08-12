package com.axelkoolhaas.rusa.model.json;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JsonCallee {
    @NonNull
    private String name;
    @NonNull
    private String method;
}
