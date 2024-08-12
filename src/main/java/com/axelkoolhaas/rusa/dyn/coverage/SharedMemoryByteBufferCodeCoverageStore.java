package com.axelkoolhaas.rusa.dyn.coverage;

import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/* TODO this functionality is not finished */
public class SharedMemoryByteBufferCodeCoverageStore implements CodeCoverageStore {

    // Path of the shared memory
    private static final String SHM_PATH = System.getProperty("java.io.tmpdir") + File.separator + "rusa-shm";

    // Size of the coverage array in bytes
    private static final int ARRAY_SIZE = 1 << 20; // 1 MB = 262,144 integers

    // Offset of the coverage array in the shared memory segment
    private static final int ARRAY_OFFSET = 0;

    // Offset of the method ID map in the shared memory segment
    // TODO why is the MAP_OFFSET at the end of the array?
    private static final int MAP_OFFSET = ARRAY_OFFSET + ARRAY_SIZE;

    // Shared memory segment
    private final ByteBuffer sharedMemory;

    // Map from method names to index IDs
    @Getter
    private final Map<String, Integer> methodIdMap;

    // TODO experiment with removing this
    // Coverage array
    private final int[] coverage;

    // Singleton instance
    private static SharedMemoryByteBufferCodeCoverageStore instance;

    private SharedMemoryByteBufferCodeCoverageStore(ByteBuffer sharedMemory) {
        this.sharedMemory = sharedMemory;
        this.methodIdMap = new HashMap<>();
        this.coverage = new int[ARRAY_SIZE / 4];
    }

    public static SharedMemoryByteBufferCodeCoverageStore getInstance() {
        if (instance == null) {
            instance = new SharedMemoryByteBufferCodeCoverageStore(openSharedMemory(true));
        }
        return instance;
    }

    public static void initialize(Map<String, List<String>> targets) {
        // using the list of targets, already map the methodIdMap
        for (Map.Entry<String, List<String>> classTypeEntry : targets.entrySet()) {
            for (String methodName : classTypeEntry.getValue()) {
                int nextId = instance.getMethodIdMap().size();
                instance.getMethodIdMap().put(classTypeEntry.getKey() + ':' + methodName, nextId);
            }
        }
    }

    @Override
    public void increment(String type, String methodName) {
        String identifier = type + methodName;

        // Check if we have already seen this method
        if (!methodIdMap.containsKey(identifier)) {
            // If not, assign it a new ID and add it to the map
            int nextId = methodIdMap.size();
            methodIdMap.put(identifier, nextId);

            // Update the method ID map in the shared memory segment
//            sharedMemory.position();
//            sharedMemory.asCharBuffer().put(methodName);
//            sharedMemory.putInt(nextId);
        }

        // Look up the ID for this method
        int methodId = methodIdMap.get(identifier);

        // Increment the count for this method
        coverage[methodId]++;
//        int counter = sharedMemory.getInt(ARRAY_OFFSET + 4 * methodId);
        sharedMemory.putInt(ARRAY_OFFSET + 4 * methodId, coverage[methodId]);

    }

    @Override
    public Map<String, Integer> getCoverage() {
        // Copy the method ID map and coverage array from the shared memory segment
        Map<String, Integer> coverageData = new HashMap<>();
        sharedMemory.position(MAP_OFFSET);
        while (sharedMemory.hasRemaining()) {
            String methodName = sharedMemory.asCharBuffer().toString();
            int methodId = sharedMemory.getInt();
            int count = sharedMemory.getInt(ARRAY_OFFSET + 4 * methodId);
            coverageData.put(methodName, count);
        }
        return coverageData;
    }

    @Override
    public void reset() {
        // Clear the method ID map and coverage array in the shared memory segment
        sharedMemory.position(MAP_OFFSET);
        sharedMemory.asCharBuffer().put("");
        sharedMemory.position(ARRAY_OFFSET);
        sharedMemory.asIntBuffer().put(coverage, 0, coverage.length);
    }

    public static ByteBuffer openSharedMemory(boolean create) {
        Set<StandardOpenOption> openOptions = create
                ? new HashSet<>(List.of(StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE))
                : new HashSet<>(List.of(StandardOpenOption.READ));

        FileChannel.MapMode mapMode = create ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY;

        try (FileChannel channel = FileChannel.open(Paths.get(SHM_PATH), openOptions)) {
            // Map the file into memory as a direct byte buffer
            return channel.map(mapMode, 0, ARRAY_SIZE); // mode, position, size

            /* "A mapping is not dependent upon the file channel that was used to create it.
                Closing the channel has no effect upon the validity of the mapping." */
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
