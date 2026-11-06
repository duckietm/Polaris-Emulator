package polaris.hotel.hotlooks;

/**
 * One ready-made outfit of the avatar editor "hot looks" tab (AIR 13). Wire layout: gender, figure.
 */
public record HotLook(String gender, String figure) {
    public static final String MALE = "M";
    public static final String FEMALE = "F";

    /** Maps any spelling the database may hold ("m", "female", " F ") onto the two wire genders. */
    public static String normalizeGender(String gender) {
        if (gender == null) {
            return MALE;
        }
        String trimmed = gender.trim();
        if (trimmed.isEmpty()) {
            return MALE;
        }
        char first = Character.toUpperCase(trimmed.charAt(0));
        return first == 'F' ? FEMALE : MALE;
    }

    public boolean isUsable() {
        return this.figure != null && !this.figure.isBlank();
    }
}
