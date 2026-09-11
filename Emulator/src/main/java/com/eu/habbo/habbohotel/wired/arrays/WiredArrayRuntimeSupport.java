package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
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
        ownerSource = normalizeSource(definition.getArrayVariableType(), ownerSource);
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
                                        null,
                                        ownerSource));
                    }
                    if (distinct.size() > WiredArraySettings.maxOwnersPerExecution()) {
                        ctx.debug("Array owner limit exceeded");
                        return List.of();
                    }
                }
            }
            case FURNI -> {
                for (HabboItem item : WiredSourceUtil.resolveItems(
                        ctx, normalizeSource(WiredArrayVariableType.FURNI, ownerSource), selectedItems)) {
                    if (item != null)
                        addOwner(
                                distinct,
                                new Owner(WiredArrayVariableType.FURNI, item.getId(), null, item, ownerSource));
                    if (distinct.size() > WiredArraySettings.maxOwnersPerExecution()) {
                        ctx.debug("Array owner limit exceeded");
                        return List.of();
                    }
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
        if (address == null || arrayDefinition == null || arrayDefinition.getArrayDefinition() == null) return null;
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
                    address.variableToken,
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
        WiredArrayVariableDefinition definition = WiredArrayDefinitionSupport.resolve(
                ctx.room(), reference.variableType, tokenItemId(reference.variableToken, reference.variableItemId));
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
                reference.variableToken,
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
        return resolveScalar(ctx, selectedItems, variableType, definitionItemId, source, capturePath, "", owner);
    }

    public static Long resolveScalar(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            int variableType,
            int definitionItemId,
            int source,
            String capturePath,
            String variableToken,
            Owner owner) {
        if (ctx == null) return null;
        source = normalizeSource(WiredArrayVariableType.fromCode(variableType), source);
        if (variableToken != null && variableToken.startsWith("internal:")) {
            return resolveInternal(
                    ctx,
                    selectedItems,
                    WiredArrayVariableType.fromCode(variableType),
                    source,
                    variableToken.substring(9),
                    owner);
        }
        definitionItemId = tokenItemId(variableToken, definitionItemId);
        if (capturePath != null && !capturePath.isBlank()) {
            if (!isValidCaptureProjectionPath(capturePath)) return null;
            return ctx.contextVariables().readArrayCapture(capturePath, ctx);
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

    public static boolean allowAssignmentWork(
            WiredContext ctx, WiredArrayVariableDefinition definition, List<Owner> owners) {
        if (definition == null || owners == null || owners.isEmpty()) return false;
        long persistentRows = 0;
        if (definition.isArrayPermanent()) {
            for (Owner owner : owners) {
                WiredArrayView value = getValue(ctx, definition, owner);
                persistentRows += 1L
                        + (value == null
                                ? 0L
                                : (long) value.getOccupiedCount()
                                        * definition
                                                .getArrayDefinition()
                                                .getFields()
                                                .size());
            }
        }
        return allowMutationWork(ctx, definition.getId(), owners.size(), 0, persistentRows);
    }

    public static boolean allowMutationWork(
            WiredContext ctx, int sourceId, int owners, long copiedEntries, long persistentRows) {
        boolean allowed = ctx != null
                && owners > 0
                && owners <= WiredArraySettings.maxOwnersPerExecution()
                && persistentRows <= WiredArraySettings.maxPersistentRowsPerMutation();
        if (allowed) {
            long cost = (copiedEntries + WiredArraySettings.usageEntriesPerUnit() - 1)
                            / WiredArraySettings.usageEntriesPerUnit()
                    + (persistentRows + WiredArraySettings.usageRowsPerUnit() - 1)
                            / WiredArraySettings.usageRowsPerUnit();
            allowed = WiredManager.tryConsumeArrayWork(ctx.room(), (int) Math.min(Integer.MAX_VALUE, cost), sourceId);
        }
        WiredArrayRuntimeMetrics.recordGuard(owners, copiedEntries, persistentRows, allowed);
        if (!allowed && ctx != null) ctx.debug("Array mutation exceeds the execution work limit");
        return allowed;
    }

    public static long estimatedStructuralRows(
            WiredArrayView value,
            WiredArrayVariableDefinition definition,
            WiredArrayStructuralOperation operation,
            int first,
            int second) {
        long occupied = value == null ? 0 : value.getOccupiedCount();
        long changed =
                switch (operation) {
                    case APPEND, SET_ENTRY, CLEAR_SLOT -> 1;
                    case SWAP -> 2;
                    case MOVE -> Math.abs((long) second - first) + 1;
                    case INSERT -> Math.max(0, occupied - first) + 1;
                    case REMOVE -> Math.max(0, occupied - first);
                    case REMOVE_FIRST, CLEAR, SHUFFLE -> occupied;
                    case REMOVE_LAST -> Math.min(1, occupied);
                };
        return changed * definition.getArrayDefinition().getFields().size() * 2 + 1;
    }

    public static boolean mutateCapture(
            WiredContext ctx, String path, WiredArrayNumericOperation operation, long operand) {
        if (ctx == null || path == null || path.startsWith("@array.") || !isValidCaptureProjectionPath(path))
            return false;
        String[] parts = path.split("\\.", 2);
        WiredArrayCaptureSnapshot capture = ctx.contextVariables().getArrayCapture(parts[0]);
        WiredArrayCaptureSnapshot.Binding binding = capture == null ? null : capture.binding();
        WiredArrayVariableDefinition definition = binding == null ? null : binding.resolve(ctx);
        if (definition == null || !definition.isArrayWritable()) return false;
        Integer fieldId = binding.fieldIds().get(parts[1].toLowerCase(java.util.Locale.ROOT));
        if (fieldId == null || definition.getArrayDefinition().getField(fieldId) == null) return false;
        WiredArrayView before = getValue(ctx, definition, binding.owner(ctx));
        if (before == null) return false;
        if (!allowMutationWork(
                ctx,
                definition.getId(),
                1,
                before.getOccupiedCount(),
                definition.isArrayPermanent()
                        ? definition.getArrayDefinition().getFields().size() + 1L
                        : 0L)) return false;
        int index;
        long previous;
        long current;
        if (definition.getArrayVariableType() == WiredArrayVariableType.CONTEXT) {
            var outcome = ctx.contextVariables()
                    .mutateCapturedField(definition.getId(), binding.runtimeId(), fieldId, operation, operand);
            if (!outcome.mutation().changed()) return false;
            index = outcome.index();
            previous = outcome.mutation().previousValue();
            current = outcome.mutation().currentValue();
        } else {
            var outcome = ctx.room()
                    .getArrayVariableManager()
                    .mutateCapturedField(
                            definition, binding.ownerId(), binding.runtimeId(), fieldId, operation, operand);
            if (!outcome.mutation().changed()) return false;
            index = outcome.index();
            previous = outcome.mutation().previousValue();
            current = outcome.mutation().currentValue();
        }
        dispatchChange(
                ctx,
                definition,
                binding.owner(ctx),
                WiredArrayChange.field(
                        index,
                        fieldId,
                        previous,
                        current,
                        before.getLengthForCondition(),
                        before.getLengthForCondition()));
        return true;
    }

    public static boolean isValidCapturePath(String capturePath) {
        return capturePath != null && CAPTURE_PATH.matcher(capturePath.trim()).matches();
    }

    /** Accepts strict metadata paths and Seth-compatible {@code alias.field} projections. */
    public static boolean isValidCaptureProjectionPath(String capturePath) {
        return capturePath != null
                && CAPTURE_PROJECTION_PATH.matcher(capturePath.trim()).matches();
    }

    public static int normalizeSource(WiredArrayVariableType type, int source) {
        if (type == WiredArrayVariableType.FURNI) {
            return switch (source) {
                case 101 -> WiredSourceUtil.SOURCE_SELECTED;
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
        if (owner != null && owner.type() == WiredArrayVariableType.USER && owner.source() == source) {
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
        if (owner != null && owner.type() == WiredArrayVariableType.FURNI && owner.source() == source) {
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
        if (owner == null || owner.id() <= 0) return;
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

    public static int tokenItemId(String token, int fallback) {
        if (token == null || token.isBlank()) return fallback;
        if (!token.startsWith("custom:")) return 0;
        try {
            return Integer.parseInt(token.substring(7));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static boolean isInternalReference(int type, String token) {
        if (token == null || !token.startsWith("internal:")) return false;
        String key = token.substring(9);
        return switch (WiredArrayVariableType.fromCode(type)) {
            case ROOM -> WiredInternalVariableSupport.canUseRoomReference(key);
            case CONTEXT -> WiredInternalVariableSupport.canUseContextReference(key);
            case USER -> WiredInternalVariableSupport.canUseUserReference(key);
            case FURNI -> WiredInternalVariableSupport.canUseFurniReference(key);
        };
    }

    private static Long resolveInternal(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            WiredArrayVariableType type,
            int source,
            String key,
            Owner owner) {
        if (!isInternalReference(type.code(), "internal:" + key)) return null;
        if (type == WiredArrayVariableType.CONTEXT) return WiredInternalVariableSupport.readContextLongValue(ctx, key);
        if (type == WiredArrayVariableType.ROOM) {
            Integer value = WiredInternalVariableSupport.readRoomValue(ctx.room(), key);
            return value == null ? null : value.longValue();
        }
        boolean matching = owner != null && owner.type() == type && owner.source() == source;
        if (type == WiredArrayVariableType.USER) {
            List<RoomUnit> users = matching && owner.unit() != null
                    ? List.of(owner.unit())
                    : WiredSourceUtil.resolveUsers(ctx, source);
            for (RoomUnit unit : users) {
                Integer value = WiredInternalVariableSupport.readUserValue(ctx.room(), unit, key);
                if (value != null) return value.longValue();
            }
        } else {
            for (HabboItem item : matching && owner.item() != null
                    ? List.of(owner.item())
                    : WiredSourceUtil.resolveItems(ctx, source, selectedItems)) {
                Integer value = WiredInternalVariableSupport.readFurniValue(ctx.room(), item, key);
                if (value != null) return value.longValue();
            }
        }
        return null;
    }

    public record Owner(WiredArrayVariableType type, int id, RoomUnit unit, HabboItem item, int source) {
        public Owner(WiredArrayVariableType type, int id, RoomUnit unit, HabboItem item) {
            this(type, id, unit, item, -1);
        }
    }
}
