package com.eu.habbo.messages.outgoing.rooms.variablefx;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxSettings;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Map;

public class VariableFxConfigUpdateComposer extends MessageComposer {
    private final List<VariableFxConfig> configs;

    public VariableFxConfigUpdateComposer(List<VariableFxConfig> configs) {
        this.configs = (configs != null) ? configs : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VariableFxConfigUpdateComposer);
        this.response.appendInt(this.configs.size());

        for (VariableFxConfig config : this.configs) {
            VariableFxSettings settings = config.settings();

            this.response.appendInt(config.configId());
            this.response.appendBoolean(settings.isUserFx());
            this.response.appendInt(settings.showMode());
            this.response.appendInt(settings.showTriggerMask());
            this.response.appendBoolean(settings.showOnMouseHover());
            this.response.appendInt(settings.showDuration());
            this.response.appendInt(config.categoryId());
            this.response.appendInt(settings.styleId());
            this.response.appendInt(settings.colorId());
            this.response.appendInt(settings.widthId());
            this.response.appendInt(settings.rendererId());
            appendLong(settings.defaultMinValue());
            appendLong(settings.defaultMaxValue());

            this.response.appendInt(config.extras().size());
            for (Map.Entry<String, String> extra : config.extras().entrySet()) {
                this.response.appendString(extra.getKey());
                this.response.appendString(extra.getValue());
            }
        }

        return this.response;
    }

    /** No appendLong on ServerMessage: a 64-bit value is two ints, high word first. */
    private void appendLong(long value) {
        this.response.appendInt((int) (value >> 32));
        this.response.appendInt((int) value);
    }
}

/** One drawn Variable FX configuration, as the client needs it to render the bar. */
record VariableFxConfig(int configId, int categoryId, VariableFxSettings settings, Map<String, String> extras) {

    VariableFxConfig {
        extras = (extras != null) ? Map.copyOf(extras) : Map.of();
    }
}
