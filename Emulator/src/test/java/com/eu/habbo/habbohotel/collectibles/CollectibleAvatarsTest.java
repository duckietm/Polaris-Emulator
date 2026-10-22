package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.AvatarOutfit;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class CollectibleAvatarsTest {
    @Test
    void gendersAreReadTheWayTheEditorReadsThem() {
        assertEquals("F", CollectibleAvatars.normalizeGender("f"));
        assertEquals("F", CollectibleAvatars.normalizeGender(" F "));
        assertEquals("M", CollectibleAvatars.normalizeGender("m"));
        assertEquals("M", CollectibleAvatars.normalizeGender(null));
        assertEquals("M", CollectibleAvatars.normalizeGender("x"));
    }

    @Test
    void anOutfitIsWornOnlyWhileTheLookIsItsFigure() {
        assertTrue(CollectibleAvatars.isWorn("hd-180-1.hr-100-61", "hd-180-1.hr-100-61"));
        assertTrue(CollectibleAvatars.isWorn("hd-180-1", " HD-180-1 "));
        assertFalse(CollectibleAvatars.isWorn("hd-180-1", "hd-180-2"));
        assertFalse(CollectibleAvatars.isWorn("", ""));
        assertFalse(CollectibleAvatars.isWorn("hd-180-1", null));
    }

    @Test
    void wardrobeIdsAreThePositiveAssetIds() {
        assertEquals(OptionalLong.of(42), CollectibleAvatars.parseAssetId("42"));
        assertEquals(OptionalLong.of(5_000_000_000L), CollectibleAvatars.parseAssetId(" 5000000000 "));
        assertTrue(CollectibleAvatars.parseAssetId("0").isEmpty());
        assertTrue(CollectibleAvatars.parseAssetId("-3").isEmpty());
        assertTrue(CollectibleAvatars.parseAssetId("4a").isEmpty());
        assertTrue(CollectibleAvatars.parseAssetId("٤٢").isEmpty());
        assertTrue(CollectibleAvatars.parseAssetId("99999999999999999999").isEmpty());
        assertTrue(CollectibleAvatars.parseAssetId(null).isEmpty());
    }

    @Test
    void outfitRowsDefaultToTheAvatarCollection() {
        AvatarOutfit outfit = new AvatarOutfit("nft_punk", null, "f", " ");
        assertEquals("", outfit.figure());
        assertEquals("F", outfit.gender());
        assertEquals(CollectibleAvatars.CONTRACT_AVATAR, outfit.contractKey());
    }

    @Test
    void avatarCollectiblesFollowTheMainSwitch() {
        assertFalse(CollectiblesSettings.defaults().avatarsActive());
        assertTrue(new CollectiblesSettings(true, 1001, 1000, true, 1000, 10, true, true).avatarsActive());
        assertFalse(new CollectiblesSettings(true, 1001, 1000, true, 1000, 10, true, true, false).avatarsActive());
        assertFalse(new CollectiblesSettings(false, 1001, 1000, true, 1000, 10, true, true, true).avatarsActive());
    }
}
