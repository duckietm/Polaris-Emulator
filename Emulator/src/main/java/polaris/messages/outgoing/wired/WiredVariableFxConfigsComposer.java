package polaris.messages.outgoing.wired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import polaris.hotel.wired.variablefx.WiredVariableFxConfig;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** The fx configs a player has to know before any status for them means anything. */
public class WiredVariableFxConfigsComposer extends MessageComposer {
    private final List<WiredVariableFxConfig> configs;

    public WiredVariableFxConfigsComposer(List<WiredVariableFxConfig> configs) {
        this.configs = (configs != null) ? configs : Collections.emptyList();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableFxConfigsComposer);
        this.response.appendInt(this.configs.size());

        for (WiredVariableFxConfig config : this.configs) {
            this.response.appendInt(config.configId());
            this.response.appendBoolean(config.userFx());
            this.response.appendInt(config.showMode());
            this.response.appendInt(config.updateMask());
            this.response.appendBoolean(config.showOnMouseHover());
            this.response.appendInt(config.showDurationMs());
            this.response.appendInt(config.category());
            this.response.appendInt(config.styleId());
            this.response.appendInt(config.colorId());
            this.response.appendInt(config.widthId());
            this.response.appendInt(config.rendererId());
            WiredVariableHoldersPageComposer.appendLong(this.response, config.defaultMinValue());
            WiredVariableHoldersPageComposer.appendLong(this.response, config.defaultMaxValue());
            this.response.appendInt(config.extra().size());
            for (Map.Entry<String, String> entry : config.extra().entrySet()) {
                this.response.appendString(entry.getKey());
                this.response.appendString(entry.getValue());
            }
        }

        return this.response;
    }
}
