package com.axelkoolhaas.rusa.stat;

import com.axelkoolhaas.rusa.model.ExtendedClassNode;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Jar {
    // Fields
    private static final Logger logger = LogManager.getLogger(Jar.class);
    private final String path;
    @Getter
    private final Map<String, ExtendedClassNode> classes;
    @Getter
    private final Map<String, List<ExtendedClassNode>> implementations; // interface -> {class, interface}
    @Getter
    private final Map<String, List<ExtendedClassNode>> realImplementations; // interface -> class
    @Getter
    // (abstract) class -> class | superClass -> List<subClass> | parent -> children
    private final Map<String, List<ExtendedClassNode>> inheritors;
    @Getter
    // class -> (abstract) class | subClass.name -> superClass | child -> parent
    private final Map<String, ExtendedClassNode> extensions;

    // Constructor
    public Jar(String path) {
        this.path = path;
        this.classes = new HashMap<>();
        this.implementations = new HashMap<>();
        this.realImplementations = new HashMap<>();
        this.inheritors = new HashMap<>();
        this.extensions = new HashMap<>();
    }

    // Methods
    public void load() {
        try (ZipFile jarFile = new ZipFile(this.path)) {
            loadJar(jarFile);
        } catch (IOException e) {
            logger.error("Error while loading JAR!");
            logger.error(e.getMessage());
            System.exit(1);
        }

        // inverse inheritors to extensions
        inheritors.forEach((parentName, childrenClassNodes) ->
                childrenClassNodes.forEach(childCn ->
                        extensions.put(childCn.name, classes.get(parentName))));

        // create actual implementers list with classes only
        implementations.forEach((interfaceName,implementors) -> implementors
                .stream()
                .filter(ExtendedClassNode::isClass)
                .forEach(cn -> {
                    realImplementations.computeIfAbsent(interfaceName, v -> new ArrayList<>());
                    realImplementations.get(interfaceName).add(cn);
                })
        );
    }

    private void loadJar(ZipFile jarFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.endsWith(".jar")) {
                parseJar(jarFile, entry);
            } else if (entryName.endsWith(".class")) {
                parseClass(jarFile, entry);
            }
        }
    }

    // Java JAR API can't handle InputStream 2 JarFile, have to convert to File.
    private void parseJar(ZipFile jarFile, ZipEntry entry) throws IOException {
        File tempFile = File.createTempFile("tmp", "jar");
        FileOutputStream tempOut= new FileOutputStream(tempFile);

        InputStream jIS = jarFile.getInputStream(entry);
        IOUtils.copy(jIS, tempOut, (int) entry.getSize());
        tempOut.close();

        ZipFile innerJar = new ZipFile(tempFile);
        loadJar(innerJar);

        if (!tempFile.delete()) {
            logger.error("Couldn't delete tmp JAR.");
        }
    }


    private void parseClass(ZipFile jar, ZipEntry entry) {
        byte[] classFileBytes;

        try {
            classFileBytes = IOUtils.toByteArray(jar.getInputStream(entry), (int) entry.getSize());
        } catch (IOException e) {
            logger.error("Error while reading class file: " + entry.getName());
            logger.error(e.getMessage());
            return;
        }

        if (!validJavaClassMagic(Arrays.copyOfRange(classFileBytes, 0, 4))) {
            logger.error("Mismatch magic in class: " + entry.getName());
            return;
        }

        // parse bytes
        ExtendedClassNode cn = getNode(classFileBytes);
        this.classes.put(cn.name, cn);

        // extends a class (and not just Object...)
        if (cn.superName != null && !cn.superName.equals("java/lang/Object")) {
            // add y extends x relation, where x is in our map
            inheritors.computeIfAbsent(cn.superName, k -> new ArrayList<>());
            inheritors.get(cn.superName).add(cn);
        }

        // implements an interface
        if (cn.interfaces.size() > 0) {
            for (String interfase : cn.interfaces) {
                implementations.computeIfAbsent(interfase, k -> new ArrayList<>());
                implementations.get(interfase).add(cn);
            }
        }
    }

    private ExtendedClassNode getNode(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ExtendedClassNode cn = new ExtendedClassNode(Opcodes.ASM9);
        try {
            // could be useful, but degrades performance quite a lot
//            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            cr.accept(cn, 0);
        } catch (Exception e) {
            logger.error("Failed to parse class with ASM reader.");
            logger.error(e.getMessage());
        }
        return cn;
    }

    public static boolean validJavaClassMagic(byte[] rawMagic) {
        /* unfortunately Java does not support unsigned int */
        final long CAFEBABE = 0xcafebabeL << 32;

        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.put(rawMagic);
        long magic = bb.getLong(0);

        return magic == CAFEBABE;
    }
}
