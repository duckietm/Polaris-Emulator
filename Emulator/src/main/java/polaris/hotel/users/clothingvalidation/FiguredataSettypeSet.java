package polaris.hotel.users.clothingvalidation;

public class FiguredataSettypeSet {
    public int id;
    public String gender;
    public boolean club;
    public boolean colorable;
    public boolean selectable;
    public boolean preselectable;
    public boolean sellable;

    public FiguredataSettypeSet(
            int id,
            String gender,
            boolean club,
            boolean colorable,
            boolean selectable,
            boolean preselectable,
            boolean sellable) {
        this.id = id;
        this.gender = gender;
        this.club = club;
        this.colorable = colorable;
        this.selectable = selectable;
        this.preselectable = preselectable;
        this.sellable = sellable;
    }
}
