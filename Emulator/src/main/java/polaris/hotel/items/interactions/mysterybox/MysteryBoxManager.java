package polaris.hotel.items.interactions.mysterybox;

import java.util.concurrent.ConcurrentHashMap;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

/**
 * Mystery box: a box and a key of the same colour, held by two different players, opened together.
 *
 * <p>Clicking the box looks for a key of that colour somebody else placed in the room and opens a
 * wait both of them see. Clicking that key while the wait is open ends it: each player draws a prize
 * from the catalog pool and both pieces of furniture are used up. Either player can walk away, and a
 * wait nobody answers expires on its own.
 *
 * <p>The open waits live in memory because they are worth nothing once the hotel restarts: the
 * furniture is still there and the two players can simply click again.
 */
public class MysteryBoxManager {
    static final int WAIT_SECONDS = 60;

    private static final MysteryBoxManager INSTANCE = new MysteryBoxManager();

    /** The open waits, by the id of the box they were opened on. */
    private final ConcurrentHashMap<Integer, MysteryBoxWait> waits = new ConcurrentHashMap<>();

    private MysteryBoxManager() {}

    public static MysteryBoxManager getInstance() {
        return INSTANCE;
    }

    /** The wait open on this box, or null when there is none or the last one has expired. */
    public MysteryBoxWait waitOn(int boxItemId) {
        MysteryBoxWait wait = this.waits.get(boxItemId);

        if (wait == null) return null;

        if (Emulator.getIntUnixTimestamp() - wait.openedAt() > WAIT_SECONDS) {
            this.waits.remove(boxItemId, wait);
            return null;
        }

        return wait;
    }

    /**
     * The box was clicked. A key of the same colour that belongs to somebody else and sits in this
     * room opens a wait; without one there is nothing to say, so the click does nothing visible.
     */
    public void clickBox(User user, Room room, UserItem box) {
        if (user == null || room == null || box == null) return;

        int owner = user.getUserInfo().getId();

        if (box.getUserId() != owner) return;

        if (this.waitOn(box.getId()) != null) return;

        String colour = MysteryBoxColour.of(box.getBaseItem().getName());
        UserItem key = this.findKey(room, colour, owner);

        if (key == null) return;

        User keyOwner = room.getUser(key.getUserId());

        if (keyOwner == null || keyOwner.getClient() == null) return;

        MysteryBoxWait wait =
                new MysteryBoxWait(box.getId(), owner, key.getId(), key.getUserId(), Emulator.getIntUnixTimestamp());
        this.waits.put(box.getId(), wait);

        MysteryBoxSupport.sendWait(user);
        MysteryBoxSupport.sendWait(keyOwner);
    }

    /** A key of this colour that belongs to somebody other than the box owner, in this room. */
    private UserItem findKey(Room room, String colour, int boxOwnerId) {
        for (UserItem item : room.getFloorItems()) {
            if (item == null || item.getBaseItem() == null) continue;

            if (!(item instanceof InteractionMysteryBoxKey)) continue;

            if (item.getUserId() == boxOwnerId) continue;

            if (!MysteryBoxColour.of(item.getBaseItem().getName()).equals(colour)) continue;

            return item;
        }

        return null;
    }

    /**
     * The key was clicked. It only means something while its box is waiting: then both players draw
     * a prize, both pieces are used up and the wait is over.
     */
    public void clickKey(User user, Room room, UserItem key) {
        if (user == null || room == null || key == null) return;

        if (key.getUserId() != user.getUserInfo().getId()) return;

        MysteryBoxWait wait = this.waitFor(key.getId());

        if (wait == null) return;

        UserItem box = room.getUserItem(wait.boxItemId());
        User boxOwner = room.getUser(wait.boxOwnerId());

        this.waits.remove(wait.boxItemId());

        if (box == null || boxOwner == null) {
            MysteryBoxSupport.sendClose(user);
            return;
        }

        MysteryBoxPrizes prizes = MysteryBoxPrizes.getInstance();
        String colour = MysteryBoxColour.of(box.getBaseItem().getName());

        MysteryBoxSupport.sendPrize(boxOwner, prizes.award(boxOwner, colour));
        MysteryBoxSupport.sendPrize(user, prizes.award(user, colour));

        this.consume(room, box);
        this.consume(room, key);
    }

    /** The wait a key is answering, if its box is still waiting for it. */
    MysteryBoxWait waitFor(int keyItemId) {
        for (MysteryBoxWait wait : this.waits.values()) {
            if (wait.keyItemId() != keyItemId) continue;

            return this.waitOn(wait.boxItemId());
        }

        return null;
    }

    /** Somebody walked away from the wait they are in; the other side is told it is over. */
    public void cancel(User user) {
        if (user == null) return;

        int userId = user.getUserInfo().getId();

        for (MysteryBoxWait wait : this.waits.values()) {
            if (wait.boxOwnerId() != userId && wait.keyOwnerId() != userId) continue;

            this.waits.remove(wait.boxItemId(), wait);

            Room room = user.getUserInfo().getCurrentRoom();
            User other = room == null
                    ? null
                    : room.getUser(wait.boxOwnerId() == userId ? wait.keyOwnerId() : wait.boxOwnerId());

            MysteryBoxSupport.sendClose(user);
            MysteryBoxSupport.sendClose(other);
            return;
        }
    }

    /** The box and the key are used up once they have been opened together. */
    private void consume(Room room, UserItem item) {
        room.removeUserItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));

        if (room.getLayout() != null) {
            room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
        }
    }
}
