package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogFeaturedPage;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FrontPageFeaturedLayout extends CatalogPage {
    public FrontPageFeaturedLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("frontpage_featured");
        String[] teaserImages = super.getTeaserImage().split(";");
        String[] specialImages = super.getSpecialImage().split(";");

        message.appendInt(1 + teaserImages.length + specialImages.length);
        message.appendString(super.getHeaderImage());
        for (String s : teaserImages) {
            message.appendString(s);
        }

        for (String s : specialImages) {
            message.appendString(s);
        }
        message.appendInt(3);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextDetails());
        message.appendString(super.getTextTeaser());
    }

    public void serializeExtra(ServerMessage message) {

        message.appendInt(Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPages().size());

        for (CatalogFeaturedPage page : Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPages().values()) {
            page.serialize(message);
        }
        message.appendInt(1); //Position
        message.appendString("NUOVO: Affare Stanza di Rilassamento");
        message.appendString("catalogue/feature_cata_vert_oly16bundle4.png");
        message.appendInt(0); //Type
        //0 : String //Page Name
        //1 : Int //Page ID
        //2 : String //Productdata
        message.appendString("");
        message.appendInt(-1);

        message.appendInt(2);
        message.appendString("Il RITORNO di Habburgers! (TUTTI furni nuovi)");
        message.appendString("catalogue/feature_cata_hort_habbergerbundle.png");
        message.appendInt(0);
        message.appendString("");
        message.appendInt(-1);

        message.appendInt(3);
        message.appendString("Habbolympics");
        message.appendString("catalogue/feature_cata_hort_olympic16.png");
        message.appendInt(0);
        message.appendString("");
        message.appendInt(-1);

        message.appendInt(4);
        message.appendString("Diventa un Membro HC");
        message.appendString("catalogue/feature_cata_hort_HC_b.png");
        message.appendInt(0);
        message.appendString("habbo_club");
        message.appendInt(-1);
    }
}
