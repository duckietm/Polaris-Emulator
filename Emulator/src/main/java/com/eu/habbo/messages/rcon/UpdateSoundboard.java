package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.google.gson.Gson;

// Ricarica i suoni della Soundboard dal DB (live), così i suoni aggiunti/caricati
// dal CMS (/admin/soundboard) si applicano senza riavviare l'emulatore.
public class UpdateSoundboard extends RCONMessage<UpdateSoundboard.SoundboardJSON> {

    public UpdateSoundboard() {
        super(SoundboardJSON.class);
    }

    @Override
    public void handle(Gson gson, SoundboardJSON object) {
        Emulator.getGameEnvironment().getSoundboardManager().reload();
    }

    static class SoundboardJSON {
    }
}
