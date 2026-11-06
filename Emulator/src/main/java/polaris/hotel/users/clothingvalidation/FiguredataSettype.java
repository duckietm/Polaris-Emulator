package polaris.hotel.users.clothingvalidation;

import java.util.TreeMap;

public class FiguredataSettype {
    public String type;
    public int paletteId;
    public boolean mandatoryMale0;
    public boolean mandatoryFemale0;
    public boolean mandatoryMale1;
    public boolean mandatoryFemale1;
    public TreeMap<Integer, FiguredataSettypeSet> sets;

    public FiguredataSettype(
            String type,
            int paletteId,
            boolean mandatoryMale0,
            boolean mandatoryFemale0,
            boolean mandatoryMale1,
            boolean mandatoryFemale1) {
        this.type = type;
        this.paletteId = paletteId;
        this.mandatoryMale0 = mandatoryMale0;
        this.mandatoryFemale0 = mandatoryFemale0;
        this.mandatoryMale1 = mandatoryMale1;
        this.mandatoryFemale1 = mandatoryFemale1;
        this.sets = new TreeMap<>();
    }

    public void addSet(FiguredataSettypeSet set) {
        this.sets.put(set.id, set);
    }

    public FiguredataSettypeSet getSet(int id) {
        return this.sets.get(id);
    }

    /**
     * @param gender Gender (M/F)
     * @return First non-sellable and selectable set for given gender
     */
    public FiguredataSettypeSet getFirstSetForGender(String gender) {
        for (FiguredataSettypeSet set : this.sets.descendingMap().values()) {
            if ((set.gender.equalsIgnoreCase(gender) || set.gender.equalsIgnoreCase("u"))
                    && !set.sellable
                    && set.selectable) {
                return set;
            }
        }

        return this.sets.size() > 0
                ? this.sets.descendingMap().entrySet().iterator().next().getValue()
                : null;
    }

    /**
     * @param gender Gender (M/F)
     * @return First non-club, non-sellable and selectable set for given gender
     */
    public FiguredataSettypeSet getFirstNonHCSetForGender(String gender) {
        for (FiguredataSettypeSet set : this.sets.descendingMap().values()) {
            if ((set.gender.equalsIgnoreCase(gender) || set.gender.equalsIgnoreCase("u"))
                    && !set.club
                    && !set.sellable
                    && set.selectable) {
                return set;
            }
        }

        return getFirstSetForGender(gender);
    }
}
