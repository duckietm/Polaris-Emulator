package com.eu.habbo.habbohotel.items;

/** SQL that finds the Variables Web API box (wf_xtra_var_web_api) among items. */
public final class WebApiBoxSql {
    /**
     * The base item ids of the box, by interaction or by item name (a {@code wf_} item with interaction
     * {@code default} takes its interaction from its name). For queries that copy items.
     */
    public static final String BASE_ITEM_IDS = "SELECT id FROM items_base "
            + "WHERE interaction_type = 'wf_xtra_var_web_api' OR item_name = 'wf_xtra_var_web_api'";

    private WebApiBoxSql() {}
}
