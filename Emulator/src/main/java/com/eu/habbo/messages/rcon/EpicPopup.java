package com.eu.habbo.messages.rcon;

import com.eu.habbo.habbohotel.users.EpicPopups;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Collection;

/**
 * The official epic popup (one framed image with a close button) from the CMS, next to {@code hotelalert} and
 * {@code imagehotelalert}: {@code image} as for {@code :epicpopup}; {@code user_id} 0 (or absent) shows it to the
 * whole hotel. Users who switched staff alerts off do not get it.
 */
public class EpicPopup extends RCONMessage<EpicPopup.JSON> {
    public EpicPopup() {
        super(EpicPopup.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        String assetUri = EpicPopups.assetUri(json.image);
        if (assetUri == null) {
            this.status = STATUS_ERROR;
            this.message = "invalid image";
            return;
        }

        Collection<Habbo> recipients = EpicPopups.recipients(json.user_id);
        if (recipients == null) {
            this.status = HABBO_NOT_FOUND;
            return;
        }

        this.message = Integer.toString(EpicPopups.show(recipients, assetUri));
    }

    static class JSON {
        @PositiveOrZero(message = "invalid user")
        public int user_id = 0;

        @NotBlank(message = "invalid image")
        @Size(max = EpicPopups.MAX_LENGTH, message = "invalid image")
        public String image = "";
    }
}
