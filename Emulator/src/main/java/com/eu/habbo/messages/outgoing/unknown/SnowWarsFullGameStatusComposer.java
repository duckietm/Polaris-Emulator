package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsFullGameStatusComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(0);
        this.response.appendInt(0); //Unused
        this.response.appendInt(0);
        this.response.appendInt(0);

        //SnowWarGameObjectData
        this.response.appendInt(1); //Count
        //{
        this.response.appendInt(3); //type
        this.response.appendInt(1); //id?

        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable
        this.response.appendInt(1); //variable

        //1: -> 11 variables.
        //4: -> 8 variables.
        //3: -> 7 variables.
        //5: -> 19 variables.
        //2: -> 9 variables.
        //}
        return this.response;
    }
}
