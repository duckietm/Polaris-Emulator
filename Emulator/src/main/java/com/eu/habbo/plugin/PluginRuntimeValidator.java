package com.eu.habbo.plugin;

import com.eu.habbo.messages.RuntimeValidationReport;

public final class PluginRuntimeValidator {

    private PluginRuntimeValidator() {
    }

    public static RuntimeValidationReport validateConfiguration(String jarName, HabboPluginConfiguration configuration) {
        RuntimeValidationReport report = new RuntimeValidationReport();

        if (configuration == null) {
            report.addError("Plugin " + jarName + " has an invalid plugin.json");
            return report;
        }

        if (isBlank(configuration.name)) {
            report.addError("Plugin " + jarName + " is missing required plugin.json field name");
        }

        if (isBlank(configuration.main)) {
            report.addError("Plugin " + jarName + " is missing required plugin.json field main");
        }

        return report;
    }

    public static RuntimeValidationReport validatePluginClass(String jarName, HabboPluginConfiguration configuration, ClassLoader classLoader) {
        RuntimeValidationReport report = validateConfiguration(jarName, configuration);

        if (report.hasErrors()) {
            return report;
        }

        try {
            Class<?> clazz = classLoader.loadClass(configuration.main);

            if (!HabboPlugin.class.isAssignableFrom(clazz)) {
                report.addError("Plugin " + pluginLabel(jarName, configuration) + " main class " + configuration.main + " must extend HabboPlugin");
                return report;
            }

            clazz.asSubclass(HabboPlugin.class).getConstructor();
        } catch (ClassNotFoundException e) {
            report.addError("Plugin " + pluginLabel(jarName, configuration) + " main class " + configuration.main + " was not found");
        } catch (NoSuchMethodException e) {
            report.addError("Plugin " + pluginLabel(jarName, configuration) + " main class " + configuration.main + " must expose a public no-argument constructor");
        } catch (NoClassDefFoundError e) {
            report.addError("Plugin " + pluginLabel(jarName, configuration) + " main class " + configuration.main + " is missing dependency " + e.getMessage());
        } catch (LinkageError e) {
            report.addError("Plugin " + pluginLabel(jarName, configuration) + " main class " + configuration.main + " linkage failed: " + e.getMessage());
        }

        return report;
    }

    private static String pluginLabel(String jarName, HabboPluginConfiguration configuration) {
        if (configuration == null || isBlank(configuration.name)) {
            return jarName;
        }

        return configuration.name + " (" + jarName + ")";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
