package com.eu.habbo.messages.outgoing.generic.alerts;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.HashMap;
import java.util.Map;

public class BubbleAlertComposer extends MessageComposer {
    private final String errorKey;
    private final Map<String, String> keys;

    public BubbleAlertComposer(String errorKey, Map<String, String> keys) {
        this.errorKey = errorKey;
        this.keys = keys;
    }

    public BubbleAlertComposer(String errorKey, String message) {
        this.errorKey = errorKey;
        this.keys = new HashMap<>();
        this.keys.put("message", message);
    }

    public BubbleAlertComposer(String errorKey) {
        this.errorKey = errorKey;
        this.keys = new HashMap<>();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BubbleAlertComposer);
        this.response.appendString(this.errorKey);
        this.response.appendInt(this.keys.size());
        for (Map.Entry<String, String> set : this.keys.entrySet()) {
            this.response.appendString(set.getKey());
            this.response.appendString(set.getValue());
        }
        return this.response;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public Map<String, String> getKeys() {
        return keys;
    }
}
