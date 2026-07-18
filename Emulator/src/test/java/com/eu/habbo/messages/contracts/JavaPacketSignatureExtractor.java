package com.eu.habbo.messages.contracts;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
            Optional<String> dynamic = dynamicControlFlow(method, side);
            if (dynamic.isPresent()) return ExtractionResult.unsupported(dynamic.get());

            TryCatchAnalysis tryCatch = analyzeTryCatch(method, side);
            if (tryCatch.unsupportedReason().isPresent()) {
                return ExtractionResult.unsupported(tryCatch.unsupportedReason().orElseThrow());
            }

            List<MethodCallExpr> calls = method.getBody().orElseThrow().findAll(MethodCallExpr.class).stream()
                    .sorted(Comparator.comparing(
                            call -> call.getBegin().orElseThrow(),
                            Comparator.comparingInt((com.github.javaparser.Position position) -> position.line)
                                    .thenComparingInt(position -> position.column)))
                    .toList();
            List<WireSchema> fields = new ArrayList<>();
            for (MethodCallExpr call : calls) {
                if (tryCatch.callsToSkip().contains(call)) continue;
                String wireType = wireType(call, side);
                if (wireType != null) {
                    fields.add(new ScalarSchema(wireType, inferredName(call)));
                    continue;
                }
                if (isLocalCall(call) && methods.containsKey(call.getNameAsString())) {
                    ExtractionResult helper = extractMethod(methods.get(call.getNameAsString()), side, methods, stack);
                    if (helper.unsupportedReason().isPresent()) return helper;
                    fields.addAll(helper.fields());
                }
            }
            Optional<String> delegation = externalDelegation(method, side);
            if (delegation.isPresent()) return ExtractionResult.unsupported(delegation.get());
            return ExtractionResult.supported(fields);
        } finally {
            stack.removeLast();
        }
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
                            Optional.of("Different packet operations across try/catch inside "
                                    + method.getNameAsString()));
                }
                catchCalls.stream()
                        .filter(call -> wireType(call, side) != null)
                        .forEach(callsToSkip::add);
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
                        .anyMatch(argument -> argument.equals("this")
                                || argument.equals("packet")
                                || argument.equals("this.packet"));
                if (receivesHandler) {
                    return Optional.of("Packet fields delegated to external constructor "
                            + creation.getTypeAsString() + " inside " + method.getNameAsString());
                }
            }
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                boolean receivesPacket = call.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument -> argument.equals("packet") || argument.equals("this.packet"));
                if (receivesPacket && !isLocalCall(call)) {
                    return Optional.of("Packet fields delegated to external reader "
                            + call.getNameAsString() + " inside " + method.getNameAsString());
                }
            }
        } else {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                boolean receivesResponse = call.getArguments().stream()
                        .map(Object::toString)
                        .anyMatch(argument -> argument.equals("response") || argument.equals("this.response"));
                if (receivesResponse && wireType(call, side) == null && !isLocalCall(call)) {
                    return Optional.of("Packet fields delegated to external serializer "
                            + call.getNameAsString() + " inside " + method.getNameAsString());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> dynamicControlFlow(MethodDeclaration method, JavaPacketSide side) {
        List<Node> controls = new ArrayList<>();
        controls.addAll(method.findAll(IfStmt.class));
        controls.addAll(method.findAll(SwitchStmt.class));
        controls.addAll(method.findAll(ForStmt.class));
        controls.addAll(method.findAll(ForEachStmt.class));
        controls.addAll(method.findAll(WhileStmt.class));
        controls.addAll(method.findAll(DoStmt.class));
        for (Node control : controls) {
            if (control.findAll(MethodCallExpr.class).stream().anyMatch(call -> wireType(call, side) != null)) {
                return Optional.of(
                        "Data-dependent packet operations in " + control.getClass().getSimpleName()
                                + " inside " + method.getNameAsString());
            }
        }
        return Optional.empty();
    }

    private static String wireType(MethodCallExpr call, JavaPacketSide side) {
        String expectedScope = side == JavaPacketSide.INCOMING ? "packet" : "response";
        if (call.getScope().isEmpty()) return null;
        String scope = call.getScope().orElseThrow().toString();
        if (!scope.equals(expectedScope) && !scope.equals("this." + expectedScope)) return null;
        return (side == JavaPacketSide.INCOMING ? INCOMING_TYPES : OUTGOING_TYPES)
                .get(call.getNameAsString());
    }

    private static boolean isLocalCall(MethodCallExpr call) {
        return call.getScope().isEmpty()
                || call.getScope().map(scope -> scope.toString().equals("this")).orElse(false);
    }

    private static String inferredName(MethodCallExpr call) {
        Node current = call;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().orElseThrow();
            if (current instanceof com.github.javaparser.ast.body.VariableDeclarator variable) {
                return variable.getNameAsString();
            }
            if (current instanceof com.github.javaparser.ast.stmt.Statement) return "";
        }
        return "";
    }
}

record TryCatchAnalysis(Set<MethodCallExpr> callsToSkip, Optional<String> unsupportedReason) {
    TryCatchAnalysis {
        callsToSkip = Set.copyOf(callsToSkip);
        unsupportedReason = unsupportedReason == null ? Optional.empty() : unsupportedReason;
    }
}
