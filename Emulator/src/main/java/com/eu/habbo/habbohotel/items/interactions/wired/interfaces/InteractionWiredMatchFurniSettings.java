package com.eu.habbo.habbohotel.items.interactions.wired.interfaces;

import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;

import java.util.Set;

public interface InteractionWiredMatchFurniSettings {
    public Set<WiredMatchFurniSetting> getMatchFurniSettings();
    public boolean shouldMatchState();
    public boolean shouldMatchRotation();
    public boolean shouldMatchPosition();
    public boolean shouldMatchAltitude();
}
