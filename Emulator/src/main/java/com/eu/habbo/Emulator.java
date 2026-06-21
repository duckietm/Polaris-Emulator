package com.eu.habbo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.eu.habbo.core.*;
import com.eu.habbo.core.consolecommands.ConsoleCommand;
import com.eu.habbo.database.Database;
import com.eu.habbo.gui.EmulatorDashboard;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.SessionResumeManager;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;
import com.eu.habbo.util.logback.ConsoleStyle;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Emulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emulator.class);
    private static final String OS_NAME = (System.getProperty("os.name") != null ? System.getProperty("os.name") : "Unknown");
    private static final String CLASS_PATH = (System.getProperty("java.class.path") != null ? System.getProperty("java.class.path") : "Unknown");
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_DIM = "\u001B[2m";

    // Fallback version, only used when running outside a packaged jar (e.g. from
    // the IDE). In production the version comes from the jar manifest below.
    public final static int MAJOR = 4;
    public final static int MINOR = 1;
    public final static int BUILD = 0;
    public final static String PREVIEW = "";

    // Tied to the Maven project version: read from the jar manifest
    // (Implementation-Version = ${project.version}, see pom assembly plugin).
    private static String resolveVersionNumber() {
        String implementation = Emulator.class.getPackage().getImplementationVersion();
        if (implementation != null && !implementation.isEmpty()) return implementation;
        String fallback = MAJOR + "." + MINOR + "." + BUILD;
        return PREVIEW.isEmpty() ? fallback : fallback + " " + PREVIEW;
    }

    public static final String version = "Arcturus Morningstar Extended " + resolveVersionNumber();
    private static final String logo =
            "\n" +
                    "███╗   ███╗ ██████╗ ██████╗ ███╗   ██╗██╗███╗   ██╗ ██████╗ ███████╗████████╗ █████╗ ██████╗ \n" +
                    "████╗ ████║██╔═══██╗██╔══██╗████╗  ██║██║████╗  ██║██╔════╝ ██╔════╝╚══██╔══╝██╔══██╗██╔══██╗\n" +
                    "██╔████╔██║██║   ██║██████╔╝██╔██╗ ██║██║██╔██╗ ██║██║  ███╗███████╗   ██║   ███████║██████╔╝\n" +
                    "██║╚██╔╝██║██║   ██║██╔══██╗██║╚██╗██║██║██║╚██╗██║██║   ██║╚════██║   ██║   ██╔══██║██╔══██╗\n" +
                    "██║ ╚═╝ ██║╚██████╔╝██║  ██║██║ ╚████║██║██║ ╚████║╚██████╔╝███████║   ██║   ██║  ██║██║  ██║\n" +
                    "╚═╝     ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝\n" +
                    "Still Rocking in 2026.\n";
    public static String build = "";
    public static long buildTimestamp = -1L;


    public static boolean isReady = false;
    public static boolean isShuttingDown = false;
    public static boolean stopped = false;
    public static boolean debugging = false;
    private static int timeStarted = 0;
    private static Runtime runtime;
    private static ConfigurationManager config;
    private static CryptoConfig crypto;
    private static TextsManager texts;
    private static GameServer gameServer;
    private static RCONServer rconServer;
    private static Logging logging;
    private static Database database;
    private static DatabaseLogger databaseLogger;
    private static ThreadPooling threading;
    private static GameEnvironment gameEnvironment;
    private static PluginManager pluginManager;
    private static BadgeImager badgeImager;
    private static final SecureRandom secureRandom = new SecureRandom();

    static {
        Thread hook = new Thread(new Runnable() {
            public synchronized void run() {
                Emulator.dispose();
            }
        });
        hook.setPriority(10);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    @SuppressWarnings("resource")

    public static void main(String[] args) throws Exception {
        try {
            boolean styledConsole = shouldStyleConsole(
                    System.getenv(),
                    System.console() != null,
                    OS_NAME,
                    System.getProperty("habbo.console.style", "auto"));
            configureAnsiConsole(styledConsole);

            Locale.setDefault(Locale.of("en"));
            setBuild();
            Emulator.stopped = false;
            ConsoleCommand.load();
            Emulator.logging = new Logging();

            System.out.println(startupHero(styledConsole));

            long startTime = System.nanoTime();

            Emulator.runtime = Runtime.getRuntime();
            Emulator.config = new ConfigurationManager("config.ini");
            Emulator.crypto = new CryptoConfig(
                    Emulator.getConfig().getBoolean("enc.enabled", false),
                    Emulator.getConfig().getValue("enc.e"),
                    Emulator.getConfig().getValue("enc.n"),
                    Emulator.getConfig().getValue("enc.d"));
            Emulator.database = new Database(Emulator.getConfig());
            Emulator.databaseLogger = new DatabaseLogger();
            Emulator.config.loaded = true;
            Emulator.config.loadFromDatabase();
            Emulator.threading = new ThreadPooling(Emulator.getConfig().getInt("runtime.threads"));
            Emulator.getDatabase().getDataSource().setMaximumPoolSize(Emulator.getConfig().getInt("runtime.threads") * 2);
            Emulator.getDatabase().getDataSource().setMinimumIdle(10);
            Emulator.pluginManager = new PluginManager();
            Emulator.pluginManager.reload();
            Emulator.getPluginManager().fireEvent(new EmulatorConfigUpdatedEvent());
            Emulator.texts = new TextsManager();

            Emulator.config.register("camera.url", "http://localhost/camera/");
            Emulator.config.register("imager.location.output.camera", "/public/camera/");
            Emulator.config.register("imager.location.output.thumbnail", "/public/camera/thumbnails/");
            Emulator.config.register("camera.price.points.publish", "1");
            Emulator.config.register("camera.price.points.publish.type", "5");
            Emulator.config.register("camera.publish.delay", "180");
            Emulator.config.register("camera.price.credits", "2");
            Emulator.config.register("camera.price.points", "0");
            Emulator.config.register("camera.price.points.type", "5");
            Emulator.config.register("camera.render.delay", "5");
            Emulator.config.register("hotel.timezone", java.time.ZoneId.systemDefault().getId());
            Emulator.config.register("gui.enabled", "0");
            Emulator.config.register("gui.autostart.enabled", "0");
            Emulator.config.register("rcon.rate_limit.enabled", "1");
            Emulator.config.register("rcon.rate_limit.limit_for_period", "60");
            Emulator.config.register("rcon.rate_limit.refresh_period_ms", "1000");
            Emulator.config.register("rcon.rate_limit.timeout_ms", "0");
            Emulator.config.register("rcon.execute_command.denied_permissions", "cmd_shutdown;cmd_give_rank");
            Emulator.config.register("rcon.execute_command.allowed_permissions", "");
            Emulator.config.register("rcon.max_payload_bytes", "65536");
            Emulator.config.register("nitro.secure.api.max_payload_bytes", "65536");
            Emulator.config.register("nitro.secure.config.max_file_bytes", "2097152");
            Emulator.config.register("nitro.secure.gamedata.max_file_bytes", "16777216");
            registerEarningsSettings();
            String hotelTimezoneId = Emulator.getConfig().getValue("hotel.timezone", java.time.ZoneId.systemDefault().getId());
            System.out.println(startupCard(hotelTimezoneId));
            Emulator.texts.register("camera.permission", "You don't have permission to use the camera!");
            Emulator.texts.register("camera.wait", "Please wait %seconds% seconds before making another picture.");
            Emulator.texts.register("camera.error.creation", "Failed to create your picture. *sadpanda*");

            File thumbnailDir = new File(Emulator.config.getValue("imager.location.output.thumbnail"));
            if (!thumbnailDir.exists()) {
                thumbnailDir.mkdirs();
            }

            new CleanerThread();
            Emulator.gameServer = new GameServer(getConfig().getValue("game.host", "127.0.0.1"), getConfig().getInt("game.port", 30000));
            Emulator.rconServer = new RCONServer(getConfig().getValue("rcon.host", "127.0.0.1"), getConfig().getInt("rcon.port", 30001));
            Emulator.gameEnvironment = new GameEnvironment();
            Emulator.gameEnvironment.load();
            Emulator.gameServer.initializePipeline();
            Emulator.gameServer.connect();
            Emulator.getGameServer().getGameClientManager().CFKeepAlive();
            Emulator.rconServer.initializePipeline();
            Emulator.rconServer.connect();
            Emulator.badgeImager = new BadgeImager();

            LOGGER.info("Arcturus Morningstar has successfully loaded.");
            LOGGER.info("System launched in: {}ms. Using {} threads!", (System.nanoTime() - startTime) / 1e6, Runtime.getRuntime().availableProcessors() * 2);
            LOGGER.info("Memory: {}/{}MB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024), (runtime.freeMemory()) / (1024 * 1024));

            Emulator.debugging = Emulator.getConfig().getBoolean("debug.mode");

            if (debugging) {
                ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                root.setLevel(Level.DEBUG);
                LOGGER.debug("Debugging enabled.");
            }

            Emulator.getPluginManager().fireEvent(new EmulatorLoadedEvent());
            Emulator.isReady = true;
            Emulator.timeStarted = getIntUnixTimestamp();

            if (shouldLaunchGui()) {
                EmulatorDashboard.launch();
            }

            if (Emulator.getConfig().getInt("runtime.threads") < (Runtime.getRuntime().availableProcessors() * 2)) {
                LOGGER.warn("Emulator settings runtime.threads ({}) can be increased to ({}) to possibly increase performance.",
                        Emulator.getConfig().getInt("runtime.threads"),
                        Runtime.getRuntime().availableProcessors() * 2);
            }

            Emulator.getThreading().run(() -> {
            }, 1500);

            // Check if console mode is true or false, default is true
            if (Emulator.getConfig().getBoolean("console.mode", true)) {

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

                while (!isShuttingDown && isReady) {
                    try {
                        String line = reader.readLine();

                        if (line != null) {
                            ConsoleCommand.handle(line);
                        }
                        System.out.println("Waiting for command: ");
                    } catch (Exception e) {
                        if (!(e instanceof IOException && e.getMessage().equals("Bad file descriptor"))) {
                            LOGGER.error("Error while reading command", e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setBuild() {
        if (Emulator.class.getProtectionDomain().getCodeSource() == null) {
            build = "UNKNOWN";
            buildTimestamp = -1L;
            return;
        }

        StringBuilder sb = new StringBuilder();
        try {
            File buildFile = new File(Emulator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            buildTimestamp = resolveBuildTimestamp(buildFile);

            if (!buildFile.isFile()) {
                build = "DEV";
                return;
            }

            String filepath = buildFile.getAbsolutePath();
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(filepath)) {
                byte[] dataBytes = new byte[1024];
                int nread = 0;
                while ((nread = fis.read(dataBytes)) != -1)
                    md.update(dataBytes, 0, nread);
                byte[] mdbytes = md.digest();
                for (int i = 0; i < mdbytes.length; i++)
                    sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }
        } catch (Exception e) {
            build = "UNKNOWN";
            buildTimestamp = -1L;
            return;
        }

        build = sb.toString();
    }

    private static long resolveBuildTimestamp(File buildFile) {
        if (buildFile != null && buildFile.exists() && buildFile.isFile()) {
            return buildFile.lastModified();
        }

        try {
            URL classUrl = Emulator.class.getResource("Emulator.class");

            if (classUrl != null) {
                if ("file".equalsIgnoreCase(classUrl.getProtocol())) {
                    File classFile = new File(classUrl.toURI());

                    if (classFile.exists()) {
                        return classFile.lastModified();
                    }
                }

                if ("jar".equalsIgnoreCase(classUrl.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) classUrl.openConnection();
                    File jarFile = new File(connection.getJarFileURL().toURI());

                    if (jarFile.exists()) {
                        return jarFile.lastModified();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (buildFile != null && buildFile.exists()) {
            return buildFile.lastModified();
        }

        return -1L;
    }

    static String startupCard(String hotelTimezoneId) {
        return "\n" +
                "+----------------------------------------------------------------+\n" +
                "| Arcturus Morningstar Extended                                  |\n" +
                "| Source : github.com/duckietm/Arcturus-Morningstar-Extended     |\n" +
                "| Scope  : Educational open-source fork by TheGeneral            |\n" +
                "| Version: " + version + "\n" +
                "| Build  : " + build + "\n" +
                "| Time   : " + formatBuildTimestamp(buildTimestamp, hotelTimezoneId) + " [" + hotelTimezoneId + "]\n" +
                "+----------------------------------------------------------------+\n";
    }

    static String startupHero() {
        return startupHero(false);
    }

    static String startupHero(boolean styled) {
        if (styled) {
            return "\n" +
                    ANSI_CYAN +
                    "   __  __  ___  ____  _   _ ___ _   _  ____ ____ _____  _    ____  \n" +
                    "  |  \\/  |/ _ \\|  _ \\| \\ | |_ _| \\ | |/ ___/ ___|_   _|/ \\  |  _ \\ \n" +
                    "  | |\\/| | | | | |_) |  \\| || ||  \\| | |  _\\___ \\ | | / _ \\ | |_) |\n" +
                    "  | |  | | |_| |  _ <| |\\  || || |\\  | |_| |___) || |/ ___ \\|  _ < \n" +
                    "  |_|  |_|\\___/|_| \\_\\_| \\_|___|_| \\_|\\____|____/ |_/_/   \\_\\_| \\_\\\n" +
                    ANSI_RESET +
                    "\n" +
                    ANSI_DIM + "+------------------------------------------------------------------------------+" + ANSI_RESET + "\n" +
                    "| " + ANSI_BOLD + ANSI_GREEN + "[OK] MORNINGSTAR EXTENDED" + ANSI_RESET + fit("", 50) + " |\n" +
                    "| " + ANSI_DIM + "Arcturus game server runtime" + ANSI_RESET + fit("", 48) + " |\n" +
                    ANSI_DIM + "+------------------------------------------------------------------------------+" + ANSI_RESET + "\n" +
                    "| " + ANSI_YELLOW + "[VER]" + ANSI_RESET + " Version : " + fit(version, 57) + " |\n" +
                    "| " + ANSI_YELLOW + "[BLD]" + ANSI_RESET + " Build   : " + fit(build.isBlank() ? "UNKNOWN" : build, 57) + " |\n" +
                    "| " + ANSI_YELLOW + "[JVM]" + ANSI_RESET + " Runtime : " + fit("Java " + System.getProperty("java.version", "unknown") + " / styled console output", 57) + " |\n" +
                    ANSI_DIM + "+------------------------------------------------------------------------------+" + ANSI_RESET + "\n";
        }

        return "\n" +
                "   __  __  ___  ____  _   _ ___ _   _  ____ ____ _____  _    ____  \n" +
                "  |  \\/  |/ _ \\|  _ \\| \\ | |_ _| \\ | |/ ___/ ___|_   _|/ \\  |  _ \\ \n" +
                "  | |\\/| | | | | |_) |  \\| || ||  \\| | |  _\\___ \\ | | / _ \\ | |_) |\n" +
                "  | |  | | |_| |  _ <| |\\  || || |\\  | |_| |___) || |/ ___ \\|  _ < \n" +
                "  |_|  |_|\\___/|_| \\_\\_| \\_|___|_| \\_|\\____|____/ |_/_/   \\_\\_| \\_\\\n" +
                "\n" +
                "+------------------------------------------------------------------------------+\n" +
                "| MORNINGSTAR EXTENDED                                                         |\n" +
                "| Arcturus game server runtime                                                  |\n" +
                "+------------------------------------------------------------------------------+\n" +
                "| Version : " + fit(version, 63) + " |\n" +
                "| Build   : " + fit(build.isBlank() ? "UNKNOWN" : build, 63) + " |\n" +
                "| Runtime : " + fit("Java " + System.getProperty("java.version", "unknown") + " / universal console output", 63) + " |\n" +
                "+------------------------------------------------------------------------------+\n";
    }

    static boolean shouldStyleConsole(Map<String, String> environment, boolean interactiveConsole, String osName, String styleProperty) {
        return ConsoleStyle.isEnabled(environment, interactiveConsole, osName, styleProperty);
    }

    static void configureAnsiConsole(boolean styledConsole) {
        if (!styledConsole || !OS_NAME.startsWith("Windows") || CLASS_PATH.contains("idea_rt.jar")) {
            return;
        }

        try {
            AnsiConsole.systemInstall();

            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root.getAppender("Console");
            if (appender != null) {
                appender.stop();
                appender.setWithJansi(true);
                appender.start();
            }
        } catch (Throwable e) {
            LOGGER.debug("Unable to install Jansi console bridge; continuing with raw console output.", e);
        }
    }

    static boolean shouldLaunchGui() {
        return Emulator.getConfig() != null && Emulator.getConfig().getBoolean("gui.autostart.enabled", false);
    }

    private static String fit(String value, int width) {
        String safe = value == null ? "" : value;
        if (safe.length() > width) {
            return safe.substring(0, Math.max(0, width - 3)) + "...";
        }

        return String.format("%-" + width + "s", safe);
    }

    private static String formatBuildTimestamp(long buildTimestamp, String timezoneId) {
        if (buildTimestamp <= 0) {
            return "UNKNOWN";
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            format.setTimeZone(TimeZone.getTimeZone(java.time.ZoneId.of(timezoneId)));
        } catch (Exception ignored) {
            format.setTimeZone(TimeZone.getDefault());
        }

        return format.format(new Timestamp(buildTimestamp));
    }

    private static void dispose() {
        if (Emulator.threading != null) {
            Emulator.threading.setCanAdd(false);
        }
        Emulator.isShuttingDown = true;
        Emulator.isReady = false;

        LOGGER.info("Stopping Arcturus Morningstar {}", version);

        if (Emulator.pluginManager != null)
            tryShutdown(() -> Emulator.pluginManager.fireEvent(new EmulatorStartShutdownEvent()));
        if (Emulator.rconServer != null) tryShutdown(() -> Emulator.rconServer.stop());
        tryShutdown(() -> SessionResumeManager.getInstance().disposeAll());
        if (Emulator.gameEnvironment != null) tryShutdown(() -> Emulator.gameEnvironment.dispose());
        if (Emulator.pluginManager != null)
            tryShutdown(() -> Emulator.pluginManager.fireEvent(new EmulatorStoppedEvent()));
        if (Emulator.pluginManager != null) tryShutdown(() -> Emulator.pluginManager.dispose());
        if (Emulator.config != null) tryShutdown(() -> Emulator.config.saveToDatabase());
        if (Emulator.gameServer != null) tryShutdown(() -> Emulator.gameServer.stop());

        LOGGER.info("Stopped Arcturus Morningstar {}", version);

        if (Emulator.database != null) tryShutdown(() -> Emulator.database.dispose());
        if (Emulator.threading != null) tryShutdown(() -> Emulator.threading.shutDown());

        Emulator.stopped = true;
    }

    private static void tryShutdown(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }
    }

    public static ConfigurationManager getConfig() {
        return config;
    }

    public static CryptoConfig getCrypto() {
        return crypto;
    }

    public static TextsManager getTexts() {
        return texts;
    }

    public static Database getDatabase() {
        return database;
    }

    public static DatabaseLogger getDatabaseLogger() {
        return databaseLogger;
    }

    public static Runtime getRuntime() {
        return runtime;
    }

    public static GameServer getGameServer() {
        return gameServer;
    }

    private static void registerEarningsSettings() {
        Emulator.config.register("earnings.enabled", "0");

        String[] categories = {
                "daily_gift",
                "games",
                "achievements",
                "marketplace",
                "hc_payday",
                "level_progress",
                "donations",
                "bonus_bag",
                "mystery_boxes",
                "club_job"
        };

        for (String category : categories) {
            String prefix = "earnings." + category + ".";
            Emulator.config.register(prefix + "enabled", "1");
            Emulator.config.register(prefix + "cooldown.seconds", "86400");
            Emulator.config.register(prefix + "credits", "0");
            Emulator.config.register(prefix + "pixels", "0");
            Emulator.config.register(prefix + "points", "0");
            Emulator.config.register(prefix + "points.type", "5");
            Emulator.config.register(prefix + "badge", "");
            Emulator.config.register(prefix + "item_id", "0");
            Emulator.config.register(prefix + "item.quantity", "1");
            Emulator.config.register(prefix + "hc.days", "0");
            Emulator.config.register(prefix + "native.enabled", (category.equals("marketplace") || category.equals("hc_payday") || category.equals("achievements") || category.equals("level_progress")) ? "1" : "0");
        }

        Emulator.config.register("earnings.achievements.min_score", "1");
        Emulator.config.register("earnings.achievements.score.step", "100");
        Emulator.config.register("earnings.level_progress.min_level", "1");
    }

    public static RCONServer getRconServer() {
        return rconServer;
    }

    /**
     * @deprecated Do not use. Please use LoggerFactory.getLogger(YourClass.class) to log.
     */
    @Deprecated
    public static Logging getLogging() {
        return logging;
    }

    public static ThreadPooling getThreading() {
        return threading;
    }

    public static GameEnvironment getGameEnvironment() {
        return gameEnvironment;
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    public static Random getRandom() {
        return ThreadLocalRandom.current();
    }
    public static SecureRandom getRandomDice() {
        return secureRandom;
    }
    public static BadgeImager getBadgeImager() {
        return badgeImager;
    }

    public static int getTimeStarted() {
        return timeStarted;
    }

    public static int getOnlineTime() {
        return getIntUnixTimestamp() - timeStarted;
    }

    public static void prepareShutdown() {
        System.exit(0);
    }

    public static int timeStringToSeconds(String timeString) {
        int totalSeconds = 0;

        Matcher m = Pattern.compile("(([0-9]*) (second|minute|hour|day|week|month|year))").matcher(timeString);
        Map<String,Integer> map = new HashMap<String,Integer>() {
            {
                put("second", 1);
                put("minute", 60);
                put("hour", 3600);
                put("day", 86400);
                put("week", 604800);
                put("month", 2628000);
                put("year", 31536000);
            }
        };

        while (m.find()) {
            try {
                int amount = Integer.parseInt(m.group(2));
                String what = m.group(3);
                totalSeconds += amount * map.get(what);
            }
            catch (Exception ignored) { }
        }

        return totalSeconds;
    }

    public static Date modifyDate(Date date, String timeString) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);

        Matcher m = Pattern.compile("(([0-9]*) (second|minute|hour|day|week|month|year))").matcher(timeString);
        Map<String, Integer> map = new HashMap<String, Integer>() {
            {
                put("second", Calendar.SECOND);
                put("minute", Calendar.MINUTE);
                put("hour", Calendar.HOUR);
                put("day", Calendar.DAY_OF_MONTH);
                put("week", Calendar.WEEK_OF_MONTH);
                put("month", Calendar.MONTH);
                put("year", Calendar.YEAR);
            }
        };

        while (m.find()) {
            try {
                int amount = Integer.parseInt(m.group(2));
                String what = m.group(3);
                c.add(map.get(what), amount);
            }
            catch (Exception ignored) { }
        }

        return c.getTime();
    }

    private static String dateToUnixTimestamp(Date date) {
        String res = "";
        Date aux = stringToDate("1970-01-01 00:00:00");
        Timestamp aux1 = dateToTimeStamp(aux);
        Timestamp aux2 = dateToTimeStamp(date);
        long difference = aux2.getTime() - aux1.getTime();
        long seconds = difference / 1000L;
        return res + seconds;
    }

    public static Date stringToDate(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date res = null;
        try {
            res = format.parse(date);
        } catch (Exception e) {
            LOGGER.error("Error parsing date", e);
        }
        return res;
    }

    public static Timestamp dateToTimeStamp(Date date) {
        return new Timestamp(date.getTime());
    }

    public static Date getDate() {
        return new Date(System.currentTimeMillis());
    }

    public static String getUnixTimestamp() {
        return dateToUnixTimestamp(getDate());
    }

    public static int getIntUnixTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public static boolean isNumeric(String string)
            throws IllegalArgumentException {
        boolean isnumeric = false;
        if ((string != null) && (!string.equals(""))) {
            isnumeric = true;
            char[] chars = string.toCharArray();
            for (char aChar : chars) {
                isnumeric = Character.isDigit(aChar);
                if (!isnumeric) {
                    break;
                }
            }
        }
        return isnumeric;
    }

    public int getUserCount() {
        return gameEnvironment.getHabboManager().getOnlineCount();
    }

    public int getRoomCount() {
        return gameEnvironment.getRoomManager().getActiveRooms().size();
    }
}
