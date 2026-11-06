package polaris.hotel.rooms;

public enum RoomUnitType {
    USER(1),
    BOT(4),
    PET(2),
    UNKNOWN(3);

    private final int typeId;

    RoomUnitType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return this.typeId;
    }
}
