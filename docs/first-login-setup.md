# First-login setup (CMS accounts)

An account made on Octane's own register screen chooses a look and a starter room there. An account a CMS makes
skips both. The first time such an account logs in, the client now shows a welcome window with the same two steps:

- **Your look**: the current avatar and a button that opens the avatar editor.
- **Your first room**: the hotel's starter rooms (`room_templates` with `enabled = '1'`, the same list the register
  screen shows), or "no room".

"Done" answers the offer. With a room chosen, the template is copied into a room of the user's own, it becomes
their home room, and they are sent there. Closing the window without "Done" keeps the offer for the next login.

## Who is asked

| Account | Asked |
| --- | --- |
| Made by a CMS, owns no room and has no home room, first login after this update | yes |
| Made by a CMS that already gave it a room or a home room | no |
| Made on Octane's register screen | no |
| Any account that logged in before this update | no |

Nothing changes for a CMS: no column or call is needed. The emulator notes the first login itself in
`users_first_login_setup` (`user_id`, `completed`); an account without a row there was never asked and never will be.

## Settings

- `hotel.first_login.setup.enabled` (`emulator_settings`, default `1`): set to `0` when the CMS has its own
  onboarding and the window should never show.

To offer the setup again to one account: `UPDATE users_first_login_setup SET completed = 0 WHERE user_id = <id>;`
(it shows at the next login).

## Texts

The window falls back to English. To translate, add these keys to the external texts: `first_login.title`,
`first_login.intro`, `first_login.look.title`, `first_login.look.desc`, `first_login.look.change`,
`first_login.room.title`, `first_login.room.skip.title`, `first_login.room.skip.desc`, `first_login.done`.

## Packets (custom)

- `9496` server to client, `FirstLoginSetupComposer`: `count`, then per room `templateId`, `title`, `description`,
  `thumbnail`. Sent at login while the offer is open.
- `9497` client to server, `FirstLoginSetupChooseEvent`: `templateId` (0 = no room). Only the first answer counts,
  and no room is made for an account that already owns one.
