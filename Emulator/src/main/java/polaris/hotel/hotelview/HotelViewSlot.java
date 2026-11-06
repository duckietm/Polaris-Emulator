package polaris.hotel.hotelview;

public record HotelViewSlot(
        int id,
        boolean enabled,
        String type,
        String title,
        String body,
        String imageUrl,
        String buttonText,
        String link,
        int progress,
        String progressLabel,
        String configJson) {}
