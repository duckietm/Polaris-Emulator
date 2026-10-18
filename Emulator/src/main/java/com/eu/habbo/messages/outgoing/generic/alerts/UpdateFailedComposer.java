package com.eu.habbo.messages.outgoing.generic.alerts;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A refused wired save: the message (an official localization key or plain text), then {@code count x (key, value)}
 * placeholder pairs the editor fills into the key's text (AIR WiredValidationError parser, pair class
 * {@code userdefinedroomevents/?_-kw?}).
 */
public class UpdateFailedComposer extends MessageComposer {
    private final String message;
    private final Map<String, String> parameters;

    public UpdateFailedComposer(String message) {
        this(message, Map.of());
    }

    public UpdateFailedComposer(String message, Map<String, String> parameters) {
        this.message = message;
        this.parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateFailedComposer);
        this.response.appendString(this.message);
        this.response.appendInt(this.parameters.size());
        for (Map.Entry<String, String> parameter : this.parameters.entrySet()) {
            this.response.appendString(parameter.getKey());
            this.response.appendString(parameter.getValue());
        }
        return this.response;
    }

    public String getMessage() {
        return message;
    }

    /** The placeholder values, in the order they are sent. */
    public Map<String, String> getParameters() {
        return parameters;
    }
}
