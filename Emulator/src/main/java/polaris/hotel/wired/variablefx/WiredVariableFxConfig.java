package polaris.hotel.wired.variablefx;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * How one variable fx looks: the presentation half of an fx box, as the client draws it. It names
 * no variable and no holder; those arrive with each {@link WiredVariableFxStatus}, which points
 * back here by {@link #configId()} (the fx box's item id).
 */
public record WiredVariableFxConfig(
        int configId,
        boolean userFx,
        int showMode,
        int updateMask,
        boolean showOnMouseHover,
        int showDurationMs,
        int category,
        int styleId,
        int colorId,
        int widthId,
        int rendererId,
        long defaultMinValue,
        long defaultMaxValue,
        Map<String, String> extra) {

    public WiredVariableFxConfig {
        extra = (extra != null) ? Collections.unmodifiableMap(new TreeMap<>(extra)) : Collections.emptyMap();
    }

    /** What a viewer sees of a config; two configs with the same signature draw the same. */
    public String signature() {
        StringBuilder builder = new StringBuilder()
                .append(this.userFx)
                .append('|')
                .append(this.showMode)
                .append('|')
                .append(this.updateMask)
                .append('|')
                .append(this.showOnMouseHover)
                .append('|')
                .append(this.showDurationMs)
                .append('|')
                .append(this.category)
                .append('|')
                .append(this.styleId)
                .append('|')
                .append(this.colorId)
                .append('|')
                .append(this.widthId)
                .append('|')
                .append(this.rendererId)
                .append('|')
                .append(this.defaultMinValue)
                .append('|')
                .append(this.defaultMaxValue);

        for (Map.Entry<String, String> entry : this.extra.entrySet()) {
            builder.append('|').append(entry.getKey()).append('=').append(entry.getValue());
        }

        return builder.toString();
    }
}
