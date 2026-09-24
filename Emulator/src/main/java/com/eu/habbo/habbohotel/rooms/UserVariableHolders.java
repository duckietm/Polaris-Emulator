package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Who holds a user variable. Habbos keep their user id; pets and bots get a negative key so the
 * three id ranges never collide in the variable stores, the database and the variables packet:
 * pet {@code p} is {@code -2p}, bot {@code b} is {@code -(2b + 1)}.
 */
public final class UserVariableHolders {
    public enum Kind {
        USER,
        PET,
        BOT
    }

    /** Largest pet or bot id that still fits in a key. */
    public static final int MAX_UNIT_ID = (Integer.MAX_VALUE - 1) / 2;

    private UserVariableHolders() {}

    public static int ofUser(int userId) {
        return userId > 0 ? userId : 0;
    }

    public static int ofPet(int petId) {
        return petId > 0 && petId <= MAX_UNIT_ID ? -2 * petId : 0;
    }

    public static int ofBot(int botId) {
        return botId > 0 && botId <= MAX_UNIT_ID ? -(2 * botId + 1) : 0;
    }

    public static int of(Kind kind, int id) {
        if (kind == null) {
            return 0;
        }
        return switch (kind) {
            case USER -> ofUser(id);
            case PET -> ofPet(id);
            case BOT -> ofBot(id);
        };
    }

    public static boolean isValid(int key) {
        return key != 0 && key != Integer.MIN_VALUE;
    }

    /** The kind of holder, or null for an invalid key. */
    public static Kind kindOf(int key) {
        if (!isValid(key)) {
            return null;
        }
        if (key > 0) {
            return Kind.USER;
        }
        return (-key) % 2 == 0 ? Kind.PET : Kind.BOT;
    }

    /** The user, pet or bot id inside the key, or 0. */
    public static int idOf(int key) {
        if (!isValid(key)) {
            return 0;
        }
        return key > 0 ? key : (-key) / 2;
    }

    /** The key of whoever stands on this unit in the room, or 0. */
    public static int of(Room room, RoomUnit unit) {
        if (room == null || unit == null || unit.getRoomUnitType() == null) {
            return 0;
        }
        switch (unit.getRoomUnitType()) {
            case USER -> {
                Habbo habbo = room.getHabbo(unit);
                return habbo != null && habbo.getHabboInfo() != null
                        ? ofUser(habbo.getHabboInfo().getId())
                        : 0;
            }
            case PET -> {
                Pet pet = room.getPet(unit);
                return pet != null ? ofPet(pet.getId()) : 0;
            }
            case BOT -> {
                Bot bot = room.getBot(unit);
                return bot != null ? ofBot(bot.getId()) : 0;
            }
            default -> {
                return 0;
            }
        }
    }

    /** The unit of the holder while it is in the room, or null. */
    public static RoomUnit unitOf(Room room, int key) {
        Kind kind = kindOf(key);
        if (room == null || kind == null) {
            return null;
        }
        int id = idOf(key);
        return switch (kind) {
            case USER -> {
                Habbo habbo = room.getHabbo(id);
                yield habbo != null ? habbo.getRoomUnit() : null;
            }
            case PET -> {
                Pet pet = room.getPet(id);
                yield pet != null ? pet.getRoomUnit() : null;
            }
            case BOT -> {
                Bot bot = room.getBot(id);
                yield bot != null ? bot.getRoomUnit() : null;
            }
        };
    }

    /** The holder's name while it is in the room, or an empty string. */
    public static String nameOf(Room room, int key) {
        Kind kind = kindOf(key);
        if (room == null || kind == null) {
            return "";
        }
        int id = idOf(key);
        String name =
                switch (kind) {
                    case USER -> {
                        Habbo habbo = room.getHabbo(id);
                        yield habbo != null && habbo.getHabboInfo() != null
                                ? habbo.getHabboInfo().getUsername()
                                : null;
                    }
                    case PET -> {
                        Pet pet = room.getPet(id);
                        yield pet != null ? pet.getName() : null;
                    }
                    case BOT -> {
                        Bot bot = room.getBot(id);
                        yield bot != null ? bot.getName() : null;
                    }
                };
        return name != null ? name : "";
    }

    public static boolean isInRoom(Room room, int key) {
        return unitOf(room, key) != null;
    }
}
