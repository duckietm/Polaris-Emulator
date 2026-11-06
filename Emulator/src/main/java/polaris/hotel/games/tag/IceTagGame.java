package polaris.hotel.games.tag;

import polaris.hotel.games.GameTeam;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;
import polaris.hotel.items.interactions.games.tag.icetag.InteractionIceTagPole;
import polaris.hotel.rooms.Room;

public class IceTagGame extends TagGame {
    private static final int MALE_SKATES = 38;
    private static final int FEMALE_SKATES = 39;

    public IceTagGame(Room room) {
        super(GameTeam.class, TagGamePlayer.class, room);
    }

    @Override
    public Class<? extends InteractionTagPole> getTagPole() {
        return InteractionIceTagPole.class;
    }

    @Override
    public int getMaleEffect() {
        return MALE_SKATES;
    }

    @Override
    public int getMaleTaggerEffect() {
        return MALE_SKATES + 7;
    }

    @Override
    public int getFemaleEffect() {
        return FEMALE_SKATES;
    }

    @Override
    public int getFemaleTaggerEffect() {
        return FEMALE_SKATES + 7;
    }
}
