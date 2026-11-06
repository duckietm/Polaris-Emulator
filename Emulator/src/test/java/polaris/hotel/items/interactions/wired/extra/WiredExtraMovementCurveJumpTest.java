package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.WiredVariableDefinitionInfo;
import polaris.hotel.rooms.variables.RoomVariableManager;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;
import polaris.hotel.wired.core.WiredVariableOperand;
import polaris.hotel.wired.protocol.WiredVariableTargets;

/**
 * Habbo's jump strength on wf_xtra_mov_curve: a signed strength next to our easing curves. Params:
 * [from variable, strength, official target, curve, intensity].
 */
class WiredExtraMovementCurveJumpTest {

    private static WiredExtraMovementCurve box() {
        return new WiredExtraMovementCurve(1, 1, mock(Item.class), "", 0, 0);
    }

    private static WiredSettings settings(int... params) {
        return new WiredSettings(params, "", new int[0], 0);
    }

    @Test
    void aJumpCarriesItsSignedStrengthAsTheStyleIntensity() {
        WiredExtraMovementCurve box = box();

        box.saveData(settings(0, -250, 1, WiredExtraMovementCurve.CURVE_JUMP, 100), null);

        assertEquals(WiredExtraMovementCurve.CURVE_JUMP, box.getCurveType());
        assertEquals(-250, box.getStyleIntensity());
    }

    @Test
    void anEasingCurveStillCarriesItsIntensityAndTheStrengthIsClamped() {
        WiredExtraMovementCurve box = box();

        box.saveData(settings(0, 5000, 1, WiredExtraMovementCurve.CURVE_BOUNCE, 40), null);

        assertEquals(40, box.getStyleIntensity());
        assertEquals(WiredExtraMovementCurve.STRENGTH_MAX, box.getStrength());
    }

    @Test
    void theStrengthSurvivesTheDatabaseAndOldBoxesGetHabbosDefault() throws Exception {
        WiredExtraMovementCurve saved = box();
        saved.saveData(settings(0, 300, 1, WiredExtraMovementCurve.CURVE_JUMP, 100), null);

        WiredExtraMovementCurve loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertEquals(300, loaded.getStrength());

        WiredExtraMovementCurve legacy = box();
        legacy.loadWiredData(row("{\"curveType\":4,\"intensity\":60}"), null);
        assertEquals(WiredExtraMovementCurve.STRENGTH_DEFAULT, legacy.getStrength());
        assertEquals(60, legacy.getIntensity());
    }

    @Test
    void aStrengthFromAVariableIsReadWhenTheFurniMovesAndFallsBackToTheTypedOne() {
        Room room = mock(Room.class);
        RoomVariableManager variables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo definition = mock(WiredVariableDefinitionInfo.class);
        when(definition.hasValue()).thenReturn(true);
        when(room.getRoomVariableManager()).thenReturn(variables);
        when(variables.getDefinitionInfo(77)).thenReturn(definition);
        when(variables.getCurrentValue(77)).thenReturn(250);

        WiredExtraMovementCurve box = box();
        WiredSettings settings = new WiredSettings(
                new int[] {1, 40, WiredVariableTargets.OFFICIAL_GLOBAL, WiredExtraMovementCurve.CURVE_JUMP, 100},
                "custom:77",
                new int[0],
                0);
        settings.setRoom(room);
        box.saveData(settings, null);

        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        assertEquals(250, box.getStyleIntensity(ctx));

        // Read once per firing: the same firing keeps its value.
        when(variables.getDefinitionInfo(77)).thenReturn(null);
        assertEquals(250, box.getStyleIntensity(ctx));

        WiredContext nextFiring = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
        assertEquals(40, box.getStyleIntensity(nextFiring));
    }

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    @Test
    void variableTokensAreStoredInTheirShortForm() {
        assertEquals("custom:5", WiredVariableOperand.normalizeToken("custom:0000005"));
        assertEquals("custom:12", WiredVariableOperand.normalizeToken("12"));
        assertEquals("", WiredVariableOperand.normalizeToken("custom:-3"));
        assertEquals("", WiredVariableOperand.normalizeToken("drop table"));
    }
}
