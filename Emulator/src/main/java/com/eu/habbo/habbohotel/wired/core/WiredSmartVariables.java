package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionBackgroundToner;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.StackHelperExtradata;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomAreaHideSupport;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;

/**
 * AIR's smart variables ({@code ~}, variable type 3): values computed from what a furni or a pet is, read only except
 * {@code ~clock.pulse_count}. A key
 * only has a value on the kind it describes (a chest's {@code ~chest.*}, a horse's {@code ~horse.*}); elsewhere it is
 * absent, which AIR shows as an invisible variable. The recolorable colour and the teleport target stay in {@link
 * WiredInternalVariableSupport}, which already wrote them.
 */
final class WiredSmartVariables {
    static final String BACKGROUND_HUE = "~background_color.hue";
    static final String BACKGROUND_SATURATION = "~background_color.saturation";
    static final String BACKGROUND_LIGHTNESS = "~background_color.lightness";
    static final String WALK_HELPER_MULTI_WALK = "~walk_helper.is_multi_walk";
    static final String AREA_ROOT_X = "~area_hide.root_x";
    static final String AREA_ROOT_Y = "~area_hide.root_y";
    static final String AREA_WIDTH = "~area_hide.width";
    static final String AREA_LENGTH = "~area_hide.length";
    static final String AREA_INVISIBLE_FURNI = "~area_hide.is_invisible_furni";
    static final String AREA_HIDING_WALLITEMS = "~area_hide.hiding_wallitems";
    static final String AREA_INVERTED = "~area_hide.inverted";
    static final String CHEST_AVAILABLE_AMOUNT = "~chest.available_amount";
    static final String CHEST_LOCKED = "~chest.locked";
    static final String CHEST_AUTO_LOCK = "~chest.is_auto_lock";
    static final String CHEST_CAPACITY = "~chest.capacity";
    static final String CHEST_OPEN = "~chest.is_open";
    static final String CHEST_DONATABLE = "~chest.is_donatable";
    static final String CLOCK_STATE = "~clock.state";
    static final String CLOCK_PULSE_COUNT = "~clock.pulse_count";
    static final String CLOCK_GAME_AWARE = "~clock.is_game_aware";

    /** AIR's {@code ~clock.state}: 0 Initial (stopped), 1 Running, 2 Paused. */
    static final int CLOCK_INITIAL = 0;

    static final int CLOCK_RUNNING = 1;
    static final int CLOCK_PAUSED = 2;

    /** A pulse is half a second, the unit of AIR's clock and delay sliders. */
    private static final int PULSE_MS = 500;

    static final String PET_LEVEL = "~pet.level";
    static final String PET_EXPERIENCE = "~pet.experience";
    static final String PET_ENERGY = "~pet.energy";
    static final String PET_HAPPINESS = "~pet.happiness";
    static final String PET_MAX_LEVEL = "~pet.max_level";
    static final String PET_EXPERIENCE_REQUIRED = "~pet.experience_required";
    static final String PET_MAX_ENERGY = "~pet.max_energy";
    static final String PET_MAX_HAPPINESS = "~pet.max_happiness";
    static final String PET_SCRATCHES = "~pet.scratches";
    static final String PET_OWNER_ID = "~pet.owner_id";
    static final String PET_CREATION_TIME = "~pet.creation_time";
    static final String HORSE_HAS_SADDLE = "~horse.has_saddle";
    static final String HORSE_IS_RIDING = "~horse.is_riding";
    static final String HORSE_CONTROLLER = "~horse.controller_user_id";
    static final String PLANT_RARITY = "~plant.rarity_level";
    static final String PLANT_SHAPE = "~plant.shape";
    static final String PLANT_COLOR = "~plant.color";
    static final String PLANT_CAN_HARVEST = "~plant.can_harvest";
    static final String PLANT_CAN_BREED = "~plant.can_breed";
    static final String PLANT_CAN_REVIVE = "~plant.can_revive";
    static final String PLANT_IS_DEAD = "~plant.is_dead";
    static final String PLANT_IS_GROWING = "~plant.is_growing";
    static final String PLANT_WELLBEING_LEFT = "~plant.remaining_wellbeing_seconds";
    static final String PLANT_GROWING_LEFT = "~plant.remaining_growing_seconds";
    static final String PLANT_MAX_WELLBEING = "~plant.max_wellbeing_seconds";

    /** The pet info window's ceilings (PetInformationComposer). */
    private static final int PET_LEVEL_CEILING = 20;

    private static final int PET_HAPPINESS_CEILING = 100;

