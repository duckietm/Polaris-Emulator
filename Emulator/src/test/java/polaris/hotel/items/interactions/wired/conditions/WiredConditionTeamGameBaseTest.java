package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.hotel.wired.core.WiredSourceUtil;

class WiredConditionTeamGameBaseTest {

    private final ExposedTeamGameBase guard = new ExposedTeamGameBase();

    @Test
    void scoresAreNonNegativeAndCapped() {
        assertEquals(0, this.guard.score(-1));
        assertEquals(42, this.guard.score(42));
        assertEquals(WiredConditionTeamGameBase.MAX_SCORE, this.guard.score(Integer.MAX_VALUE));
    }

    @Test
    void userSourcesFallBackToTriggerWhenUnknown() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, this.guard.userSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, this.guard.userSource(WiredSourceUtil.SOURCE_SELECTOR));
    }

    @Test
    void teamTypesAndPlacementsStayInSupportedRange() {
        assertEquals(1, this.guard.placement(-1));
        assertEquals(4, this.guard.placement(4));
        assertEquals(1, this.guard.explicitTeamType(-1));
        assertEquals(4, this.guard.explicitTeamType(4));
    }

    private static class ExposedTeamGameBase extends WiredConditionTeamGameBase {
        private ExposedTeamGameBase() {
            super(0, 0, null, "", 0, 0);
        }

        @Override
        public polaris.hotel.wired.WiredConditionType getType() {
            return polaris.hotel.wired.WiredConditionType.TEAM_HAS_SCORE;
        }

        @Override
        public boolean evaluate(polaris.hotel.wired.core.WiredContext ctx) {
            return false;
        }

        @Override
        public boolean execute(polaris.hotel.rooms.RoomUnit roomUnit, polaris.hotel.rooms.Room room, Object[] stuff) {
            return false;
        }

        @Override
        public boolean saveData(polaris.hotel.items.interactions.wired.WiredSettings settings) {
            return false;
        }

        @Override
        public void onPickUp() {}

        @Override
        public String getWiredData() {
            return "";
        }

        @Override
        public void loadWiredData(java.sql.ResultSet set, polaris.hotel.rooms.Room room) {}

        @Override
        public void serializeWiredData(polaris.messages.ServerMessage message, polaris.hotel.rooms.Room room) {}

        int score(int value) {
            return this.normalizeScore(value);
        }

        int userSource(int value) {
            return this.normalizeUserSource(value);
        }

        int placement(int value) {
            return this.normalizePlacement(value);
        }

        int explicitTeamType(int value) {
            return this.normalizeExplicitTeamType(value);
        }
    }
}
