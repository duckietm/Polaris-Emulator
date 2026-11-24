package polaris.hotel.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CompanionPrizeTest {
    @Test
    void aBotIsReadLikeACatalogBot() {
        CompanionPrize.Bot bot = CompanionPrize.bot("bartender;name:Frank;figure:hr-100.hd-180-1;gender:f;motto:Hi");

        assertEquals("bartender", bot.type());
        assertEquals("Frank", bot.data().get("name"));
        assertEquals("hr-100.hd-180-1", bot.figure());
        assertEquals("F", bot.data().get("gender"));
        assertEquals("Hi", bot.data().get("motto"));
    }

    @Test
    void onlyTheFigureIsRequiredForABot() {
        CompanionPrize.Bot bot = CompanionPrize.bot("figure:hd-180-1");

        assertEquals("generic", bot.type());
        assertEquals("Bot", bot.data().get("name"));
        assertEquals("M", bot.data().get("gender"));
        assertEquals("", bot.data().get("motto"));

        assertNull(CompanionPrize.bot(null));
        assertNull(CompanionPrize.bot("  "));
        assertNull(CompanionPrize.bot("bartender;name:Frank"));
    }

    @Test
    void aPetIsItsTypeRaceColourAndName() {
        CompanionPrize.Pet horse = CompanionPrize.pet("15 2 ffffff Shadowfax the Grey");

        assertEquals(15, horse.type());
        assertEquals(2, horse.race());
        assertEquals("FFFFFF", horse.color());
        assertEquals("Shadowfax the Grey", horse.name());
        assertEquals("15 2 FFFFFF", horse.figure());

        assertEquals(new CompanionPrize.Pet(0, 0, "FFFFFF", null), CompanionPrize.pet("0"));
        assertEquals("Pet", CompanionPrize.pet("0").nameOrDefault());
        assertEquals("Shadowfax the Grey", horse.nameOrDefault());
    }

    @Test
    void anythingElseIsNoPet() {
        assertNull(CompanionPrize.pet(null));
        assertNull(CompanionPrize.pet(""));
        assertNull(CompanionPrize.pet("horse"));
        assertNull(CompanionPrize.pet("15 x"));
        assertNull(CompanionPrize.pet("-1 0"));
        assertNull(CompanionPrize.pet("15 2 red"));
    }

    @Test
    void aLongNameIsCut() {
        assertEquals(
                25, CompanionPrize.pet("15 2 FFFFFF " + "a".repeat(40)).name().length());
        assertEquals(
                25,
                CompanionPrize.bot("name:" + "b".repeat(40) + ";figure:hd-180-1")
                        .data()
                        .get("name")
                        .length());
    }
}
