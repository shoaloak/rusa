package com.axelkoolhaas.rusa;

import com.axelkoolhaas.rusa.model.json.JsonNodes;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;

public class FileUtil {
    private static final Logger logger = LogManager.getLogger(FileUtil.class);

    public static void saveObject(final String path, Object obj) throws IOException {
        try (FileOutputStream fout = new FileOutputStream(path);
             ObjectOutputStream oos = new ObjectOutputStream(fout)) {
            oos.writeObject(obj);
        }
    }

    public static Object loadObject(final String path) throws IOException, ClassNotFoundException {
        try (FileInputStream fin = new FileInputStream(path);
             ObjectInputStream ois = new ObjectInputStream(fin)) {
            return ois.readObject();
        }
    }

    public static void saveText(final String path, String data) throws IOException {
        try (FileWriter writer = new FileWriter(path);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            bufferedWriter.write(data);
        }
    }

    public static String loadText(final String path) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();

        try (FileReader reader = new FileReader(path);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                contentBuilder.append(line).append(System.lineSeparator());
            }
        }

        return contentBuilder.toString();
    }

    public static void appendText(final String path, String data, boolean flush) throws IOException {
        try (FileWriter writer = new FileWriter(path, true);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            bufferedWriter.write(data);
            bufferedWriter.newLine(); // Optionally add a newline after appending
            if (flush) {
                bufferedWriter.flush();
            }
        }
    }

    public static boolean isValidFilePath(final String inputPath) {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            return false;
        }

        // Remove quotes from the path if present
        String sanitizedPath = inputPath.replace("\"", "").trim();

        // make sure it's a valid path
        Path resolvedPath;
        try {
            resolvedPath = Paths.get(sanitizedPath);
        } catch (InvalidPathException | NullPointerException e) {
            return false;
        }

        if (Files.isDirectory(resolvedPath)) {
            return false;
        }

        // Existing file
        if (Files.exists(resolvedPath)) {
            return Files.isRegularFile(resolvedPath) && Files.isWritable(resolvedPath);
        }

        // Non-existing file
        Path parentPath = resolvedPath.getParent();
        if (parentPath != null) {
            return Files.exists(parentPath) && Files.isDirectory(parentPath) && Files.isWritable(parentPath);
        }

        // Loose file, check if current path is writable
        return Files.isWritable(Path.of(System.getProperty("user.dir")));
    }



    public static void touch(final String filePath) {
        Objects.requireNonNull(filePath, "filePath is null");

        Path path = Paths.get(filePath);

        try {
            if (Files.exists(path)) {
                Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
            } else {
                Files.createFile(path);
            }
        } catch (IOException e) {
            logger.error("could not create file {}: {}", filePath, e.getMessage());
        }
    }

    public static JsonNodes readJsonCG(String jsonPath) {
        Gson gson = new Gson();
        try (BufferedReader in = new BufferedReader(new FileReader(jsonPath))) {
            return gson.fromJson(in, JsonNodes.class);
        } catch (IOException e) {
            logger.error("Could not find json");
            System.exit(1);
        }
        return null;
    }
}
