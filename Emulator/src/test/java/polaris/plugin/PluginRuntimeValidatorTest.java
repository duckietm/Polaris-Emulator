package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.messages.RuntimeValidationReport;

class PluginRuntimeValidatorTest {

    @Test
    void reportsMissingPluginConfigurationFields() {
        RuntimeValidationReport report =
                PluginRuntimeValidator.validateConfiguration("broken.jar", new PluginConfiguration());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("broken.jar")));
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("main")));
    }

    @Test
    void reportsMissingPluginMainClass() {
        PluginConfiguration configuration = configuration("Missing", "polaris.plugin.DoesNotExist");

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass(
                "missing.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("DoesNotExist"));
    }

    @Test
    void reportsClassThatIsNotAHabboPlugin() {
        PluginConfiguration configuration = configuration("WrongType", NotAPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass(
                "wrong.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("PolarisPlugin"));
    }

    @Test
    void reportsPluginWithoutPublicNoArgumentConstructor() {
        PluginConfiguration configuration = configuration("NoCtor", MissingPublicConstructorPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass(
                "ctor.jar", configuration, getClass().getClassLoader());

        assertTrue(report.hasErrors());
        assertTrue(report.errors().get(0).message().contains("public no-argument constructor"));
    }

    @Test
    void acceptsValidPluginClass() {
        PluginConfiguration configuration = configuration("Valid", ValidPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validatePluginClass(
                "valid.jar", configuration, getClass().getClassLoader());

        assertFalse(report.hasErrors());
    }

    @Test
    void rejectsPluginMetadataContainingControlCharacters() {
        PluginConfiguration configuration = configuration("Unsafe\nPlugin", ValidPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validateConfiguration("unsafe.jar", configuration);

        assertTrue(report.hasErrors());
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("invalid name")));
    }

    @Test
    void rejectsInvalidPluginMainClassName() {
        PluginConfiguration configuration = configuration("Unsafe", "../polaris.BadPlugin");

        RuntimeValidationReport report = PluginRuntimeValidator.validateConfiguration("unsafe.jar", configuration);

        assertTrue(report.hasErrors());
        assertTrue(report.errors().stream().anyMatch(issue -> issue.message().contains("invalid main")));
    }

    @Test
    void keepsLegacyPluginNamespaceCompatible() {
        PluginConfiguration configuration = configuration("Legacy", ValidPlugin.class.getName());

        RuntimeValidationReport report = PluginRuntimeValidator.validateConfiguration("legacy.jar", configuration);

        assertFalse(report.hasErrors());
    }

    private static PluginConfiguration configuration(String name, String main) {
        PluginConfiguration configuration = new PluginConfiguration();
        configuration.name = name;
        configuration.main = main;
        return configuration;
    }

    public static final class ValidPlugin extends PolarisPlugin {
        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(User user, String key) {
            return false;
        }
    }

    public static final class MissingPublicConstructorPlugin extends PolarisPlugin {
        private MissingPublicConstructorPlugin() {}

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(User user, String key) {
            return false;
        }
    }

    public static final class NotAPlugin {}
}
