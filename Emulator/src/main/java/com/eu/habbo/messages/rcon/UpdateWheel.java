package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.google.gson.Gson;

// Ricarica i premi/settings della Ruota della Fortuna dal DB (live), così le
// modifiche fatte dal CMS (/admin/wheel) si applicano senza riavviare l'emulatore.
public class UpdateWheel extends RCONMessage<UpdateWheel.WheelJSON> {

    public UpdateWheel() {
        super(WheelJSON.class);
    }

    @Override
    public void handle(Gson gson, WheelJSON object) {
        Emulator.getGameEnvironment().getWheelManager().reload();
    }

    static class WheelJSON {
    }
}
