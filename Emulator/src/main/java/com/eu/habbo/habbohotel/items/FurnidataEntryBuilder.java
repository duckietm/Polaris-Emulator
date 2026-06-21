package com.eu.habbo.habbohotel.items;

/**
 * Builds a complete furnidata entry object (single-line JSON5) from an {@link Item}
 * (its items_base row) plus a display name/description. Used by the Furni Editor
 * upsert path when a furni has no furnidata entry yet. Field shape mirrors the
 * hotel's existing furnidata entries; {@code id} is the item's sprite id so the
 * renderer resolves the furni's name/data by typeId.
 */
public final class FurnidataEntryBuilder {

    private FurnidataEntryBuilder() {}

    public static String build(Item item, String name, String description) {
        String classname = item.getName() != null ? item.getName() : "";
        String safeName = (name != null && !name.isBlank()) ? name
                : (item.getFullName() != null && !item.getFullName().isBlank()) ? item.getFullName()
                : classname;
        String safeDesc = description != null ? description : "";
        String customParams = item.getCustomParams() != null ? item.getCustomParams() : "";

        StringBuilder b = new StringBuilder(256);
        b.append("{\"id\":").append(item.getSpriteId());
        b.append(",\"classname\":\"").append(esc(classname)).append('"');
        b.append(",\"revision\":0,\"category\":\"unknown\",\"defaultdir\":0");
        b.append(",\"xdim\":").append(item.getWidth());
        b.append(",\"ydim\":").append(item.getLength());
        b.append(",\"partcolors\":{\"color\":[]}");
        b.append(",\"name\":\"").append(esc(safeName)).append('"');
        b.append(",\"description\":\"").append(esc(safeDesc)).append('"');
        b.append(",\"adurl\":\"\",\"offerid\":-1,\"buyout\":false,\"rentofferid\":-1,\"rentbuyout\":false,\"bc\":false,\"excludeddynamic\":false");
        b.append(",\"customparams\":\"").append(esc(customParams)).append('"');
        b.append(",\"specialtype\":1");
        b.append(",\"canstandon\":").append(item.allowWalk());
        b.append(",\"cansiton\":").append(item.allowSit());
        b.append(",\"canlayon\":").append(item.allowLay());
        b.append('}');
        return b.toString();
    }

    /** Escape for a JSON string value; collapse control chars to spaces. */
    private static String esc(String v) {
        StringBuilder b = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n' || c == '\r' || c == '\t') b.append(' ');
            else b.append(c);
        }
        return b.toString();
    }
}
