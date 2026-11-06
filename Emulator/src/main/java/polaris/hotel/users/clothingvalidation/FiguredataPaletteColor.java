package polaris.hotel.users.clothingvalidation;

public class FiguredataPaletteColor {
    public int id;
    public int index;
    public boolean club;
    public boolean selectable;
    public String colorHex;

    public FiguredataPaletteColor(int id, int index, boolean club, boolean selectable, String colorHex) {
        this.id = id;
        this.index = index;
        this.club = club;
        this.selectable = selectable;
        this.colorHex = colorHex;
    }
}
