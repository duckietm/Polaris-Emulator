package polaris.hotel.games.tag;

import polaris.hotel.games.GameTeam;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;
import polaris.hotel.rooms.Room;

public class RollerskateGame extends TagGame {
    public RollerskateGame(Room room) {
        super(GameTeam.class, TagGamePlayer.class, room);
    }

    @Override
    public Class<? extends InteractionTagPole> getTagPole() {
        return null;
    }

    @Override
    public int getMaleEffect() {
        return 55;
    }

    @Override
    public int getMaleTaggerEffect() {
        return 57;
    }

    @Override
    public int getFemaleEffect() {
        return 56;
    }

    @Override
    public int getFemaleTaggerEffect() {
        return 58;
    }
}
