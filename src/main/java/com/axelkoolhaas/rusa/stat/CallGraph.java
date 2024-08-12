package com.axelkoolhaas.rusa.stat;

import com.axelkoolhaas.rusa.model.CompositeNode;
import com.axelkoolhaas.rusa.model.ExtendedClassNode;
import com.axelkoolhaas.rusa.model.cmd.CommandLineTarget;
import com.axelkoolhaas.rusa.model.json.JsonMethod;
import com.axelkoolhaas.rusa.model.json.JsonNode;
import com.axelkoolhaas.rusa.model.json.JsonNodes;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import javax.swing.text.html.Option;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.axelkoolhaas.rusa.AnalysisAssistant.SPRING_WEB_MVC;

public class CallGraph {
    private static final List<String> BLACKLIST = Arrays.asList("jdk", "java", "kotlin", "groovy");
    // Fields
    private static final Logger logger = LogManager.getLogger(CallGraph.class);
    // Reference to the Java archive that is being analyzed
    private final Jar jar;
     // The root of the graph used to navigate towards the entry methods
    private final CompositeNode root;
    // Methods that have already been parsed and shouldn't be explored anymore
    private final HashSet<MethodNode> parsedMethods;
    // A map to save time when searching destination methods
    private final HashMap<MethodNode, CompositeNode> methodProcedureMap;

    // Constructor
    public CallGraph(Jar jar) {
        this.jar = jar;
        this.root = CompositeNode.builder().root(true).build();
        this.parsedMethods = new HashSet<>();
        this.methodProcedureMap = new HashMap<>();
    }

    // Methods
    /**
     * Construct the call graph by parsing the classes inside the jar.
     */
    public void construct() {
        Queue<CompositeNode> unexploredMethods = new LinkedList<>();

        // Find REST controller methods and add them to the queue.
        this.jar.getClasses().values()
                .stream()
                .filter(classNode -> !classNode.name.startsWith(SPRING_WEB_MVC))
                .filter(this::isRestController)
                .flatMap(this::collectMappingMethods)
                .forEach(pair -> addToUnexploredMethods(pair, unexploredMethods));


        if (!validMethods(unexploredMethods)) {
            logger.warn("No valid methods found");
            return;
        }

        // link root to entry points
        this.root.addCallees(unexploredMethods);
        unexploredMethods.forEach(cn -> cn.addCaller(this.root));

        // We will do breadth first search parsing of the classes
        // since at lower levels we expect Spring native methods and/or libraries,
        while (!unexploredMethods.isEmpty()) {
            CompositeNode unexploredMethod = unexploredMethods.poll();

            Optional<List<CompositeNode>> callees = parseProcedure(unexploredMethod);
            callees.ifPresent(unexploredMethods::addAll);

            // if (target == found) break;
            // NOTE: this would not be complete, since there could be multiple paths to a destination
        }
    }

    /**
     * Sanity check to ensure the found methods are valid
     * @param unexploredMethods
     * @return true if all methods are valid, false otherwise
     */
    private boolean validMethods(Queue<CompositeNode> unexploredMethods) {
        if (unexploredMethods.isEmpty()) {
            return false;
        }
        // Check if all methods really belong to their class
        boolean valid = unexploredMethods
                    .stream()
                    .allMatch(compositeNode -> compositeNode.getOwner().methods.contains(compositeNode.getMethod()));
        if (!valid) {
            logger.error("Found methods that don't belong to their class!");
        }
        return valid;
    }

