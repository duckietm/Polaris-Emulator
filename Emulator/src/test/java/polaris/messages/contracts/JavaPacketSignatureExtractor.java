package polaris.messages.contracts;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.VoidType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

enum JavaPacketSide {
    INCOMING,
    OUTGOING
}

record ExtractionResult(List<WireSchema> fields, Optional<String> unsupportedReason) {
    ExtractionResult {
        fields = List.copyOf(fields);
        unsupportedReason = unsupportedReason == null ? Optional.empty() : unsupportedReason;
    }

    static ExtractionResult supported(List<WireSchema> fields) {
        return new ExtractionResult(fields, Optional.empty());
    }

    static ExtractionResult unsupported(String reason) {
        return new ExtractionResult(List.of(), Optional.of(reason));
    }
}

final class JavaPacketSignatureExtractor {
    private static final Map<String, String> INCOMING_TYPES = Map.ofEntries(
            Map.entry("readByte", "byte"),
            Map.entry("readShort", "short"),
            Map.entry("readInt", "int"),
            Map.entry("readLong", "long"),
            Map.entry("readBoolean", "boolean"),
            Map.entry("readString", "string"),
            Map.entry("readBytes", "bytes"));
    private static final Map<String, String> OUTGOING_TYPES = Map.ofEntries(
            Map.entry("appendByte", "byte"),
            Map.entry("appendShort", "short"),
            Map.entry("appendInt", "int"),
            Map.entry("appendLong", "long"),
            Map.entry("appendBoolean", "boolean"),
            Map.entry("appendString", "string"),
            Map.entry("appendBytes", "bytes"));

    private JavaSourceIndex index;

