package com.eu.habbo.habbohotel.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * A game re-schedules its own loop and stops only on finding the state IDLE, so a tick
 * already in the queue when stop() and start() come back to back used to wake to a
 * RUNNING game and carry on beside the chain start() had just begun - one more loop per
 * cycle, which a wired control clock on a repeater adds twice a second.
 */
class GameTickChainTest {

    /** A game that only counts its ticks; the loop body is irrelevant here. */
    private static final class CountingGame extends Game {
        private int ticks;

        private CountingGame(Room room) {
            super(GameTeam.class, GamePlayer.class, room, false);
        }

        @Override
        public void initialise() {}

        @Override
        public void run() {
            this.ticks++;
            this.scheduleTick(1000);
        }

        private void tick() {
            this.scheduleTick(1000);
        }
    }

    @Test
    void aTickLeftOverFromAReplacedChainDoesNotRun() {
        List<Runnable> queue = new ArrayList<>();

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            queue.add(i.getArgument(0));
            return null;
        });

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getItemsOfType(WiredBlob.class)).thenReturn(new HashSet<>());
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(0);

            CountingGame game = new CountingGame(room);

            game.start();
            game.tick(); // the chain start() began queues its next tick
            assertEquals(1, queue.size());

            // STOP then START, as a wired control clock does: the queued tick belongs to
            // the chain that has just been replaced.
            game.stop();
            game.start();

            queue.remove(0).run();
            assertEquals(0, game.ticks, "a tick from the replaced chain must not run the game");
        }
    }

    @Test
    void aTickOfTheLiveChainRuns() {
        List<Runnable> queue = new ArrayList<>();

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            queue.add(i.getArgument(0));
            return null;
        });

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getItemsOfType(WiredBlob.class)).thenReturn(new HashSet<>());
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(0);

            CountingGame game = new CountingGame(room);

            game.start();
            game.tick();
            queue.remove(0).run();

            assertEquals(1, game.ticks);
            assertEquals(1, queue.size(), "the loop keeps exactly one tick in flight");
        }
    }
}
