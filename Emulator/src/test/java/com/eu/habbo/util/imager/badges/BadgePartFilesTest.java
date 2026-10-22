package com.eu.habbo.util.imager.badges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BadgePartFilesTest {
    @TempDir
    Path folder;

    private File png(String name, int width, int height) throws Exception {
        File file = this.folder.resolve(name).toFile();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "PNG", file);
        return file;
    }

    @Test
    void partNamesNeverLeaveTheFolder() {
        assertEquals("symbol_star", BadgePartFiles.partName("symbol_star.gif"));
        assertEquals("base_basic_1", BadgePartFiles.partName("base_basic_1.png"));
        assertNull(BadgePartFiles.partName("../../etc/passwd"));
        assertNull(BadgePartFiles.partName("sub/dir"));
        assertNull(BadgePartFiles.partName("a b"));
        assertNull(BadgePartFiles.partName(""));
        assertNull(BadgePartFiles.partFile(this.folder.toFile(), "..\\secret"));
        assertEquals(
                new File(this.folder.toFile(), "badgepart_symbol_star.png"),
                BadgePartFiles.partFile(this.folder.toFile(), "symbol_star.gif"));
    }

    @Test
    void readsASmallPart() throws Exception {
        BufferedImage image = BadgePartFiles.read(this.png("badgepart_ok.png", 13, 13));

        assertEquals(13, image.getWidth());
    }

    @Test
    void refusesAPartLargerThanTheBadgeBeforeDecodingIt() throws Exception {
        File file = this.png("badgepart_big.png", 2000, 40);

        assertThrows(Exception.class, () -> BadgePartFiles.read(file));
    }

    @Test
    void refusesLargeFilesAndOtherFormats() throws Exception {
        File huge = this.folder.resolve("badgepart_huge.png").toFile();
        Files.write(huge.toPath(), new byte[(int) BadgePartFiles.MAX_FILE_BYTES + 1]);
        File bmp = this.folder.resolve("badgepart_bmp.png").toFile();
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "BMP", bmp);

        assertThrows(Exception.class, () -> BadgePartFiles.read(huge));
        assertThrows(Exception.class, () -> BadgePartFiles.read(bmp));
        assertThrows(Exception.class, () -> BadgePartFiles.read(new File(this.folder.toFile(), "missing.png")));
    }
}
