package polaris.hotel.wired.leaderboard;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The settings of one variable leaderboard box (wf_xtra_var_leaderboard), as the official AIR
 * editor stores them. Validated by {@link WiredLeaderboardCodec}; immutable.
 */
public final class WiredLeaderboardConfig {

    /** One shown column: a {@code COLUMN_*} type, its header label and a {@code WIDTH_*} width. */
    public record Column(int type, String label, int width) {}

    private final String title;
    private final String description;
    private final int sortMode;
    private final int pageSizeOption;
    private final List<Column> columns;
    private final boolean filterEnabled;
    private final boolean compareEnabled;
    private final int operandKind;
    private final String filterVariableId;
    private final String operandVariableId;
    private final String comparisonLiteral;
    private final SortedMap<String, String> displayOverrides;
    private final int tileX;
    private final int tileY;
    private final int offsetX;
    private final int altitude;

    WiredLeaderboardConfig(
            String title,
            String description,
            int sortMode,
            int pageSizeOption,
            List<Column> columns,
            boolean filterEnabled,
            boolean compareEnabled,
            int operandKind,
            String filterVariableId,
            String operandVariableId,
            String comparisonLiteral,
            SortedMap<String, String> displayOverrides,
            int tileX,
            int tileY,
            int offsetX,
            int altitude) {
        this.title = title;
        this.description = description;
        this.sortMode = sortMode;
        this.pageSizeOption = pageSizeOption;
        this.columns = List.copyOf(columns);
        this.filterEnabled = filterEnabled;
        this.compareEnabled = compareEnabled;
        this.operandKind = operandKind;
        this.filterVariableId = filterVariableId;
        this.operandVariableId = operandVariableId;
        this.comparisonLiteral = comparisonLiteral;
        this.displayOverrides = Collections.unmodifiableSortedMap(new TreeMap<>(displayOverrides));
        this.tileX = tileX;
        this.tileY = tileY;
        this.offsetX = offsetX;
        this.altitude = altitude;
    }

    public String title() {
        return this.title;
    }

    public String description() {
        return this.description;
    }

    public int sortMode() {
        return this.sortMode;
    }

    public int pageSizeOption() {
        return this.pageSizeOption;
    }

    public List<Column> columns() {
        return this.columns;
    }

    public boolean filterEnabled() {
        return this.filterEnabled;
    }

    public boolean compareEnabled() {
        return this.compareEnabled;
    }

    public int operandKind() {
        return this.operandKind;
    }

    public String filterVariableId() {
        return this.filterVariableId;
    }

    public String operandVariableId() {
        return this.operandVariableId;
    }

    public String comparisonLiteral() {
        return this.comparisonLiteral;
    }

    /** Display names by variable id, in the codec's string order. */
    public SortedMap<String, String> displayOverrides() {
        return this.displayOverrides;
    }

    public int tileX() {
        return this.tileX;
    }

    public int tileY() {
        return this.tileY;
    }

    public int offsetX() {
        return this.offsetX;
    }

    public int altitude() {
        return this.altitude;
    }

    public WiredLeaderboardConfig withLocation(int tileX, int tileY, int offsetX, int altitude) {
        return new WiredLeaderboardConfig(
                this.title,
                this.description,
                this.sortMode,
                this.pageSizeOption,
                this.columns,
                this.filterEnabled,
                this.compareEnabled,
                this.operandKind,
                this.filterVariableId,
                this.operandVariableId,
                this.comparisonLiteral,
                this.displayOverrides,
                tileX,
                tileY,
                offsetX,
                altitude);
    }

    public int entriesPerPage() {
        return WiredLeaderboardCodec.PAGE_SIZES[this.pageSizeOption];
    }

    public boolean hasColumn(int type) {
        for (Column column : this.columns) {
            if (column.type() == type) {
                return true;
            }
        }
        return false;
    }
}
