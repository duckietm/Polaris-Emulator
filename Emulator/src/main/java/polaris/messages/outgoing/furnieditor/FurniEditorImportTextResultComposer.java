package polaris.messages.outgoing.furnieditor;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Outgoing 10049 — result of an "import texts from Habbo" request.
 * Carries the official furnidata name/description for a classname (or found=false).
 */
public class FurniEditorImportTextResultComposer extends MessageComposer {
    private final boolean found;
    private final String name;
    private final String description;
    private final String classname;

    public FurniEditorImportTextResultComposer(boolean found, String name, String description, String classname) {
        this.found = found;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.classname = classname == null ? "" : classname;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniEditorImportTextResultComposer);
        this.response.appendBoolean(this.found);
        this.response.appendString(this.name);
        this.response.appendString(this.description);
        this.response.appendString(this.classname);
        return this.response;
    }
}
