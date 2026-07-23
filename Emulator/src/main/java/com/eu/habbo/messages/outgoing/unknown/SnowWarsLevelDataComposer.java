package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsLevelDataComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3874);
        this.response.appendInt(0);
        this.response.appendInt(10); //MapID
        this.response.appendInt(2);
        this.response.appendInt(2);

        this.response.appendInt(0); //PlayerID
        this.response.appendString("Admin");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(1);

        this.response.appendInt(1); //PlayerID
        this.response.appendString("Droppy");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(2);

        this.response.appendInt(50);
        this.response.appendInt(1);

        this.response.appendString("00000000000000000000000000000000000000000000000000" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxx000000000000000xxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxx00000000000000000xxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxx0000000000000000000xxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxx000000000000000000000xxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxx00000000000000000000000xxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxx0000000000000000000000000xxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxx000000000000000000000000000xxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxx00000000000000000000000000000xxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxx0000000000000000000000000000000xxxxxxxxxxxxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000xxxxxxxxxxxx" + (char) 13 +
                "xxxxx0000000000000000000000000000000000xxxxxxxxxxx" + (char) 13 +
                "xxxxx00000000000000000000000000000000000xxxxxxxxxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000000xxxxxxxxx" + (char) 13 +
                "xxxxx0000000000000000000000000000000000000xxxxxxxx" + (char) 13 +
                "xxxxx00000000000000000000000000000000000000xxxxxxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000000000xxxxxx" + (char) 13 +
                "xxxxx0000000000000000000000000000000000000000xxxxx" + (char) 13 +
                "0xxxx00000000000000000000000000000000000000000xxxx" + (char) 13 +
                "xxxxx00000000000000000000000000000000000000000xxxx" + (char) 13 +
                "xxxxx00000000000000000000000000000000000000000xxxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000000000000xxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000000000000xxx" + (char) 13 +
                "xxxxx000000000000000000000000000000000000000000xxx" + (char) 13 +
                "xxxxxx00000000000000000000000000000000000000000xxx" + (char) 13 +
                "xxxxxxx0000000000000000000000000000000000000000xxx" + (char) 13 +
                "xxxxxxxx0000000000000000000000000000000000000xxxxx" + (char) 13 +
                "xxxxxxxxx00000000000000000000000000000000000xxxxxx" + (char) 13 +
                "xxxxxxxxxx000000000000000000000000000000000xxxxxxx" + (char) 13 +
                "xxxxxxxxxxx00000000000000000000000000000000xxxx0xx" + (char) 13 +
                "xxxxxxxxxxxx0000000000000000000000000000000xxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxx00000000000000000000000000000xxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxx0000000000000000000000000000xxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxx00000000000000000000000000xxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxx0000000000000000000000000xxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxx00000000000000000000000xxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxx0000000000000000000000xxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxx00000000000000000000xxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxx000000000000000xxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxx0000000000000xxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxx00000000000xxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxx0000000xxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + (char) 13 +
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        this.response.appendInt(0);
        //{
        //}

        return this.response;
    }
}
