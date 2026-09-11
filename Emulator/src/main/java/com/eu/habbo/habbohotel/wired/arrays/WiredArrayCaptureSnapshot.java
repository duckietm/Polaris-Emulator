package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WiredArrayCaptureSnapshot {
    private final boolean found;
    private final int index;
    private final int length;
    private final boolean occupied;
    private final Map<String, Long> fields;
    private final Binding binding;

    private WiredArrayCaptureSnapshot(
            boolean found, int index, int length, boolean occupied, Map<String, Long> fields) {
        this(found, index, length, occupied, fields, null);
    }

    private WiredArrayCaptureSnapshot(
            boolean found, int index, int length, boolean occupied, Map<String, Long> fields, Binding binding) {
        this.binding = binding;
        this.found = found;
        this.index = index;
        this.length = length;
        this.occupied = occupied;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static WiredArrayCaptureSnapshot missing(int length) {
        return new WiredArrayCaptureSnapshot(false, -1, length, false, Map.of());
    }

    public static WiredArrayCaptureSnapshot found(
            WiredArrayDefinition definition, int index, int length, WiredArrayEntry entry) {
        Map<String, Long> fields = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            fields.put(field.getName().toLowerCase(Locale.ROOT), entry.getValue(field.getId()));
        }
        return new WiredArrayCaptureSnapshot(true, index, length, true, fields);
    }

    public static WiredArrayCaptureSnapshot bound(
            WiredArrayVariableDefinition definition,
            WiredArrayRuntimeSupport.Owner owner,
            int index,
            int length,
            WiredArrayEntry entry) {
        return bound(definition, owner, index, length, entry, null);
    }

    public static WiredArrayCaptureSnapshot bound(
            WiredArrayVariableDefinition definition,
            WiredArrayRuntimeSupport.Owner owner,
            int index,
            int length,
            WiredArrayEntry entry,
            Object valueLease) {
        WiredArrayCaptureSnapshot capture = found(definition.getArrayDefinition(), index, length, entry);
        Map<String, Integer> fieldIds = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getArrayDefinition().getFields()) {
            fieldIds.put(field.getName().toLowerCase(Locale.ROOT), field.getId());
        }
        Binding binding = new Binding(
                definition.getId(),
                definition.getArrayVariableType().code(),
                owner.id(),
                entry.getRuntimeId(),
                Map.copyOf(fieldIds),
                valueLease);
        return new WiredArrayCaptureSnapshot(true, index, length, true, capture.fields, binding);
    }

    public Binding binding() {
        return this.binding;
    }

    public Long read(String fieldName, WiredContext ctx) {
        if (this.binding == null || ctx == null) return this.read(fieldName);
        String field = fieldName.toLowerCase(Locale.ROOT);
        if (field.equals("found")) return this.found ? 1L : 0L;
        WiredArrayVariableDefinition definition = this.binding.resolve(ctx);
        if (definition == null) return null;
        WiredArrayView value = WiredArrayRuntimeSupport.getValue(ctx, definition, this.binding.owner(ctx));
        int currentIndex = value == null ? -1 : value.findEntryIndex(this.binding.runtimeId());
        return switch (field) {
            case "index" -> (long) (currentIndex < 0 ? this.index : currentIndex);
            case "length" -> value == null ? null : (long) value.getLengthForCondition();
            case "occupied" -> currentIndex < 0 ? 0L : 1L;
            default -> {
                Integer fieldId = this.binding.fieldIds().get(field);
                yield currentIndex < 0 || fieldId == null ? null : value.readField(currentIndex, fieldId);
            }
        };
    }

    public record Binding(
            int definitionItemId,
            int variableType,
            int ownerId,
            long runtimeId,
            Map<String, Integer> fieldIds,
            Object valueLease) {
        public WiredArrayVariableDefinition resolve(WiredContext ctx) {
            WiredArrayVariableDefinition definition =
                    WiredArrayDefinitionSupport.resolve(ctx.room(), variableType, definitionItemId);
            return definition != null && definition.isArray() && definition.isArraySourceValid() ? definition : null;
        }

        public WiredArrayRuntimeSupport.Owner owner(WiredContext ctx) {
            WiredArrayVariableType type = WiredArrayVariableType.fromCode(variableType);
            if (ctx == null || ctx.room() == null) return null;
            return switch (type) {
                case USER -> {
                    var habbo = ctx.room().getHabbo(ownerId);
                    yield habbo == null
                            ? null
                            : new WiredArrayRuntimeSupport.Owner(type, ownerId, habbo.getRoomUnit(), null);
                }
                case FURNI -> {
                    var item = ctx.room().getHabboItem(ownerId);
                    yield item == null ? null : new WiredArrayRuntimeSupport.Owner(type, ownerId, null, item);
                }
                default -> new WiredArrayRuntimeSupport.Owner(type, ownerId, null, null);
            };
        }
    }

    public Long read(String fieldName) {
        if (fieldName == null) return null;
        return switch (fieldName.toLowerCase(Locale.ROOT)) {
            case "found" -> this.found ? 1L : 0L;
            case "index" -> (long) this.index;
            case "length" -> (long) this.length;
            case "occupied" -> this.occupied ? 1L : 0L;
            default -> this.fields.get(fieldName.toLowerCase(Locale.ROOT));
        };
    }
}
