package com.eu.habbo.util.logback;

import ch.qos.logback.classic.Level;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class ConsoleStyle {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";

    private static final int LOGGER_WIDTH = 22;

    private ConsoleStyle() {
    }

    public static boolean isRuntimeEnabled() {
        return isEnabled(
                System.getenv(),
                System.console() != null,
                System.getProperty("os.name", "Unknown"),
                System.getProperty("habbo.console.style", "auto"));
    }

    public static boolean isEnabled(Map<String, String> environment, boolean interactiveConsole, String osName, String styleProperty) {
        String style = styleProperty == null ? "auto" : styleProperty.trim().toLowerCase(Locale.ROOT);
        if (style.equals("ansi") || style.equals("color") || style.equals("colours") || style.equals("colors")) {
            return true;
        }
        if (style.equals("plain") || style.equals("none") || style.equals("false") || style.equals("off")) {
            return false;
        }
        if (!interactiveConsole) {
            return false;
        }

        Map<String, String> env = environment == null ? Collections.emptyMap() : environment;
        if (env.containsKey("NO_COLOR")) {
            return false;
        }
        if (env.containsKey("WT_SESSION") || env.containsKey("ANSICON") || "ON".equalsIgnoreCase(env.get("ConEmuANSI"))) {
            return true;
        }

        String term = env.getOrDefault("TERM", "");
        if (term.equalsIgnoreCase("dumb")) {
            return false;
        }
        if (!term.isBlank() && (term.contains("xterm") || term.contains("ansi") || term.contains("screen") || term.contains("tmux"))) {
            return true;
        }

        return osName == null || !osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    public static String level(Level level, boolean styled) {
        String name = level == null ? "INFO" : level.toString();
        String plain = String.format("%-5s", name);

        if (!styled) {
            return plain;
        }

        if (Level.ERROR.equals(level)) {
            return ANSI_BOLD + ANSI_RED + "[x] " + plain + ANSI_RESET;
        }
        if (Level.WARN.equals(level)) {
            return ANSI_YELLOW + "[!] " + plain + ANSI_RESET;
        }
        if (Level.DEBUG.equals(level) || Level.TRACE.equals(level)) {
            return ANSI_DIM + "[.] " + plain + ANSI_RESET;
        }

        return ANSI_GREEN + "[i] " + plain + ANSI_RESET;
    }

    public static String logger(String loggerName, boolean styled) {
        String compact = compactLoggerName(loggerName);
        String plain = fit(compact, LOGGER_WIDTH);
        return styled ? ANSI_CYAN + plain + ANSI_RESET : plain;
    }

    private static String compactLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "";
        }

        int lastDot = loggerName.lastIndexOf('.');
        return lastDot >= 0 ? loggerName.substring(lastDot + 1) : loggerName;
    }

    private static String fit(String value, int width) {
        String safe = value == null ? "" : value;
        if (safe.length() > width) {
            return safe.substring(0, Math.max(0, width - 3)) + "...";
        }

        return String.format("%-" + width + "s", safe);
    }
}