    /**
     * Find REST controller Mapping methods
     * It could be that the class methods don't have a Mapping annotation, e.g., when OpenAPI generated.
     * In this case, we need to check the interfaces that the class implements.
     * @param restClassNode:
     */
    private Stream<Map.Entry<ExtendedClassNode, MethodNode>> collectMappingMethods(ExtendedClassNode restClassNode) {
        if (restClassNode.interfaces.isEmpty()) {
            // No interfaces, use the Mapping methods from the class
            return restClassNode.methods
                    .stream()
                    .filter(this::isMappingMethod)
                    .map(classMethodNode -> new AbstractMap.SimpleEntry<>(restClassNode, classMethodNode));
        } else {
            // Class implements interfaces, collect Mapping methods through the interfaces
            return restClassNode.interfaces
                    .stream()
//                    .peek(interfaceName -> System.out.println("Found interface: " + interfaceName))
                    .flatMap(interfaceName -> this.jar.getClasses().values()
                            .stream()
                            .filter(node -> node.name.equals(interfaceName)))
//                    .peek(interfaceNode -> System.out.println("Found interface: " + interfaceNode.name))
                    .flatMap(restInterfaceNode -> restInterfaceNode.methods
                            .stream()
                            .filter(this::isMappingMethod))
//                    .peek(interfaceMethodNode -> System.out.println("Found interface method: " + interfaceMethodNode.name))
                    .flatMap(interfaceMethodNode -> restClassNode.methods
                            .stream()
                            .filter(classMethodNode -> classMethodNode.name.equals(interfaceMethodNode.name)))
                            .map(classMethodNode -> new AbstractMap.SimpleEntry<>(restClassNode, classMethodNode));
        }
    }

    private boolean isRestController(ExtendedClassNode classNode) {
        return classNode.visibleAnnotations != null &&
                classNode.visibleAnnotations.stream()
                        .anyMatch(annotation -> annotation.desc.endsWith("RestController;"));
    }

