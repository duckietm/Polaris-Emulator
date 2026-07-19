package com.eu.habbo.networking.gameserver.handlers;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SustainedUnwritableHandlerTest {

    @Test
    void closesAChannelThatRemainsUnwritableForTheWholeTimeout() {
        EmbeddedChannel channel = channelWithTimeout(5);

        setWritable(channel, false);
        channel.advanceTimeBy(4, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
        assertTrue(channel.isOpen());

        channel.advanceTimeBy(1, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertFalse(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    void keepsAChannelOpenWhenWritabilityRecovers() {
        EmbeddedChannel channel = channelWithTimeout(5);

        setWritable(channel, false);
        channel.advanceTimeBy(4, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
        setWritable(channel, true);
        channel.advanceTimeBy(2, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertTrue(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    void repeatedUnwritableEventsDoNotExtendTheOriginalDeadline() {
        EmbeddedChannel channel = channelWithTimeout(5);

        setWritable(channel, false);
        channel.advanceTimeBy(4, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
        channel.pipeline().fireChannelWritabilityChanged();
        channel.advanceTimeBy(1, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertFalse(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    private static EmbeddedChannel channelWithTimeout(long seconds) {
        return new EmbeddedChannel(new SustainedUnwritableHandler(seconds, TimeUnit.SECONDS));
    }

    private static void setWritable(EmbeddedChannel channel, boolean writable) {
        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, writable);
        channel.runPendingTasks();
    }
}
