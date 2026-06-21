# Arcturus Morningstar Extended #

Arcturus Morningstar Extended is as a fork of Arcturus Emulator by TheGeneral and modified by Krews. Arcturus Morningstar Extended is also released under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.txt) 
and is developed for free by talented developers and is compatible with the following client revision/community projects:

| Community Clients | [Nitro Client](https://github.com/billsonnn/nitro-react) |
| ------------- | ------------- |

---

## ⚡ What's new in this branch (`java25-wired-modernization`)

This branch brings the emulator up to **Java 25 (LTS)** and massively expands the **WIRED**
subsystem. Every change keeps the full unit-test suite green (**414 tests, 0 failures**) and the
new wired boxes were validated live in-room. Detailed design notes live under
[`docs/superpowers/specs/`](docs/superpowers/specs).

### Runtime & build — Java 25
- Compiles and runs on **JDK 25**: `pom.xml` uses a single, coherent `maven.compiler.release=25`
  (previously an inconsistent mix of source 19 / target 19 / release 21).
- Start scripts enable **ZGC + Compact Object Headers** (`-XX:+UseZGC -XX:+UseCompactObjectHeaders`)
  for lower-latency GC and a smaller heap footprint.
- Dependency bumps (HikariCP 7.1.0, MariaDB JDBC 3.5.9) and a **GitHub Actions CI** workflow that
  runs `mvn verify` on JDK 25 for every push/PR.
- Validated end-to-end: full boot + classic CMS login confirmed on Java 25 + ZGC.

### Java 25 language modernization
All conversions are behaviour-preserving (compiler-enforced where possible) and applied package by
package, each verified by the test suite:
- **Pattern matching for `instanceof`** — ~340 conversions across the whole module (test-then-cast,
  guarded-`&&`, and negated early-exit forms); non-candidates correctly skipped.
- **Switch expressions** — value-mapper / return-only `switch` statements rewritten to arrow form.
- **`Stream.toList()` / `Collectors.toUnmodifiableSet()`** — ~50 sites; applied via a two-stage
  adversarial review (each result traced into callers/constructors/serialization) so no mutable
  use becomes an unmodifiable-collection runtime error.
- **Sequenced collections** — `getFirst()/getLast()/removeLast()` where the receiver is a
  `SequencedCollection` and the access is provably non-empty.
- **`Math.clamp(...)`** replacing nested `min(max(...))`; a YouTube playlist loader moved to a
  virtual-thread executor.
- **Records** — 8 immutable carriers (`FriendRequest`, `VoucherHistoryEntry`, `HabboMention`,
  `EarningsReward/Entry/ClaimResult`, `GuideChatMessage`, `CryptoConfig`) converted to Java records.

### Concurrency correctness
- `volatile` added to cross-thread shared fields (session-resume visibility races) and the
  non-thread-safe `gnu.trove` set in `RoomUnit` replaced with a `ConcurrentHashMap`-backed set.
- **Opt-in virtual-thread packet handler** (`io.packet.handler.virtual=1`) backed by a shared
  server-lifetime executor group.

### WIRED subsystem — 100+ new interactions
A full study of the wired engine (see
[`2026-06-21-wired-study.md`](docs/superpowers/specs/2026-06-21-wired-study.md)) plus a categorized
gap analysis against the furnidata produced **102 newly registered wired `interaction_type`s and 45
new classes**:
- **Furnidata aliases & parameter-variants** — 56 furnidata names bound to existing behaviour
  (e.g. `wf_act_freeze_habbo` → freeze, `wf_act_raise_furni` → set-altitude).
- **New triggers** — `wf_trg_starts_dancing / stops_dancing / idles / unidles` (the events already
  fired from the room manager; only the trigger classes were missing).
- **New effects** — currency (`give_credits / give_duckets / give_diamonds / give_experience`),
  badges (`give_badge / give_userbadge / remove_badge / give_achievement`), user actions
  (`sit / lay / make_fast_walk / walk_to_furni / move_user_tiles / make_user_say / say_command /
  give_look / open_habbo_pages / log`), room (`toggle_moodlight / reset_highscores /
  all_users_leave_team`), profile **tags** (`add_tag / remove_tag`, with a new persisted
  `HabboStats` tag API), and **negative-branch** effects (`neg_show_message / neg_log`).
- **New conditions** — currency checks, `freeze / not_freeze`, furni range, same-height, owned-badge
  (`owns_badge`), owned-furni (`owns_furni`), `motto_contains`, and `has_at_least_x_items`.
- Where a dedicated client dialog is missing, the
  [Nitro patch list](docs/superpowers/specs/2026-06-21-nitro-wired-dialog-patch-list.md) documents
  exactly what the client would need.

### Database
- [`Database Updates/013_wired_interaction_type_fix.sql`](Database%20Updates/013_wired_interaction_type_fix.sql)
  fixes existing wired furni (selectors `wf_slc_*`, variables `wf_var_*`, signals, extras `wf_xtra_*`)
  that shipped in `items_base` with `interaction_type = 'default'` and were therefore unplaceable —
  the whole selector / variable / signal wired subsystem. The migration sets `interaction_type =
  item_name` for the affected rows (scoped to real registered wired types, idempotent), taking
  placeable wired types from 257 → 309.

> **Requires an emulator restart** after running migration 013 (`items_base` is read at startup).

---

## Download ##
[Latest compiled version](https://github.com/duckietm/Arcturus-Morningstar-Extended/tree/main/Latest_Compiled_Version)

## Connection ##
Use the BUILD-IN Websocket so do NOT load any websocket plugin!

### How do I connect to my emulator using Secure Websockets (wss)?
You have several options to add WSS support to your websocket server. 

- You can add your certificate and key file to the path `/ssl/cert.pem` and `/ssl/privkey.pem` to add WSS support directly to the server **Note**:The client will not accept self-signed certificates, you must use a certificate signed by a CA (you can get one for free from letsencrypt.org)
  
- **RECOMMENDED** You can proxy WSS with either cloudflare or nginx. **Note**: Adding a proxy means that you will have to configure `ws.nitro.ip.header` so that the plugin is able to get the player's real ip address, and not the IP address of the proxy.

### Proxying WSS with Cloudflare
You can easily proxy wss traffic using Cloudflare. However, you should first make sure that your `ws.nitro.port` is set to one that is listed as HTTPS Cloudflare Compatible in the following link:
https://support.cloudflare.com/hc/en-us/articles/200169156-Which-ports-will-Cloudflare-work-with-

As of writing this, the following ports are listed as compatible:
- 443
- 2053
- 2083
- 2087
- 2096
- 8443

After your port is set to one that is compatible, create a new A record for a subdomain that will be used for websocket connections, and make sure that it is set to be proxied by Cloudflare (the cloud should be orange if it is being proxied). It should be pointing to your emulator IP.

Finally, create a new page rule under the Page Rules tab in Cloudflare and disable SSL for the subdomain you created above. You will now be able to connect using secure websockets using the following example url, where I created an A record for the subdomain `ws` and I set my `ws.nitro.port` to 2096: `wss://ws.example.com:2096` 

### Branches ###
There are two main branches in use on the Arcturus Morningstar git. Below the pros an

| master | Tested on a production hotel and is stable for every day use with no known serious exploits. |
| ------------- | ------------- |
| dev | The most up-to-date, but features may not work as intended. Use at your own risk. |

There is no set timeframe on when new versions will be released or when the stable branch will be updated

### Database ###
We have placed the myBoBBa database [myBobba](https://github.com/ObjectRetros/2023-hotel-files) to get you started on building your Retro hotel.
Also there is a file call UpdateDatabase.sql this will hold all the updates that are required, please run this after the myBoBBa Database import

## Can I Help!? ##
#### Reporting Bugs: ####
You can report problems via the [Issue Tracker](https://github.com/duckietm/Arcturus-Morningstar-Extended/issues*

###### * When making an bug report or a feature request use the template we provide so that it can be categorized correctly and we have more information to replicate a bug or implement a feature correctly. ######
#### Can I contribute code to this project? ####
Of Course! if you have fixed a bug from the git please feel free to do a [merge request](https://github.com/duckietm/Arcturus-Morningstar-Extended/issues)*

## Support 
We also have a [Discord channel](https://discord.gg/3VeyZXf5) where you can find some more information.

###### * Anyone is allowed to fork the project and make pull requests, we make no guarantee that pull requests will be approved into the project. Please Do NOT push code which does not replicate behaviour on habbo.com, instead make the behaviour configurable or as a plugin. ######

## Plugin System ##
The robust Plugin System included in the original Arcturus release is also included in Arcturus Morningstar, if you're interested in making your own plugins, feel free to ask around on our discord and we'll point you in the right direction! 

### Credits ###
    
       - TheGeneral (Arcturus Emulator)
	   - Beny
	   - Alejandro
	   - Capheus
	   - Skeletor
	   - Harmonic
	   - Mike
	   - Remco
	   - zGrav
	   - Quadral
	   - Harmony
	   - Swirny
	   - ArpyAge
	   - Mikkel
	   - Rodolfo
	   - Kitt Mustang
	   - Snaiker
	   - nttzx
	   - necmi
	   - Dome
	   - Jose Flores
	   - Cam
	   - Oliver
	   - Narzo
	   - Tenshie
	   - MartenM
	   - Ridge
	   - SenpaiDipper
	   - Thijmen
	   - Brenoepic
	   - Stankman
	   - Laynester
	   - Bill
	   - Mikee
	   - Merijn
	   - Puffin
	   - ObjectRetros
	   - EntenKoeniq
	   - DuckieTM
