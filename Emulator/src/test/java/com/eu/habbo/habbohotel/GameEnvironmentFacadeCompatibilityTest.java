package com.eu.habbo.habbohotel;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class GameEnvironmentFacadeCompatibilityTest {

    @Test
    void everyManagerGetterReturnsItsExactBackingInstance() throws Exception {
        GameEnvironment environment = new GameEnvironment();
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object originalConfig = configField.get(null);
        configField.set(null, mock(ConfigurationManager.class));

        try {
            for (Method getter : GameEnvironment.class.getDeclaredMethods()) {
                if (!Modifier.isPublic(getter.getModifiers())
                        || getter.getParameterCount() != 0
                        || !getter.getName().startsWith("get")
                        || getter.getReturnType() == void.class) {
                    continue;
                }

                String fieldName = Introspector.decapitalize(getter.getName().substring(3));
                Field field = GameEnvironment.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object installed = mock(field.getType());
                field.set(environment, installed);

                assertSame(installed, getter.invoke(environment), getter.getName());
            }
        } finally {
            configField.set(null, originalConfig);
        }
    }
}
