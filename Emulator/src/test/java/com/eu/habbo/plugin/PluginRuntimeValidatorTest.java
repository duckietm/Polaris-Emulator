package com.eu.habbo.plugin;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.RuntimeValidationReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRuntimeValidatorTest {

    @Test
    void reportsMissingPluginConfigurationFields() {
        RuntimeValidationReport report = PluginRuntimeValidator.validateConfiguration("broken.jar", new HabboPluginConfiguration());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("broken.jar")));
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("main")));
    }

    @Test
    void reportsMissingPluginMainClass() {
        HabboPluginConfiguration configuration = configuration("Missing", "com.eu.habbo.plugin.DoesNotExist");

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass("missing.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("DoesNotExist"));
    }

    @Test
    void reportsClassThatIsNotAHabboPlugin() {
        HabboPluginConfiguration configuration = configuration("WrongType", NotAPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass("wrong.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("HabboPlugin"));
    }

    @Test
    void reportsPluginWithoutPublicNoArgumentConstructor() {
        HabboPluginConfiguration configuration = configuration("NoCtor", MissingPublicConstructorPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass("ctor.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("public no-argument constructor"));
    }

    @Test
    void acceptsValidPluginClass() {
        HabboPluginConfiguration configuration = configuration("Valid", ValidPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass("valid.jar", configuration, getClass().getClassLoader());

        assertFalse(report.hasErrors());
    }

    private static HabboPluginConfiguration configuration(String name, String main) {
        HabboPluginConfiguration configuration = new HabboPluginConfiguration();
        configuration.name = name;
        configuration.main = main;
        return configuration;
    }

    public static final class ValidPlugin extends HabboPlugin {
        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public boolean hasPermission(Habbo habbo, String key) {
            return false;
        }
    }

    public static final class MissingPublicConstructorPlugin extends HabboPlugin {
        private MissingPublicConstructorPlugin() {
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public boolean hasPermission(Habbo habbo, String key) {
            return false;
        }
    }

    public static final class NotAPlugin {
    }
}
