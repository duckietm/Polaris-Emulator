package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;

class WiredMovementPhysicsTest {

    @Test
    void theMovingFurniNeverBlocksOrIsPassedThroughByItself() {
        WiredMovementPhysics physics = new WiredMovementPhysics(false, Set.of(1, 2), Set.of(), Set.of(2));
        WiredMovementPhysics movingTwo = physics.withoutFurni(2);

        assertTrue(physics.isBlockingFurni(item(2)));
        assertFalse(movingTwo.isBlockingFurni(item(2)));
        assertFalse(movingTwo.hasBlockingFurni());
        assertTrue(movingTwo.shouldIgnoreFurni(item(1)));
        assertTrue(movingTwo.isActive());
        assertFalse(new WiredMovementPhysics(false, Set.of(3), Set.of(), Set.of())
                .withoutFurni(3)
                .isActive());
    }

    @Test
    void theSelectionCountsTowardsTheBudget() {
        WiredContext ctx = mock(WiredContext.class);
        WiredTargets targets = new WiredTargets();
        for (int i = 1; i <= 250; i++) {
            targets.addItem(item(i));
        }
        targets.addUser(mock(RoomUnit.class));
        when(ctx.targets()).thenReturn(targets);

        assertEquals(25, WiredStackExecutor.selectionCost(ctx));
    }

    private static UserItem item(int id) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }
}