    static final List<String> FURNI_KEYS = List.of(
            BACKGROUND_HUE,
            BACKGROUND_SATURATION,
            BACKGROUND_LIGHTNESS,
            WALK_HELPER_MULTI_WALK,
            AREA_ROOT_X,
            AREA_ROOT_Y,
            AREA_WIDTH,
            AREA_LENGTH,
            AREA_INVISIBLE_FURNI,
            AREA_HIDING_WALLITEMS,
            AREA_INVERTED,
            CHEST_AVAILABLE_AMOUNT,
            CHEST_LOCKED,
            CHEST_AUTO_LOCK,
            CHEST_CAPACITY,
            CHEST_OPEN,
            CHEST_DONATABLE,
            CLOCK_STATE,
            CLOCK_PULSE_COUNT,
            CLOCK_GAME_AWARE);

    static final List<String> USER_KEYS = List.of(
            PET_LEVEL,
            PET_EXPERIENCE,
            PET_ENERGY,
            PET_HAPPINESS,
            PET_MAX_LEVEL,
            PET_EXPERIENCE_REQUIRED,
            PET_MAX_ENERGY,
            PET_MAX_HAPPINESS,
            PET_SCRATCHES,
            PET_OWNER_ID,
            PET_CREATION_TIME,
            HORSE_HAS_SADDLE,
            HORSE_IS_RIDING,
            HORSE_CONTROLLER,
            PLANT_RARITY,
            PLANT_SHAPE,
            PLANT_COLOR,
            PLANT_CAN_BREED,
            PLANT_CAN_HARVEST,
            PLANT_CAN_REVIVE,
            PLANT_IS_DEAD,
            PLANT_IS_GROWING,
            PLANT_WELLBEING_LEFT,
            PLANT_GROWING_LEFT,
            PLANT_MAX_WELLBEING);

    private WiredSmartVariables() {}

    static boolean isFurniKey(String key) {
        return FURNI_KEYS.contains(key);
    }

    static boolean isUserKey(String key) {
        return USER_KEYS.contains(key);
    }

    static boolean hasFurniValue(HabboItem item, String key) {
        return readFurniValue(item, key) != null;
    }

    /** The value of a furni smart variable, or null when the furni is not of its kind. */
    static Integer readFurniValue(HabboItem item, String key) {
        if (item == null || key == null) return null;

        if (key.startsWith("~background_color.")) {
            if (!(item instanceof InteractionBackgroundToner)) return null;
            String[] data = item.getExtradata() == null
                    ? new String[0]
                    : item.getExtradata().split(":");
            int index =
                    switch (key) {
                        case BACKGROUND_HUE -> 1;
                        case BACKGROUND_SATURATION -> 2;
                        case BACKGROUND_LIGHTNESS -> 3;
                        default -> -1;
                    };
            // A toner that never stored its colour shows the default 126/126/126.
            if (index < 0) return null;
            if (data.length != 4) return 126;
            return parse(data[index]);
        }

        if (WALK_HELPER_MULTI_WALK.equals(key)) {
            return item instanceof InteractionStackWalkHelper
                    ? flag(StackHelperExtradata.multiWalk(item.getExtradata()))
                    : null;
        }

        if (key.startsWith("~area_hide.")) {
            if (!(item instanceof InteractionAreaHideControl)) return null;
            return switch (key) {
                case AREA_ROOT_X -> RoomAreaHideSupport.getRootX(item);
                case AREA_ROOT_Y -> RoomAreaHideSupport.getRootY(item);
                case AREA_WIDTH -> RoomAreaHideSupport.getWidth(item);
                case AREA_LENGTH -> RoomAreaHideSupport.getLength(item);
                case AREA_INVISIBLE_FURNI -> flag(RoomAreaHideSupport.isInvisibilityEnabled(item));
                case AREA_HIDING_WALLITEMS -> flag(RoomAreaHideSupport.includesWallItems(item));
                case AREA_INVERTED -> flag(RoomAreaHideSupport.isInverted(item));
                default -> null;
            };
        }

        if (key.startsWith("~chest.")) {
            if (!(item instanceof InteractionWiredChest chest) || chest.getContents() == null) return null;
            return switch (key) {
                case CHEST_AVAILABLE_AMOUNT -> chest.availableAmount();
                case CHEST_LOCKED -> flag(chest.getContents().isLocked());
                case CHEST_AUTO_LOCK -> flag(chest.getContents().isAutoLock());
                case CHEST_CAPACITY -> chest.getContents().getCapacity();
                case CHEST_OPEN -> flag(chest.getContents().isAccessOpen());
                case CHEST_DONATABLE -> flag(chest.getContents().isAccessDonate());
                default -> null;
            };
        }

        if (key.startsWith("~clock.")) {
            if (!(item instanceof InteractionGameTimer clock)) return null;
            return switch (key) {
                case CLOCK_STATE -> clock.isPaused() ? CLOCK_PAUSED : clock.isRunning() ? CLOCK_RUNNING : CLOCK_INITIAL;
                case CLOCK_PULSE_COUNT -> clockPulses(clock);
                // Both kinds start the room's games when they start and end them when they stop.
                case CLOCK_GAME_AWARE -> 1;
                default -> null;
            };
        }

        return null;
    }

