package com.eu.habbo.messages.outgoing.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailableCommandsComposerTest {
    private static Command command(String permission, String... keys) {
        return new Command(permission, keys) {
            @Override
            public boolean handle(GameClient gameClient, String[] params) {
                return false;
            }
        };
    }

    @Test
    void collapsesCommandsThatShareAPrimaryKey() {
        Command furnidata = command("cmd_furnidata", "furnidata", "fd");
        Command furnidataClash = command("cmd_other", "furnidata");
        Command about = command("cmd_about", "about");

        List<Command> result =
                AvailableCommandsComposer.distinctByPrimaryKey(Arrays.asList(furnidata, furnidataClash, about));

        assertEquals(2, result.size());
        assertSame(furnidata, result.get(0), "first command with a primary key wins");
        assertSame(about, result.get(1));
    }

    @Test
    void skipsCommandsWithoutAUsableKey() {
        Command noKeys = command("cmd_nokeys");
        Command blankKey = command("cmd_blank", "");
        Command ok = command("cmd_ok", "ok");

        List<Command> result = AvailableCommandsComposer.distinctByPrimaryKey(Arrays.asList(noKeys, blankKey, ok));

        assertEquals(1, result.size());
        assertSame(ok, result.get(0));
    }

    @Test
    void keepsDistinctCommandsInOrder() {
        Command a = command("cmd_a", "a");
        Command b = command("cmd_b", "b");
        Command c = command("cmd_c", "c");

        List<Command> result = AvailableCommandsComposer.distinctByPrimaryKey(Arrays.asList(a, b, c));

        assertEquals(List.of(a, b, c), result);
    }
}
