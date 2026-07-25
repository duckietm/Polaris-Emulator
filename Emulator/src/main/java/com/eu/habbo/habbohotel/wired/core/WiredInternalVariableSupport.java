package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class WiredInternalVariableSupport {
    private static final ThreadLocal<Boolean> USER_MOVE_INSTANT_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<UserMoveBatch> USER_MOVE_BATCH = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_MOVE_BATCH_DEPTH = new ThreadLocal<>();

    private WiredInternalVariableSupport() {
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }

        String normalized = key.trim();

        return switch (normalized) {
            case "@position.x" -> "@position_x";
            case "@position.y" -> "@position_y";
            case "@effect" -> "@effect_id";
            case "@handitems" -> "@handitem_id";
            case "@is_mute" -> "@is_muted";
            case "@teams.red.score" -> "@team_red_score";
            case "@teams.green.score" -> "@team_green_score";
            case "@teams.blue.score" -> "@team_blue_score";
            case "@teams.yellow.score" -> "@team_yellow_score";
            case "@teams.red.size" -> "@team_red_size";
            case "@teams.green.size" -> "@team_green_size";
            case "@teams.blue.size" -> "@team_blue_size";
            case "@teams.yellow.size" -> "@team_yellow_size";
            default -> normalized;
        };
    }

    public static boolean canUseUserDestination(String key) {
        String normalized = normalizeKey(key);
        return "@position_x".equals(normalized) || "@position_y".equals(normalized) || "@direction".equals(normalized);
    }

    public static boolean canUseFurniDestination(String key) {
        String normalized = normalizeKey(key);
        return "@state".equals(normalized) || "@position_x".equals(normalized) || "@position_y".equals(normalized)
            || "@rotation".equals(normalized) || "@altitude".equals(normalized);
    }

    public static boolean canUseUserReference(String key) {
        String normalized = normalizeKey(key);

        return "@index".equals(normalized) || "@type".equals(normalized) || "@gender".equals(normalized)
            || "@level".equals(normalized) || "@achievement_score".equals(normalized) || "@is_hc".equals(normalized)
            || "@has_rights".equals(normalized) || "@is_group_admin".equals(normalized) || "@is_owner".equals(normalized)
            || "@is_muted".equals(normalized) || "@is_trading".equals(normalized) || "@is_frozen".equals(normalized)
            || "@effect_id".equals(normalized) || "@team_score".equals(normalized) || "@team_color".equals(normalized)
            || "@team_type".equals(normalized) || "@sign".equals(normalized) || "@dance".equals(normalized)
            || "@is_idle".equals(normalized) || "@handitem_id".equals(normalized) || "@position_x".equals(normalized)
            || "@position_y".equals(normalized) || "@direction".equals(normalized) || "@altitude".equals(normalized)
            || "@favourite_group_id".equals(normalized) || "@room_entry.method".equals(normalized)
            || "@room_entry.teleport_id".equals(normalized) || "@user_id".equals(normalized)
            || "@bot_id".equals(normalized) || "@pet_id".equals(normalized) || "@pet_owner_id".equals(normalized);
    }

    public static boolean canUseFurniReference(String key) {
        String normalized = normalizeKey(key);

        return "~teleport.target_id".equals(normalized) || "@id".equals(normalized) || "@class_id".equals(normalized)
            || "@height".equals(normalized) || "@state".equals(normalized) || "@position_x".equals(normalized)
            || "@position_y".equals(normalized) || "@rotation".equals(normalized) || "@altitude".equals(normalized)
            || "@is_invisible".equals(normalized) || "@type".equals(normalized) || "@is_stackable".equals(normalized)
            || "@can_stand_on".equals(normalized) || "@can_sit_on".equals(normalized) || "@can_lay_on".equals(normalized)
            || "@owner_id".equals(normalized) || "@wallitem_offset".equals(normalized)
            || "@dimensions.x".equals(normalized) || "@dimensions.y".equals(normalized);
    }

    public static boolean canUseRoomReference(String key) {
        String normalized = normalizeKey(key);

        return "@furni_count".equals(normalized) || "@user_count".equals(normalized) || "@wired_timer".equals(normalized)
            || "@team_red_score".equals(normalized) || "@team_green_score".equals(normalized) || "@team_blue_score".equals(normalized)
            || "@team_yellow_score".equals(normalized) || "@team_red_size".equals(normalized) || "@team_green_size".equals(normalized)
            || "@team_blue_size".equals(normalized) || "@team_yellow_size".equals(normalized) || "@room_id".equals(normalized)
            || "@group_id".equals(normalized) || "@timezone_server".equals(normalized) || "@timezone_client".equals(normalized)
            || "@current_time".equals(normalized) || "@current_time.millisecond_of_second".equals(normalized)
            || "@current_time.seconds_of_minute".equals(normalized) || "@current_time.minute_of_hour".equals(normalized)
            || "@current_time.hour_of_day".equals(normalized) || "@current_time.day_of_week".equals(normalized)
            || "@current_time.day_of_month".equals(normalized) || "@current_time.day_of_year".equals(normalized)
            || "@current_time.week_of_year".equals(normalized) || "@current_time.month_of_year".equals(normalized)
            || "@current_time.year".equals(normalized);
    }

    public static boolean canUseContextReference(String key) {
        String normalized = normalizeKey(key);

        return "@selector_furni_count".equals(normalized) || "@selector_user_count".equals(normalized)
            || "@signal_furni_count".equals(normalized) || "@signal_user_count".equals(normalized)
            || "@antenna_id".equals(normalized) || "@chat_type".equals(normalized) || "@chat_style".equals(normalized);
    }

    public static boolean hasUserValue(Room room, RoomUnit roomUnit, String key) {
        if (room == null || roomUnit == null) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        Bot bot = room.getBot(roomUnit);
        Pet pet = room.getPet(roomUnit);
        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "@index", "@type", "@level", "@achievement_score", "@position_x", "@position_y", "@direction", "@altitude" -> true;
            case "@gender" -> habbo != null || bot != null;
            case "@is_hc" -> habbo != null && habbo.getHabboStats().hasActiveClub();
            case "@has_rights" -> habbo != null && (room.hasRights(habbo) || room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS));
            case "@is_group_admin" -> habbo != null && room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN);
            case "@is_owner" -> habbo != null && room.isOwner(habbo);
            case "@is_muted" -> (habbo != null && room.isMuted(habbo)) || (pet != null && pet.isMuted());
            case "@is_trading" -> habbo != null && room.getActiveTradeForHabbo(habbo) != null;
            case "@is_frozen" -> WiredFreezeUtil.isFrozen(roomUnit);
            case "@effect_id" -> roomUnit.getEffectId() > 0;
            case "@team_score", "@team_color", "@team_type" -> getTeamEffectData(roomUnit.getEffectId()) != null;
            case "@sign" -> roomUnit.hasStatus(RoomUnitStatus.SIGN);
            case "@dance" -> roomUnit.getDanceType() != null && roomUnit.getDanceType() != DanceType.NONE;
            case "@is_idle" -> roomUnit.isIdle();
            case "@handitem_id" -> roomUnit.getHandItem() > 0;
            case "@favourite_group_id" -> habbo != null && habbo.getHabboStats().guild > 0;
            case "@room_entry.method" -> habbo != null && hasRoomEntryMethod(habbo);
            case "@room_entry.teleport_id" -> habbo != null && habbo.getHabboInfo().getRoomEntryTeleportId() > 0;
            case "@user_id" -> habbo != null;
            case "@bot_id" -> bot != null;
            case "@pet_id" -> pet != null;
            case "@pet_owner_id" -> pet != null && pet.getUserId() > 0;
            default -> false;
        };
    }

    public static boolean hasFurniValue(HabboItem item, String key) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "@id", "@class_id", "@height", "@state", "@position_x", "@position_y", "@rotation", "@altitude",
                "@is_invisible", "@type", "@owner_id", "@dimensions.x", "@dimensions.y" -> true;
            case "~teleport.target_id" -> item.getTeleportTargetId() > 0;
            case "@wallitem_offset" -> item.getBaseItem().getType() == FurnitureType.WALL;
            case "@is_stackable" -> item.getBaseItem().allowStack();
            case "@can_stand_on" -> item.getBaseItem().allowWalk();
            case "@can_sit_on" -> item.getBaseItem().allowSit();
            case "@can_lay_on" -> item.getBaseItem().allowLay();
            default -> false;
        };
    }

    public static boolean hasRoomValue(Room room, String key) {
        return room != null && canUseRoomReference(key);
    }

    public static Integer readUserValue(Room room, RoomUnit roomUnit, String key) {
        if (room == null || roomUnit == null) {
            return null;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        Bot bot = room.getBot(roomUnit);
        Pet pet = room.getPet(roomUnit);
        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "@index" -> roomUnit.getId();
            case "@type" -> getUserTypeValue(habbo, bot, pet);
            case "@gender" -> getGenderValue(habbo, bot);
            case "@level" -> (roomUnit.getRightsLevel() != null) ? roomUnit.getRightsLevel().level : 0;
            case "@achievement_score" -> (habbo != null) ? habbo.getHabboStats().getAchievementScore() : null;
            case "@is_hc" -> (habbo != null && habbo.getHabboStats().hasActiveClub()) ? 1 : 0;
            case "@has_rights" -> (habbo != null && (room.hasRights(habbo) || room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS))) ? 1 : 0;
            case "@is_group_admin" -> (habbo != null && room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN)) ? 1 : 0;
            case "@is_owner" -> (habbo != null && room.isOwner(habbo)) ? 1 : 0;
            case "@is_muted" -> ((habbo != null && room.isMuted(habbo)) || (pet != null && pet.isMuted())) ? 1 : 0;
            case "@is_trading" -> (habbo != null && room.getActiveTradeForHabbo(habbo) != null) ? 1 : 0;
            case "@is_frozen" -> WiredFreezeUtil.isFrozen(roomUnit) ? 1 : 0;
            case "@effect_id" -> roomUnit.getEffectId();
            case "@team_score" -> getUserTeamScore(room, habbo);
            case "@team_color" -> getTeamColorId(roomUnit.getEffectId());
            case "@team_type" -> getTeamTypeId(roomUnit.getEffectId());
            case "@sign" -> parseStatusInteger(roomUnit, RoomUnitStatus.SIGN);
            case "@dance" -> (roomUnit.getDanceType() != null) ? roomUnit.getDanceType().getType() : 0;
            case "@is_idle" -> roomUnit.isIdle() ? 1 : 0;
            case "@handitem_id" -> roomUnit.getHandItem();
            case "@position_x" -> (int) roomUnit.getX();
            case "@position_y" -> (int) roomUnit.getY();
            case "@direction" -> (roomUnit.getBodyRotation() != null) ? (int) roomUnit.getBodyRotation().getValue() : 0;
            case "@altitude" -> (int) Math.round(roomUnit.getZ() * 100);
            case "@favourite_group_id" -> (habbo != null) ? habbo.getHabboStats().guild : null;
            case "@room_entry.method" -> getRoomEntryMethodValue(habbo);
            case "@room_entry.teleport_id" -> (habbo != null) ? habbo.getHabboInfo().getRoomEntryTeleportId() : null;
            case "@user_id" -> (habbo != null) ? habbo.getHabboInfo().getId() : null;
            case "@bot_id" -> (bot != null) ? bot.getId() : null;
            case "@pet_id" -> (pet != null) ? pet.getId() : null;
            case "@pet_owner_id" -> (pet != null) ? pet.getUserId() : null;
            default -> null;
        };
    }

    public static boolean writeUserValue(Room room, RoomUnit roomUnit, String key, int value) {
        Boolean instantOverride = USER_MOVE_INSTANT_OVERRIDE.get();

        if (instantOverride != null) {
            return writeUserValue(room, roomUnit, key, value, WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION, instantOverride);
        }

        return writeUserValue(room, roomUnit, key, value, WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION, false);
    }

    public static boolean writeUserValue(Room room, RoomUnit roomUnit, String key, int value, int animationDuration, boolean noAnimation) {
        if (room == null || roomUnit == null) {
            return false;
        }

        String normalized = normalizeKey(key);

        if (stageUserMoveIfPossible(room, roomUnit, normalized, value, animationDuration, noAnimation)) {
            return true;
        }

        return switch (normalized) {
            case "@position_x" -> moveUserTo(room, roomUnit, value, roomUnit.getY(), animationDuration, noAnimation);
            case "@position_y" -> moveUserTo(room, roomUnit, roomUnit.getX(), value, animationDuration, noAnimation);
            case "@direction" -> {
                RoomUserRotation rotation = RoomUserRotation.fromValue(value);
                yield WiredUserMovementHelper.updateUserDirection(room, roomUnit, rotation, rotation);
            }
            default -> false;
        };
    }

    public static UserMoveInstantScope beginUserMoveInstantOverride(boolean instant) {
        Boolean previousValue = USER_MOVE_INSTANT_OVERRIDE.get();
        USER_MOVE_INSTANT_OVERRIDE.set(instant);
        return new UserMoveInstantScope(previousValue);
    }

    public static UserMoveBatchScope beginUserMoveBatch() {
        Integer previousDepth = USER_MOVE_BATCH_DEPTH.get();
        int nextDepth = (previousDepth == null) ? 1 : (previousDepth + 1);
        USER_MOVE_BATCH_DEPTH.set(nextDepth);

        if (nextDepth == 1) {
            USER_MOVE_BATCH.set(new UserMoveBatch());
        }

        return new UserMoveBatchScope(previousDepth);
    }

    public static Integer readFurniValue(Room room, HabboItem item, String key) {
        if (room == null || item == null) {
            return null;
        }

        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "~teleport.target_id" -> item.getTeleportTargetId();
            case "@id" -> item.getId();
            case "@class_id" -> (item.getBaseItem() != null) ? item.getBaseItem().getId() : null;
            case "@height" -> (item.getBaseItem() != null) ? (int) Math.round(item.getBaseItem().getHeight() * 100) : null;
            case "@state" -> parseInteger(item.getExtradata());
            case "@position_x" -> (int) item.getX();
            case "@position_y" -> (int) item.getY();
            case "@rotation" -> item.getRotation();
            case "@altitude" -> (int) Math.round(item.getZ() * 100);
            case "@is_invisible" -> 0;
            case "@type" -> 0;
            case "@is_stackable" -> (item.getBaseItem() != null && item.getBaseItem().allowStack()) ? 1 : 0;
            case "@can_stand_on" -> (item.getBaseItem() != null && item.getBaseItem().allowWalk()) ? 1 : 0;
            case "@can_sit_on" -> (item.getBaseItem() != null && item.getBaseItem().allowSit()) ? 1 : 0;
            case "@can_lay_on" -> (item.getBaseItem() != null && item.getBaseItem().allowLay()) ? 1 : 0;
            case "@wallitem_offset" -> ((item.getBaseItem() != null) && item.getBaseItem().getType() == FurnitureType.WALL && item.getWallPosition() != null && !item.getWallPosition().trim().isEmpty()) ? 1 : 0;
            case "@dimensions.x" -> (item.getBaseItem() != null) ? (int) item.getBaseItem().getWidth() : null;
            case "@dimensions.y" -> (item.getBaseItem() != null) ? (int) item.getBaseItem().getLength() : null;
            case "@owner_id" -> item.getUserId();
            default -> null;
        };
    }

    public static boolean writeFurniValue(Room room, HabboItem item, String key, int value) {
        if (room == null || item == null) {
            return false;
        }

        String normalized = normalizeKey(key);

        if ("@state".equals(normalized)) {
            item.setExtradata(String.valueOf(normalizeFurniStateValue(item, value)));
            room.updateItemState(item);
            return true;
        }

        if (item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return false;
        }

        return switch (normalized) {
            case "@position_x" -> moveFurniTo(room, item, value, item.getY(), item.getRotation(), item.getZ());
            case "@position_y" -> moveFurniTo(room, item, item.getX(), value, item.getRotation(), item.getZ());
            case "@rotation" -> moveFurniTo(room, item, item.getX(), item.getY(), value, item.getZ());
            case "@altitude" -> moveFurniTo(room, item, item.getX(), item.getY(), item.getRotation(), value / 100.0);
            default -> false;
        };
    }

    public static Integer readRoomValue(Room room, String key) {
        if (room == null) {
            return null;
        }

        ZonedDateTime now = HotelDateTimeUtil.now();
        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "@furni_count" -> room.getFloorItems().size() + room.getWallItems().size();
            case "@user_count" -> room.getUserCount();
            case "@wired_timer" -> (int) (WiredManager.getTickService().getTickCount() / 10L);
            case "@team_red_score" -> getTeamMetric(room, GameTeamColors.RED, true);
            case "@team_green_score" -> getTeamMetric(room, GameTeamColors.GREEN, true);
            case "@team_blue_score" -> getTeamMetric(room, GameTeamColors.BLUE, true);
            case "@team_yellow_score" -> getTeamMetric(room, GameTeamColors.YELLOW, true);
            case "@team_red_size" -> getTeamMetric(room, GameTeamColors.RED, false);
            case "@team_green_size" -> getTeamMetric(room, GameTeamColors.GREEN, false);
            case "@team_blue_size" -> getTeamMetric(room, GameTeamColors.BLUE, false);
            case "@team_yellow_size" -> getTeamMetric(room, GameTeamColors.YELLOW, false);
            case "@room_id" -> room.getId();
            case "@group_id" -> room.getGuildId();
            case "@timezone_server" -> now.getOffset().getTotalSeconds() / 60;
            case "@timezone_client" -> 0;
            case "@current_time" -> (int) now.toEpochSecond();
            case "@current_time.millisecond_of_second" -> now.getNano() / 1_000_000;
            case "@current_time.seconds_of_minute" -> now.getSecond();
            case "@current_time.minute_of_hour" -> now.getMinute();
            case "@current_time.hour_of_day" -> now.getHour();
            case "@current_time.day_of_week" -> now.getDayOfWeek().getValue();
            case "@current_time.day_of_month" -> now.getDayOfMonth();
            case "@current_time.day_of_year" -> now.getDayOfYear();
            case "@current_time.week_of_year" -> now.get(WeekFields.of(Locale.ITALY).weekOfWeekBasedYear());
            case "@current_time.month_of_year" -> now.getMonthValue();
            case "@current_time.year" -> now.getYear();
            default -> null;
        };
    }

    public static Integer readContextValue(WiredContext ctx, String key) {
        if (ctx == null) {
            return null;
        }

        String normalized = normalizeKey(key);

        return switch (normalized) {
            case "@selector_furni_count" -> countIterable(ctx.targets() != null ? ctx.targets().items() : null);
            case "@selector_user_count" -> countIterable(ctx.targets() != null ? ctx.targets().users() : null);
            case "@signal_furni_count" -> ctx.event().getSignalFurniCount();
            case "@signal_user_count" -> ctx.event().getSignalUserCount();
            case "@antenna_id" -> ctx.event().getSignalChannel();
            case "@chat_type" -> ctx.event().getChatType();
            case "@chat_style" -> ctx.event().getChatStyle();
            default -> null;
        };
    }

    private static Integer getUserTypeValue(Habbo habbo, Bot bot, Pet pet) {
        if (habbo != null) return 1;
        if (pet != null) return 2;
        if (bot != null) return 4;
        return null;
    }

    private static Integer getGenderValue(Habbo habbo, Bot bot) {
        HabboGender gender = null;

        if (habbo != null && habbo.getHabboInfo() != null) {
            gender = habbo.getHabboInfo().getGender();
        } else if (bot != null) {
            gender = bot.getGender();
        }

        if (gender == null) {
            return -1;
        }

        return (gender == HabboGender.F) ? 1 : 0;
    }

    private static Integer getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) {
            return null;
        }

        Game game = resolveTeamGame(room, habbo);
        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();

        if (game == null || gamePlayer.getTeamColor() == null) {
            return gamePlayer.getScore();
        }

        GameTeam team = game.getTeam(gamePlayer.getTeamColor());
        return (team != null) ? team.getTotalScore() : gamePlayer.getScore();
    }

    private static Integer getTeamColorId(int effectId) {
        TeamEffectData data = getTeamEffectData(effectId);
        return (data != null) ? data.colorId : null;
    }

    private static Integer getTeamTypeId(int effectId) {
        TeamEffectData data = getTeamEffectData(effectId);
        return (data != null) ? data.typeId : null;
    }

    private static TeamEffectData getTeamEffectData(int effectId) {
        if (effectId <= 0) {
            return null;
        }

        if (effectId >= 223 && effectId <= 226) return new TeamEffectData(effectId - 222, 0);
        if (effectId >= 33 && effectId <= 36) return new TeamEffectData(effectId - 32, 1);
        if (effectId >= 40 && effectId <= 43) return new TeamEffectData(effectId - 39, 2);

        return null;
    }

    private static int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = resolveTeamGame(room, null);
        if (game == null || color == null) {
            return 0;
        }

        GameTeam team = game.getTeam(color);
        if (team == null) {
            return 0;
        }

        return score ? team.getTotalScore() : team.getMembers().size();
    }

    private static Game resolveTeamGame(Room room, Habbo habbo) {
        if (room == null) {
            return null;
        }

        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getCurrentGame() != null) {
            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
            if (game != null) {
                return game;
            }
        }

        Game game = room.getGame(com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame.class);
        if (game != null) {
            return game;
        }

        game = room.getGame(com.eu.habbo.habbohotel.games.freeze.FreezeGame.class);
        if (game != null) {
            return game;
        }

        return room.getGame(com.eu.habbo.habbohotel.games.wired.WiredGame.class);
    }

    private static boolean hasRoomEntryMethod(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }

        String roomEntryMethod = habbo.getHabboInfo().getRoomEntryMethod();
        return roomEntryMethod != null && !roomEntryMethod.trim().isEmpty() && !"unknown".equalsIgnoreCase(roomEntryMethod);
    }

    private static Integer getRoomEntryMethodValue(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        String roomEntryMethod = habbo.getHabboInfo().getRoomEntryMethod();

        if (roomEntryMethod == null || roomEntryMethod.trim().isEmpty()) {
            return 0;
        }

        return switch (roomEntryMethod.trim().toLowerCase(Locale.ROOT)) {
            case "door" -> 1;
            case "teleport" -> 2;
            default -> 3;
        };
    }

    private static int parseStatusInteger(RoomUnit roomUnit, RoomUnitStatus status) {
        if (roomUnit == null || status == null || !roomUnit.hasStatus(status)) {
            return 0;
        }

        return parseInteger(roomUnit.getStatus(status));
    }

    private static boolean moveUserTo(Room room, RoomUnit roomUnit, int x, int y, int animationDuration, boolean noAnimation) {
        if (room == null || roomUnit == null || room.getLayout() == null) {
            return false;
        }

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) {
            return false;
        }

        double targetZ = WiredUserMovementHelper.resolveUserTargetZ(room, targetTile);
        return WiredUserMovementHelper.moveUser(
            room,
            roomUnit,
            targetTile,
            targetZ,
            roomUnit.getBodyRotation(),
            roomUnit.getHeadRotation(),
            animationDuration,
            noAnimation);
    }

    private static boolean stageUserMoveIfPossible(Room room, RoomUnit roomUnit, String normalizedKey, int value, int animationDuration, boolean noAnimation) {
        if (room == null || roomUnit == null || normalizedKey == null) {
            return false;
        }

        if (!"@position_x".equals(normalizedKey) && !"@position_y".equals(normalizedKey)) {
            return false;
        }

        UserMoveBatch batch = USER_MOVE_BATCH.get();

        if (batch == null) {
            return false;
        }

        UserMoveBatchEntry entry = batch.entries.computeIfAbsent(roomUnit.getId(), ignored ->
            new UserMoveBatchEntry(room, roomUnit, roomUnit.getX(), roomUnit.getY(), animationDuration, noAnimation));

        entry.animationDuration = animationDuration;
        entry.noAnimation = noAnimation;

        if ("@position_x".equals(normalizedKey)) {
            entry.targetX = value;
            entry.xDirty = true;
        } else {
            entry.targetY = value;
            entry.yDirty = true;
        }

        if (entry.xDirty && entry.yDirty && !entry.noAnimation) {
            executeUserMoveBatchEntry(entry);
        }

        return true;
    }

    private static void flushUserMoveBatch(UserMoveBatch batch) {
        if (batch == null || batch.entries.isEmpty()) {
            return;
        }

        for (UserMoveBatchEntry entry : batch.entries.values()) {
            executeUserMoveBatchEntry(entry);
        }
    }

    private static void executeUserMoveBatchEntry(UserMoveBatchEntry entry) {
        if (entry == null || entry.room == null || entry.roomUnit == null || entry.room.getLayout() == null) {
            return;
        }

        if (!entry.xDirty && !entry.yDirty) {
            return;
        }

        RoomTile targetTile = entry.room.getLayout().getTile((short) entry.targetX, (short) entry.targetY);

        if (targetTile == null || targetTile.state == RoomTileState.INVALID) {
            return;
        }

        double targetZ = WiredUserMovementHelper.resolveUserTargetZ(entry.room, targetTile);

        WiredUserMovementHelper.moveUser(
            entry.room,
            entry.roomUnit,
            targetTile,
            targetZ,
            entry.roomUnit.getBodyRotation(),
            entry.roomUnit.getHeadRotation(),
            entry.animationDuration,
            entry.noAnimation);

        entry.targetX = entry.roomUnit.getX();
        entry.targetY = entry.roomUnit.getY();
        entry.xDirty = false;
        entry.yDirty = false;
    }

    private static boolean moveFurniTo(Room room, HabboItem item, int x, int y, int rotation, double z) {
        if (room == null || item == null || room.getLayout() == null) {
            return false;
        }

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) {
            return false;
        }

        FurnitureMovementError error = room.moveFurniTo(item, targetTile, rotation, z, null, true, true);
        return error == FurnitureMovementError.NONE;
    }

    private static int normalizeFurniStateValue(HabboItem item, int value) {
        if (item == null || item.getBaseItem() == null) {
            return value;
        }

        int stateCount = item.getBaseItem().getStateCount();
        if (stateCount <= 0) {
            return value;
        }

        int wrappedValue = value % stateCount;
        if (wrappedValue < 0) {
            wrappedValue += stateCount;
        }

        return wrappedValue;
    }

    private static int parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int countIterable(Iterable<?> values) {
        if (values == null) {
            return 0;
        }

        int count = 0;

        for (Object ignored : values) {
            count++;
        }

        return count;
    }

    private static class TeamEffectData {
        final int colorId;
        final int typeId;

        TeamEffectData(int colorId, int typeId) {
            this.colorId = colorId;
            this.typeId = typeId;
        }
    }

    public static final class UserMoveInstantScope implements AutoCloseable {
        private final Boolean previousValue;
        private boolean closed;

        private UserMoveInstantScope(Boolean previousValue) {
            this.previousValue = previousValue;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }

            this.closed = true;

            if (this.previousValue == null) {
                USER_MOVE_INSTANT_OVERRIDE.remove();
                return;
            }

            USER_MOVE_INSTANT_OVERRIDE.set(this.previousValue);
        }
    }

    public static final class UserMoveBatchScope implements AutoCloseable {
        private final Integer previousDepth;
        private boolean closed;

        private UserMoveBatchScope(Integer previousDepth) {
            this.previousDepth = previousDepth;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }

            this.closed = true;

            Integer currentDepth = USER_MOVE_BATCH_DEPTH.get();

            if (currentDepth == null || currentDepth <= 1) {
                UserMoveBatch currentBatch = USER_MOVE_BATCH.get();

                if (currentBatch != null) {
                    flushUserMoveBatch(currentBatch);
                }

                USER_MOVE_BATCH.remove();

                if (this.previousDepth == null) {
                    USER_MOVE_BATCH_DEPTH.remove();
                } else {
                    USER_MOVE_BATCH_DEPTH.set(this.previousDepth);
                }

                return;
            }

            USER_MOVE_BATCH_DEPTH.set(currentDepth - 1);
        }
    }

    private static final class UserMoveBatch {
        private final java.util.LinkedHashMap<Integer, UserMoveBatchEntry> entries = new java.util.LinkedHashMap<>();
    }

    private static final class UserMoveBatchEntry {
        private final Room room;
        private final RoomUnit roomUnit;
        private int targetX;
        private int targetY;
        private int animationDuration;
        private boolean noAnimation;
        private boolean xDirty;
        private boolean yDirty;

        private UserMoveBatchEntry(Room room, RoomUnit roomUnit, int targetX, int targetY, int animationDuration, boolean noAnimation) {
            this.room = room;
            this.roomUnit = roomUnit;
            this.targetX = targetX;
            this.targetY = targetY;
            this.animationDuration = animationDuration;
            this.noAnimation = noAnimation;
            this.xDirty = false;
            this.yDirty = false;
        }
    }

}
