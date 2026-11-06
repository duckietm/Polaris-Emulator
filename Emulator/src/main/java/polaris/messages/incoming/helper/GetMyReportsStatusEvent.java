package polaris.messages.incoming.helper;

import polaris.hotel.modtool.MyReportStatusRepository;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.MyReportsStatusComposer;

/**
 * AIR 13 composer 2935 (`HabboHelp.requestReportsStatus`): the player opened the
 * "my reports" window, so send back every report they filed with its decision and appeal
 * state.
 */
public class GetMyReportsStatusEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new MyReportsStatusComposer(MyReportStatusRepository.findByReporter(
                this.client.getUser().getUserInfo().getId())));
    }
}
