package polaris.messages.rcon;

import com.google.gson.Gson;
import polaris.Emulator;

public class UpdateWordfilter extends RCONMessage<UpdateWordfilter.WordFilterJSON> {

    public UpdateWordfilter() {
        super(WordFilterJSON.class);
    }

    @Override
    public void handle(Gson gson, WordFilterJSON object) {
        Emulator.getGameEnvironment().getWordFilter().reload();
    }

    static class WordFilterJSON {}
}
