package com.axelkoolhaas.rusa.dyn;

import com.axelkoolhaas.rusa.AnalysisAssistant;
import com.axelkoolhaas.rusa.FileUtil;
import com.axelkoolhaas.rusa.dyn.advice.MethodTracerAdvice;
import com.axelkoolhaas.rusa.model.json.JsonMethod;
import com.axelkoolhaas.rusa.model.json.JsonNode;
import com.axelkoolhaas.rusa.model.json.JsonNodes;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.axelkoolhaas.rusa.PrintHelper.RUSA_LOGO;

public class PreEntry {
    public static final String DISTANCE_TREE = "distanceTree";
    public static final String MODE = "mode";
    public static final String RESULTS_PATH = "resultsPath";

    private static final Logger logger = LogManager.getLogger(PreEntry.class);

    /**
     * This is the entry point for the agent.
     * It is called by the JVM when the agent is loaded.
     * @param arg The path to the target.json file
     * @param inst The instrumentation object
     */
    public static void premain(String arg, Instrumentation inst) throws IOException {
        Map<String,String> settingsMap = setSettings(arg);

        JsonNodes jsonCG = FileUtil.readJsonCG(settingsMap.get(DISTANCE_TREE));
        final List<String> classTargets = PreEntry.getClassTargets(jsonCG);
        final List<String> methodTargets = PreEntry.getMethodTargets(jsonCG);
        final Map<String, List<JsonMethod>> targets = PreEntry.getTargets(jsonCG);

        CallRecorder.setInstance(targets, settingsMap);

        AgentBuilder agentBuilder = new AgentBuilder.Default()
                // access to JVM internals
//                .LambdaInstrumentationStrategy.LambdaMetafactoryFactory.Loader.UsingUnsafe()

                // no longer possible to rebase a class, but does allow retransformation
                // i.e. change already loaded classes' bytecode with no new method limit
                // This is a restriction in premain, but maybe better in the future since we can run faster and do more.
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)

                // Transforming a class is a costly operation, so exclude rusa from instrumentation
                // Byte Buddy by default ignores
                //      itself, so "net.bytebuddy."
                //      any synthetic method (bridge methods are handled automatically)
                //      the Object.finalize() method
                .ignore(ElementMatchers.nameStartsWith(AnalysisAssistant.PACKAGE_PREFIX))
//                        .or(ElementMatchers.nameStartsWith("org.springframework")))

                // we instrument only classes with distance
                .type(ElementMatchers.namedOneOf(classTargets.toArray(new String[0])))

                // declare actual transformer
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
//                        .visit(Advice.to(MethodTracer.class).on(ElementMatchers.isMethod())))
                        // (TODO maybe use different advice for constructors)
                        .visit(Advice.to(MethodTracerAdvice.class)
                                .on(ElementMatchers.namedOneOf(methodTargets.toArray(new String[0]))))
                        );
//                        .method(method -> method.getDeclaredAnnotations().isAnnotationPresent(MyCustomAnnotation.class))
//                        .visit(Advice.to(MethodTracer.class).on(MethodDescription::isMethod)))
//                        .method(ElementMatchers.any())
//                        .intercept(Advice.to(MethodTracer.class)))
//                .with(new TracerListener);


        System.out.print(RUSA_LOGO);
        if (settingsMap.get(MODE).equals("synergy")) {
            Thread thread = new Thread(ZmqServer.getInstance());
            thread.start();
        } else {
            logger.info("Running in standalone mode, no ZMQ server started");
        }
        agentBuilder.installOn(inst);

        // this will be reached immediately
    }

    /**
     * Returns a list of all classes that have a method with distance
     */
    public static List<String> getClassTargets(JsonNodes jn) {
        return jn.getClasses()
                .stream()
                .filter(jsonNode -> jsonNode.getMethods()
                        .stream()
                        .anyMatch(jsonMethod -> jsonMethod.getDistance() != null))
                .map(JsonNode::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all methods with distance
     */
    public static List<String> getMethodTargets(JsonNodes jn) {
        return jn.getClasses()
                .stream()
                .flatMap(node -> node.getMethods()
                        .stream()
                        .filter(jsonMethod -> jsonMethod.getDistance() != null))
                .map(JsonMethod::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns a map of all classes with methods with distance
     */
//    public static Map<String,List<String>> getTargets(JsonNodes jn) {
    public static Map<String,List<JsonMethod>> getTargets(JsonNodes jn) {
        return jn.getClasses()
                .stream()
                .filter(jsonNode -> jsonNode.getMethods()
                        .stream()
                        .anyMatch(jsonMethod -> jsonMethod.getDistance() != null))
                .collect(Collectors.toMap(JsonNode::getName, JsonNode::getMethods)
                );
    }

    public static Map<String,String> setSettings(String arg) {
        final String usage = String.format("""
                Usage:
                java -javaagent:rusa.jar=args -jar restWebApp.jar
                where args is a comma-separated list of arguments:
                    %s=<tree>.json\t# default is distance_tree.json
                    %s=<synergy,standalone>\t# default is synergy, standalone to operate without Rusa frontend
                    %s=<path>.result\t# optional file to store results
                """, DISTANCE_TREE, MODE, RESULTS_PATH);

        Map<String,String> argumentsMap = new HashMap<>();

        // Default values
        argumentsMap.put(DISTANCE_TREE, "distance_tree.json");
        argumentsMap.put(MODE, "synergy");

        // If no arguments are given, return default values
        if (arg == null) {
            System.out.print(usage);
            return argumentsMap;
        }

        // Parse arguments
        try {
            argumentsMap.putAll(Arrays.stream(arg.split(","))
                    .map(s -> s.split("="))
                    .collect(Collectors.toMap(s -> s[0], s -> s[1])));
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.print(usage);
            System.exit(1);
        }

        // Check if the distance tree argument has a valid path
        if (!FileUtil.isValidFilePath(argumentsMap.get(DISTANCE_TREE))) {
            logger.error("Invalid path for distance tree");
            System.exit(1);
        }

        // Check if the optional results file argument has a valid path
        if (argumentsMap.containsKey(RESULTS_PATH)) {
            if (!FileUtil.isValidFilePath(argumentsMap.get(RESULTS_PATH))) {
                logger.error("Invalid path for results file");
                System.exit(1);
            }

            // Create the results file
            FileUtil.touch(argumentsMap.get(RESULTS_PATH));
        }

        return argumentsMap;
    }
}
