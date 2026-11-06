package polaris.hotel.modtool;

public class ModToolSanctionLevelItem {
    public int sanctionLevel;
    public String sanctionType;
    public int sanctionHourLength;
    public int sanctionProbationDays;

    public ModToolSanctionLevelItem(
            int sanctionLevel, String sanctionType, int sanctionHourLength, int sanctionProbationDays) {
        this.sanctionLevel = sanctionLevel;
        this.sanctionType = sanctionType;
        this.sanctionHourLength = sanctionHourLength;
        this.sanctionProbationDays = sanctionProbationDays;
    }
}
