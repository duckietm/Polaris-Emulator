package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/**
 * {@link PetPackageComposer} followed by the pet in the package, which the client shows in the name window. The pet
 * is written as the official client reads it: type, palette, colour, an unused int, then the custom parts (none for
 * a packaged pet).
 */
public class PetPackageWithFigureComposer extends PetPackageComposer {
    private final int type;
    private final int palette;
    private final String colour;

    PetPackageWithFigureComposer(UserItem item, int type, int palette, String colour) {
        super(item);
        this.type = type;
        this.palette = palette;
        this.colour = colour;
    }

    /**
     * The composer for a figure in the form {@code "[type] [palette] [colour]"}, as {@code PetPackage.figureIn}
     * gives it; null when the figure is not in that form.
     */
    static PetPackageWithFigureComposer parse(UserItem item, String figure) {
        if (figure == null) return null;

        String[] parts = figure.trim().split("\\s+");
        if (parts.length != 3) return null;

        try {
            return new PetPackageWithFigureComposer(
                    item, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
        } catch (NumberFormatException notAFigure) {
            return null;
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        super.composeInternal();
        this.response.appendInt(this.type);
        this.response.appendInt(this.palette);
        this.response.appendString(this.colour);
        this.response.appendInt(0);
        this.response.appendInt(0);
        return this.response;
    }
}
