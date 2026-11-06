package polaris.hotel.hotlooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HotLooksManagerTest {
    private static final HotLook MALE_A = new HotLook("M", "hd-180-1.ch-210-66");
    private static final HotLook MALE_B = new HotLook("M", "hd-180-2.ch-210-62");
    private static final HotLook FEMALE = new HotLook("F", "hd-600-1.ch-630-66");
    private static final HotLook BLANK = new HotLook("M", "  ");

    @Test
    void normalizesEveryGenderSpellingOntoTheWireValues() {
        assertEquals("M", HotLook.normalizeGender("m"));
        assertEquals("M", HotLook.normalizeGender(" Male "));
        assertEquals("F", HotLook.normalizeGender("female"));
        assertEquals("F", HotLook.normalizeGender("F"));
        assertEquals("M", HotLook.normalizeGender(null));
        assertEquals("M", HotLook.normalizeGender(""));
    }

    @Test
    void selectsOnlyUsableLooksOfTheRequestedGenderInOrder() {
        List<HotLook> looks = List.of(FEMALE, MALE_A, BLANK, MALE_B);

        assertEquals(List.of(MALE_A, MALE_B), HotLooksManager.select(looks, "M", 20));
        assertEquals(List.of(FEMALE), HotLooksManager.select(looks, "F", 20));
    }

    @Test
    void capsTheCountAtTheRequestedAndOfficialLimit() {
        List<HotLook> looks = java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> new HotLook("M", "hd-180-" + index))
                .toList();

        assertEquals(3, HotLooksManager.select(looks, "M", 3).size());
        assertEquals(
                HotLooksManager.MAX_PER_GENDER,
                HotLooksManager.select(looks, "M", 0).size());
        assertEquals(
                HotLooksManager.MAX_PER_GENDER,
                HotLooksManager.select(looks, "M", 99).size());
        assertTrue(HotLooksManager.select(List.of(), "M", 5).isEmpty());
    }
}
