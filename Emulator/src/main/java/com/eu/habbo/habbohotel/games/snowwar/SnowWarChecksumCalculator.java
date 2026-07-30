package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarPileObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarTreeObject;
import java.util.List;

/**
 * Deterministic game state checksum (README 13 / 12.2).
 * MUST be calculated BEFORE deferred state changes are applied.
 * Plain 32-bit int arithmetic; overflow wraps identically on the client.
 */
public final class SnowWarChecksumCalculator {

    private SnowWarChecksumCalculator() {}

    public static int calculate(SnowWarGame game, List<SnowWarSnowballObject> snowballs, int turn) {
        int checksum = SnowWarMath.iterateSeed(turn);

        for (SnowWarMachineObject machine : game.getMachines()) {
            checksum += machine.getChecksumContribution();
        }

        for (SnowWarPileObject pile : game.getPiles()) {
            if (pile.isAlive()) {
                checksum += pile.getChecksumContribution();
            }
        }

        for (SnowWarTreeObject tree : game.getTrees()) {
            if (tree.isAlive()) {
                checksum += tree.getChecksumContribution();
            }
        }

        for (SnowWarGamePlayer player : game.getActivePlayers()) {
            if (player.getAvatar() != null) {
                checksum += player.getAvatar().getChecksumContribution();
            }
        }

        for (SnowWarSnowballObject ball : snowballs) {
            checksum += ball.getChecksumContribution();
        }

        return checksum;
    }
}
