package polaris.hotel.items.interactions.wired.interfaces;

import java.util.Set;
import polaris.hotel.wired.WiredMatchFurniSetting;

public interface InteractionWiredMatchFurniSettings {
    public Set<WiredMatchFurniSetting> getMatchFurniSettings();

    public boolean shouldMatchState();

    public boolean shouldMatchRotation();

    public boolean shouldMatchPosition();

    public boolean shouldMatchAltitude();
}
