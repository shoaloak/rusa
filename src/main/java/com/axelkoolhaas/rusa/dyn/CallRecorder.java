package com.axelkoolhaas.rusa.dyn;

import com.axelkoolhaas.rusa.FileUtil;
import com.axelkoolhaas.rusa.model.json.JsonDTO;
import com.axelkoolhaas.rusa.model.json.JsonMethod;
import com.google.gson.Gson;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CallRecorder {
    private static final Logger logger = LogManager.getLogger(CallRecorder.class);
    private static final Gson gson = new Gson();
    private static CallRecorder instance;
    // A map of class names to a list of method names which should have distance
    @Getter
    private final Map<String, List<JsonMethod>> targets;
    @Getter
    private final Map<String, String> settingsMap;

    private CallRecorder(Map<String, List<JsonMethod>> targets, Map<String,String> settingsMap) {
        this.targets = targets;
        this.settingsMap = settingsMap;
    }

    public static CallRecorder getInstance() {
        if (instance == null) {
            logger.error("Set CallRecorder before usage.");
            System.exit(1);
        }
        return instance;
    }

    public static void setInstance(Map<String, List<JsonMethod>> targets, Map<String,String> settingsMap) {
        if (instance != null) {
            logger.error("CallRecorder already set.");
            System.exit(1);
        }
        instance = new CallRecorder(targets, settingsMap);
    }

    public static void beforeMethod(String type, String method) {
        logger.debug("hit: " + type + " " + method);

        // Solution for AsmVisitorWrapper inaccuracy
        Map<String, List<JsonMethod>> targets = CallRecorder.getInstance().getTargets();
        // Verify if targets contains type and method
        JsonMethod target = targets != null ? targets.getOrDefault(type, Collections.emptyList())
                .stream()
                .filter(m -> m.getName().equals(method))
                .findFirst()
                .orElse(null) : null;

        if (target == null) {
            // not an actual target
            return;
        }

//        ZmqServer.getInstance().publish(type + ":" + method);
//        System.out.println(target.getDistance());
//        ZmqServer.getInstance().publish(target.getDistance());

        // Retrieve settings
        Map<String, String> settingsMap = CallRecorder.getInstance().getSettingsMap();
        String mode = settingsMap.get(PreEntry.MODE);
        String resultsPath = settingsMap.get(PreEntry.RESULTS_PATH);

        // Send/store CFG feedback
        JsonDTO jsonDTO = new JsonDTO(type, method, target.getDistance(), null);
        String json = gson.toJson(jsonDTO);

        switch (mode) {
            case "synergy":
                ZmqServer.getInstance().addFeedback(jsonDTO);
                break;

            case "standalone":
                logger.info(json);
                break;

            default:
                logger.error("Unrecognized mode: {}", mode);
                System.exit(1);
                break;
        }

        // Write to file if specified
        if (resultsPath != null) {
            try {
                FileUtil.appendText(resultsPath, json, true);
            } catch (Exception e) {
                logger.error("Could not write to {}: {}", resultsPath, e.getMessage(), e);
                System.exit(1);
            }
        }
    }

    public static void afterMethod() {

    }
}
