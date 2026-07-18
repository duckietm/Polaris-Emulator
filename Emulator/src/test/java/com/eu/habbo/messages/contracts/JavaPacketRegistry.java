package com.eu.habbo.messages.contracts;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class JavaPacketRegistry {
    enum Direction {
        CLIENT_TO_SERVER("client_to_server"),
        SERVER_TO_CLIENT("server_to_client");

        private final String manifestName;

        Direction(String manifestName) {
            this.manifestName = manifestName;
        }

        String manifestName() {
            return manifestName;
        }
    }

    record RegisteredPacket(Direction direction, int header, String symbol, Path source) {
    }

    record DeclaredPacket(Direction direction, int header, String symbol) {
    }

    private record HeaderTable(Map<String, Integer> values, Map<String, String> canonicalSymbols) {
        int require(String symbol, Direction direction) {
            Integer header = values.get(symbol);
            if (header == null) throw new IllegalArgumentException(
                    direction.manifestName() + " symbol " + symbol + " has no numeric header declaration");
            return header;
        }

        String canonical(String symbol) {
            return canonicalSymbols.getOrDefault(symbol, symbol);
        }
    }

    private final Map<String, RegisteredPacket> active;
    private final List<DeclaredPacket> declaredOnly;

    private JavaPacketRegistry(Map<String, RegisteredPacket> active, List<DeclaredPacket> declaredOnly) {
        this.active = Map.copyOf(active);
        this.declaredOnly = List.copyOf(declaredOnly);
    }

    static JavaPacketRegistry discover(Path javaSourceRoot) throws IOException {
        Path incomingFile = javaSourceRoot.resolve("com/eu/habbo/messages/incoming/Incoming.java");
        Path outgoingFile = javaSourceRoot.resolve("com/eu/habbo/messages/outgoing/Outgoing.java");
        Path packetManager = javaSourceRoot.resolve("com/eu/habbo/messages/PacketManager.java");
        HeaderTable incoming = parseHeaders(incomingFile);
        HeaderTable outgoing = parseHeaders(outgoingFile);
        Map<String, List<Path>> classes = indexClasses(
                javaSourceRoot.resolve("com/eu/habbo/messages/incoming"));

        List<RegisteredPacket> discovered = new ArrayList<>();
        CompilationUnit manager = parse(packetManager);
        for (MethodCallExpr call : manager.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("registerHandler") || call.getArguments().size() < 2) continue;
            Optional<String> symbol = referencedSymbol(call.getArgument(0), "Incoming");
            Optional<String> className = classLiteralName(call.getArgument(1));
            if (symbol.isEmpty() || className.isEmpty()) continue;
            int header = incoming.require(symbol.get(), Direction.CLIENT_TO_SERVER);
            if (header <= 0) continue;
            Path source = requireUniqueSource(classes, className.get());
            discovered.add(new RegisteredPacket(Direction.CLIENT_TO_SERVER, header, symbol.get(), source));
        }

        Path outgoingRoot = javaSourceRoot.resolve("com/eu/habbo/messages/outgoing");
        try (Stream<Path> files = Files.walk(outgoingRoot)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                if (source.equals(outgoingFile)) continue;
                CompilationUnit unit = parse(source);
                for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                    if (!call.getNameAsString().equals("init") || call.getArguments().isEmpty()) continue;
                    Optional<String> symbol = referencedSymbol(call.getArgument(0), "Outgoing");
                    if (symbol.isEmpty()) continue;
                    int header = outgoing.require(symbol.get(), Direction.SERVER_TO_CLIENT);
                    if (header <= 0) continue;
                    discovered.add(new RegisteredPacket(Direction.SERVER_TO_CLIENT, header, symbol.get(), source));
                }
            }
        }

        Map<String, RegisteredPacket> active = new LinkedHashMap<>();
        Map<String, RegisteredPacket> byHeader = new HashMap<>();
        for (RegisteredPacket packet : discovered.stream()
                .distinct()
                .sorted(Comparator.comparing((RegisteredPacket packet) -> packet.direction().manifestName())
                        .thenComparingInt(RegisteredPacket::header)
                        .thenComparing(RegisteredPacket::symbol))
                .toList()) {
            String key = key(packet.direction(), packet.header());
            RegisteredPacket previous = byHeader.putIfAbsent(key, packet);
            if (previous != null && !previous.symbol().equals(packet.symbol())) {
                HeaderTable table = packet.direction() == Direction.CLIENT_TO_SERVER ? incoming : outgoing;
                String previousCanonical = table.canonical(previous.symbol());
                String packetCanonical = table.canonical(packet.symbol());
                if (!previousCanonical.equals(packetCanonical)) {
                    throw new IllegalArgumentException(
                            "duplicate active " + packet.direction().manifestName() + " header " + packet.header()
                                    + ": " + previous.symbol() + " and " + packet.symbol());
                }
                if (packet.symbol().equals(packetCanonical)) {
                    byHeader.put(key, packet);
                    active.put(key, packet);
                }
                continue;
            }
            active.put(key, packet);
        }

        List<DeclaredPacket> declaredOnly = new ArrayList<>();
        addDeclaredOnly(declaredOnly, active, Direction.CLIENT_TO_SERVER, incoming);
        addDeclaredOnly(declaredOnly, active, Direction.SERVER_TO_CLIENT, outgoing);
        return new JavaPacketRegistry(active, declaredOnly);
    }

    RegisteredPacket require(Direction direction, int header) {
        RegisteredPacket packet = active.get(key(direction, header));
        if (packet == null) throw new IllegalArgumentException(
                "no active " + direction.manifestName() + " packet for header " + header);
        return packet;
    }

    List<RegisteredPacket> active() {
        return active.values().stream()
                .sorted(Comparator.comparing((RegisteredPacket packet) -> packet.direction().manifestName())
                        .thenComparingInt(RegisteredPacket::header))
                .toList();
    }

    List<DeclaredPacket> declaredOnly() {
        return declaredOnly;
    }

    private static void addDeclaredOnly(
            List<DeclaredPacket> result,
            Map<String, RegisteredPacket> active,
            Direction direction,
            HeaderTable declarations) {
        for (Map.Entry<String, Integer> declaration : declarations.values().entrySet()) {
            if (declaration.getValue() <= 0) continue;
            RegisteredPacket packet = active.get(key(direction, declaration.getValue()));
            if (packet == null || !declarations.canonical(packet.symbol())
                    .equals(declarations.canonical(declaration.getKey()))) {
                result.add(new DeclaredPacket(direction, declaration.getValue(), declaration.getKey()));
            }
        }
    }

    private static HeaderTable parseHeaders(Path path) throws IOException {
        CompilationUnit unit = parse(path);
        Map<String, Expression> declarations = new LinkedHashMap<>();
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            for (var variable : field.getVariables()) {
                if (!variable.getType().isPrimitiveType()
                        || !variable.getType().asPrimitiveType().getType().asString().equals("int")
                        || variable.getInitializer().isEmpty()) continue;
                declarations.put(variable.getNameAsString(), variable.getInitializer().orElseThrow());
            }
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, Expression> declaration : declarations.entrySet()) {
                if (headers.containsKey(declaration.getKey())) continue;
                Integer value = integerLiteral(declaration.getValue());
                if (value == null && declaration.getValue().isNameExpr()) {
                    value = headers.get(declaration.getValue().asNameExpr().getNameAsString());
                }
                if (value != null) {
                    headers.put(declaration.getKey(), value);
                    changed = true;
                }
            }
        } while (changed);
        if (headers.size() != declarations.size()) {
            List<String> unresolved = declarations.keySet().stream()
                    .filter(symbol -> !headers.containsKey(symbol))
                    .toList();
            throw new IllegalArgumentException("unresolved numeric header declarations in " + path + ": " + unresolved);
        }
        Map<String, String> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, Expression> declaration : declarations.entrySet()) {
            String symbol = declaration.getKey();
            Expression expression = declaration.getValue();
            String root = symbol;
            while (expression.isNameExpr()) {
                root = expression.asNameExpr().getNameAsString();
                expression = declarations.get(root);
                if (expression == null) break;
            }
            canonical.put(symbol, root);
        }
        return new HeaderTable(Map.copyOf(headers), Map.copyOf(canonical));
    }

    private static Integer integerLiteral(Expression expression) {
        if (expression.isIntegerLiteralExpr()) return expression.asIntegerLiteralExpr().asNumber().intValue();
        if (expression.isUnaryExpr()
                && expression.asUnaryExpr().getOperator()
                == com.github.javaparser.ast.expr.UnaryExpr.Operator.MINUS
                && expression.asUnaryExpr().getExpression().isIntegerLiteralExpr()) {
            return -expression.asUnaryExpr().getExpression().asIntegerLiteralExpr().asNumber().intValue();
        }
        return null;
    }

    private static Optional<String> referencedSymbol(Expression expression, String owner) {
        if (!expression.isFieldAccessExpr()) return Optional.empty();
        FieldAccessExpr access = expression.asFieldAccessExpr();
        if (!access.getScope().toString().equals(owner)) return Optional.empty();
        return Optional.of(access.getNameAsString());
    }

    private static Optional<String> classLiteralName(Expression expression) {
        if (!expression.isClassExpr()) return Optional.empty();
        String type = expression.asClassExpr().getType().asString();
        return Optional.of(type.substring(type.lastIndexOf('.') + 1));
    }

    private static Map<String, List<Path>> indexClasses(Path root) throws IOException {
        Map<String, List<Path>> result = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                String fileName = path.getFileName().toString();
                result.computeIfAbsent(fileName.substring(0, fileName.length() - 5), ignored -> new ArrayList<>())
                        .add(path);
            }
        }
        return result;
    }

    private static Path requireUniqueSource(Map<String, List<Path>> classes, String className) {
        List<Path> matches = classes.getOrDefault(className, List.of());
        if (matches.isEmpty()) throw new IllegalArgumentException("source for " + className + " is missing");
        if (matches.size() > 1) throw new IllegalArgumentException(
                "source for " + className + " is ambiguous: " + matches);
        return matches.getFirst();
    }

    private static CompilationUnit parse(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Java source is missing: " + path);
        return StaticJavaParser.parse(path);
    }

    private static String key(Direction direction, int header) {
        return direction.manifestName() + ':' + header;
    }
}
