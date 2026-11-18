package com.eu.habbo.messages.outgoing.navigator;

import com.eu.habbo.habbohotel.navigation.NavigatorRoleplaySupport;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class NewNavigatorMetaDataComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorMetaDataComposer);
        List<String> viewCodes =
                NavigatorRoleplaySupport.getNavigatorViewCodes(NavigatorRoleplaySupport.isRoleplayPluginEnabled());
        this.response.appendInt(viewCodes.size());
        for (String viewCode : viewCodes) {
            this.response.appendString(viewCode);
            this.response.appendInt(0);
        }

        return this.response;
    }
}