    private boolean isMappingMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null &&
                methodNode.visibleAnnotations.stream()
                        .anyMatch(annotation -> annotation.desc.endsWith("Mapping;"));
    }

    private void addToUnexploredMethods(Map.Entry<ExtendedClassNode, MethodNode> entry, Queue<CompositeNode> unexploredMethods) {
        ExtendedClassNode classNode = entry.getKey();
        MethodNode methodNode = entry.getValue();

        unexploredMethods.add(CompositeNode.builder().owner(classNode).method(methodNode).build());
    }

    private Optional<List<CompositeNode>> parseProcedure(CompositeNode toExploreCNMethod) {
        // check if node has been parsed already, otherwise return
        if (parsedMethods.contains(toExploreCNMethod.getMethod())) {
            return Optional.empty();
        }
        // haven't seen method before, remember it
        parsedMethods.add(toExploreCNMethod.getMethod());

        // potential return callees
        List<CompositeNode> callees = new ArrayList<>();

        // Iterate through instructions and look for calls
        for (AbstractInsnNode insnNode : toExploreCNMethod.getMethod().instructions) {

            if (insnNode.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN) {
                assert insnNode instanceof InvokeDynamicInsnNode;
                parseInvokeDynamicInsn((InvokeDynamicInsnNode) insnNode, toExploreCNMethod).ifPresent(callees::add);
            }

            if (insnNode.getType() == AbstractInsnNode.METHOD_INSN) {
                assert insnNode instanceof MethodInsnNode;
                parseMethodInsn((MethodInsnNode) insnNode, toExploreCNMethod).ifPresent(callees::addAll);
            }
        }

        return callees.isEmpty() ? Optional.empty() : Optional.of(callees);
    }

    private Optional<CompositeNode> parseInvokeDynamicInsn(InvokeDynamicInsnNode dynamicIns, CompositeNode toExploreCNMethod) {
        // We ignore Java >= 9 String concatenation
        if (dynamicIns.bsm.getName().equals("makeConcatWithConstants")) {
            return Optional.empty();
        }

        ExtendedClassNode calleeClass = toExploreCNMethod.getOwner();
        // find target of InvokeDynamic through BootstrapMethod Arguments
        Handle handle = (Handle)dynamicIns.bsmArgs[1];

        // If the calleeClass matches the handle owner, we look for the method in the calleeClass
        if (handle.getOwner().equals(calleeClass.name)) {
            // Look if the callee class has the method
            Optional<MethodNode> optionalDestMn = calleeClass.methods.stream()
                    .filter(mn -> mn.name.equals(handle.getName()))
                    .findFirst();

            if (optionalDestMn.isPresent()) {
                return addOrCreateLink(toExploreCNMethod, calleeClass, optionalDestMn.get());
            }
        }

        // no match? try to find owner in jar
        ExtendedClassNode handleClass = jar.getClasses().get(handle.getOwner());
        if (handleClass == null) {
            if (BLACKLIST.stream().noneMatch(word -> handle.getOwner().startsWith(word))) {
                // We ignore SDK classes
                logger.warn("Couldn't find (invoke)class: {} in jar", handle.getOwner());
            }
            return Optional.empty();
        }

        // find the method in the handle class
        Optional<MethodNode> optionalDestMn = handleClass.methods.stream()
                .filter(mn -> mn.name.equals(handle.getName()))
                .findFirst();

        if (optionalDestMn.isPresent()) {
            return addOrCreateLink(toExploreCNMethod, calleeClass, optionalDestMn.get());
        }

        return Optional.empty();
    }

    private Optional<List<CompositeNode>> parseMethodInsn(MethodInsnNode functionIns, CompositeNode toExploreCNMethod) {
        // find target class:method  inside jar
        ExtendedClassNode calleeClass = jar.getClasses().get(functionIns.owner);
        if (calleeClass == null) {
            if (BLACKLIST.stream().noneMatch(word -> functionIns.owner.startsWith(word))) {
                // We ignore SDK classes
                logger.warn("Couldn't find (method)class: {} in jar", functionIns.owner);
            }
            return Optional.empty();
        }

        // first look if the class itself has the method (and ensure it is not an interface)
        if (!calleeClass.methods.isEmpty() && !calleeClass.isInterface()) {
            Optional<MethodNode> optionalDestMn = calleeClass.methods
                    .stream()
                    .filter(mn -> mn.name.equals(functionIns.name))
                    .findAny();

            if (optionalDestMn.isPresent()) {
                return addOrCreateLink(toExploreCNMethod, calleeClass, optionalDestMn.get())
                        .map(Collections::singletonList);
            }
        }
//        // potentially cleaner solution for class and abstract class branches, but not simple, i think
//        if (calleeClass.methods.size() > 0) {
//            Optional<CompositeNode> cn = findAndAddMethod(functionIns, toExploreCNMethod, calleeClass, calleeClass);
//            if (cn.isPresent()) {
//                return cn;
//            }
//        }

        if (calleeClass.name.equals("org/springframework/samples/petclinic/model/Owner") && functionIns.name.equals("getId")) {
            System.out.println("broken case?");
        }

        // otherwise look if the callee has (transiently) extended an (abstract) class with the method
        if (calleeClass.superName != null && !calleeClass.superName.equals("java/lang/Object")) {
            ExtendedClassNode superClass = jar.getExtensions().get(calleeClass.name);
            if (superClass == null) {
                // throw exception?
                return Optional.empty();
            }

            Optional<Map.Entry<ExtendedClassNode, MethodNode>> optionalDest =
                    findMethodInSuperclass(superClass, functionIns.name);

            if (optionalDest.isPresent()) {
                // TODO might be wrong. Could be instrumentation wants calleeClass instead of actual definition recursively?
                // i.e. calleeClass instead of optionalDest.get().getKey()
                return addOrCreateLink(toExploreCNMethod, optionalDest.get().getKey(), optionalDest.get().getValue())
                        .map(Collections::singletonList);
            }
        }

        // otherwise, look if there is an implementation of the method
        if (calleeClass.isInterface()) {
            // find the implementation
            List<ExtendedClassNode> implementations = jar.getRealImplementations().get(functionIns.owner);

            if (implementations == null || implementations.isEmpty()) {
                return Optional.empty();
            }

            // doesn't account for multiple implementations?
//            Optional<AbstractMap.SimpleEntry<ExtendedClassNode, MethodNode>> optionalImpl = implementations
//                    .stream()
//                    .flatMap(impl -> impl.methods
//                        .stream()
//                        .filter(mn -> mn.name.equals(functionIns.name))
//                        .map(mn -> new AbstractMap.SimpleEntry<>(impl, mn)))
//                    .findAny();
//            if (optionalImpl.isPresent()) {
//                return addOrCreateLink(toExploreCNMethod, optionalImpl.get().getKey(), optionalImpl.get().getValue());
//            }

            List<AbstractMap.SimpleEntry<ExtendedClassNode, MethodNode>> matchingImplementations = implementations
                    .stream()
                    .flatMap(impl -> impl.methods
                            .stream()
                            .filter(mn -> mn.name.equals(functionIns.name))
                            .map(mn -> new AbstractMap.SimpleEntry<>(impl, mn)))
                    .toList();

            if (!matchingImplementations.isEmpty()) {
                Optional<List<CompositeNode>> compositeNodes = Optional.of(new ArrayList<>());
                for (AbstractMap.SimpleEntry<ExtendedClassNode, MethodNode> implementation : matchingImplementations) {
                    addOrCreateLink(toExploreCNMethod, implementation.getKey(), implementation.getValue())
                            .ifPresent(node -> compositeNodes.get().add(node));
                }
                return compositeNodes;
            }
        }


        // TODO: we seriously need to rethink logic here
        // eg. org/springframework/samples/petclinic/model/Owner:getId breaks
       // interfaces is empty, TODO: look at calleeClass.superName

        // otherwise, look if any implemented interface has the method
        if (calleeClass.interfaces != null) {
            // get interfaces that the callee implements
            Queue<ExtendedClassNode> destInterfaceClassNodes = calleeClass.interfaces
                    .stream()
                    .map(interfase -> jar.getClasses().get(interfase))
                    .collect(Collectors.toCollection(LinkedList::new));

            // If the interfaces are default Java interfaces, they are not in the jar
            // Thus, /java/util/Map breaks the code
            if (destInterfaceClassNodes.isEmpty()) {
                return Optional.empty();
            }

            // loop through found interfaces
            while (!destInterfaceClassNodes.isEmpty()) {
                ExtendedClassNode currentInterface = destInterfaceClassNodes.poll();
                if (currentInterface == null) {
                    // throw exception?
                    return Optional.empty();
                }

                // Spring creates from JPARepository a repository at runtime and routes accordingly using JDK proxy.
                // https://stackoverflow.com/questions/38509882/how-are-spring-data-repositories-actually-implemented

                // check if interface has matching method
                Optional<MethodNode> methodMatch = currentInterface.methods
                        .stream()
                        .filter(mn -> mn.name.equals(functionIns.name))
                        .findAny();

                if (methodMatch.isPresent()) {
//                    jar.getRealImplementations().get(currentInterface.name)
//                            .stream()
//                            .findFirst()
//                            .ifPresent(cn -> {
//                                addOrCreateLink(toExploreCNMethod, cn, methodMatch.get()).ifPresent(callees::add);
//                                return; // breaks
//                            });
                    List<ExtendedClassNode> classNodes = jar.getRealImplementations().get(currentInterface.name);
                    if (classNodes != null) {

                        Optional<List<CompositeNode>> compositeNodes = Optional.of(new ArrayList<>());
                        for (ExtendedClassNode classNode : classNodes) {
                            addOrCreateLink(toExploreCNMethod, classNode, methodMatch.get())
                                    .ifPresent(node -> compositeNodes.get().add(node));
                        }
                        return compositeNodes;
                    }

                    return addOrCreateLink(toExploreCNMethod, currentInterface, methodMatch.get())
                            .map(Collections::singletonList);
                }

                // No match found, add interfaces of interface to explore
                destInterfaceClassNodes.addAll(currentInterface.interfaces
                        .stream()
                        .map(interfase -> jar.getClasses().get(interfase))
                        .collect(Collectors.toCollection(LinkedList::new)));
            }
        }

        return Optional.empty();
    }

    /**
     * Recursively finds a method in the superclass of the given class node.
     * @param classNode the class node to start the search from
     * @param methodName the name of the method to find
     * @return the method node if found, empty otherwise
     */
    private Optional<Map.Entry<ExtendedClassNode, MethodNode> > findMethodInSuperclass(ExtendedClassNode classNode, String methodName) {
        Optional<MethodNode> optionalMethodNode = classNode.methods.stream()
                .filter(mn -> mn.name.equals(methodName))
                .findFirst();

        if (optionalMethodNode.isPresent()) {
            return Optional.of(new AbstractMap.SimpleEntry<>(classNode, optionalMethodNode.get()));
        }

        if (classNode.superName != null) {
            ExtendedClassNode superNode = jar.getExtensions().get(classNode.name);
            if (superNode != null) {
                return findMethodInSuperclass(superNode, methodName);
            }
        }

        return Optional.empty();
    }

