package polaris.messages.incoming.housekeeping;

import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.users.profile.ProfileImageService;

/**
 * Removes one picture from the review list of uploaded profile pictures (hotel.profile_images); with the block
 * flag set the owner also stops being able to upload. The owner's profile goes back to the gallery choice.
 */
public class HousekeepingRemoveProfileImageEvent extends HousekeepingHandler {
    public static final String ACTION_KEY = "profile_image.remove";

    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.PROFILE_IMAGES;
    }

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int pictureId = this.packet.readInt();
        boolean block = this.packet.readBoolean();

        this.reply(ACTION_KEY, remove(this.operator(), pictureId, block, ProfileImageService.get()));
    }

    /** Removes a reviewed picture; on success the result's id is the picture id. */
    static HousekeepingResult remove(
            HousekeepingOperator operator, int pictureId, boolean block, ProfileImageService service) {
        if (pictureId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        int ownerId = service.ownerOf(pictureId);

        if (ownerId <= 0 || !service.removeReviewed(pictureId, operator.id(), block)) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_USER,
                ownerId,
                "",
                "picture=" + pictureId + (block ? " block=1" : ""),
                operator.ip());
        return HousekeepingResult.ok(pictureId);
    }
}
