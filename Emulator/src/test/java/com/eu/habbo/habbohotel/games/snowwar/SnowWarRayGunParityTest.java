package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnowWarRayGunParityTest {

    @Test
    void firesWhenThePlayerReachesTheRearTileFacingTheRayGun() {
        SnowWarItem rayGun = new SnowWarItem("ads_igorraygun", 10, 10, 2);
        rayGun.setSize(1, 2);
        SnowWarMap map =
                new SnowWarMap(1, Collections.nCopies(30, "0".repeat(30)), List.of(rayGun), List.of(), List.of());
        SnowWarAttributes attributes = new SnowWarAttributes();
        attributes.setCurrentPosition(new SnowWarPoint(9, 10));
        attributes.setRotation(2);

        assertSame(rayGun, SnowWarGameTask.findUsableRayGun(map, attributes));
    }

    @Test
    void recognizesTheArcticRayGunRearTileRegardlessOfApproachDirection() {
        SnowWarItem rayGun = new SnowWarItem("ads_igorraygun", 41, 33, 6);
        rayGun.setSize(1, 2);
        SnowWarMap map =
                new SnowWarMap(8, Collections.nCopies(50, "0".repeat(50)), List.of(rayGun), List.of(), List.of());
        SnowWarAttributes attributes = new SnowWarAttributes();
        attributes.setCurrentPosition(new SnowWarPoint(43, 33));

        assertSame(rayGun, SnowWarGameTask.findUsableRayGun(map, attributes));
    }

    @Test
    void reproducesTheGameCenterSevenBallSpread() {
        SnowWarItem rayGun = new SnowWarItem("ads_igorraygun", 10, 10, 2);

        assertArrayEquals(
                new SnowWarPoint[] {
                    new SnowWarPoint(25, 10),
                    new SnowWarPoint(25, 11),
                    new SnowWarPoint(26, 10),
                    new SnowWarPoint(24, 11),
                    new SnowWarPoint(24, 9),
                    new SnowWarPoint(26, 9),
                    new SnowWarPoint(26, 11)
                },
                SnowWarGameTask.getRayGunBurstTargets(rayGun));
    }
}
