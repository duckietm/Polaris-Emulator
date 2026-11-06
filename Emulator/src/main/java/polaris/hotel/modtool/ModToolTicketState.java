package polaris.hotel.modtool;

public enum ModToolTicketState {
    CLOSED(0),
    OPEN(1),
    PICKED(2);

    private final int state;

    ModToolTicketState(int state) {
        this.state = state;
    }

    public static ModToolTicketState getState(int number) {
        for (ModToolTicketState s : ModToolTicketState.values()) {
            if (s.state == number) return s;
        }

        return CLOSED;
    }

    public int getState() {
        return this.state;
    }
}
