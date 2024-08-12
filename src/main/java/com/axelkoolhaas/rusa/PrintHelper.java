package com.axelkoolhaas.rusa;

public class PrintHelper {

    public static final String RUSA_LOGO = """
                 /|     |\\
              `__\\\\     //__´
                 ||     ||
              \\__`\\     /´__/
                `_\\\\   //_´     ____
                _.,:---;,._    |  _ \\ _   _ ___  __ _
                \\_:    :_/     | |_) | | | / __|/ _` |
                  |O` ´O|      |  _ <| |_| \\__ \\ (_| |
                  |     |      |_| \\_\\\\__,_|___/\\__,_|
                  ,\\.-./,      =======================
                 ;  `-´  ;     Rusa Fuzzer      (v0.1)
            """;

    public static void line(char c) {
        System.out.println(new String(new char[80]).replace('\0', c));
    }
}
