package polaris.messages.outgoing.rooms.items.rentablespaces;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionRentableSpace;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RentableSpaceInfoComposer extends MessageComposer {
    public static final int SPACE_ALREADY_RENTED = 100;
    public static final int SPACE_EXTEND_NOT_RENTED = 101;
    public static final int SPACE_EXTEND_NOT_RENTED_BY_YOU = 102;
    public static final int CAN_RENT_ONLY_ONE_SPACE = 103;
    public static final int NOT_ENOUGH_CREDITS = 200;
    public static final int NOT_ENOUGH_PIXELS = 201;
    public static final int CANT_RENT_NO_PERMISSION = 202;
    public static final int CANT_RENT_NO_CLUB = 203;
    public static final int CANT_RENT = 300;
    public static final int CANT_RENT_GENERIC = 400;
    // :test 194 b:1 i:101 i:1 s:Admin i:10 i:10

    private final User user;
    private final UserItem item;
    private final int errorCode;

    public RentableSpaceInfoComposer(User user, UserItem item) {
        this.user = user;
        this.item = item;
        this.errorCode = 0;
    }

    public RentableSpaceInfoComposer(User user, UserItem item, int errorCode) {
        this.user = user;
        this.item = item;
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (!(this.item instanceof InteractionRentableSpace)) return null;

        this.response.init(Outgoing.RentableSpaceInfoComposer);
        this.response.appendBoolean(((InteractionRentableSpace) this.item).isRented()); // In Use
        this.response.appendInt(this.errorCode); // Error code.
        this.response.appendInt(((InteractionRentableSpace) this.item).getRenterId()); // User ID
        this.response.appendString(((InteractionRentableSpace) this.item).getRenterName()); // Current Owner
        this.response.appendInt(((InteractionRentableSpace) this.item).getEndTimestamp()
                - Emulator.getIntUnixTimestamp()); // Seconds Remaining
        this.response.appendInt(((InteractionRentableSpace) this.item).rentCost()); // Price
        return this.response;
    }

    public User getUser() {
        return user;
    }

    public UserItem getItem() {
        return item;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
