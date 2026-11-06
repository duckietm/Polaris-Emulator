package polaris.messages.outgoing.navigator;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorCollapsedCategoriesComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorCollapsedCategoriesComposer);
        this.response.appendInt(46);
        this.response.appendString("new_ads");
        this.response.appendString("friend_finding");
        this.response.appendString("staffpicks");
        this.response.appendString("with_friends");
        this.response.appendString("with_rights");
        this.response.appendString("query");
        this.response.appendString("recommended");
        this.response.appendString("my_groups");
        this.response.appendString("favorites");
        this.response.appendString("history");
        this.response.appendString("top_promotions");
        this.response.appendString("campaign_target");
        this.response.appendString("friends_rooms");
        this.response.appendString("groups");
        this.response.appendString("metadata");
        this.response.appendString("history_freq");
        this.response.appendString("highest_score");
        this.response.appendString("competition");
        this.response.appendString("category__Agencies");
        this.response.appendString("category__Role Playing");
        this.response.appendString("category__Global Chat & Discussi");
        this.response.appendString("category__GLOBAL BUILDING AND DE");
        this.response.appendString("category__global party");
        this.response.appendString("category__global games");
        this.response.appendString("category__global fansite");
        this.response.appendString("category__global help");
        this.response.appendString("category__Trading");
        this.response.appendString("category__global personal space");
        this.response.appendString("category__Habbo Life");
        this.response.appendString("category__TRADING");
        this.response.appendString("category__global official");
        this.response.appendString("category__global trade");
        this.response.appendString("category__global reviews");
        this.response.appendString("category__global bc");
        this.response.appendString("category__global personal space");
        this.response.appendString("eventcategory__Hottest Events");
        this.response.appendString("eventcategory__Parties & Music");
        this.response.appendString("eventcategory__Role Play");
        this.response.appendString("eventcategory__Help Desk");
        this.response.appendString("eventcategory__Trading");
        this.response.appendString("eventcategory__Games");
        this.response.appendString("eventcategory__Debates & Discuss");
        this.response.appendString("eventcategory__Grand Openings");
        this.response.appendString("eventcategory__Friending");
        this.response.appendString("eventcategory__Jobs");
        this.response.appendString("eventcategory__Group Events");
        return this.response;
    }
}
