package com.axelkoolhaas.rusa.dyn.advice;

import com.axelkoolhaas.rusa.dyn.CallRecorder;
import net.bytebuddy.asm.Advice;

import java.util.List;

public class MethodTracerAdvice {


    @Advice.OnMethodEnter(
            inline = false // false allows for breakpoints, but lose access to target state TODO: do we gain access to our own state?
    )
    /*                            @Advice.Argument(0) String Template_of_first_parameter
     *                            @Advice.Return(readOnly = false) String ret // allows for modification of ret val
     * */
    public static void enter(
            @Advice.Origin("#t") String type,
            @Advice.Origin("#m") String method,
            @Advice.Origin("#d") String desc,
            @Advice.Origin("#s") String signature,
            @Advice.Origin("#r") String retType,
//            @Advice.Origin("#p") String propName, // breaks, no stacktrace though
            @Advice.AllArguments Object[] args) {

//        System.out.println("Entering method...");

        /* reference:
        type: com.axelkoolhaas.sqlirestapi.controller.ArticleController; method: findArticle; signature: (java.lang.String)
        desc: (Ljava/lang/String;)Lcom/axelkoolhaas/sqlirestapi/model/Article;; return: com.axelkoolhaas.sqlirestapi.model.Article
        no. args: 1
         */
//        System.out.printf("type: %s; method: %s; signature: %s\n", type, method, signature);
//        System.out.printf("desc: %s; return: %s\n", desc, retType);
//        System.out.println("no. args: " + args.length);

        CallRecorder.beforeMethod(type, method);
    }

    // todo catch exception
    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
//    @Advice.OnMethodExit(onThrowable = Throwable.class)
//    public static void exit(@Advice.Enter StackItem item) {
    public static void exit() {
//        System.out.println("exiting method...");
    }
}
