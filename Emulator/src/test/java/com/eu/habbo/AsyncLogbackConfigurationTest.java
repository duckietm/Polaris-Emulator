package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class AsyncLogbackConfigurationTest {

    private static final Path CONFIGURATION = Path.of("src/main/resources/logback.xml");
    private static final String ASYNC_APPENDER = "ch.qos.logback.classic.AsyncAppender";

    @Test
    void activeRootAppendersUseExplicitAsyncPolicies() throws Exception {
        Document document = configuration();

        assertAsync(document, "Console", "ConsoleDelegate", "2048", "20", "true");
        assertAsync(document, "FileDebug", "FileDebugRolling", "4096", "20", "true");
        assertAsync(document, "FileErrors", "FileErrorsRolling", "2048", "0", "false");
        assertAsync(document, "FileErrorsSql", "FileErrorsSqlRolling", "2048", "0", "false");
        assertAsync(document, "SlowQueries", "SlowQueriesRolling", "2048", "0", "false");
    }

    @Test
    void everyRollingFileHasAnAggregateSizeCap() throws Exception {
        Document document = configuration();

        assertTotalSizeCap(document, "FileDebugRolling", "500MB");
        assertTotalSizeCap(document, "FileErrorsRolling", "500MB");
        assertTotalSizeCap(document, "FileErrorsSqlRolling", "500MB");
        assertTotalSizeCap(document, "SlowQueriesRolling", "500MB");
    }

    @Test
    void runtimeDebugModeStillRaisesTheRootLevel() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/" + "Emulator.java"));

        assertTrue(source.contains("root.setLevel(Level.DEBUG)"));
    }

    @Test
    void configurationStopsTheLoggerContextOnShutdown() throws Exception {
        Document document = configuration();

        NodeList hooks = document.getElementsByTagName("shutdownHook");
        assertEquals(1, hooks.getLength());
        Element hook = (Element) hooks.item(0);
        assertEquals("ch.qos.logback.core.hook.DefaultShutdownHook", hook.getAttribute("class"));
    }

    private static void assertAsync(
            Document document,
            String name,
            String delegate,
            String queueSize,
            String discardingThreshold,
            String neverBlock) {
        Element appender = appender(document, name);
        assertEquals(ASYNC_APPENDER, appender.getAttribute("class"));
        assertEquals(queueSize, childText(appender, "queueSize"));
        assertEquals(discardingThreshold, childText(appender, "discardingThreshold"));
        assertEquals(neverBlock, childText(appender, "neverBlock"));
        assertEquals(delegate, firstChild(appender, "appender-ref").getAttribute("ref"));
    }

    private static void assertTotalSizeCap(Document document, String appenderName, String expected) {
        Element rollingPolicy = firstChild(appender(document, appenderName), "rollingPolicy");
        assertEquals(expected, childText(rollingPolicy, "totalSizeCap"));
    }

    private static Document configuration() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(CONFIGURATION.toFile());
    }

    private static Element appender(Document document, String name) {
        NodeList appenders = document.getElementsByTagName("appender");
        for (int index = 0; index < appenders.getLength(); index++) {
            Element appender = (Element) appenders.item(index);
            if (name.equals(appender.getAttribute("name"))) {
                return appender;
            }
        }
        throw new AssertionError("Missing appender " + name);
    }

    private static String childText(Element parent, String tag) {
        return firstChild(parent, tag).getTextContent().trim();
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList children = parent.getElementsByTagName(tag);
        if (children.getLength() == 0) {
            throw new AssertionError("Missing " + tag + " under " + parent.getAttribute("name"));
        }
        return (Element) children.item(0);
    }
}
