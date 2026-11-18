package polaris.messages.contracts;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The Java sources under one root, by simple class name, so a packet delegated to another class can be read
 * there. No symbol solver: a name resolves through the file itself, its explicit imports, its package, or a name
 * that is unique in the root; anything else stays unresolved.
 */
final class JavaSourceIndex {
    private static final Map<Path, JavaSourceIndex> INDEXES = new ConcurrentHashMap<>();

    private final Map<String, List<Path>> byName = new HashMap<>();
    private final Map<Path, CompilationUnit> units = new ConcurrentHashMap<>();
    private final Map<Path, String> texts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> overrides = new ConcurrentHashMap<>();

    private JavaSourceIndex(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.computeIfAbsent(
                                    path.getFileName().toString().replaceFirst("\\.java$", ""),
                                    name -> new ArrayList<>())
                            .add(path.toAbsolutePath().normalize()));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** The index of the nearest enclosing {@code java} directory: the source root, or the fixtures' folder. */
    static JavaSourceIndex forSource(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        Path root = absolute.getParent();
        for (Path current = absolute.getParent(); current != null; current = current.getParent()) {
            if (current.getFileName() != null
                    && current.getFileName().toString().equals("java")) {
                root = current;
                break;
            }
        }
        return INDEXES.computeIfAbsent(root, JavaSourceIndex::new);
    }

    CompilationUnit unit(Path path) {
        return units.computeIfAbsent(path.toAbsolutePath().normalize(), absolute -> {
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25);
            try {
                return StaticJavaParser.parse(absolute);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
    }

    /** A type named in {@code from}, by its simple or qualified name, or empty when it is not exactly one type. */
    Optional<TypeDeclaration<?>> resolve(String typeName, CompilationUnit from) {
        String name = simpleName(typeName);
        for (TypeDeclaration<?> local : from.findAll(TypeDeclaration.class)) {
            if (local.getNameAsString().equals(name)) return Optional.of(local);
        }
        List<Path> candidates = byName.getOrDefault(name, List.of());
        Path chosen = null;
        if (candidates.size() == 1) chosen = candidates.getFirst();
        else if (candidates.size() > 1) {
            for (ImportDeclaration declaration : from.getImports()) {
                if (declaration.isAsterisk() || declaration.isStatic()) continue;
                String imported = declaration.getNameAsString();
                if (!imported.endsWith("." + name)) continue;
                String suffix = imported.replace('.', '/') + ".java";
                chosen = candidates.stream()
                        .filter(path -> path.toString().replace('\\', '/').endsWith(suffix))
                        .findFirst()
                        .orElse(null);
            }
            if (chosen == null && from.getStorage().isPresent()) {
                Path directory = from.getStorage()
                        .get()
                        .getPath()
                        .toAbsolutePath()
                        .normalize()
                        .getParent();
                chosen = candidates.stream()
                        .filter(path -> path.getParent().equals(directory))
                        .findFirst()
                        .orElse(null);
            }
        }
        if (chosen == null) return Optional.empty();
        return unit(chosen).getTypes().stream()
                .filter(type -> type.getNameAsString().equals(name))
                .<TypeDeclaration<?>>map(type -> type)
                .findFirst();
    }

    /** Whether a class below {@code typeName} declares a method of that name and arity, at any depth. */
    boolean overriddenBelow(String typeName, String method, int arity) {
        return overrides.computeIfAbsent(
                typeName + "#" + method + "/" + arity, key -> overriddenBelow(typeName, method, arity, 0));
    }

    private boolean overriddenBelow(String typeName, String method, int arity, int depth) {
        if (depth > 4) return true;
        Pattern extendsType = Pattern.compile("\\bextends\\s+" + Pattern.quote(typeName) + "\\b");
        for (List<Path> paths : byName.values()) {
            for (Path path : paths) {
                if (!extendsType.matcher(text(path)).find()) continue;
                for (ClassOrInterfaceDeclaration subclass : unit(path).findAll(ClassOrInterfaceDeclaration.class)) {
                    boolean extendsIt = subclass.getExtendedTypes().stream()
                            .anyMatch(type -> type.getNameAsString().equals(typeName));
                    if (!extendsIt) continue;
                    boolean declares = subclass.getMethodsByName(method).stream()
                            .anyMatch(candidate -> candidate.getParameters().size() == arity);
                    if (declares || overriddenBelow(subclass.getNameAsString(), method, arity, depth + 1)) return true;
                }
            }
        }
        return false;
    }

    private String text(Path path) {
        return texts.computeIfAbsent(path, absolute -> {
            try {
                return Files.readString(absolute, StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
    }

    static String simpleName(String typeName) {
        String withoutArguments = typeName.replaceAll("<.*>", "").trim();
        return withoutArguments.substring(withoutArguments.lastIndexOf('.') + 1);
    }
}
