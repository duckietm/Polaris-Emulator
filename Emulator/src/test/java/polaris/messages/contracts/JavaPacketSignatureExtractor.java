package polaris.messages.contracts;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
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

    ExtractionResult extract(Path source, JavaPacketSide side, String rootMethod) throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25);
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
        if (root == null) throw new IllegalArgumentException("Method " + rootMethod + " not found in " + source);
        return extractMethod(root, side, methods, new ArrayDeque<>());
    }

    private ExtractionResult extractMethod(
            MethodDeclaration method,
            JavaPacketSide side,
            Map<String, MethodDeclaration> methods,
            Deque<String> stack) {
        String methodName = method.getNameAsString();
        if (stack.contains(methodName)) {
            return ExtractionResult.unsupported("Recursive packet helper call: " + methodName);
        }
        stack.addLast(methodName);
        try {
            Map<MethodCallExpr, ListLoop> loops = listLoops(method, side);
            Optional<String> dynamic = dynamicControlFlow(method, side, methods, loops);
            if (dynamic.isPresent()) return ExtractionResult.unsupported(dynamic.get());

            TryCatchAnalysis tryCatch = analyzeTryCatch(method, side);
            if (tryCatch.unsupportedReason().isPresent()) {
                return ExtractionResult.unsupported(tryCatch.unsupportedReason().orElseThrow());
            }

            ExtractionResult fields = extractScope(
                    method.getBody().orElseThrow(), new ScopeContext(method, side, methods, stack, tryCatch, loops));
            if (fields.unsupportedReason().isPresent()) return fields;
            Optional<String> delegation = externalDelegation(method, side);
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
        List<MethodCallExpr> calls = scope.findAll(MethodCallExpr.class).stream()
                .sorted(Comparator.comparing(
                        call -> call.getBegin().orElseThrow(),
                        Comparator.comparingInt((com.github.javaparser.Position position) -> position.line)
                                .thenComparingInt(position -> position.column)))
                .toList();
        List<WireSchema> fields = new ArrayList<>();
        for (MethodCallExpr call : calls) {
            if (context.tryCatch().callsToSkip().contains(call)) continue;
            if (insideLoopOf(call, scope, loopNodes)) continue;
            ListLoop loop = context.loops().get(call);
            if (loop != null) {
                ExtractionResult item = extractScope(loop.body(), context);
                if (item.unsupportedReason().isPresent()) return item;
                fields.add(new ListSchema(loop.countType(), item.fields()));
                continue;
            }
            String wireType = wireType(call, context.side());
            if (wireType != null) {
                ScalarSchema field = new ScalarSchema(wireType, inferredName(call));
                IfStmt guard = call.findAncestor(IfStmt.class).orElse(null);
                fields.add(
                        guard != null && isOptionalTail(guard, context.method(), context.side())
                                ? new OptionalSchema(inferredName(call), List.of(field))
                                : field);
                continue;
            }
            if (isLocalCall(call) && context.methods().containsKey(call.getNameAsString())) {
                ExtractionResult helper = extractMethod(
                        context.methods().get(call.getNameAsString()),
                        context.side(),
                        context.methods(),
                        context.stack());
                if (helper.unsupportedReason().isPresent()) return helper;
                fields.addAll(helper.fields());
            }
        }
        return ExtractionResult.supported(fields);
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
            if (!(statement instanceof ForEachStmt) && !(statement instanceof ForStmt)) continue;
            Statement body = statement instanceof ForEachStmt each ? each.getBody() : ((ForStmt) statement).getBody();
            if (!body.findAll(BreakStmt.class).isEmpty()
                    || !body.findAll(ContinueStmt.class).isEmpty()
                    || !body.findAll(ReturnStmt.class).isEmpty()) continue;
            Optional<Statement> previous = previousSibling(statement);
            if (previous.isEmpty()) continue;
            Optional<MethodCallExpr> counter =
                    side == JavaPacketSide.OUTGOING ? outgoingCounter(previous.get(), statement) : Optional.empty();
            counter.ifPresent(call -> loops.put(call, new ListLoop(statement, wireType(call, side), body)));
        }
        return loops;
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

    private static Optional<String> externalDelegation(MethodDeclaration method, JavaPacketSide side) {
        if (side == JavaPacketSide.INCOMING) {
            for (ObjectCreationExpr creation : method.findAll(ObjectCreationExpr.class)) {
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
                if (receivesPacket && !isLocalCall(call)) {
                    return Optional.of("Packet fields delegated to external reader " + call.getNameAsString()
                            + " inside " + method.getNameAsString());
                }
            }
        } else {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                boolean receivesResponse = call.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument -> argument.equals("response") || argument.equals("this.response"));
                if (receivesResponse && wireType(call, side) == null && !isLocalCall(call)) {
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

    /** Whether a node reads or writes packet fields, directly or through local helper methods. */
    private static boolean writesPacket(
            Node node, JavaPacketSide side, Map<String, MethodDeclaration> methods, Set<String> visited) {
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            if (wireType(call, side) != null) return true;
            String name = call.getNameAsString();
            if (isLocalCall(call)
                    && methods.containsKey(name)
                    && visited.add(name)
                    && writesPacket(methods.get(name), side, methods, visited)) return true;
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
        JavaPacketSide side,
        Map<String, MethodDeclaration> methods,
        Deque<String> stack,
        TryCatchAnalysis tryCatch,
        Map<MethodCallExpr, ListLoop> loops) {}

record TryCatchAnalysis(Set<MethodCallExpr> callsToSkip, Optional<String> unsupportedReason) {
    TryCatchAnalysis {
        callsToSkip = Set.copyOf(callsToSkip);
        unsupportedReason = unsupportedReason == null ? Optional.empty() : unsupportedReason;
    }
}
