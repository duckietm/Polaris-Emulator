package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** The value one fx shows on one avatar or furni right now. */
public record WiredVariableFxStatus(
        Key key,
        boolean initialize,
        long value,
        Long overrideMinValue,
        Long overrideMaxValue,
        Map<String, String> extra) {

    /**
     * What identifies a shown fx value: the config, the variable feeding it and the avatar (room
     * unit id) or furni (item id) it is drawn over.
     */
    public record Key(int configId, String variableId, boolean userEntity, int entityId) {
        /** The client splits this at the first '|': config id, then the variable id. */
        public String configAndVariable() {
            return this.configId + "|" + this.variableId;
        }

        /** The removal key: config, variable, "u" or "f", entity (a furni by the id the client knows it by). */
        public String wireKey() {
            return this.configId + "|" + this.variableId + "|" + (this.userEntity ? "u" : "f") + "|"
                    + BuildersClubItemIds.toWire(this.entityId, !this.userEntity);
        }
    }

    public WiredVariableFxStatus {
        extra = (extra != null) ? Collections.unmodifiableMap(new TreeMap<>(extra)) : Collections.emptyMap();
    }

    public WiredVariableFxStatus withInitialize(boolean flag) {
        return new WiredVariableFxStatus(
                this.key, flag, this.value, this.overrideMinValue, this.overrideMaxValue, this.extra);
    }

    public boolean hasOverrides() {
        return this.overrideMinValue != null && this.overrideMaxValue != null;
    }

    /** What a viewer sees of a status; "initialize" is how it is delivered, not what it says. */
    public String signature() {
        StringBuilder builder = new StringBuilder()
                .append(this.value)
                .append('|')
                .append(this.overrideMinValue)
                .append('|')
                .append(this.overrideMaxValue);

        for (Map.Entry<String, String> entry : this.extra.entrySet()) {
            builder.append('|').append(entry.getKey()).append('=').append(entry.getValue());
        }

        return builder.toString();
    }
}