//    private Optional<CompositeNode> findAndAddMethod(MethodInsnNode functionIns, CompositeNode toExploreCNMethod,
//                                                     ExtendedClassNode calleeClass,
//                                                     ExtendedClassNode calleeOrSuperClass) {
//        Optional<MethodNode> optionalDestMn = calleeOrSuperClass.methods
//                .stream()
//                .filter(mn -> mn.name.equals(functionIns.name))
//                .findAny();
//
//        if (optionalDestMn.isPresent()) {
//            return addOrCreateLink(toExploreCNMethod, calleeClass, optionalDestMn.get());
//        }
//        return Optional.empty();
//    }

    /**
     * Creates a node with link to parent node if it does not exist yet.
     * Otherwise, if it does exists, add bidirectional link between parent node and cn.
     * @param parentNode class where the function is called
     * @param calleeClass class where the function is defined
     * @param destMn method node of the function
     * @return Optional.empty() if node already exists, Optional.of(cn) if node was created
     */
    private Optional<CompositeNode> addOrCreateLink(CompositeNode parentNode, ExtendedClassNode calleeClass, MethodNode destMn) {
        // Fetch method node from map
        CompositeNode cn = methodProcedureMap.get(destMn);

        if (cn == null || !cn.getOwner().name.equals(calleeClass.name)) {
            // never seen this callee, create with link
            cn = CompositeNode.builder().owner(calleeClass).method(destMn).build();
            parentNode.addCallee(cn);
            cn.addCaller(parentNode);
            methodProcedureMap.put(destMn, cn);
            return Optional.of(cn);
        }

        // this node already exists, only add link
        parentNode.addCallee(cn);
        cn.addCaller(parentNode);
        return Optional.empty();
    }

    public void calculateDistance(List<CommandLineTarget> targets) {
        // TODO for now only one target
        if (targets.size() != 1) {
            logger.warn("Multiple targets currently not implemented. First target will be used.");
        }
        CommandLineTarget commandLineTarget = targets.get(0);

        // perform BFS to find target within graph
        CompositeNode target = findTarget(commandLineTarget).orElseGet(() -> {
            logger.error("Could not find specified target.");
            System.exit(1);
            return null;
        });

        // calculate distances
        Queue<CompositeNode> nodeQueue = new LinkedList<>(Collections.singletonList(target));
        target.setDistance(0);

        while (!nodeQueue.isEmpty()) {
            CompositeNode srcNode = nodeQueue.poll();
            Integer currentDistance = srcNode.getDistance();

//            System.out.println(currentDistance);
//            System.out.println(srcNode.print() + "\n");

            // add children to explore
            srcNode.getCallers()
                    .forEach(dstNode -> {
                        // TODO maybe make lambda's less weight? what is optimal weight calculation?
                        if (dstNode.getDistance() == null) {
                            dstNode.setDistance(currentDistance + 1);
                        } else if (dstNode.getDistance() > currentDistance + 1) {
                            dstNode.setDistance(currentDistance + 1);
                        }

//                        System.out.println("adding " + dstNode.print());
                        nodeQueue.add(dstNode);
                    });
        }
    }

    private Optional<CompositeNode> findTarget(CommandLineTarget commandLineTarget) {
        Queue<CompositeNode> nodeQueue = new LinkedList<>(Collections.singletonList(this.root));
        Set<CompositeNode> alreadyExplored = new HashSet<>();
        CompositeNode target = null;

        while (!nodeQueue.isEmpty()) {
            CompositeNode compositeNode = nodeQueue.poll();

            // add children to explore
            compositeNode.getCallees()
                    .forEach(cn -> {
                        if (!alreadyExplored.contains(cn)) {
                            nodeQueue.add(cn);
                        }
                    });

            if (!compositeNode.isRoot() && compositeNode.getMethod().name.equals(commandLineTarget.getMethodName())
                    && compositeNode.getOwner().name.equals(commandLineTarget.getInternalClassName()) ) {
                target = compositeNode;
                break;
            }

            alreadyExplored.addAll(compositeNode.getCallees());
        }

        return Optional.ofNullable(target);
    }

    public void printDotGraph(String pathname) {
        Queue<CompositeNode> toPrintNodes = new LinkedList<>(Collections.singletonList(this.root));
        Set<CompositeNode> alreadyExplored = new HashSet<>();
        StringBuilder sb = new StringBuilder();

        sb.append("digraph G {\n");
        while (!toPrintNodes.isEmpty()) {
            CompositeNode compositeNode = toPrintNodes.poll();

            // add children to explore
            compositeNode.getCallees()
                    .forEach(cn -> {
                        if (!alreadyExplored.contains(cn)) {
                            toPrintNodes.add(cn);
                        }
                    });

            //    "procSrc" -> "procDst";
            compositeNode.getCallees().forEach(cn ->
                    sb.append("    ")
                            .append('"').append(compositeNode.printAbbrev()).append('"')
                            .append(" -> ")
                            .append('"').append(cn.printAbbrev()).append('"')
                            .append(";\n"));

            alreadyExplored.addAll(compositeNode.getCallees());
        }
        sb.append("}");

        if (pathname != null) {
            try (FileWriter f = new FileWriter(pathname)) {
                f.write(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(sb);
        }
    }

    public String createJson() {
        Queue<CompositeNode> toPrintNodes = new LinkedList<>(this.root.getCallees());
        Set<CompositeNode> alreadyExplored = new HashSet<>();
        JsonNodes jsonNodes = new JsonNodes();

        CompositeNode compositeNode;
        while (!toPrintNodes.isEmpty()) {
            compositeNode = toPrintNodes.poll();

            // add children to explore
            compositeNode.getCallees()
                    .forEach(cn -> {
                        if (!alreadyExplored.contains(cn)) {
                            toPrintNodes.add(cn);
                        }
                    });

            JsonNode currentJsonNode = jsonNodes.findOrCreateNode(compositeNode);
            JsonMethod currentJsonMethod = currentJsonNode.findOrCreateMethod(compositeNode);
            currentJsonMethod.addCallees(compositeNode);

            alreadyExplored.addAll(compositeNode.getCallees());
        }

        Gson gson = new Gson();
        return gson.toJson(jsonNodes);
    }

    public void saveJson(String path) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(path))) {
            out.write(this.createJson());
        } catch (IOException e) {
            logger.error("Could not save json.");
            logger.error(e.getMessage());
        }
    }
}

