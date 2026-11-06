package polaris.hotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The official "give furni from chest" order: random, oldest first or newest first. The rows that
 * leave are the stored rows themselves, so the chest and its aggregate count stay in step.
 */
class ChestFurniIterationTest {

    private static ChestStorage chestWith(int... baseItemIds) {
        ChestStorage storage = new ChestStorage();
        for (int id : baseItemIds) {
            ChestFurniStoredItem item = new ChestFurniStoredItem();
            item.baseItemId = id;
            storage.addFurniItem(item);
        }
        return storage;
    }

    private static List<Integer> ids(List<ChestFurniStoredItem> items) {
        return items.stream().map(item -> item.baseItemId).toList();
    }

    @Test
    void oldestFirstAndNewestFirstTakeFromEitherEnd() {
        ChestStorage oldest = chestWith(1, 2, 3);
        assertEquals(List.of(1, 2), ids(oldest.removeFurniInOrder(1, 2, new Random(1))));
        assertEquals(1, oldest.furniItemCount());
        assertEquals(1, oldest.total(ChestStorage.KIND_FURNI), "the aggregate count follows the rows");

        ChestStorage newest = chestWith(1, 2, 3);
        assertEquals(List.of(3, 2), ids(newest.removeFurniInOrder(2, 2, new Random(1))));
    }

    @Test
    void randomTakesWhatIsThereAndNeverMore() {
        ChestStorage storage = chestWith(1, 2, 3);

        List<ChestFurniStoredItem> taken = storage.removeFurniInOrder(0, 10, new Random(7));

        assertEquals(3, taken.size());
        assertEquals(0, storage.furniItemCount());
        assertEquals(0, storage.total(ChestStorage.KIND_FURNI));
    }
}
