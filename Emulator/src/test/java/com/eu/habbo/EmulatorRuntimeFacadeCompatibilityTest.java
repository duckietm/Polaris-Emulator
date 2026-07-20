package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmulatorRuntimeFacadeCompatibilityTest {

    @Test
    void serviceGettersReturnTheExactInstalledStaticInstances() throws Exception {
        Map<String, String> gettersByField = new LinkedHashMap<>();
        gettersByField.put("config", "getConfig");
        gettersByField.put("crypto", "getCrypto");
        gettersByField.put("texts", "getTexts");
        gettersByField.put("database", "getDatabase");
        gettersByField.put("databaseLogger", "getDatabaseLogger");
        gettersByField.put("runtime", "getRuntime");
        gettersByField.put("gameServer", "getGameServer");
        gettersByField.put("rconServer", "getRconServer");
        gettersByField.put("logging", "getLogging");
        gettersByField.put("threading", "getThreading");
        gettersByField.put("gameEnvironment", "getGameEnvironment");
        gettersByField.put("pluginManager", "getPluginManager");
        gettersByField.put("badgeImager", "getBadgeImager");

        Field runtimeOwner = Emulator.class.getDeclaredField("polarisRuntime");
        runtimeOwner.setAccessible(true);
        Object originalRuntimeOwner = runtimeOwner.get(null);
        Map<Field, Object> originalValues = new LinkedHashMap<>();
        try {
            runtimeOwner.set(null, null);
            for (Map.Entry<String, String> entry : gettersByField.entrySet()) {
                Field field = Emulator.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                originalValues.put(field, field.get(null));
                Object installed = field.getType() == Runtime.class ? Runtime.getRuntime() : mock(field.getType());
                field.set(null, installed);
                Method getter = Emulator.class.getMethod(entry.getValue());

                assertSame(installed, getter.invoke(null), entry.getValue());
            }
        } finally {
            for (Map.Entry<Field, Object> entry : originalValues.entrySet()) {
                entry.getKey().set(null, entry.getValue());
            }
            runtimeOwner.set(null, originalRuntimeOwner);
        }
    }

    @Test
    void secureRandomGetterKeepsTheSingletonIdentity() throws Exception {
        Field field = Emulator.class.getDeclaredField("secureRandom");
        field.setAccessible(true);

        assertSame((SecureRandom) field.get(null), Emulator.getRandomDice());
    }
}
