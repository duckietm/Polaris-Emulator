package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class WiredArrayRuntimeSupport {
    private static final Pattern CAPTURE_PATH =
            Pattern.compile("^@array\\.[A-Za-z0-9_]{1,40}\\.[A-Za-z0-9_]{1,40}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPTURE_PROJECTION_PATH =
            Pattern.compile("^(?:@array\\.)?[A-Za-z0-9_]{1,40}\\.[A-Za-z0-9_]{1,40}$", Pattern.CASE_INSENSITIVE);

    private WiredArrayRuntimeSupport() {}

    public static List<Owner> resolveOwners(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            WiredArrayVariableDefinition definition,
            int ownerSource) {
        if (ctx == null || definition == null || !definition.isArray()) return List.of();
        LinkedHashMap<String, Owner> distinct = new LinkedHashMap<>();
        Room room = ctx.room();

        switch (definition.getArrayVariableType()) {
            case ROOM ->
                addOwner(
                        distinct,
                        new Owner(
                                WiredArrayVariableType.ROOM,
                                definition.getArrayStorageRoomId(room.getId()),
                                null,
                                null));
            case CONTEXT ->
                addOwner(distinct, new Owner(WiredArrayVariableType.CONTEXT, definition.getId(), null, null));
            case USER -> {
                for (RoomUnit unit :
                        WiredSourceUtil.resolveUsers(ctx, normalizeSource(WiredArrayVariableType.USER, ownerSource))) {
                    Habbo habbo = room.getHabbo(unit);
                    if (habbo != null) {
                        addOwner(
                                distinct,
                                new Owner(
                                        WiredArrayVariableType.USER,
                                        habbo.getHabboInfo().getId(),
                                        unit,
                                        null));
                    }
                    if (distinct.size() >= WiredArraySettings.maxOwnersPerExecution()) break;
                }
            }
            case FURNI -> {
                for (HabboItem item : WiredSourceUtil.resolveItems(
                        ctx, normalizeSource(WiredArrayVariableType.FURNI, ownerSource), selectedItems)) {
                    if (item != null)
                        addOwner(distinct, new Owner(WiredArrayVariableType.FURNI, item.getId(), null, item));
                    if (distinct.size() >= WiredArraySettings.maxOwnersPerExecution()) break;
                }
            }
        }

        return new ArrayList<>(distinct.values());
    }

    public static WiredArrayView getValue(WiredContext ctx, WiredArrayVariableDefinition definition, Owner owner) {
        if (ctx == null || definition == null || owner == null) return null;
        if (definition.getArrayVariableType() == WiredArrayVariableType.CONTEXT) {
            return ctx.contextVariables().getArrayView(definition.getId(), definition.getArrayDefinition());
        }
        return ctx.room().getArrayVariableManager().getValue(definition, owner.id());
    }

    public static RoomArrayVariableManager.MutationOutcome mutate(
            WiredContext ctx,
            WiredArrayVariableDefinition definition,
            Owner owner,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues) {
        if (definition.getArrayVariableType() == WiredArrayVariableType.CONTEXT) {
            WiredArrayMutationResult result = ctx.contextVariables()
                    .mutateArray(
                            definition.getId(),
                            definition.getArrayDefinition(),
                            operation,
                            firstIndex,
                            secondIndex,
                            entryValues);
            return new RoomArrayVariableManager.MutationOutcome(
                    result, ctx.contextVariables().getArrayView(definition.getId(), definition.getArrayDefinition()));
        }
        return ctx.room()
                .getArrayVariableManager()
                .mutate(definition, owner.id(), operation, firstIndex, secondIndex, entryValues);
    }

    public static Integer resolveIndex(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            WiredArrayAddress address,
            WiredArrayVariableDefinition arrayDefinition,
            Owner owner) {
        if (address == null || arrayDefinition == null) return null;
        long value;
        if (address.mode == WiredArrayAddress.CONSTANT) {
            value = address.value;
        } else if (address.mode == WiredArrayAddress.VARIABLE) {
            Long resolved = resolveScalar(
                    ctx,
                    selectedItems,
                    address.variableType,
                    address.variableItemId,
                    address.variableSource,
                    address.capturePath,
                    owner);
            if (resolved == null) return null;
            value = resolved;
        } else {
            return null;
        }
        return value >= 0 && value < arrayDefinition.getArrayDefinition().getMaxEntries() ? (int) value : null;
    }

    public static Long resolveReference(
            WiredContext ctx, Collection<HabboItem> selectedItems, WiredArrayReference reference, Owner owner) {
        if (reference == null) return null;
        if (reference.mode == WiredArrayReference.CONSTANT) {
            try {
                return Long.parseLong(reference.value == null ? "" : reference.value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (reference.mode != WiredArrayReference.VARIABLE) return null;
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(ctx.room(), reference.variableType, reference.variableItemId);
        if (definition != null && definition.isArray()) {
            List<Owner> owners = resolveOwners(ctx, selectedItems, definition, reference.variableSource);
            Owner referenceOwner = matchingOwner(owners, owner);
            if (referenceOwner == null) return null;
            Integer index = resolveIndex(ctx, selectedItems, reference.address, definition, referenceOwner);
            WiredArrayView value = getValue(ctx, definition, referenceOwner);
            return index == null || value == null ? null : value.readField(index, reference.address.fieldId);
        }
        return resolveScalar(
                ctx,
                selectedItems,
                reference.variableType,
                reference.variableItemId,
                reference.variableSource,
                reference.capturePath,
                owner);
    }

    public static Long resolveScalar(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            int variableType,
            int definitionItemId,
            int source,
            String capturePath,
            Owner owner) {
        if (ctx == null) return null;
        if (capturePath != null && !capturePath.isBlank()) {
            if (!isValidCapturePath(capturePath)) return null;
            return ctx.contextVariables().readArrayCapture(capturePath);
        }

        WiredArrayVariableType type = WiredArrayVariableType.fromCode(variableType);
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(ctx.room(), type.code(), definitionItemId);
        if (definition == null || definition.isArray() || !definition.hasValue()) return null;

        return switch (type) {
            case ROOM -> (long) ctx.room().getRoomVariableManager().getCurrentValue(definitionItemId);
            case CONTEXT -> {
                Integer value = WiredContextVariableSupport.getCurrentValue(ctx, definitionItemId);
                yield value == null ? null : value.longValue();
            }
            case USER -> resolveUserScalar(ctx, definitionItemId, source, owner);
            case FURNI -> resolveFurniScalar(ctx, selectedItems, definitionItemId, source, owner);
        };
    }

    public static boolean compare(long value, long reference, int comparison) {
        return switch (comparison) {
            case 0 -> value > reference;
            case 1 -> value >= reference;
            case 2 -> value == reference;
            case 3 -> value <= reference;
            case 4 -> value < reference;
            case 5 -> value != reference;
            default -> false;
        };
    }

    public static boolean isValidCapturePath(String capturePath) {
        return capturePath != null && CAPTURE_PATH.matcher(capturePath.trim()).matches();
    }

    /** Accepts strict metadata paths and Seth-compatible read-only {@code alias.field} projections. */
    public static boolean isValidCaptureProjectionPath(String capturePath) {
        return capturePath != null
                && CAPTURE_PROJECTION_PATH.matcher(capturePath.trim()).matches();
    }

    public static int normalizeSource(WiredArrayVariableType type, int source) {
        if (type == WiredArrayVariableType.FURNI) {
            return switch (source) {
                case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL ->
                    source;
                default -> WiredSourceUtil.SOURCE_TRIGGER;
            };
        }
        if (type == WiredArrayVariableType.USER) {
            return switch (source) {
                case WiredSourceUtil.SOURCE_CLICKED_USER,
                        WiredSourceUtil.SOURCE_SELECTOR,
                        WiredSourceUtil.SOURCE_SIGNAL -> source;
                default -> WiredSourceUtil.SOURCE_TRIGGER;
            };
        }
        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static boolean dispatchChange(
            WiredContext ctx, WiredArrayVariableDefinition definition, Owner owner, WiredArrayChange change) {
        if (ctx == null || definition == null || owner == null || change == null) return false;
        int targetType =
                switch (definition.getArrayVariableType()) {
                    case USER -> 0;
                    case FURNI -> 1;
                    case CONTEXT -> 2;
                    case ROOM -> 3;
                };
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, ctx.room())
                .actor(owner.unit() != null ? owner.unit() : ctx.actor().orElse(null))
                .sourceItem(owner.item())
                .variableTargetType(targetType)
                .variableDefinitionItemId(definition.getId())
                .arrayChange(change)
                .contextVariableScope(ctx.contextVariables())
                .triggeredByEffect(true)
                .build();
        return WiredManager.handleEvent(event);
    }

    private static Long resolveUserScalar(WiredContext ctx, int definitionItemId, int source, Owner owner) {
        if (owner != null && owner.type() == WiredArrayVariableType.USER) {
            return (long) ctx.room().getUserVariableManager().getCurrentValue(owner.id(), definitionItemId);
        }
        List<RoomUnit> units = WiredSourceUtil.resolveUsers(ctx, normalizeSource(WiredArrayVariableType.USER, source));
        if (units.isEmpty()) return null;
        Habbo habbo = ctx.room().getHabbo(units.get(0));
        return habbo == null
                ? null
                : (long) ctx.room()
                        .getUserVariableManager()
                        .getCurrentValue(habbo.getHabboInfo().getId(), definitionItemId);
    }

    private static Long resolveFurniScalar(
            WiredContext ctx, Collection<HabboItem> selectedItems, int definitionItemId, int source, Owner owner) {
        if (owner != null && owner.type() == WiredArrayVariableType.FURNI) {
            return (long) ctx.room().getFurniVariableManager().getCurrentValue(owner.id(), definitionItemId);
        }
        List<HabboItem> items =
                WiredSourceUtil.resolveItems(ctx, normalizeSource(WiredArrayVariableType.FURNI, source), selectedItems);
        return items.isEmpty()
                ? null
                : (long) ctx.room()
                        .getFurniVariableManager()
                        .getCurrentValue(items.get(0).getId(), definitionItemId);
    }

    private static void addOwner(Map<String, Owner> owners, Owner owner) {
        if (owner == null || owner.id() <= 0 || owners.size() >= WiredArraySettings.maxOwnersPerExecution()) return;
        owners.putIfAbsent(owner.type().code() + ":" + owner.id(), owner);
    }

    private static Owner matchingOwner(List<Owner> owners, Owner destinationOwner) {
        if (owners == null || owners.isEmpty()) return null;
        if (destinationOwner != null) {
            for (Owner owner : owners) {
                if (owner.type() == destinationOwner.type() && owner.id() == destinationOwner.id()) return owner;
            }
        }
        return owners.get(0);
    }

    public record Owner(WiredArrayVariableType type, int id, RoomUnit unit, HabboItem item) {}
}
