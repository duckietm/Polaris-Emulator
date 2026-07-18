package com.eu.habbo.database.migrations;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationCatalog {
    public static final int BASELINE_VERSION = 27;
    private static final String RESOURCE_ROOT = "db/migrations";
    private static final Pattern SCRIPT_NAME = Pattern.compile("^(\\d{3})_([A-Za-z0-9_]+)\\.sql$");
    private static final Pattern LOWERCASE_DESCRIPTION = Pattern.compile("^[a-z0-9_]+$");

    private final List<MigrationDescriptor> migrations;

    private MigrationCatalog(List<MigrationDescriptor> migrations) {
        this.migrations = List.copyOf(migrations);
    }

    public List<MigrationDescriptor> migrations() {
        return this.migrations;
    }

    public static MigrationCatalog load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try {
            Map<String, String> resources = discoverResources(classLoader);
            if (resources.isEmpty()) {
                throw new MigrationValidationException(
                        "No packaged migrations found under " + RESOURCE_ROOT);
            }
            return fromResources(resources);
        } catch (IOException | URISyntaxException error) {
            throw new MigrationValidationException("Unable to discover packaged migrations", error);
        }
    }

    static MigrationCatalog fromResources(Map<String, String> resources) {
        Objects.requireNonNull(resources, "resources");
        List<MigrationDescriptor> descriptors = new ArrayList<>();
        Map<Integer, String> scriptsByVersion = new HashMap<>();

        for (Map.Entry<String, String> resource : resources.entrySet()) {
            String scriptName = resource.getKey();
            Matcher matcher = SCRIPT_NAME.matcher(scriptName);
            if (!matcher.matches()) {
                throw new MigrationValidationException(
                        "Invalid migration filename '" + scriptName
                                + "'; expected NNN_description.sql");
            }

            int version = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2);
            if (version == 0) {
                throw new MigrationValidationException(
                        "Migration version must be positive: " + scriptName);
            }
            if (version > BASELINE_VERSION && !LOWERCASE_DESCRIPTION.matcher(description).matches()) {
                throw new MigrationValidationException(
                        "Managed migration descriptions must be lowercase: " + scriptName);
            }

            String existing = scriptsByVersion.putIfAbsent(version, scriptName);
            if (existing != null) {
                throw new MigrationValidationException(
                        "Duplicate migration version " + version + ": " + existing + " and " + scriptName);
            }

            String sql = resource.getValue();
            descriptors.add(new MigrationDescriptor(
                    version,
                    description,
                    scriptName,
                    sql,
                    sha256(sql)));
        }

        descriptors.sort(Comparator.comparingInt(MigrationDescriptor::version));
        validateManagedSequence(descriptors);
        return new MigrationCatalog(descriptors);
    }

    private static void validateManagedSequence(List<MigrationDescriptor> descriptors) {
        int expected = BASELINE_VERSION + 1;
        for (MigrationDescriptor descriptor : descriptors) {
            if (descriptor.version() <= BASELINE_VERSION) continue;
            if (descriptor.version() != expected) {
                throw new MigrationValidationException(
                        "Expected managed migration version " + expected
                                + " but found " + descriptor.version()
                                + " in " + descriptor.scriptName());
            }
            expected++;
        }
    }

    private static Map<String, String> discoverResources(ClassLoader classLoader)
            throws IOException, URISyntaxException {
        Map<String, String> resources = new LinkedHashMap<>();
        Enumeration<URL> roots = classLoader.getResources(RESOURCE_ROOT);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            switch (root.getProtocol()) {
                case "file" -> discoverDirectory(resources, classLoader, root.toURI());
                case "jar" -> discoverJar(resources, classLoader, root);
                default -> throw new MigrationValidationException(
                        "Unsupported migration resource protocol: " + root.getProtocol());
            }
        }
        return resources;
    }

    private static void discoverDirectory(
            Map<String, String> resources,
            ClassLoader classLoader,
            URI root) throws IOException {
        try (var paths = Files.list(Path.of(root))) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .forEach(name -> resources.put(name, readResource(classLoader, name)));
        }
    }

    private static void discoverJar(
            Map<String, String> resources,
            ClassLoader classLoader,
            URL root) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            List<String> names = new ArrayList<>();
            Enumeration<JarEntry> entries = jar.entries();
            String prefix = RESOURCE_ROOT + "/";
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".sql")) continue;
                String relative = name.substring(prefix.length());
                if (!relative.contains("/")) names.add(relative);
            }
            Collections.sort(names);
            for (String name : names) resources.put(name, readResource(classLoader, name));
        }
    }

    private static String readResource(ClassLoader classLoader, String name) {
        String path = RESOURCE_ROOT + "/" + name;
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new MigrationValidationException("Migration resource disappeared: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new MigrationValidationException("Unable to read migration resource " + path, error);
        }
    }

    static String sha256(String sql) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
