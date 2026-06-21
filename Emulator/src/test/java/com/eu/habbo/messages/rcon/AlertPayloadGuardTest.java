package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertPayloadGuardTest {
    @Test
    void hotelAlertPayloadIsBoundedAndUrlValidated() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/HotelAlert.java"));

        assertTrue(source.contains("@NotBlank(message = \"invalid message\")"),
                "HotelAlert must reject blank global alerts");
        assertTrue(source.contains("@Size(max = 4096"),
                "HotelAlert must bound global alert text");
        assertTrue(source.contains("@Pattern(regexp = \"^$|https?://.+\""),
                "HotelAlert must reject non-http alert links");
    }

    @Test
    void imageHotelAlertPayloadIsBounded() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ImageHotelAlert.java"));

        assertTrue(source.contains("@NotBlank(message = \"invalid bubble\")"),
                "ImageHotelAlert must require a bubble key");
        assertTrue(source.contains("@Pattern(regexp = \"[A-Za-z0-9_.-]+\""),
                "ImageHotelAlert bubble keys must be constrained to safe token characters");
        assertTrue(source.contains("@Size(max = 2048"),
                "ImageHotelAlert URL/image fields must be bounded");
        assertTrue(source.contains("@Pattern(regexp = \"^$|https?://.+\""),
                "ImageHotelAlert must reject non-http links");
    }

    @Test
    void imageUserAlertPayloadIsBoundedAndTargetsValidUsers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ImageAlertUser.java"));

        assertTrue(source.contains("@Positive(message = \"invalid user\")"),
                "ImageAlertUser must reject invalid target users before execution");
        assertTrue(source.contains("@NotBlank(message = \"invalid bubble\")"),
                "ImageAlertUser must require a bubble key");
        assertTrue(source.contains("@Size(max = 4096"),
                "ImageAlertUser must bound alert text");
        assertTrue(source.contains("@Pattern(regexp = \"^$|https?://.+\""),
                "ImageAlertUser must reject non-http links");
    }
}
