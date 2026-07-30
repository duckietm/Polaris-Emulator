package com.eu.habbo.habbohotel.items;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared discovery and construction for registered WIRED compatibility tests. */
public final class WiredInteractionRegistryFixture {
    private static final Path ITEM_MANAGER =
            Path.of("src", "main", "java", "com", "eu", "habbo", "habbohotel", "items", "ItemManager.java");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([A-Za-z0-9_$.]+);", Pattern.MULTILINE);
    private static final Pattern INTERACTION = Pattern.compile(
            "new\\s+ItemInteraction\\(\\s*\"(wf_[^\"]+)\"\\s*,\\s*([A-Za-z0-9_$.]+)\\.class\\s*\\)", Pattern.MULTILINE);

    private WiredInteractionRegistryFixture() {}

    public static List<Registration> registrations() throws Exception {
        return registrations(source());
    }

    public static Set<Class<? extends InteractionWired>> wiredTypes() throws Exception {
        String source = source();
        Map<String, String> imports = imports(source);
        Set<Class<? extends InteractionWired>> result = new LinkedHashSet<>();
        for (Registration registration : registrations(source)) {
            Class<?> type = resolveType(imports, registration.sourceType());
            if (InteractionWired.class.isAssignableFrom(type)) {
                result.add(type.asSubclass(InteractionWired.class));
            }
        }
        return result;
    }

    public static InteractionWired instantiate(Class<? extends InteractionWired> type) throws Exception {
        Constructor<? extends InteractionWired> constructor =
                type.getConstructor(int.class, int.class, Item.class, String.class, int.class, int.class);
        Item baseItem = mock(Item.class);
        when(baseItem.getSpriteId()).thenReturn(123);
        when(baseItem.getName()).thenReturn("wired_fixture");
        return constructor.newInstance(4242, 7, baseItem, "0", 0, 0);
    }

    public static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        if (result instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            result = invocation.getCause();
        }
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static String source() throws Exception {
        return Files.readString(ITEM_MANAGER, StandardCharsets.UTF_8);
    }

    private static List<Registration> registrations(String source) {
        Matcher matcher = INTERACTION.matcher(source);
        List<Registration> registrations = new ArrayList<>();
        while (matcher.find()) {
            registrations.add(new Registration(matcher.group(1), matcher.group(2)));
        }
        return registrations;
    }

    private static Map<String, String> imports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        return imports;
    }

    private static Class<?> resolveType(Map<String, String> imports, String sourceName) throws ClassNotFoundException {
        int nestedSeparator = sourceName.indexOf('.');
        String outerName = nestedSeparator < 0 ? sourceName : sourceName.substring(0, nestedSeparator);
        String fullyQualifiedOuter = imports.get(outerName);
        if (fullyQualifiedOuter == null) {
            throw new ClassNotFoundException("No import found for registered type " + sourceName);
        }
        String nestedName = nestedSeparator < 0 ? "" : "$" + sourceName.substring(nestedSeparator + 1);
        return Class.forName(fullyQualifiedOuter + nestedName);
    }

    public record Registration(String key, String sourceType) {}
}
