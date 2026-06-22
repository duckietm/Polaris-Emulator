package com.eu.habbo.habbohotel.rooms;

public class RoomMoodlightData {
    private int id;
    private boolean enabled;
    private boolean backgroundOnly;
    private String color;
    private int intensity;

    public RoomMoodlightData(int id, boolean enabled, boolean backgroundOnly, String color, int intensity) {
        this.id = id;
        this.enabled = enabled;
        this.backgroundOnly = backgroundOnly;
        this.color = color;
        this.intensity = intensity;
    }

    public static RoomMoodlightData fromString(String s) {
        if (s != null) {
            String[] data = s.split(",");

            if (data.length == 5) {
                try {
                    return new RoomMoodlightData(Integer.parseInt(data[1]), data[0].equalsIgnoreCase("2"), data[2].equalsIgnoreCase("2"), data[3], Integer.parseInt(data[4]));
                } catch (NumberFormatException ignored) {
                    // Malformed (non-integer id/intensity) record: fall back to the
                    // default preset rather than throwing. fromString is fed
                    // DB-stored and client-supplied (moodlight extradata) values,
                    // and a throw here would abort room load / a packet handler.
                }
            }
        }

        return new RoomMoodlightData(1, true, true, "#000000", 255);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isBackgroundOnly() {
        return this.backgroundOnly;
    }

    public void setBackgroundOnly(boolean backgroundOnly) {
        this.backgroundOnly = backgroundOnly;
    }

    public String getColor() {
        return this.color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getIntensity() {
        return this.intensity;
    }

    public void setIntensity(int intensity) {
        this.intensity = intensity;
    }

    public String toString() {
        return (this.enabled ? 2 : 1) + "," + this.id + "," + (this.backgroundOnly ? 2 : 1) + "," + this.color + "," + this.intensity;
    }
}
