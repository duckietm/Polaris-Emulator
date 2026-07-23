package com.eu.habbo.habbohotel.hotelview;

public record HotelViewScene(
        String backgroundUrl,
        String leftUrl,
        String rightUrl,
        String drapeUrl,
        int leftX,
        int leftY,
        int rightX,
        int rightY,
        int drapeX,
        int drapeY,
        int hallOfFameX,
        int hallOfFameY,
        boolean hallOfFameEnabled,
        String hallOfFameMode,
        int hallOfFameCurrencyType,
        java.util.List<HotelViewHallOfFameUser> hallOfFameUsers
) {
    public static final HotelViewScene EMPTY = new HotelViewScene("", "", "", "", -1, -1, -1, -1, -1, -1, -1, -1, false, "latest_registered", 0, java.util.List.of());
}
