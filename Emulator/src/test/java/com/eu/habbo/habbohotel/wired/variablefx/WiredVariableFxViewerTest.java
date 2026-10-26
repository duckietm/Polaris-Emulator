package com.eu.habbo.habbohotel.wired.variablefx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A viewer is only ever sent what changed for them; the first batch is a full sync. */
class WiredVariableFxViewerTest {

    private static final WiredVariableFxStatus.Key HP = new WiredVariableFxStatus.Key(10, "user:1", true, 5);
    private static final WiredVariableFxStatus.Key XP = new WiredVariableFxStatus.Key(11, "user:2", true, 5);

    private static WiredVariableFxStatus status(WiredVariableFxStatus.Key key, long value) {
        return new WiredVariableFxStatus(key, false, value, null, null, Map.of());
    }

    private static Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> wanted(WiredVariableFxStatus... statuses) {
        Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> map = new LinkedHashMap<>();
        for (WiredVariableFxStatus status : statuses) map.put(status.key(), status);
        return map;
    }

    @Test
    void theFirstBatchIsAFullSyncWithEverythingInitialised() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();

        WiredVariableFxViewer.Batch batch = viewer.diff(wanted(status(HP, 50), status(XP, 7)));

        assertTrue(batch.initializeAll());
        assertEquals(2, batch.updates().size());
        assertTrue(batch.updates().stream().allMatch(WiredVariableFxStatus::initialize));
        assertTrue(batch.removed().isEmpty());
        assertTrue(viewer.isSynced());
        assertEquals(2, viewer.sentCount());
    }

    @Test
    void anUnchangedStateSendsNothing() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50)));

        WiredVariableFxViewer.Batch batch = viewer.diff(wanted(status(HP, 50)));

        assertFalse(batch.initializeAll());
        assertTrue(batch.isEmpty());
    }

    @Test
    void aChangedValueIsAnUpdateAndAGoneKeyIsARemoval() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50), status(XP, 7)));

        WiredVariableFxViewer.Batch batch = viewer.diff(wanted(status(HP, 49)));

        assertEquals(1, batch.updates().size());
        assertEquals(49, batch.updates().get(0).value());
        // The client animates a change; only a status it never had is drawn straight away.
        assertFalse(batch.updates().get(0).initialize());
        assertEquals(List.of(XP), batch.removed());
        assertEquals(1, viewer.sentCount());
    }

    @Test
    void aKeyThatComesBackAfterRemovalIsInitialisedAgain() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50)));
        viewer.diff(wanted());

        WiredVariableFxViewer.Batch batch = viewer.diff(wanted(status(HP, 50)));

        assertEquals(1, batch.updates().size());
        assertTrue(batch.updates().get(0).initialize());
        assertFalse(batch.initializeAll());
    }

    @Test
    void overridesAndExtrasArePartOfWhatCountsAsAChange() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50)));

        WiredVariableFxStatus withRange = new WiredVariableFxStatus(HP, false, 50, 0L, 200L, Map.of());
        assertEquals(1, viewer.diff(wanted(withRange)).updates().size());

        WiredVariableFxStatus withExtra =
                new WiredVariableFxStatus(HP, false, 50, 0L, 200L, Map.of("current_level", "2"));
        assertEquals(1, viewer.diff(wanted(withExtra)).updates().size());
        assertTrue(viewer.diff(wanted(withExtra)).isEmpty());
    }

    @Test
    void aVariableGivenToAHolderTheViewerHasIsAChangeNotAnInitialize() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50)));

        // XP is new to the viewer, but its holder (user 5) is not: AIR sends it without initialize.
        WiredVariableFxViewer.Batch batch = viewer.diff(wanted(status(HP, 50), status(XP, 3)));

        assertEquals(1, batch.updates().size());
        assertFalse(batch.updates().get(0).initialize());
        assertFalse(batch.initializeAll());
    }

    @Test
    void aBoxSavedAgainSendsAllItsStatusesInitialized() {
        WiredVariableFxViewer viewer = new WiredVariableFxViewer();
        viewer.diff(wanted(status(HP, 50), status(XP, 7)));

        WiredVariableFxViewer.Batch batch =
                viewer.diff(wanted(status(HP, 50), status(XP, 7)), java.util.Set.of(HP.configId()));

        assertTrue(batch.initializeAll());
        assertEquals(
                List.of(HP),
                batch.updates().stream().map(WiredVariableFxStatus::key).toList());
        assertTrue(batch.updates().get(0).initialize());
    }
}
