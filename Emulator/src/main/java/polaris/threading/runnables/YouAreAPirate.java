package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomChatType;
import polaris.hotel.users.User;

public class YouAreAPirate implements Runnable {
    public static String[] iamapirate = new String[] {
        "Do what you want, 'cause a pirate is free,",
        "You are a pirate!",
        "",
        "Yar har, fiddle di dee,",
        "Being a pirate is all right with me,",
        "Do what you want 'cause a pirate is free, ",
        "You are a pirate!",
        "Yo Ho, ahoy and avast,",
        "Being a pirate is really badass!",
        "Hang the black flag at the end of the mast!",
        "You are a pirate!",
        "",
        "You are a pirate! - Yay!",
        "",
        "We've got us a map, (a map! )",
        "To lead us to a hidden box,",
        "That's all locked up with locks! (with locks! )",
        "And buried deep away!",
        "",
        "We'll dig up the box, (the box! )",
        "We know it's full of precious booty! ",
        "Burst open the locks!",
        "And then we'll say hooray! ",
        "",
        "Yar har, fiddle di dee,",
        "Being a pirate is all right with me!",
        "Do what you want 'cause a pirate is free, ",
        "",
        "You are a pirate!",
        "Yo Ho, ahoy and avast,",
        "Being a Pirate is really badass!",
        "Hang the black flag",
        "At the end of the mast!",
        "You are a pirate!",
        "",
        "Hahaha!",
        "",
        "",
        "We're sailing away (set sail! ), ",
        "Adventure awaits on every shore!",
        "We set sail and explore (ya-har! )",
        "And run and jump all day (Yay! )",
        "We float on our boat (the boat! )",
        "Until it's time to drop the anchor, ",
        "Then hang up our coats (aye-aye! )",
        "Until we sail again!",
        "",
        "Yar har, fiddle di dee,",
        "Being a pirate is all right with me!",
        "Do what you want 'cause a pirate is free, ",
        "You are a pirate!",
        "",
        "Yar har, wind at your back, lads,",
        "Wherever you go!",
        "",
        "",
        "Blue sky above and blue ocean below,",
        "You are a pirate!",
        "",
        "You are a pirate!"
    };

    public final User user;
    public final Room room;

    private int index = 0;
    private int oldEffect;

    public YouAreAPirate(User user, Room room) {
        this.user = user;
        this.room = room;
        this.oldEffect = this.user.getRoomUnit().getEffectId();
        this.room.giveEffect(this.user, 161, -1);
    }

    @Override
    public void run() {
        if (this.room != this.user.getUserInfo().getCurrentRoom()) {
            this.finish(false);
            return;
        }

        // A mute landing mid-song (moderator or flood) has to stop the singing too,
        // otherwise the song keeps shouting on behalf of a muted user.
        if (!this.user.getUserStats().allowTalk()) {
            this.finish(true);
            return;
        }

        if (!iamapirate[this.index].isEmpty()) {
            this.room.talk(
                    this.user,
                    new RoomChatMessage(iamapirate[this.index], this.user, RoomChatMessageBubbles.PIRATE),
                    RoomChatType.SHOUT);
        }
        this.index++;

        if (this.index == iamapirate.length) {
            this.finish(true);
            return;
        }

        Emulator.getThreading().run(this, iamapirate[this.index - 1].length() * 100L);
    }

    private void finish(boolean stillInRoom) {
        if (stillInRoom) {
            this.room.giveEffect(this.user, this.oldEffect, -1);
        }

        this.user.getUserStats().singingPirate.set(false);
    }
}
