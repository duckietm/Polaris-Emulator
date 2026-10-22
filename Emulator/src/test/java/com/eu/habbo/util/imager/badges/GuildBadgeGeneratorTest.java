package com.eu.habbo.util.imager.badges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuildBadgeGeneratorTest {
    @TempDir
    Path folder;

    private static BufferedImage badge() {
        return new BufferedImage(GuildBadgeComposer.SIZE, GuildBadgeComposer.SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void writesTheBadgeOnceWithoutLeavingTemporaryFiles() throws Exception {
        AtomicInteger drawn = new AtomicInteger();
        GuildBadgeGenerator generator = new GuildBadgeGenerator(
                this.folder::toFile,
                code -> {
                    drawn.incrementAndGet();
                    return badge();
                },
                10);

        assertTrue(generator.submit("b001034"));
        assertTrue(generator.awaitIdle(5000));
        assertTrue(generator.submit("b001034"), "already on disk");
        assertTrue(generator.awaitIdle(5000));

        File file = this.folder.resolve("b001034.png").toFile();
        assertTrue(file.isFile());
        assertEquals(39, ImageIO.read(file).getWidth());
        assertEquals(1, drawn.get());
        try (var files = Files.list(this.folder)) {
            assertEquals(1, files.count());
        }
        generator.shutdown();
    }

    @Test
    void neverWritesOutsideTheFolderOrForANonCode() {
        GuildBadgeGenerator generator = new GuildBadgeGenerator(this.folder::toFile, code -> badge(), 10);

        assertFalse(generator.submit("../b001034"));
        assertFalse(generator.submit("b001034.png"));
        assertNull(generator.outputFile("x/y"));
        generator.shutdown();
    }

    @Test
    void dropsWorkThatDoesNotFitTheQueue() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        GuildBadgeGenerator generator = new GuildBadgeGenerator(
                this.folder::toFile,
                code -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return badge();
                },
                1);

        assertTrue(generator.submit("b001034")); // running
        Thread.sleep(100);
        assertTrue(generator.submit("b001044")); // queued
        assertFalse(generator.submit("b001054"), "queue full");
        assertTrue(generator.submit("b001044"), "already queued costs nothing");

        release.countDown();
        assertTrue(generator.awaitIdle(5000));
        assertTrue(this.folder.resolve("b001044.png").toFile().isFile());
        assertFalse(this.folder.resolve("b001054.png").toFile().exists());
        generator.shutdown();
    }

    @Test
    void writesNothingForABadgeThatCannotBeDrawn() throws Exception {
        GuildBadgeGenerator generator = new GuildBadgeGenerator(this.folder::toFile, code -> null, 10);

        assertTrue(generator.submit("b001034"));
        assertTrue(generator.awaitIdle(5000));
        try (var files = Files.list(this.folder)) {
            assertEquals(0, files.count());
        }
        generator.shutdown();
    }
}
