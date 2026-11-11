package polaris.messages.outgoing.users.firstlogin;

import java.util.List;
import polaris.hotel.users.firstlogin.FirstLoginSetupService;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The first-login setup offer (custom, 9496): the starter rooms a new account may take. The client shows its welcome
 * window, with the look step and these rooms, and answers with {@code FirstLoginSetupChooseEvent}.
 */
public class FirstLoginSetupComposer extends MessageComposer {
    private final List<FirstLoginSetupService.Template> templates;

    public FirstLoginSetupComposer(List<FirstLoginSetupService.Template> templates) {
        this.templates = List.copyOf(templates);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FirstLoginSetupComposer);
        this.response.appendInt(this.templates.size());
        for (FirstLoginSetupService.Template template : this.templates) {
            this.response.appendInt(template.id());
            this.response.appendString(template.title());
            this.response.appendString(template.description());
            this.response.appendString(template.thumbnail());
        }
        return this.response;
    }
}
