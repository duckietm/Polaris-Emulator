package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FriendsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendsComposer.class);

    private final int totalPages;
    private final int pageIndex;
    private final Collection<MessengerBuddy> friends;

    public FriendsComposer(int totalPages, int pageIndex, Collection<MessengerBuddy> friends) {
        this.totalPages = totalPages;
        this.pageIndex = pageIndex;
        this.friends = friends;
    }

    @Override
    protected ServerMessage composeInternal() {
        try {
            this.response.init(Outgoing.FriendsComposer);

            this.response.appendInt(this.totalPages);
            this.response.appendInt(this.pageIndex);
            this.response.appendInt(this.friends.size());

            for (MessengerBuddy row : this.friends) {
                this.response.appendInt(row.getId());
                this.response.appendString(row.getUsername());
                this.response.appendInt(row.getGender().equals(HabboGender.M) ? 0 : 1);
                this.response.appendBoolean(row.getOnline() == 1);
                this.response.appendBoolean(row.inRoom()); //IN ROOM
                this.response.appendString(row.getLook()); // send look for offline friends too (loaded from DB)
                this.response.appendInt(row.getCategoryId()); //Friends category
                this.response.appendString(row.getMotto());
                this.response.appendString(""); //Last seen as DATETIMESTRING
                this.response.appendString(""); //Realname or Facebookame as String
                this.response.appendBoolean(false); //Offline messaging.
                this.response.appendBoolean(false);
                this.response.appendBoolean(false);
                this.response.appendShort(row.getRelation());
            }
            return this.response;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
        return null;
    }

    public static ArrayList<ServerMessage> getMessagesForBuddyList(Collection<MessengerBuddy> buddies) {
        ArrayList<ServerMessage> messages = new ArrayList<ServerMessage>();
        Set<MessengerBuddy> friends = new HashSet<MessengerBuddy>();

        int totalPages = (int)Math.ceil(buddies.size() / 750.0);
        int page = 0;

        for(MessengerBuddy buddy : buddies) {
            friends.add(buddy);

            if(friends.size() == 750) {
                messages.add(new FriendsComposer(totalPages, page, friends).compose());
                friends.clear();
                page++;
            }
        }

        if(page == 0 || friends.size() > 0) {
            messages.add(new FriendsComposer(totalPages, page, friends).compose());
        }

        return messages;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public Collection<MessengerBuddy> getFriends() {
        return friends;
    }
}