    ExtractionResult extract(Path source, JavaPacketSide side, String rootMethod) throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25);
        this.index = JavaSourceIndex.forSource(source);
        CompilationUnit unit = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration type = unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("No class found in " + source));
        Map<String, MethodDeclaration> methods = new HashMap<>();
        for (MethodDeclaration method : type.getMethods()) {
            if (methods.putIfAbsent(method.getNameAsString(), method) != null) {
                return ExtractionResult.unsupported(
                        "Overloaded local helper method cannot be resolved: " + method.getNameAsString());
            }
        }
        MethodDeclaration root = methods.get(rootMethod);
        // The file name only: the message lands in the committed manifest and must not depend on the checkout path.
        if (root == null)
            throw new IllegalArgumentException("Method " + rootMethod + " not found in " + source.getFileName());
        return extractMethod(root, type, side, methods, new ArrayDeque<>(), false);
    }

    /** {@code strict} (inside a delegate) refuses conditional packet operations anywhere, not only in list items. */
    private ExtractionResult extractMethod(
            MethodDeclaration method,
            TypeDeclaration<?> owner,
            JavaPacketSide side,
            Map<String, MethodDeclaration> methods,
            Deque<String> stack,
            boolean strict) {
        String methodName = method.getNameAsString();
        String key = owner.getNameAsString() + "#" + methodName;
        if (stack.contains(key)) {
            return ExtractionResult.unsupported("Recursive packet helper call: " + methodName);
        }
        if (method.getBody().isEmpty()) {
            return ExtractionResult.unsupported("Packet method " + methodName + " has no body");
        }
        stack.addLast(key);
        try {
            Map<MethodCallExpr, ListLoop> loops = listLoops(method, side);
            Optional<String> dynamic = dynamicControlFlow(method, side, methods, loops);
            if (dynamic.isPresent()) return ExtractionResult.unsupported(dynamic.get());

            TryCatchAnalysis tryCatch = analyzeTryCatch(method, side);
            if (tryCatch.unsupportedReason().isPresent()) {
                return ExtractionResult.unsupported(tryCatch.unsupportedReason().orElseThrow());
            }

            Set<Node> resolved = Collections.newSetFromMap(new IdentityHashMap<>());
            ExtractionResult fields = extractScope(
                    method.getBody().orElseThrow(),
                    new ScopeContext(method, owner, side, methods, stack, tryCatch, loops, resolved, strict));
            if (fields.unsupportedReason().isPresent()) return fields;
            Optional<String> delegation = externalDelegation(method, side, resolved);
            if (delegation.isPresent()) return ExtractionResult.unsupported(delegation.get());
            return fields;
        } finally {
            stack.removeLast();
        }
    }

    /** The packet operations inside one scope: the method body, or the body of a recognised list loop. */
    private ExtractionResult extractScope(Node scope, ScopeContext context) {
        Set<Node> loopNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        context.loops().values().forEach(loop -> loopNodes.add(loop.loop()));
        List<Expression> nodes = new ArrayList<>(scope.findAll(MethodCallExpr.class));
        scope.findAll(ObjectCreationExpr.class).stream()
                .filter(creation -> packetArgument(creation.getArguments(), context.side()) >= 0)
                .forEach(nodes::add);
        nodes.sort(Comparator.comparing(
                node -> node.getBegin().orElseThrow(),
                Comparator.comparingInt((com.github.javaparser.Position position) -> position.line)
                        .thenComparingInt(position -> position.column)));
        List<WireSchema> fields = new ArrayList<>();
        for (Expression node : nodes) {
            if (insideLoopOf(node, scope, loopNodes)) continue;
            if (node instanceof ObjectCreationExpr creation) {
                Optional<String> refused = delegateGuard(creation, scope, context.side());
                if (refused.isPresent()) return ExtractionResult.unsupported(refused.get());
                ExtractionResult delegated = extractDelegate(creation, context);
                if (delegated.unsupportedReason().isPresent()) return delegated;
                fields.addAll(delegated.fields());
                continue;
            }
            MethodCallExpr call = (MethodCallExpr) node;
            if (context.tryCatch().callsToSkip().contains(call)) continue;
            ListLoop loop = context.loops().get(call);
            if (loop != null) {
                ExtractionResult item = extractScope(loop.body(), context);
                if (item.unsupportedReason().isPresent()) return item;
                if (item.fields().isEmpty()) {
                    return ExtractionResult.unsupported("List item reads nothing statically inside "
                            + context.method().getNameAsString());
                }
                fields.add(new ListSchema(loop.countType(), item.fields()));
                continue;
            }
            String wireType = wireType(call, context.side());
            if (wireType == null && isUnknownPacketOperation(call, context.side())) {
                return ExtractionResult.unsupported("Unknown packet operation " + call.getNameAsString() + " inside "
                        + context.method().getNameAsString());
            }
            // Outside a list a conditional read keeps its old reading; inside one it would misdescribe every item.
            boolean inListItem = scope != context.method().getBody().orElseThrow();
            if (wireType != null && (inListItem || context.strict()) && insideConditionalExpression(call, scope)) {
                return ExtractionResult.unsupported(
                        "Conditional packet operation " + call + " inside a list item or a delegate");
            }
            if (wireType != null) {
                ScalarSchema field = new ScalarSchema(wireType, inferredName(call));
                IfStmt guard = call.findAncestor(IfStmt.class).orElse(null);
                fields.add(
                        guard != null && isOptionalTail(guard, context.method(), context.side())
                                ? new OptionalSchema(inferredName(call), List.of(field))
                                : field);
                continue;
            }
            int packetIndex = packetArgument(call.getArguments(), context.side());
            if (isLocalCall(call) && context.methods().containsKey(call.getNameAsString())) {
                MethodDeclaration helper = context.methods().get(call.getNameAsString());
                if (helper == null) {
                    return ExtractionResult.unsupported(
                            "Overloaded local helper method cannot be resolved: " + call.getNameAsString());
                }
                ExtractionResult helped = extractMethod(
                        packetIndex >= 0 ? withPacketParameter(helper, packetIndex, context.side()) : helper,
                        context.owner(),
                        context.side(),
                        context.methods(),
                        context.stack(),
                        context.strict());
                if (helped.unsupportedReason().isPresent()) return helped;
                fields.addAll(helped.fields());
                continue;
            }
            if (packetIndex >= 0) {
                Optional<String> refused = delegateGuard(call, scope, context.side());
                if (refused.isPresent()) return ExtractionResult.unsupported(refused.get());
                ExtractionResult delegated = extractDelegate(call, context);
                if (delegated.unsupportedReason().isPresent()) return delegated;
                fields.addAll(delegated.fields());
            }
        }
        return ExtractionResult.supported(fields);
    }

    /**
     * The fields written or read by the method or constructor the packet is handed to, in another class. Only a
     * receiver whose type is declared, concrete and not overridden below is followed: anything else is exempt.
     */
    private ExtractionResult extractDelegate(Expression node, ScopeContext context) {
        String label = node instanceof MethodCallExpr call
                ? call.getNameAsString()
                : ((ObjectCreationExpr) node).getTypeAsString();
        String kind = node instanceof ObjectCreationExpr
                ? "external constructor"
                : context.side() == JavaPacketSide.INCOMING ? "external reader" : "external serializer";
        String unresolved = "Packet fields delegated to " + kind + " " + label + " inside "
                + context.method().getNameAsString();
        CompilationUnit from = context.owner().findCompilationUnit().orElse(null);
        if (from == null) return ExtractionResult.unsupported(unresolved);

        TypeDeclaration<?> type;
        CallableDeclaration<?> target;
        int packetIndex;
        if (node instanceof ObjectCreationExpr creation) {
            packetIndex = packetArgument(creation.getArguments(), context.side());
            type = index.resolve(creation.getTypeAsString(), from).orElse(null);
            if (type == null) return ExtractionResult.unsupported(unresolved);
            List<ConstructorDeclaration> constructors = type.getConstructors().stream()
                    .filter(constructor -> constructor.getParameters().size()
                            == creation.getArguments().size())
                    .toList();
            if (constructors.size() != 1) return ExtractionResult.unsupported(unresolved);
            target = constructors.getFirst();
        } else {
            MethodCallExpr call = (MethodCallExpr) node;
            packetIndex = packetArgument(call.getArguments(), context.side());
            if (call.getScope().isEmpty()) return ExtractionResult.unsupported(unresolved);
            Expression receiver = call.getScope().get();
            Optional<String> declared = Optional.empty();
            boolean isStatic = false;
            if (receiver instanceof NameExpr name) {
                declared = declaredType(name.getNameAsString(), context);
                if (declared.isEmpty()) {
                    declared = Optional.of(name.getNameAsString());
                    isStatic = true;
                }
            } else if (receiver instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
                declared = fieldType(field.getNameAsString(), context.owner());
            }
            if (declared.isEmpty()
                    || declared.get().equals("var")
                    || declared.get().endsWith("]")) {
                return ExtractionResult.unsupported(unresolved);
            }
            type = index.resolve(declared.get(), from).orElse(null);
            if (type == null) return ExtractionResult.unsupported(unresolved);
            if (!isStatic
                    && type instanceof ClassOrInterfaceDeclaration declaration
                    && (declaration.isInterface() || declaration.isAbstract())) {
                return ExtractionResult.unsupported(unresolved + ": " + type.getNameAsString()
                        + " is abstract, the method cannot be resolved exactly");
            }
            MethodDeclaration method = findMethod(
                            type, call.getNameAsString(), call.getArguments().size(), from, 0)
                    .orElse(null);
            if (method == null || method.getBody().isEmpty() || isStatic != method.isStatic()) {
                return ExtractionResult.unsupported(unresolved);
            }
            if (!isStatic
                    && index.overriddenBelow(
                            type.getNameAsString(),
                            call.getNameAsString(),
                            call.getArguments().size())) {
                return ExtractionResult.unsupported(
                        unresolved + ": " + call.getNameAsString() + " is overridden below " + type.getNameAsString());
            }
            type = method.findAncestor(TypeDeclaration.class)
                    .<TypeDeclaration<?>>map(owner -> owner)
                    .orElse(type);
            target = method;
        }
        String expected = context.side() == JavaPacketSide.INCOMING ? "packet" : "response";
        if (usesName(target, packetIndex, expected, type)) {
            return ExtractionResult.unsupported(unresolved + ": it already uses the name " + expected);
        }
        MethodDeclaration copy = withPacketParameter(target, packetIndex, context.side());
        boolean forwardsToConstructor = copy.findAll(ExplicitConstructorInvocationStmt.class).stream()
                .anyMatch(invocation -> packetArgument(invocation.getArguments(), context.side()) >= 0);
        if (forwardsToConstructor) {
            return ExtractionResult.unsupported(unresolved + ": it hands the packet to another constructor");
        }
        boolean storesPacket = copy.findAll(NameExpr.class).stream()
                .filter(name -> name.getNameAsString().equals(expected))
                .anyMatch(name -> name.getParentNode()
                        .map(parent -> (parent instanceof AssignExpr assign && assign.getValue() == name)
                                || (parent instanceof VariableDeclarator variable
                                        && variable.getInitializer().orElse(null) == name))
                        .orElse(false));
        if (storesPacket) return ExtractionResult.unsupported(unresolved + ": it keeps the packet to use it later");
        if (target instanceof MethodDeclaration method && !method.isStatic()) {
            Optional<String> overridden = overriddenHelper(copy, type);
            if (overridden.isPresent()) {
                return ExtractionResult.unsupported(unresolved + ": it calls " + overridden.get()
                        + ", which is overridden below " + type.getNameAsString());
            }
        }
        context.resolved().add(node);
        ExtractionResult result = extractMethod(copy, type, context.side(), methodsOf(type), context.stack(), true);
        if (result.fields().stream().anyMatch(field -> field instanceof OptionalSchema)) {
            return ExtractionResult.unsupported("Optional tail inside the delegated " + kind + " " + label);
        }
        return result;
    }

    /**
     * A delegate reached only sometimes (a ternary, {@code &&} or {@code ||}), inside a lambda that may run any
     * number of times, or after packet operations in its own arguments, cannot be described as a fixed sequence.
     */
    private static Optional<String> delegateGuard(Expression node, Node scope, JavaPacketSide side) {
        String label = node instanceof MethodCallExpr call ? call.getNameAsString() : node.toString();
        if (insideConditionalExpression(node, scope)) {
            return Optional.of("Packet fields delegated to " + label + " under a condition");
        }
        for (Node current = node; current.getParentNode().isPresent() && current != scope; ) {
            current = current.getParentNode().orElseThrow();
            if (current instanceof LambdaExpr) {
                return Optional.of("Packet fields delegated to " + label + " inside a lambda");
            }
        }
        List<Expression> arguments =
                node instanceof MethodCallExpr call ? call.getArguments() : ((ObjectCreationExpr) node).getArguments();
        boolean readsInArguments = arguments.stream()
                .flatMap(argument -> argument.findAll(MethodCallExpr.class).stream())
                .anyMatch(call -> wireType(call, side) != null);
        if (readsInArguments) return Optional.of("Packet operations inside the arguments of " + label);
        return Optional.empty();
    }

    /** Whether the delegate already uses the name its packet parameter would be renamed to. */
    private static boolean usesName(
            CallableDeclaration<?> callable, int position, String expected, TypeDeclaration<?> owner) {
        if (position < 0 || position >= callable.getParameters().size()) return false;
        if (callable.getParameter(position).getNameAsString().equals(expected)) return false;
        boolean inBody = callable.findAll(NameExpr.class).stream()
                        .anyMatch(name -> name.getNameAsString().equals(expected))
                || callable.findAll(FieldAccessExpr.class).stream()
                        .anyMatch(field -> field.getNameAsString().equals(expected))
                || callable.findAll(VariableDeclarator.class).stream()
                        .anyMatch(variable -> variable.getNameAsString().equals(expected));
        return inBody || owner.getFieldByName(expected).isPresent();
    }

    /** A local helper the delegate calls, at any depth, that a class below the delegate's class overrides. */
    private Optional<String> overriddenHelper(MethodDeclaration delegate, TypeDeclaration<?> type) {
        Map<String, MethodDeclaration> methods = methodsOf(type);
        Deque<MethodDeclaration> pending = new ArrayDeque<>(List.of(delegate));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            for (MethodCallExpr call : pending.removeFirst().findAll(MethodCallExpr.class)) {
                if (!isLocalCall(call) || !seen.add(call.getNameAsString())) continue;
                if (index.overriddenBelow(
                        type.getNameAsString(),
                        call.getNameAsString(),
                        call.getArguments().size())) {
                    return Optional.of(call.getNameAsString());
                }
                MethodDeclaration helper = methods.get(call.getNameAsString());
                if (helper != null) pending.add(helper);
            }
        }
        return Optional.empty();
    }

    /** The method of that name and arity in a type or its superclasses; empty when there is not exactly one. */
    private Optional<MethodDeclaration> findMethod(
            TypeDeclaration<?> type, String name, int arity, CompilationUnit from, int depth) {
        if (depth > 5) return Optional.empty();
        List<MethodDeclaration> candidates = type.getMethodsByName(name).stream()
                .filter(method -> method.getParameters().size() == arity)
                .toList();
        if (candidates.size() > 1) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.getFirst());
        if (!(type instanceof ClassOrInterfaceDeclaration declaration)
                || declaration.getExtendedTypes().isEmpty()) return Optional.empty();
        CompilationUnit unit = type.findCompilationUnit().orElse(from);
        return index.resolve(declaration.getExtendedTypes().get(0).getNameAsString(), unit)
                .flatMap(parent -> findMethod(parent, name, arity, unit, depth + 1));
    }

    /**
     * The declared type of a local variable, a parameter or a field named {@code name}, as written. Two locals of
     * that name in different blocks give {@code var}, which is refused: the block scope is not tracked.
     */
    private static Optional<String> declaredType(String name, ScopeContext context) {
        List<VariableDeclarator> locals = context.method().findAll(VariableDeclarator.class).stream()
                .filter(variable -> variable.getNameAsString().equals(name))
                .toList();
        if (locals.size() > 1) return Optional.of("var");
        if (locals.size() == 1) return Optional.of(locals.getFirst().getTypeAsString());
        for (Parameter parameter : context.method().getParameters()) {
            if (parameter.getNameAsString().equals(name)) return Optional.of(parameter.getTypeAsString());
        }
        return fieldType(name, context.owner());
    }

    private static Optional<String> fieldType(String name, TypeDeclaration<?> owner) {
        return owner.getFieldByName(name)
                .flatMap(field -> field.getVariables().stream()
                        .filter(variable -> variable.getNameAsString().equals(name))
                        .findFirst()
                        .map(VariableDeclarator::getTypeAsString));
    }

    /** The methods of a type by name; a name declared more than once maps to null, so a call to it is refused. */
    private static Map<String, MethodDeclaration> methodsOf(TypeDeclaration<?> type) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        for (MethodDeclaration method : type.getMethods()) {
            String name = method.getNameAsString();
            methods.put(name, methods.containsKey(name) ? null : method);
        }
        return methods;
    }

    /** The position of the packet among call arguments ({@code packet} or {@code response}), or -1. */
    private static int packetArgument(List<Expression> arguments, JavaPacketSide side) {
        String expected = side == JavaPacketSide.INCOMING ? "packet" : "response";
        for (int position = 0; position < arguments.size(); position++) {
            String argument = arguments.get(position).toString();
            if (argument.equals(expected) || argument.equals("this." + expected)) return position;
        }
        return -1;
    }

    /**
     * A copy of the method or constructor whose parameter at {@code position} is renamed to {@code packet} or
     * {@code response}, so the rules that recognise packet operations by that name apply inside it as well.
     */
    private static MethodDeclaration withPacketParameter(
            CallableDeclaration<?> callable, int position, JavaPacketSide side) {
        MethodDeclaration copy;
        if (callable instanceof MethodDeclaration method) copy = method.clone();
        else {
            ConstructorDeclaration constructor = (ConstructorDeclaration) callable;
            copy = new MethodDeclaration(new NodeList<>(), new VoidType(), constructor.getNameAsString());
            copy.setParameters(
                    constructor.getParameters().stream().map(Parameter::clone).collect(NodeList.toNodeList()));
            copy.setBody(constructor.getBody().clone());
        }
        if (position < 0 || position >= copy.getParameters().size()) return copy;
        String expected = side == JavaPacketSide.INCOMING ? "packet" : "response";
        Parameter parameter = copy.getParameter(position);
        String original = parameter.getNameAsString();
        if (original.equals(expected)) return copy;
        parameter.setName(expected);
        copy.findAll(NameExpr.class).stream()
                .filter(name -> name.getNameAsString().equals(original))
                .forEach(name -> name.setName(expected));
        return copy;
    }

    private static boolean insideLoopOf(Node call, Node scope, Set<Node> loopNodes) {
        Node current = call;
        while (current.getParentNode().isPresent() && current != scope) {
            current = current.getParentNode().orElseThrow();
            if (current != scope && loopNodes.contains(current)) return true;
        }
        return false;
    }

    /**
     * Loops written as a list: the count of the collection immediately before a loop over that same collection,
     * keyed by the call that writes or reads the count. Anything else stays a data-dependent loop.
     */
    private static Map<MethodCallExpr, ListLoop> listLoops(MethodDeclaration method, JavaPacketSide side) {
        Map<MethodCallExpr, ListLoop> loops = new IdentityHashMap<>();
        for (Statement statement : method.findAll(Statement.class)) {
            Statement body;
            if (statement instanceof ForEachStmt each) body = each.getBody();
            else if (statement instanceof ForStmt indexed) body = indexed.getBody();
            else if (statement instanceof WhileStmt loop) body = loop.getBody();
            else continue;
            if (!body.findAll(BreakStmt.class).isEmpty()
                    || !body.findAll(ContinueStmt.class).isEmpty()
                    || !body.findAll(ReturnStmt.class).isEmpty()) continue;
            Optional<MethodCallExpr> counter = side == JavaPacketSide.OUTGOING
                    ? previousSibling(statement).flatMap(previous -> outgoingCounter(previous, statement))
                    : incomingCounter(statement, body);
            counter.ifPresent(call -> loops.put(call, new ListLoop(statement, wireType(call, side), body)));
        }
        return loops;
    }

    /**
     * {@code int n = packet.readInt()} followed by a loop that runs {@code n} times. Guards on {@code n} that read
     * nothing from the packet may sit between them; the body must not touch {@code n}.
     */
    private static Optional<MethodCallExpr> incomingCounter(Statement loop, Statement body) {
        Optional<Statement> previous = previousSibling(loop);
        while (previous.isPresent() && previous.get() instanceof IfStmt guard) {
            boolean reads = guard.findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> wireType(call, JavaPacketSide.INCOMING) != null || isLocalCall(call));
            if (reads) return Optional.empty();
            previous = previousSibling(previous.get());
        }
        if (previous.isEmpty()
                || !(previous.get() instanceof ExpressionStmt expression)
                || !(expression.getExpression() instanceof VariableDeclarationExpr declaration)
                || declaration.getVariables().size() != 1) return Optional.empty();
        VariableDeclarator variable = declaration.getVariable(0);
        if (variable.getInitializer().isEmpty() || !(variable.getInitializer().get() instanceof MethodCallExpr call))
            return Optional.empty();
        String countType = wireType(call, JavaPacketSide.INCOMING);
        if (!"int".equals(countType) && !"short".equals(countType)) return Optional.empty();
        String count = variable.getNameAsString();
        boolean bodyTouchesCount = body.findAll(NameExpr.class).stream()
                .anyMatch(name -> name.getNameAsString().equals(count));
        if (bodyTouchesCount) return Optional.empty();

        if (loop instanceof WhileStmt decrementing) {
            return decrementing.getCondition().toString().equals(count + "-- > 0")
                    ? Optional.of(call)
                    : Optional.empty();
        }
        if (!(loop instanceof ForStmt indexed)
                || indexed.getCompare().isEmpty()
                || !(indexed.getCompare().get() instanceof BinaryExpr compare)
                || compare.getOperator() != BinaryExpr.Operator.LESS
                || !compare.getLeft().isNameExpr()
                || !compare.getRight().toString().equals(count)) return Optional.empty();
        return isZeroBasedIncrement(indexed, compare.getLeft().asNameExpr().getNameAsString())
                ? Optional.of(call)
                : Optional.empty();
    }

    /** {@code response.appendInt(xs.size())} followed by a loop over {@code xs}. */
    private static Optional<MethodCallExpr> outgoingCounter(Statement previous, Statement loop) {
        if (!(previous instanceof ExpressionStmt expression)
                || !(expression.getExpression() instanceof MethodCallExpr call)) return Optional.empty();
        String countType = wireType(call, JavaPacketSide.OUTGOING);
        if (!"int".equals(countType) && !"short".equals(countType)) return Optional.empty();
        if (call.getArguments().size() != 1) return Optional.empty();
        Optional<String> collection = sizeOf(call.getArgument(0));
        if (collection.isEmpty()) return Optional.empty();
        if (loop instanceof ForEachStmt each) {
            return each.getIterable().toString().equals(collection.get()) ? Optional.of(call) : Optional.empty();
        }
        ForStmt indexed = (ForStmt) loop;
        if (indexed.getCompare().isEmpty()
                || !(indexed.getCompare().get() instanceof BinaryExpr compare)
                || compare.getOperator() != BinaryExpr.Operator.LESS
                || !compare.getLeft().isNameExpr()
                || !sizeOf(compare.getRight()).equals(collection)) return Optional.empty();
        return isZeroBasedIncrement(indexed, compare.getLeft().asNameExpr().getNameAsString())
                ? Optional.of(call)
                : Optional.empty();
    }

    /** {@code xs.size()} or {@code xs.length} as the text of {@code xs}. */
    private static Optional<String> sizeOf(Expression expression) {
        if (expression instanceof MethodCallExpr size
                && size.getNameAsString().equals("size")
                && size.getArguments().isEmpty()
                && size.getScope().isPresent()) {
            return Optional.of(size.getScope().get().toString());
        }
        if (expression instanceof FieldAccessExpr length
                && length.getNameAsString().equals("length")) {
            return Optional.of(length.getScope().toString());
        }
        return Optional.empty();
    }

    private static boolean isZeroBasedIncrement(ForStmt loop, String index) {
        if (loop.getInitialization().size() != 1
                || !(loop.getInitialization().getFirst().orElseThrow() instanceof VariableDeclarationExpr declaration)
                || declaration.getVariables().size() != 1) return false;
        VariableDeclarator variable = declaration.getVariable(0);
        if (!variable.getNameAsString().equals(index)
                || variable.getInitializer()
                        .map(value -> !value.toString().equals("0"))
                        .orElse(true)) return false;
        if (loop.getUpdate().size() != 1) return false;
        String update = loop.getUpdate().getFirst().orElseThrow().toString();
        return update.equals(index + "++") || update.equals("++" + index) || update.equals(index + " += 1");
    }

    private static Optional<Statement> previousSibling(Statement statement) {
        if (!(statement.getParentNode().orElse(null) instanceof BlockStmt block)) return Optional.empty();
        List<Statement> statements = block.getStatements();
        for (int index = 1; index < statements.size(); index++) {
            if (statements.get(index) == statement) return Optional.of(statements.get(index - 1));
        }
        return Optional.empty();
    }

    private static TryCatchAnalysis analyzeTryCatch(MethodDeclaration method, JavaPacketSide side) {
        Set<MethodCallExpr> callsToSkip = new HashSet<>();
        for (TryStmt tryStatement : method.findAll(TryStmt.class)) {
            if (tryStatement.getCatchClauses().isEmpty()) continue;
            List<String> tryFields = wireTypes(tryStatement.getTryBlock().findAll(MethodCallExpr.class), side);
            for (var catchClause : tryStatement.getCatchClauses()) {
                List<MethodCallExpr> catchCalls = catchClause.getBody().findAll(MethodCallExpr.class);
                List<String> catchFields = wireTypes(catchCalls, side);
                if (tryFields.isEmpty() || catchFields.isEmpty()) continue;
                if (!tryFields.equals(catchFields)) {
                    return new TryCatchAnalysis(
                            Set.of(),
                            Optional.of(
                                    "Different packet operations across try/catch inside " + method.getNameAsString()));
                }
                catchCalls.stream().filter(call -> wireType(call, side) != null).forEach(callsToSkip::add);
            }
        }
        return new TryCatchAnalysis(callsToSkip, Optional.empty());
    }

    private static List<String> wireTypes(List<MethodCallExpr> calls, JavaPacketSide side) {
        return calls.stream()
                .sorted(Comparator.comparing(call -> call.getBegin().orElseThrow()))
                .map(call -> wireType(call, side))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Packet handed to a constructor or method that the scope did not follow; {@code resolved} are those it did. */
    private static Optional<String> externalDelegation(
            MethodDeclaration method, JavaPacketSide side, Set<Node> resolved) {
        if (side == JavaPacketSide.INCOMING) {
            for (ObjectCreationExpr creation : method.findAll(ObjectCreationExpr.class)) {
                if (resolved.contains(creation)) continue;
                boolean receivesHandler = creation.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument ->
                                argument.equals("this") || argument.equals("packet") || argument.equals("this.packet"));
                if (receivesHandler) {
                    return Optional.of("Packet fields delegated to external constructor " + creation.getTypeAsString()
                            + " inside " + method.getNameAsString());
                }
            }
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                boolean receivesPacket = call.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument -> argument.equals("packet") || argument.equals("this.packet"));
                if (receivesPacket && !isLocalCall(call) && !resolved.contains(call)) {
                    return Optional.of("Packet fields delegated to external reader " + call.getNameAsString()
                            + " inside " + method.getNameAsString());
                }
            }
        } else {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                boolean receivesResponse = call.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument -> argument.equals("response") || argument.equals("this.response"));
                if (receivesResponse
                        && wireType(call, side) == null
                        && !isLocalCall(call)
                        && !resolved.contains(call)) {
                    return Optional.of("Packet fields delegated to external serializer " + call.getNameAsString()
                            + " inside " + method.getNameAsString());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> dynamicControlFlow(
            MethodDeclaration method,
            JavaPacketSide side,
            Map<String, MethodDeclaration> methods,
            Map<MethodCallExpr, ListLoop> loops) {
        Set<Node> listLoops = Collections.newSetFromMap(new IdentityHashMap<>());
        loops.values().forEach(loop -> listLoops.add(loop.loop()));
        List<Node> controls = new ArrayList<>();
        controls.addAll(method.findAll(IfStmt.class));
        controls.addAll(method.findAll(SwitchStmt.class));
        controls.addAll(method.findAll(ForStmt.class));
        controls.addAll(method.findAll(ForEachStmt.class));
        controls.addAll(method.findAll(WhileStmt.class));
        controls.addAll(method.findAll(DoStmt.class));
        for (Node control : controls) {
            if (listLoops.contains(control)) continue;
            if (control instanceof IfStmt guard && isOptionalTail(guard, method, side)) continue;
            if (writesPacket(control, side, methods, new HashSet<>())) {
                return Optional.of("Data-dependent packet operations in "
                        + control.getClass().getSimpleName() + " inside " + method.getNameAsString());
            }
        }
        return Optional.empty();
    }

    /** Whether a node reads or writes packet fields, directly, through local helpers, or by handing the packet on. */
    private static boolean writesPacket(
            Node node, JavaPacketSide side, Map<String, MethodDeclaration> methods, Set<String> visited) {
        for (ObjectCreationExpr creation : node.findAll(ObjectCreationExpr.class)) {
            if (packetArgument(creation.getArguments(), side) >= 0) return true;
        }
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            if (wireType(call, side) != null) return true;
            String name = call.getNameAsString();
            boolean local = isLocalCall(call) && methods.containsKey(name);
            if (!local && packetArgument(call.getArguments(), side) >= 0) return true;
            if (local && methods.get(name) == null) return true;
            if (local && visited.add(name) && writesPacket(methods.get(name), side, methods, visited)) return true;
        }
        return false;
    }

    private static boolean isOptionalTail(IfStmt guard, MethodDeclaration method, JavaPacketSide side) {
        if (side != JavaPacketSide.INCOMING || guard.getElseStmt().isPresent()) return false;
        String condition = guard.getCondition().toString();
        if (!condition.equals("this.packet.bytesAvailable() > 0") && !condition.equals("packet.bytesAvailable() > 0"))
            return false;
        List<MethodCallExpr> fields = guard.findAll(MethodCallExpr.class).stream()
                .filter(call -> wireType(call, side) != null)
                .toList();
        if (fields.size() != 1 || inferredName(fields.getFirst()).isEmpty()) return false;
        MethodCallExpr field = fields.getFirst();
        return method.findAll(MethodCallExpr.class).stream()
                .filter(call -> wireType(call, side) != null)
                .noneMatch(call ->
                        call.getBegin().orElseThrow().isAfter(field.getBegin().orElseThrow()));
    }

    private static String wireType(MethodCallExpr call, JavaPacketSide side) {
        String expectedScope = side == JavaPacketSide.INCOMING ? "packet" : "response";
        if (call.getScope().isEmpty()) return null;
        String scope = call.getScope().orElseThrow().toString();
        if (!scope.equals(expectedScope) && !scope.equals("this." + expectedScope)) return null;
        return (side == JavaPacketSide.INCOMING ? INCOMING_TYPES : OUTGOING_TYPES).get(call.getNameAsString());
    }

    /** A read or append on the packet that the analyzer has no wire type for, such as readDouble or appendRawBytes. */
    private static boolean isUnknownPacketOperation(MethodCallExpr call, JavaPacketSide side) {
        String expectedScope = side == JavaPacketSide.INCOMING ? "packet" : "response";
        String prefix = side == JavaPacketSide.INCOMING ? "read" : "append";
        if (call.getScope().isEmpty()) return false;
        String scope = call.getScope().orElseThrow().toString();
        return (scope.equals(expectedScope) || scope.equals("this." + expectedScope))
                && call.getNameAsString().startsWith(prefix);
    }

    private static boolean insideConditionalExpression(Node node, Node scope) {
        Node current = node;
        while (current.getParentNode().isPresent() && current != scope) {
            current = current.getParentNode().orElseThrow();
            if (current instanceof ConditionalExpr) return true;
            if (current instanceof BinaryExpr binary
                    && (binary.getOperator() == BinaryExpr.Operator.AND
                            || binary.getOperator() == BinaryExpr.Operator.OR)) return true;
        }
        return false;
    }

    private static boolean isLocalCall(MethodCallExpr call) {
        return call.getScope().isEmpty()
                || call.getScope().map(scope -> scope.toString().equals("this")).orElse(false);
    }

    private static String inferredName(MethodCallExpr call) {
        Node current = call;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().orElseThrow();
            if (current instanceof com.github.javaparser.ast.expr.AssignExpr assignment
                    && assignment.getOperator() == com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN
                    && assignment.getTarget().isNameExpr()) {
                return assignment.getTarget().asNameExpr().getNameAsString();
            }
            if (current instanceof com.github.javaparser.ast.body.VariableDeclarator variable) {
                return variable.getNameAsString();
            }
            if (current instanceof com.github.javaparser.ast.stmt.Statement) return "";
        }
        return "";
    }
}

record ListLoop(Statement loop, String countType, Statement body) {}

record ScopeContext(
        MethodDeclaration method,
        TypeDeclaration<?> owner,
        JavaPacketSide side,
        Map<String, MethodDeclaration> methods,
        Deque<String> stack,
        TryCatchAnalysis tryCatch,
        Map<MethodCallExpr, ListLoop> loops,
        Set<Node> resolved,
        boolean strict) {}

record TryCatchAnalysis(Set<MethodCallExpr> callsToSkip, Optional<String> unsupportedReason) {
    TryCatchAnalysis {
        callsToSkip = Set.copyOf(callsToSkip);
        unsupportedReason = unsupportedReason == null ? Optional.empty() : unsupportedReason;
    }
}
