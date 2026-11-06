package polaris.hotel.items.recolorable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The recolourable furni palettes read as the official client reads them. */
class RecolorablePalettesTest {

    @Test
    void theShippedPalettesAreHabbos() {
        RecolorablePalettes palettes = RecolorablePalettes.shipped();

        assertEquals(69, palettes.colorsFor("bc_quartercircle_recolorable").size());
        assertEquals(
                0xFFFFFF, palettes.colorsFor("bc_quartercircle_recolorable").get(0));
        assertEquals(
                List.of(0x636568, 0x667660, 0x5A5045, 0x7F603B, 0x714A23, 0x7D3822),
                palettes.colorsFor("bc_wood_recolorable"));
        assertEquals(14, palettes.colorsFor("bc_block_glass_recolorable").size(), "classic palette");
        assertEquals(69, palettes.colorsFor("not_listed").size(), "furni not listed use the default palette");
    }

    @Test
    void theLastPaletteNamingAFurniWinsAndBadColoursAreSkipped() {
        RecolorablePalettes palettes = new RecolorablePalettes(Map.of(
                RecolorablePalettes.PALETTE_TO_FURNI,
                "a=x_recolorable; b = X_Recolorable ,y",
                "recolorable_bc.palette.a",
                "FF0000",
                "recolorable_bc.palette.b",
                "00ff00, nothex ,0000FF",
                "recolorable_bc.palette.default",
                "FFFFFF"));

        assertEquals(List.of(0x00FF00, 0x0000FF), palettes.colorsFor("x_recolorable"));
        assertEquals(List.of(0xFFFFFF), palettes.colorsFor("z"));
        assertTrue(palettes.allows("y", 0x0000FF));
        assertFalse(palettes.allows("y", 0xFF0000));
        assertEquals(0x0000FF, palettes.nearest("y", 0x0000F0));
    }

    @Test
    void rgbTextIsReadStrictly() {
        assertEquals(0xFF8000, RecolorablePalettes.parseRgb("255,128,0"));
        assertEquals(0xFF8000, RecolorablePalettes.parseRgb(" 255 , 128 , 0 "));
        assertEquals(-1, RecolorablePalettes.parseRgb("256,0,0"));
        assertEquals(-1, RecolorablePalettes.parseRgb("1,2"));
        assertEquals(-1, RecolorablePalettes.parseRgb(null));
        assertEquals("255,128,0", RecolorablePalettes.formatRgb(0xFF8000));
    }
}
