package com.axelkoolhaas.rusa.stat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URISyntaxException;
import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        CommandLineParser commandLineParser = new CommandLineParser(getMainJarName());
        commandLineParser.parseArgs(args);

        Jar jar = new Jar(commandLineParser.getJarPath());
        logger.info("Loading JAR.");
        jar.load();
        logger.info("Loaded.");

        // working debug
//        ClassNode cn = jar.getExtensions().get("com/axelkoolhaas/sqlirestapi/config/BasicAuthenticationConfig");
//        var e = jar.getExtensions();
//        var c = jar.getClasses();
//        var i = jar.getImplementations();
//        var r = jar.getRealImplementations();
//        var h = jar.getInheritors();

        CallGraph callgraph = new CallGraph(jar);
        logger.info("Constructing call graph.");
        callgraph.construct();

        logger.info("Printing dot call graph.");
        callgraph.printDotGraph("callgraph.dot");
//        callgraph.printDotGraph(null);

        logger.info("Calculating heuristic");
        callgraph.calculateDistance(commandLineParser.getTargets());

        logger.info("Printing JSON distance tree.");
        callgraph.saveJson("distance_tree.json");
    }

    public static String getMainJarName() {
        String jarPath;

        try {
            jarPath = getMainJarPath();
        } catch (Exception e) {
            logger.error("Failed to get Main JAR path");
            logger.error(e.getMessage());
            return "rusa";
        }

        return Paths.get(jarPath).getFileName().toString();
    }

    public static String getMainJarPath() throws NullPointerException, URISyntaxException {
        return Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
    }
}
