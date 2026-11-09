package polaris.messages.rcon;

import com.google.gson.Gson;
import polaris.core.ContentReloader;

/**
 * {@code reloadcontent}: reloads one kind of hotel content from the database, as its in-game {@code :update_*} command
 * does, so tools such as the All-in-1-Manager apply an edit without a logged-in staff member. Data: {@code target}, one
 * of {@link ContentReloader#targets()} (case, spaces and underscores are ignored).
 */
public class ReloadContent extends RCONMessage<ReloadContent.ReloadContentJSON> {

    public ReloadContent() {
        super(ReloadContentJSON.class);
    }

    @Override
    public void handle(Gson gson, ReloadContentJSON json) {
        ContentReloader reloader = ContentReloader.installed();
        if (reloader == null) {
            this.status = STATUS_ERROR;
            this.message = "The hotel is still starting.";
            return;
        }

        String target = json == null ? null : json.target;
        if (!reloader.has(target)) {
            this.status = STATUS_ERROR;
            this.message = "Unknown target. Use one of: " + String.join(", ", reloader.targets());
            return;
        }

        try {
            reloader.reload(target);
            this.message = "Reloaded " + target.trim() + ".";
        } catch (RuntimeException e) {
            this.status = SYSTEM_ERROR;
            this.message = "Reloading " + target.trim() + " failed: " + e.getMessage();
        }
    }

    static class ReloadContentJSON {
        public String target;
    }
}
