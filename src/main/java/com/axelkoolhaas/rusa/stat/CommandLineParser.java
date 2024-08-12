package com.axelkoolhaas.rusa.stat;

import com.axelkoolhaas.rusa.model.cmd.CommandLineTarget;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CommandLineParser {
    // Constants
    private static final Set<String> FILE_FLAG = Set.of("-f", "--file");
    private static final Set<String> TARGET_FLAG = Set.of("-t", "--target");

    // Fields
    private final Logger logger = LogManager.getLogger(CommandLineParser.class);
    private final String selfJarPath;
    @Getter
    private String jarPath;
    private final List<CommandLineTarget> targets;

    // Getters and setters
    private void setJarPath(String jarPath) {
        if (!this.jarPath.equals("")) {
            logger.warn("JAR path already specified, last one will be used.");
        }
        this.jarPath = jarPath;
    }

    private void addTarget(String target) {
        String[] parts = target.split(":");

        // Validate the input
//        if (parts.length != 3) {
        if (parts.length != 2) {
            logger.error("Not enough : arguments for " + TARGET_FLAG + " target flag use.");
            System.exit(1);
        }
        for (String part : parts) {
            if (part.length() == 0) {
                logger.error("Empty argument for " + TARGET_FLAG + " flag use.");
                System.exit(1);
            }
        }
//        targets.add(new CommandLineTarget(parts[0], parts[1], parts[2]));
        targets.add(new CommandLineTarget(parts[0], parts[0].replace('.','/'), parts[1]));
    }

    public List<CommandLineTarget> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    // Constructor
    public CommandLineParser(String selfJarPath) {
        this.selfJarPath = selfJarPath;
        this.jarPath =  "";
        this.targets = new ArrayList<>();
    }

    // Methods
    public void parseArgs(String[] args) {
        // first do some basic checks
        if (args == null || args.length < 4) {
            System.out.printf("%s usage:\n", selfJarPath);
            System.out.printf("""
                    Statically:\trun and supply JAR file and target method arguments
                    e.g. java --jar %s %s app.jar %s fully.qualified.class.name:targetMethod
                    """, selfJarPath, FILE_FLAG, TARGET_FLAG);
//                    e.g. java --jar %s %s app.jar %s fully.qualified.class.name:targetMethod:lineNumber
            System.out.printf("Dynamically:\t" +
                    "attach as agent when running JAR: java -javaagent:%s app.jar\n", selfJarPath);
            System.exit(1);
        }

        // Loop through the arguments and parse them
        for (int i = 0; i < args.length; i++) {
            if (FILE_FLAG.contains(args[i])) {
                // Parse the JAR argument
                if (i + 1 < args.length) {
                    setJarPath(args[i + 1]);
                } else {
                    logger.error("Missing argument for {} flag use.", FILE_FLAG);
                }
            } else if (TARGET_FLAG.contains(args[i])) {
                // Parse the -t argument
                if (i + 1 < args.length) {
                    addTarget(args[i + 1]);
                } else {
                    logger.error("Missing argument for {} flag use.", TARGET_FLAG);
                }
            }
        }

        // ensure we have the minimum requirements
        if (jarPath.equals("") || targets.size() == 0) {
            System.err.printf("""
                    Error: Missing required arguments.
                    e.g. java --jar %s %s app.jar %s fully.qualified.class.name:targetMethod
                    """, selfJarPath, FILE_FLAG, TARGET_FLAG);
            System.exit(1);
        }
    }
}
