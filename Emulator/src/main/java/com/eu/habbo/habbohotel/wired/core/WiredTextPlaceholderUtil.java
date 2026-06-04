package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputFurniName;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.util.HotelDateTimeUtil;
import gnu.trove.set.hash.THashSet;

import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class WiredTextPlaceholderUtil {
    private static final char PRESERVED_SPACE = '\u00A0';
    private static final int MAX_PLACEHOLDER_EXPANSION_LENGTH = 16384;
    private static final int MAX_PLACEHOLDER_REPLACEMENTS = 512;

    private WiredTextPlaceholderUtil() {
    }

    public static String applyUsernamePlaceholders(WiredContext ctx, String text) {
        if (ctx == null || text == null || text.isEmpty()) {
            return text;
        }

        Room room = ctx.room();
        HabboItem triggerItem = ctx.triggerItem();

        if (room == null || triggerItem == null || room.getRoomSpecialTypes() == null) {
            return text;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());
        if (extras == null || extras.isEmpty()) {
            return text;
        }

        String resolvedText = text;

        int replacementCount = 0;

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraTextOutputUsername) {
                WiredExtraTextOutputUsername usernameExtra = (WiredExtraTextOutputUsername) extra;
                String placeholderToken = usernameExtra.getPlaceholderToken();

                if (!placeholderToken.isEmpty() && resolvedText.contains(placeholderToken)) {
                    resolvedText = replaceWithBudget(resolvedText, placeholderToken, buildUsernameReplacement(ctx, usernameExtra));
                    replacementCount++;
                }

                if (shouldStopPlaceholderExpansion(resolvedText, replacementCount)) {
                    break;
                }

                continue;
            }

            if (extra instanceof WiredExtraTextOutputFurniName) {
                WiredExtraTextOutputFurniName furniExtra = (WiredExtraTextOutputFurniName) extra;
                String placeholderToken = furniExtra.getPlaceholderToken();

                if (!placeholderToken.isEmpty() && resolvedText.contains(placeholderToken)) {
                    resolvedText = replaceWithBudget(resolvedText, placeholderToken, buildFurniNameReplacement(ctx, furniExtra));
                    replacementCount++;
                }

                if (shouldStopPlaceholderExpansion(resolvedText, replacementCount)) {
                    break;
                }

                continue;
            }

            if (extra instanceof WiredExtraTextOutputVariable) {
                WiredExtraTextOutputVariable variableExtra = (WiredExtraTextOutputVariable) extra;
                String placeholderToken = variableExtra.getPlaceholderToken();

                if (!placeholderToken.isEmpty() && resolvedText.contains(placeholderToken)) {
                    resolvedText = replaceWithBudget(resolvedText, placeholderToken, buildVariableReplacement(ctx, variableExtra));
                    replacementCount++;
                }

                if (shouldStopPlaceholderExpansion(resolvedText, replacementCount)) {
                    break;
                }
            }
        }

        return preserveRepeatedSpaces(resolvedText);
    }

    private static boolean shouldStopPlaceholderExpansion(String resolvedText, int replacementCount) {
        return replacementCount >= MAX_PLACEHOLDER_REPLACEMENTS
                || (resolvedText != null && resolvedText.length() >= MAX_PLACEHOLDER_EXPANSION_LENGTH);
    }

    private static String replaceWithBudget(String input, String placeholderToken, String replacement) {
        if (input == null || input.isEmpty() || placeholderToken == null || placeholderToken.isEmpty()) {
            return input;
        }

        if (replacement == null) {
            replacement = "";
        }

        int matchIndex = input.indexOf(placeholderToken);
        if (matchIndex < 0) {
            return input;
        }

        StringBuilder builder = new StringBuilder(Math.min(MAX_PLACEHOLDER_EXPANSION_LENGTH, input.length()));
        int searchIndex = 0;

        while (matchIndex >= 0) {
            builder.append(input, searchIndex, matchIndex);

            int remainingCapacity = MAX_PLACEHOLDER_EXPANSION_LENGTH - builder.length();
            if (remainingCapacity <= 0) {
                return builder.substring(0, MAX_PLACEHOLDER_EXPANSION_LENGTH);
            }

            if (replacement.length() <= remainingCapacity) {
                builder.append(replacement);
            } else {
                builder.append(replacement, 0, remainingCapacity);
                return builder.toString();
            }

            searchIndex = matchIndex + placeholderToken.length();
            if (builder.length() >= MAX_PLACEHOLDER_EXPANSION_LENGTH) {
                return builder.substring(0, MAX_PLACEHOLDER_EXPANSION_LENGTH);
            }

            matchIndex = input.indexOf(placeholderToken, searchIndex);
        }

        int remainingCapacity = MAX_PLACEHOLDER_EXPANSION_LENGTH - builder.length();
        if (remainingCapacity <= 0) {
            return builder.substring(0, MAX_PLACEHOLDER_EXPANSION_LENGTH);
        }

        int tailLength = Math.min(input.length() - searchIndex, remainingCapacity);
        builder.append(input, searchIndex, searchIndex + tailLength);
        return builder.toString();
    }

    private static String preserveRepeatedSpaces(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char currentChar = text.charAt(index);
            if (currentChar != ' ') {
                result.append(currentChar);
                index++;
                continue;
            }

            int startIndex = index;
            while (index < text.length() && text.charAt(index) == ' ') {
                index++;
            }

            int spaceCount = index - startIndex;
            if (spaceCount == 1) {
                result.append(' ');
                continue;
            }

            for (int spaceIndex = 0; spaceIndex < spaceCount; spaceIndex++) {
                result.append(PRESERVED_SPACE);
            }
        }

        return result.toString();
    }

    public static boolean requiresActor(Room room, HabboItem stackItem) {
        if (room == null || stackItem == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(stackItem.getX(), stackItem.getY());
        if (extras == null || extras.isEmpty()) {
            return false;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraTextOutputUsername) {
                int userSource = ((WiredExtraTextOutputUsername) extra).getUserSource();
                if (userSource == WiredSourceUtil.SOURCE_TRIGGER || userSource == WiredSourceUtil.SOURCE_CLICKED_USER) {
                    return true;
                }
            }

            if (extra instanceof WiredExtraTextOutputVariable && ((WiredExtraTextOutputVariable) extra).requiresActor()) {
                return true;
            }
        }

        return false;
    }

    private static String buildUsernameReplacement(WiredContext ctx, WiredExtraTextOutputUsername extra) {
        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, extra.getUserSource());
        if (users.isEmpty()) {
            return "";
        }

        LinkedHashSet<Integer> seenUserIds = new LinkedHashSet<>();
        List<String> usernames = new ArrayList<>();

        for (RoomUnit unit : users) {
            if ((unit == null) || !seenUserIds.add(unit.getId())) {
                continue;
            }

            String roomUnitName = getRoomUnitName(ctx.room(), unit);
            if (roomUnitName == null || roomUnitName.trim().isEmpty()) {
                continue;
            }

            usernames.add(roomUnitName);
        }

        if (usernames.isEmpty()) {
            return "";
        }

        if (extra.getPlaceholderType() == WiredExtraTextOutputUsername.TYPE_MULTIPLE) {
            return String.join(extra.getDelimiter(), usernames);
        }

        return usernames.get(0);
    }

    private static String buildFurniNameReplacement(WiredContext ctx, WiredExtraTextOutputFurniName extra) {
        List<HabboItem> items;
        if (extra.getFurniSource() == WiredSourceUtil.SOURCE_SELECTOR) {
            items = WiredSourceUtil.resolveSelectorItems(ctx, true);
        } else {
            items = WiredSourceUtil.resolveItems(ctx, extra.getFurniSource(), extra.getItems());
        }

        if (items.isEmpty()) {
            return "";
        }

        LinkedHashSet<Integer> seenItemIds = new LinkedHashSet<>();
        List<String> furniNames = new ArrayList<>();

        for (HabboItem item : items) {
            if ((item == null) || (item.getBaseItem() == null) || !seenItemIds.add(item.getId())) {
                continue;
            }

            String furniName = item.getBaseItem().getDisplayName();
            if (furniName == null || furniName.trim().isEmpty()) {
                furniName = item.getBaseItem().getName();
            }

            if (furniName == null || furniName.trim().isEmpty()) {
                continue;
            }

            furniNames.add(furniName);
        }

        if (furniNames.isEmpty()) {
            return "";
        }

        if (extra.getPlaceholderType() == WiredExtraTextOutputFurniName.TYPE_MULTIPLE) {
            return String.join(extra.getDelimiter(), furniNames);
        }

        return furniNames.get(0);
    }

    private static String buildVariableReplacement(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        List<String> values = switch (extra.getTargetType()) {
            case WiredExtraTextOutputVariable.TARGET_FURNI -> collectFurniVariableValues(ctx, extra);
            case WiredExtraTextOutputVariable.TARGET_CONTEXT -> collectContextVariableValues(ctx, extra);
            case WiredExtraTextOutputVariable.TARGET_ROOM -> collectRoomVariableValues(ctx, extra);
            default -> collectUserVariableValues(ctx, extra);
        };

        if (values.isEmpty()) {
            return "";
        }

        if (extra.getPlaceholderType() == WiredExtraTextOutputVariable.TYPE_MULTIPLE) {
            return String.join(extra.getDelimiter(), values);
        }

        return values.get(0);
    }

    private static List<String> collectUserVariableValues(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        Room room = ctx.room();
        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, extra.getUserSource());
        if (room == null || users.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Integer> seenUserIds = new LinkedHashSet<>();
        List<String> values = new ArrayList<>();

        for (RoomUnit roomUnit : users) {
            if (roomUnit == null || !seenUserIds.add(roomUnit.getId())) {
                continue;
            }

            String value = resolveUserVariableValue(room, roomUnit, extra);
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }

        return values;
    }

    private static List<String> collectFurniVariableValues(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        Room room = ctx.room();
        if (room == null) {
            return List.of();
        }

        extra.refresh(room);

        List<HabboItem> items = (extra.getFurniSource() == WiredSourceUtil.SOURCE_SELECTOR)
                ? WiredSourceUtil.resolveSelectorItems(ctx, true)
                : WiredSourceUtil.resolveItems(ctx, extra.getFurniSource(), extra.getItems());

        if (items.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Integer> seenItemIds = new LinkedHashSet<>();
        List<String> values = new ArrayList<>();

        for (HabboItem item : items) {
            if (item == null || !seenItemIds.add(item.getId())) {
                continue;
            }

            String value = resolveFurniVariableValue(room, item, extra);
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }

        return values;
    }

    private static List<String> collectRoomVariableValues(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        Room room = ctx.room();
        if (room == null) {
            return List.of();
        }

        String value = resolveRoomVariableValue(room, extra);
        return value == null ? List.of() : List.of(value);
    }

    private static List<String> collectContextVariableValues(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        if (ctx == null) {
            return List.of();
        }

        String value = resolveContextVariableValue(ctx, extra);
        return value == null ? List.of() : List.of(value);
    }

    private static String resolveUserVariableValue(Room room, RoomUnit roomUnit, WiredExtraTextOutputVariable extra) {
        if (room == null || roomUnit == null) {
            return null;
        }

        if (WiredExtraTextOutputVariable.isInternalVariableToken(extra.getVariableToken())) {
            Integer value = readUserInternalValue(room, roomUnit, WiredExtraTextOutputVariable.getInternalVariableKey(extra.getVariableToken()));
            return value != null ? String.valueOf(value) : null;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || !room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), extra.getVariableItemId())) {
            return null;
        }

        Integer value = room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), extra.getVariableItemId());
        if (extra.getDisplayType(room) == WiredExtraTextOutputVariable.DISPLAY_TEXTUAL) {
            return WiredVariableTextConnectorSupport.toText(room, extra.getVariableItemId(), value);
        }

        return value != null ? String.valueOf(value) : null;
    }

    private static String resolveFurniVariableValue(Room room, HabboItem item, WiredExtraTextOutputVariable extra) {
        if (room == null || item == null) {
            return null;
        }

        if (WiredExtraTextOutputVariable.isInternalVariableToken(extra.getVariableToken())) {
            Integer value = readFurniInternalValue(room, item, WiredExtraTextOutputVariable.getInternalVariableKey(extra.getVariableToken()));
            return value != null ? String.valueOf(value) : null;
        }

        if (!room.getFurniVariableManager().hasVariable(item.getId(), extra.getVariableItemId())) {
            return null;
        }

        Integer value = room.getFurniVariableManager().getCurrentValue(item.getId(), extra.getVariableItemId());
        if (extra.getDisplayType(room) == WiredExtraTextOutputVariable.DISPLAY_TEXTUAL) {
            return WiredVariableTextConnectorSupport.toText(room, extra.getVariableItemId(), value);
        }

        return value != null ? String.valueOf(value) : null;
    }

    private static String resolveRoomVariableValue(Room room, WiredExtraTextOutputVariable extra) {
        if (room == null) {
            return null;
        }

        if (WiredExtraTextOutputVariable.isInternalVariableToken(extra.getVariableToken())) {
            Integer value = readRoomInternalValue(room, WiredExtraTextOutputVariable.getInternalVariableKey(extra.getVariableToken()));
            return value != null ? String.valueOf(value) : null;
        }

        Integer value = room.getRoomVariableManager().getCurrentValue(extra.getVariableItemId());
        if (extra.getDisplayType(room) == WiredExtraTextOutputVariable.DISPLAY_TEXTUAL) {
            return WiredVariableTextConnectorSupport.toText(room, extra.getVariableItemId(), value);
        }

        return value != null ? String.valueOf(value) : null;
    }

    private static String resolveContextVariableValue(WiredContext ctx, WiredExtraTextOutputVariable extra) {
        if (ctx == null) {
            return null;
        }

        if (WiredExtraTextOutputVariable.isInternalVariableToken(extra.getVariableToken())) {
            Integer value = WiredInternalVariableSupport.readContextValue(ctx, WiredExtraTextOutputVariable.getInternalVariableKey(extra.getVariableToken()));
            return value != null ? String.valueOf(value) : null;
        }

        if (!WiredContextVariableSupport.hasVariable(ctx, extra.getVariableItemId())) {
            return null;
        }

        Integer value = WiredContextVariableSupport.getCurrentValue(ctx, extra.getVariableItemId());
        if (extra.getDisplayType(ctx.room()) == WiredExtraTextOutputVariable.DISPLAY_TEXTUAL) {
            return WiredVariableTextConnectorSupport.toText(ctx.room(), extra.getVariableItemId(), value);
        }

        return value != null ? String.valueOf(value) : null;
    }

    private static String getRoomUnitName(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);
            return (habbo != null && habbo.getHabboInfo() != null) ? habbo.getHabboInfo().getUsername() : "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
            Bot bot = room.getBot(roomUnit);
            return (bot != null) ? bot.getName() : "";
        }

        if (roomUnit.getRoomUnitType() == RoomUnitType.PET) {
            Pet pet = room.getPet(roomUnit);
            return (pet != null) ? pet.getName() : "";
        }

        return "";
    }

    private static Integer readUserInternalValue(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.readUserValue(room, roomUnit, key);
    }

    private static Integer readFurniInternalValue(Room room, HabboItem item, String key) {
        return WiredInternalVariableSupport.readFurniValue(room, item, key);
    }

    private static Integer readRoomInternalValue(Room room, String key) {
        return WiredInternalVariableSupport.readRoomValue(room, key);
    }

    private static Integer getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo().getGamePlayer() == null) {
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
        return data == null ? null : data.colorId;
    }

    private static Integer getTeamTypeId(int effectId) {
        TeamEffectData data = getTeamEffectData(effectId);
        return data == null ? null : data.typeId;
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

        Game wiredGame = room.getGame(WiredGame.class);
        if (wiredGame != null) {
            return wiredGame;
        }

        Game freezeGame = room.getGame(FreezeGame.class);
        if (freezeGame != null) {
            return freezeGame;
        }

        return room.getGame(BattleBanzaiGame.class);
    }

    private static TeamEffectData getTeamEffectData(int effectValue) {
        if (effectValue <= 0) {
            return null;
        }

        if (effectValue >= 223 && effectValue <= 226) {
            return new TeamEffectData(effectValue - 222, 0);
        }

        if (effectValue >= 33 && effectValue <= 36) {
            return new TeamEffectData(effectValue - 32, 1);
        }

        if (effectValue >= 40 && effectValue <= 43) {
            return new TeamEffectData(effectValue - 39, 2);
        }

        return null;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static class TeamEffectData {
        final int colorId;
        final int typeId;

        TeamEffectData(int colorId, int typeId) {
            this.colorId = colorId;
            this.typeId = typeId;
        }
    }
}
