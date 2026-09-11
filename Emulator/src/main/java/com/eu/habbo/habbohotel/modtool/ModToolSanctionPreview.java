package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.outgoing.unknown.ModToolSanctionDataComposer;
import java.util.ArrayList;

/**
 * Builds the {@code ModToolSanctionDataComposer} (2782, official
 * {@code SanctionStatusMessageEvent} payload read by
 * {@code IssueManager.updateSanctionData}) that answers the client's
 * {@code requestSanctionData} / {@code requestSanctionDataForAccount}.
 *
 * <p>The official client only displays the sanction the moderator would apply
 * if they closed the ticket with the selected CFH topic, so the preview is
 * derived from the data the emulator already owns: the escalating
 * {@code sanction_levels} rows when {@code hotel.sanctions.enabled} is on, and
 * otherwise the {@link ModToolPreset} attached to the CFH topic.
 */
public final class ModToolSanctionPreview {
    private static final String FALLBACK_SANCTION_TYPE = "ALERT";
    private static final int HOURS_PER_DAY = 24;

    private ModToolSanctionPreview() {}

    /**
     * @param issueId    the issue the preview belongs to, or -1 for the account variant
     * @param accountId  the account the preview belongs to, or -1 for the issue variant
     * @param categoryId the CFH topic currently selected in the dropdown
     * @param targetId   the user the sanction would hit (the reported user of the issue)
     */
    public static ModToolSanctionDataComposer forTopic(int issueId, int accountId, int categoryId, int targetId) {
        CfhTopic topic = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(categoryId);

        String name = FALLBACK_SANCTION_TYPE;
        int sanctionLengthInHours = 0;
        int probationDays = 0;
        boolean avatarOnly = false;

        ModToolSanctionLevelItem levelItem = nextSanctionLevel(targetId);

        if (levelItem != null) {
            name = levelItem.sanctionType;
            sanctionLengthInHours = levelItem.sanctionHourLength;
            probationDays = levelItem.sanctionProbationDays;
            avatarOnly = "MUTE".equalsIgnoreCase(levelItem.sanctionType);
        } else if (topic != null && topic.defaultSanction != null) {
            ModToolPreset preset = topic.defaultSanction;

            name = preset.name;
            avatarOnly = preset.banLength <= 0 && preset.muteLength > 0;
            sanctionLengthInHours =
                    (preset.banLength > 0 ? preset.banLength : Math.max(preset.muteLength, 0)) * HOURS_PER_DAY;
        }

        return new ModToolSanctionDataComposer(
                issueId,
                accountId,
                new ModToolSanctionDataComposer.CFHSanction(
                        name, sanctionLengthInHours, probationDays, avatarOnly, "", ""));
    }

    private static ModToolSanctionLevelItem nextSanctionLevel(int targetId) {
        if (targetId <= 0 || !Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
            return null;
        }

        ModToolSanctions sanctions = Emulator.getGameEnvironment().getModToolSanctions();
        ArrayList<ModToolSanctionItem> items = sanctions.getSanctions(targetId).get(targetId);

        int level = 1;

        if (items != null && !items.isEmpty()) {
            level = items.get(items.size() - 1).sanctionLevel + 1;
        }

        return sanctions.getSanctionLevelItem(level);
    }
}
