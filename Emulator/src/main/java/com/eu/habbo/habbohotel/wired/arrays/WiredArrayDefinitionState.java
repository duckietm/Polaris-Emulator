package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.slf4j.Logger;

/**
 * Array schema slot shared by the variable-definition boxes. A stored schema this server cannot read
 * is retained verbatim instead of degrading to a scalar, so lowering a limit or downgrading the
 * emulator never silently discards a builder's array.
 */
public final class WiredArrayDefinitionState {
    private WiredArrayDefinition definition;
    private WiredVariableDefinitionData unavailableData;

    public WiredArrayDefinition definition() {
        return this.definition;
    }

    /** True once a usable schema is present. */
    public boolean isArray() {
        return this.definition != null;
    }

    /** True when a stored schema exists but could not be parsed by this server. */
    public boolean isUnavailable() {
        return this.unavailableData != null;
    }

    /** True when the box is declared as an array, readable or not. */
    public boolean isDeclared() {
        return this.definition != null || this.unavailableData != null;
    }

    public void clear() {
        this.definition = null;
        this.unavailableData = null;
    }

    public void assign(WiredArrayDefinition replacement) {
        this.definition = replacement;
        this.unavailableData = null;
    }

    /** Reads a persisted schema, keeping it as unavailable when it cannot be parsed. */
    public void restore(WiredVariableDefinitionData stored, Logger logger, String kind, int itemId, int roomId) {
        try {
            this.assign(WiredArrayDefinitionSupport.parseStoredArrayDefinition(stored));
        } catch (IllegalArgumentException exception) {
            this.definition = null;
            this.unavailableData =
                    stored != null && stored.isArray() ? WiredVariableDefinitionData.copyOf(stored) : null;
            logger.warn(
                    "Wired {} variable {} in room {} has an unavailable array definition: {}",
                    kind,
                    itemId,
                    roomId,
                    exception.getMessage());
        }
    }

    /** Schema to persist, or {@code null} for a scalar box. */
    public WiredVariableDefinitionData persisted(String variableName) {
        if (this.definition != null) {
            return WiredVariableDefinitionData.array(variableName, this.definition);
        }
        if (this.unavailableData == null) {
            return null;
        }
        WiredVariableDefinitionData data = WiredVariableDefinitionData.copyOf(this.unavailableData);
        data.name = variableName == null ? "" : variableName;
        return data;
    }

    /** Editor payload: the bare name for a scalar, otherwise the schema plus the current server limits. */
    public String editorString(String variableName) {
        WiredVariableDefinitionData data = this.persisted(variableName);
        if (data == null) {
            return variableName == null ? "" : variableName;
        }
        data.serverMaxEntries = WiredArraySettings.maxEntries();
        data.serverMaxPopulatedCells = WiredArraySettings.maxPopulatedCellsPerOwner();
        return WiredManager.getGson().toJson(data);
    }
}
