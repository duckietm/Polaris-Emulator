package polaris.hotel.users.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.profile.ProfileImageService.Kind;
import polaris.hotel.users.profile.ProfileImageService.ProfileImageException;

class ProfileImageServiceTest {
    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test
    void acceptsAPictureOfTheKindsExactSizeAndWritesAJpeg() throws Exception {
        BufferedImage image = ProfileImageService.decode(Kind.BANNER, png(1280, 220));
        byte[] jpeg = ProfileImageService.encodeJpeg(image);

        assertEquals(1280, image.getWidth());
        assertTrue(ProfileImageService.isJpeg(jpeg));
        assertEquals(220, ImageIO.read(new java.io.ByteArrayInputStream(jpeg)).getHeight());
    }

    @Test
    void refusesAnotherSize() {
        ProfileImageException error =
                assertThrows(ProfileImageException.class, () -> ProfileImageService.decode(Kind.BANNER, png(640, 110)));

        assertEquals("invalid", error.getCode());
    }

    @Test
    void refusesWhatIsNotAJpegOrPng() {
        byte[] gif = "GIF89a....".getBytes();

        assertEquals(
                "invalid",
                assertThrows(ProfileImageException.class, () -> ProfileImageService.decode(Kind.BANNER, gif))
                        .getCode());
        assertEquals(
                "invalid",
                assertThrows(ProfileImageException.class, () -> ProfileImageService.decode(Kind.BANNER, new byte[0]))
                        .getCode());
    }

    @Test
    void refusesMoreThanTheRequestLimit() {
        byte[] large = new byte[ProfileImageService.MAX_BYTES + 1];
        large[0] = (byte) 0xFF;
        large[1] = (byte) 0xD8;
        large[2] = (byte) 0xFF;

        assertEquals(
                "too_large",
                assertThrows(ProfileImageException.class, () -> ProfileImageService.decode(Kind.BACKGROUND, large))
                        .getCode());
    }

    @Test
    void servesOnlyTheFileNamesItWrites() {
        assertTrue(ProfileImageService.isValidFileName("12_banner_0123456789abcdef.jpg"));
        assertTrue(ProfileImageService.isValidFileName("12_background_0123456789abcdef.jpg"));
        assertFalse(ProfileImageService.isValidFileName("../config.ini"));
        assertFalse(ProfileImageService.isValidFileName("12_banner_0123456789abcdef.jpg/../x"));
        assertFalse(ProfileImageService.isValidFileName("12_avatar_0123456789abcdef.jpg"));
        assertFalse(ProfileImageService.isValidFileName(null));
    }

    @Test
    void kindsMatchTheSizesTheClientCropsTo() {
        assertEquals(Kind.BANNER, Kind.fromKey("banner"));
        assertEquals(Kind.BACKGROUND, Kind.fromKey("BACKGROUND"));
        assertEquals(null, Kind.fromKey("avatar"));
        assertEquals(1280, Kind.BANNER.width);
        assertEquals(220, Kind.BANNER.height);
        assertEquals(1380, Kind.BACKGROUND.height);
    }

    @Test
    void acceptsOnlyAWellFormedBackgroundStyle() {
        assertEquals("1e7cb8;0b2a40;135;12", ProfileImageService.normalizeBackgroundStyle(" 1E7CB8;0b2a40;135;12 "));
        assertEquals("", ProfileImageService.normalizeBackgroundStyle(""));
        assertEquals(null, ProfileImageService.normalizeBackgroundStyle("1e7cb8;0b2a40;30;12"));
        assertEquals(null, ProfileImageService.normalizeBackgroundStyle("red;blue;0;0"));
        assertEquals(null, ProfileImageService.normalizeBackgroundStyle("1e7cb8;0b2a40;0;0;url(x)"));
        assertEquals(12, ProfileImageService.backgroundStylePattern("1e7cb8;0b2a40;135;12"));
        assertEquals(0, ProfileImageService.backgroundStylePattern(""));
    }

    @Test
    void reportsTheFeaturesTheHotelHasOn() {
        assertEquals(7, ProfileImageService.Settings.DEFAULTS.features());
        assertEquals(
                ProfileImageService.FEATURE_UPLOAD,
                new ProfileImageService.Settings(true, "x", 0, false, false).features());
        assertEquals(
                ProfileImageService.FEATURE_BANNER | ProfileImageService.FEATURE_THEME,
                new ProfileImageService.Settings(false, "x", 0, true, true).features());
    }
}