    /**
     * The time a counter or timer shows, in pulses (half seconds): what an up counter has counted, what a game timer
     * has left.
     */
    static int clockPulses(InteractionGameTimer clock) {
        if (clock instanceof InteractionGameUpCounter counter)
            return (int) Math.min(Integer.MAX_VALUE, counter.getCurrentTimeMs() / PULSE_MS);
        return Math.max(0, clock.getTimeNow()) * 2;
    }

    /**
     * Set the time a counter or timer shows to {@code pulses} half seconds (AIR {@code ~clock.pulse_count} is
     * writable). An up counter is set as Adjust Clock's "set" does, so a "clock reaches" trigger for that time fires; a
     * game timer keeps whole seconds and goes on from there when running.
     */
    static boolean writeClockPulses(Room room, HabboItem item, int pulses) {
        if (room == null || !(item instanceof InteractionGameTimer clock)) return false;

        int value = Math.max(0, pulses);
        if (clock instanceof InteractionGameUpCounter counter) {
            counter.adjustCounter(room, 2, 0, value);
            return true;
        }

        clock.setTimeNow(value / 2);
        clock.setExtradata(clock.getTimeNow() + "\t" + clock.getBaseTime());
        room.updateItem(clock);
        clock.needsUpdate(true);
        return true;
    }

    static boolean hasUserValue(Pet pet, String key) {
        return readUserValue(pet, key) != null;
    }

    /** The value of a user smart variable (pets, horses and plants), or null when the user is not of its kind. */
    static Integer readUserValue(Pet pet, String key) {
        if (pet == null || key == null) return null;

        if (key.startsWith("~plant.")) {
            if (!(pet instanceof MonsterplantPet plant)) return null;
            return switch (key) {
                case PLANT_RARITY -> plant.getRarity();
                // AIR's shape is 0..11; our plant types are 1..12 (bodyRarity).
                case PLANT_SHAPE -> Math.max(0, plant.getType() - 1);
                case PLANT_COLOR -> plant.getHue();
                case PLANT_CAN_BREED -> flag(plant.canBreed());
                // A grown plant that is alive (AIR's harvest button is never shown, so the flag has no client use).
                case PLANT_CAN_HARVEST -> flag(plant.isFullyGrown() && !plant.isDead());
                case PLANT_CAN_REVIVE, PLANT_IS_DEAD -> flag(plant.isDead());
                case PLANT_IS_GROWING -> flag(!plant.isFullyGrown());
                case PLANT_WELLBEING_LEFT -> plant.remainingTimeToLive();
                case PLANT_GROWING_LEFT -> plant.remainingGrowTime();
                case PLANT_MAX_WELLBEING -> MonsterplantPet.timeToLive;
                default -> null;
            };
        }

        if (key.startsWith("~horse.")) {
            if (!(pet instanceof RideablePet horse)) return null;
            Habbo rider = horse.getRider();
            return switch (key) {
                case HORSE_HAS_SADDLE -> flag(horse.hasSaddle());
                case HORSE_IS_RIDING -> flag(rider != null);
                case HORSE_CONTROLLER ->
                    rider != null && rider.getHabboInfo() != null
                            ? rider.getHabboInfo().getId()
                            : 0;
                default -> null;
            };
        }

        if (!key.startsWith("~pet.") || pet instanceof MonsterplantPet) return null;
        return switch (key) {
            case PET_LEVEL -> pet.getLevel();
            case PET_EXPERIENCE -> pet.getExperience();
            case PET_ENERGY -> pet.getEnergy();
            case PET_HAPPINESS -> pet.getHappiness();
            case PET_MAX_LEVEL -> PET_LEVEL_CEILING;
            case PET_EXPERIENCE_REQUIRED -> experienceRequired(pet);
            case PET_MAX_ENERGY -> pet.getMaxEnergy();
            case PET_MAX_HAPPINESS -> PET_HAPPINESS_CEILING;
            case PET_SCRATCHES -> pet.getRespect();
            case PET_OWNER_ID -> pet.getUserId();
            case PET_CREATION_TIME -> pet.getCreated();
            default -> null;
        };
    }

    /** The experience the pet's next level needs, as the pet info window shows it. */
    private static int experienceRequired(Pet pet) {
        int level = pet.getLevel();
        return level >= 1 && level <= PetManager.experiences.length
                ? PetManager.experiences[level - 1]
                : pet.getExperience();
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static Integer parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
