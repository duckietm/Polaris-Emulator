package polaris.hotel.users.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.profile.ProfileImageService.ProfileImageException;

class AnimatedBannerTest {
    private static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] twoFrameGif() throws Exception {
        return AnimatedBanner.encode(List.of(solid(200, 50, Color.RED), solid(200, 50, Color.BLUE)), List.of(20, 30));
    }

    @Test
    void placesEveryFrameAndKeepsTheirTiming() throws Exception {
        byte[] gif = twoFrameGif();
        byte[] banner = AnimatedBanner.render(gif, new AnimatedBanner.Placement(6.4, 640, 110, true));

        assertTrue(AnimatedBanner.isGif(banner));

        List<AnimatedBanner.Frame> frames = AnimatedBanner.decode(banner);
        assertEquals(2, frames.size());
        assertEquals(AnimatedBanner.WIDTH, frames.get(0).image().getWidth());
        assertEquals(AnimatedBanner.HEIGHT, frames.get(0).image().getHeight());
        assertEquals(20, frames.get(0).delayCentiseconds());
        assertEquals(30, frames.get(1).delayCentiseconds());

        int first = frames.get(0).image().getRGB(320, 55);
        int second = frames.get(1).image().getRGB(320, 55);
        assertTrue(((first >> 16) & 0xFF) > 200, "the first frame is red");
        assertTrue((second & 0xFF) > 200, "the second frame is blue");
    }

    @Test
    void refusesAGifWithoutPlacementOrAnotherFormat() {
        assertEquals(
                "invalid",
                assertThrows(
                                ProfileImageException.class,
                                () -> AnimatedBanner.render(
                                        twoFrameGif(), new AnimatedBanner.Placement(Double.NaN, 0, 0, false)))
                        .getCode());
        assertEquals(
                "invalid",
                assertThrows(ProfileImageException.class, () -> AnimatedBanner.decode("PNG....".getBytes()))
                        .getCode());
        assertFalse(AnimatedBanner.isGif(new byte[] {1, 2, 3}));
    }

    @Test
    void animatedBannersAreServedUnderTheirOwnExtension() {
        assertTrue(ProfileImageService.isValidFileName("12_banner_0123456789abcdef.gif"));
        assertFalse(ProfileImageService.isValidFileName("12_banner_0123456789abcdef.png"));
    }
}
