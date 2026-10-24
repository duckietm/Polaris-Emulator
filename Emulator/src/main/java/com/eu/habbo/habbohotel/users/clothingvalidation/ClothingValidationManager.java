package com.eu.habbo.habbohotel.users.clothingvalidation;

import com.eu.habbo.habbohotel.users.Habbo;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClothingValidationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClothingValidationManager.class);

    public static volatile String FIGUREDATA_URL = "";
    public static volatile boolean VALIDATE_ON_HC_EXPIRE = false;
    public static volatile boolean VALIDATE_ON_LOGIN = false;
    public static volatile boolean VALIDATE_ON_CHANGE_LOOKS = false;
    public static volatile boolean VALIDATE_ON_MIMIC = false;
    public static volatile boolean VALIDATE_ON_MANNEQUIN = false;
    public static volatile boolean VALIDATE_ON_FBALLGATE = false;

    private static final Figuredata FIGUREDATA = new Figuredata();

    /** {@code users.look} is varchar(256): a longer figure makes every profile write fail. */
    public static final int MAX_FIGURE_LENGTH = 256;

    /** figuredata defines around a dozen set types; this only has to be an upper bound. */
    private static final int MAX_FIGURE_PARTS = 24;

    /** One part: a two-letter set type and its colour/model numbers, e.g. {@code hr-115-42}. */
    private static final Pattern FIGURE_PART = Pattern.compile("^[a-zA-Z]{2}(-\\d{1,6}){1,5}$");

    /**
     * Whether a figure string is shaped like a figure at all — checked for every incoming look,
     * including when figuredata validation is off (the default), because the damage a malformed
     * one does is structural rather than cosmetic: anything past 256 characters makes the single
     * {@code UPDATE users SET motto, online, look, ...} of {@code HabboInfo.run()} fail for the
     * rest of the session, so motto, home room, backgrounds and {@code online=0} on logout are
     * all silently lost, and the string is broadcast to every client in the room as it arrived.
     *
     * <p>This is deliberately structural only: which parts a user may actually wear is
     * {@link #validateLook} territory and needs figuredata loaded.
     */
    public static boolean isStructurallyValidFigure(String figure) {
        if (figure == null) return false;

        String trimmed = figure.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_FIGURE_LENGTH) return false;

        String[] parts = trimmed.split("\\.", -1);
        if (parts.length > MAX_FIGURE_PARTS) return false;

        for (String part : parts) {
            if (!FIGURE_PART.matcher(part).matches()) return false;
        }
        return true;
    }

    /**
     * Parses the new figuredata.xml file
     * @param newUrl URI of figuredata.xml file. Can be a file path or URL
     */
    public static void reloadFiguredata(String newUrl) {
        try {
            FIGUREDATA.parseXML(newUrl);
        } catch (Exception e) {
            VALIDATE_ON_HC_EXPIRE = false;
            VALIDATE_ON_LOGIN = false;
            VALIDATE_ON_CHANGE_LOOKS = false;
            VALIDATE_ON_MIMIC = false;
            VALIDATE_ON_MANNEQUIN = false;
            VALIDATE_ON_FBALLGATE = false;
            LOGGER.error("Caught exception", e);
        }
    }

    /**
     * Validates a figure string on a given user
     * @param habbo User to validate
     * @return Cleaned figure string
     */
    public static String validateLook(Habbo habbo) {
        return validateLook(
                habbo.getHabboInfo().getLook(),
                habbo.getHabboInfo().getGender().name(),
                habbo.getHabboStats().hasActiveClub(),
                habbo.getInventory().getWardrobeComponent().getWearableSets());
    }

    /**
     * Validates a given figure string and gender on a given user
     * @param habbo User to validate
     * @param look Figure string
     * @param gender Gender (M/F)
     * @return Cleaned figure string
     */
    public static String validateLook(Habbo habbo, String look, String gender) {
        return validateLook(
                look,
                gender,
                habbo.getHabboStats().hasActiveClub(),
                habbo.getInventory().getWardrobeComponent().getWearableSets());
    }

    /**
     * Validates a given figure string against a given gender
     * @param look Figure string
     * @param gender Gender (M/F)
     * @return Cleaned figure string
     */
    public static String validateLook(String look, String gender) {
        return validateLook(look, gender, false, new IntOpenHashSet());
    }

    /**
     * Validates a given figure string against a given gender with club clothing option
     * @param look Figure string
     * @param gender Gender (M/F)
     * @param isHC Boolean indicating if club clothing is permitted
     * @return Cleaned figure string
     */
    public static String validateLook(String look, String gender, boolean isHC) {
        return validateLook(look, gender, isHC, new IntOpenHashSet());
    }

    /**
     * Validates a figure string with all available options
     * @param look Figure string
     * @param gender Gender (M/F)
     * @param isHC Boolean indicating if club clothing is permitted
     * @param ownedClothing Array of owned clothing set IDs. If sellable and setId not in this array clothing will be removed
     * @return Cleaned figure string
     */
    public static String validateLook(String look, String gender, boolean isHC, IntCollection ownedClothing) {
        if (FIGUREDATA.palettes.size() == 0 || FIGUREDATA.settypes.size() == 0) return look;

        String[] newLookParts = look.split(Pattern.quote("."));
        ArrayList<String> lookParts = new ArrayList<>();

        Map<String, String[]> parts = new HashMap<>();

        // add mandatory settypes
        for (String lookpart : newLookParts) {
            if (lookpart.contains("-")) {
                String[] data = lookpart.split(Pattern.quote("-"));
                FiguredataSettype settype = FIGUREDATA.settypes.get(data[0]);
                if (settype != null) {
                    parts.put(data[0], data);
                }
            }
        }

        FIGUREDATA.settypes.entrySet().stream()
                .filter(x -> !parts.containsKey(x.getKey()))
                .forEach(x -> {
                    FiguredataSettype settype = x.getValue();

                    if (gender.equalsIgnoreCase("M") && !isHC && !settype.mandatoryMale0) return;

                    if (gender.equalsIgnoreCase("F") && !isHC && !settype.mandatoryFemale0) return;

                    if (gender.equalsIgnoreCase("M") && isHC && !settype.mandatoryMale1) return;

                    if (gender.equalsIgnoreCase("F") && isHC && !settype.mandatoryFemale1) return;

                    parts.put(x.getKey(), new String[] {x.getKey()});
                });

        parts.forEach((key, data) -> {
            try {
                if (data.length >= 1) {
                    FiguredataSettype settype = FIGUREDATA.settypes.get(data[0]);
                    if (settype == null) {
                        // throw new Exception("Set type " + data[0] + " does not exist");
                        return;
                    }

                    FiguredataPalette palette = FIGUREDATA.palettes.get(settype.paletteId);
                    if (palette == null) {
                        throw new Exception("Palette " + settype.paletteId + " does not exist");
                    }

                    int setId;
                    FiguredataSettypeSet set;

                    setId = Integer.parseInt(data.length >= 2 ? data[1] : "-1");
                    set = settype.getSet(setId);

                    if (set == null
                            || (set.club && !isHC)
                            || !set.selectable
                            || (set.sellable && !ownedClothing.contains(set.id))
                            || (!set.gender.equalsIgnoreCase("U") && !set.gender.equalsIgnoreCase(gender))) {
                        if (gender.equalsIgnoreCase("M") && !isHC && !settype.mandatoryMale0) return;

                        if (gender.equalsIgnoreCase("F") && !isHC && !settype.mandatoryFemale0) return;

                        if (gender.equalsIgnoreCase("M") && isHC && !settype.mandatoryMale1) return;

                        if (gender.equalsIgnoreCase("F") && isHC && !settype.mandatoryFemale1) return;

                        set = settype.getFirstNonHCSetForGender(gender);
                        setId = set.id;
                    }

                    ArrayList<String> dataParts = new ArrayList<>();

                    int color1 = -1;
                    int color2 = -1;

                    if (set.colorable) {
                        color1 = data.length >= 3 ? Integer.parseInt(data[2]) : -1;
                        FiguredataPaletteColor color = palette.getColor(color1);
                        if (color == null || (color.club && !isHC)) {
                            color1 = palette.getFirstNonHCColor().id;
                        }
                    }

                    if (data.length >= 4 && set.colorable) {
                        color2 = Integer.parseInt(data[3]);
                        FiguredataPaletteColor color = palette.getColor(color2);
                        if (color == null || (color.club && !isHC)) {
                            color2 = palette.getFirstNonHCColor().id;
                        }
                    }

                    dataParts.add(settype.type);
                    dataParts.add("" + setId);

                    if (color1 > -1) {
                        dataParts.add("" + color1);
                    }

                    if (color2 > -1) {
                        dataParts.add("" + color2);
                    }

                    lookParts.add(String.join("-", dataParts));
                }
            } catch (Exception e) {
                // habbo.alert(e.getMessage());
                LOGGER.error("Error in clothing validation", e);
            }
        });

        return String.join(".", lookParts);
    }
}
