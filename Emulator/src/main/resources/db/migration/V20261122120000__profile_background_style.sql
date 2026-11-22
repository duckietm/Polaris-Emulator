-- A profile background of the player's own: two colours, the angle of the gradient between them and a
-- gallery card background laid over it, as "rrggbb;rrggbb;angle;pattern". Empty when they use the gallery.
ALTER TABLE `users`
ADD COLUMN IF NOT EXISTS `profile_background_style` VARCHAR(32) NOT NULL DEFAULT '' AFTER `profile_background_image`;
