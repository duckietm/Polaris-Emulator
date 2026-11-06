package polaris.hotel.users;

/** Told when a furni's position or rotation changes, so the room's tile index can follow it. */
@FunctionalInterface
public interface ItemFootprintListener {
    void onFootprintChanged(UserItem item);
}
