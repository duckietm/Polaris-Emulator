# Wired Guide: every box and how to connect them

Updated 18 October 2026

## How wired works

Wired is a set of special furni that make your room do things on its own. You build a **stack**: wired boxes placed on top of each other on one tile. Every box in that stack works together.

A stack always has the same parts:

| Part | What it does | Example |
| --- | --- | --- |
| **Trigger** (when) | Starts the stack when something happens | A user says a word, walks on a tile, a timer runs out |
| **Condition** (only if) | Checks something first. If the check fails, nothing happens | Only if the user wears a badge, only if a furni is on |
| **Selector** (who or what) | Picks the users or furni the effects work on | All users in a team, all furni of one type |
| **Effect** (do) | Does the actual thing | Move a furni, teleport a user, show a message |
| **Add-on** (how) | Changes how the stack runs | Pick one random effect, run effects in order, limit how often it runs |

One stack can hold several triggers, conditions, selectors, effects and add-ons. Stacks can also talk to each other with **signals** and remember things in **variables**.

### What happens when a stack fires

1. A trigger notices something, for example a user stepping on a tile. That user becomes the **triggering user**, and the furni they stepped on becomes the **triggering furni**.
2. Selectors and filters pick the users or furni the effects will use. If the stack needs targets and nothing is picked, it stops here.
3. All conditions in the stack are checked. By default every condition must pass. With the right add-on, one passing condition is enough. If they fail, only the "on fail" effects run.
4. The effects run. Without an add-on they all run at the same moment. With **Execute in order** they run one after the other.
5. Effects with a delay wait first. Delays count in steps of 0.5 seconds, or 0.05 seconds with the precise delay option.

![How a stack runs: trigger, selectors, conditions, then effects or on-fail effects](images/how-a-stack-runs.png)

When the conditions pass the effects run; when they fail, only the on-fail effects run. Add-ons change how each step behaves.

### Picking what a box works on

Most boxes need to know which furni or users to use. In the box you choose a **source**:

- **Picked furni**: the furni you clicked while the box was open.
- **Triggering user or furni**: whoever or whatever started the stack.
- **Selector**: the users or furni chosen by a selector box in the same stack.
- **Signal**: the users or furni that came along with a signal from another stack.

### Who can build wired

The room owner and users with rights can place and edit wired. In the wired settings the owner decides who may look at wired (inspect) and who may change it (modify). Room visitors only see what the wired does.

## Triggers: when a stack starts

Every stack needs at least one trigger. The trigger decides when the stack starts and who the **triggering user** and **triggering furni** are. Boxes marked *extra* are additions on this hotel that Habbo does not have.

### Users

| Trigger | Starts when | Triggering user / furni |
| --- | --- | --- |
| User says keyword | Someone says a word or sentence. You choose: contains, exact match, or all words. You can hide the message and make it work for the owner only | The user who spoke. Their sentence can be shown again with $(trigger\_text) |
| Say your username *(extra)* | A user says their own name | That user |
| User walks on furni | Someone steps onto one of the picked furni (up to 20) | The user, and the furni stepped on |
| User walks off furni | Someone steps off one of the picked furni | The user, and the furni |
| User clicks furni | Someone clicks one of the picked furni | The user, and the clicked furni |
| User clicks tile | Someone clicks an invisible click tile | The user, and the tile |
| User clicks user | Someone clicks another avatar or a bot. You can stop the info menu from opening | The clicker. The clicked user is available as **clicked user** |
| User performs action | Someone waves, blows a kiss, laughs, gives a thumbs up, wakes up, goes idle, sits, stands, lies down, holds up a sign or dances. You can pick a sign number or dance | The user |
| User starts or stops dancing *(extra)* | Someone starts or stops dancing | The user |
| User goes idle / stops idling *(extra)* | Someone goes AFK or comes back | The user |
| User enters room | Someone enters. You can limit it to one name | The user |
| User leaves room | Someone leaves. You can limit it to one name | The user |
| User presses key *(extra)* | Someone presses a chosen keyboard key | The user |
| User gets hand item *(extra)* | Someone receives a hand item (any, or one chosen item) | The user |

### Furni and games

| Trigger | Starts when | Triggering user / furni |
| --- | --- | --- |
| User toggles furni | A user switches one of the picked furni | The user, and the furni |
| Furni state changed | One of the picked furni changes state for any reason, also by wired | The furni |
| Dice rolled *(extra)* | A dice is rolled (any number, or one chosen number) | The dice, and who rolled it |
| Collision | A furni moved by wired bumps into a user | The user that was hit, and the furni |
| Bot reaches furni | A bot arrives at one of the picked furni | The bot, and the furni |
| Bot reaches user | A bot arrives at a user | The **bot**. The user it reached is available as **reached user** |
| Game starts / Game ends | A game in the room starts or ends | Nobody |
| Score reached | A team's score passes a number you choose | The team |
| Team wins / Team loses *(extra)* | A team wins or loses a game | The team |
| Counter reaches time | A picked game counter reaches the time you set (up to 99 minutes) | The counter |

### Time and room

| Trigger | Starts when |
| --- | --- |
| Repeat every X seconds | Again and again, in steps of 0.5 seconds (default 5 seconds) |
| Repeat short | Very fast, every 50 to 500 milliseconds |
| Repeat long | Slowly, in steps of 5 seconds (default 50 seconds) |
| Execute once after X seconds | Once, X seconds after the room loads or after **Reset timers** |
| Execute once (long) *(extra)* | Same, in steps of 5 seconds |
| Room initialized | Once, when the room loads, before any other trigger |

### Talking to other stacks

| Trigger | Starts when | Triggering user / furni |
| --- | --- | --- |
| Signal received | A **Send signal** box sends to one of the picked antennas | The antenna. Users and furni sent with the signal come along as the **signal** source |
| Variable changed | A variable on a user, furni or the room is created, changed or removed | The user or furni holding the variable |
| Receive Link message | A WIRED Link in this room receives a message from another room | Nobody. The message is shown with $(trigger\_text) |
| Receive Link dataframe | A WIRED Link receives a set of values from another room | Nobody |
| Transaction completed / failed | A contract started by **Initiate transaction** succeeds or fails | The user who did the transaction |

## Effects: what a stack does

Effects do the actual work. Every effect has a **delay** setting, so it can wait before it runs. In each effect you choose which furni or users it works on (see *Picking what a box works on*).

### Furni: state and look

| Effect | What it does |
| --- | --- |
| Toggle furni state | Switches the furni to its next state (or the previous one) |
| Toggle to random state | Puts the furni in a random state |
| Roll dice *(extra)* | Rolls the dice as if someone clicked them |
| Match furni to snapshot | Puts furni back as they were when you saved the box: state, direction, position and height, each one optional |
| Set furni transparency | Makes furni see-through (0 to 100) for everyone or only some users, with a smooth fade. Can also make them click-through |
| Set furni highlight | Gives furni an outline, glow, darken, colour tint, brighten or neon effect, optionally pulsing |
| Toggle dimmer *(extra)* | Turns the room dimmer on or off |
| Reset highscores *(extra)* | Clears the highscore boards in the room |

### Furni: moving

| Effect | What it does |
| --- | --- |
| Move and rotate furni | Moves furni one tile in a direction (or random) and/or turns it |
| Move furni in direction | Keeps moving furni in one direction. You choose what happens when it is blocked: wait, turn left or right, turn back, or random |
| Chase | Moves the furni towards the nearest user |
| Flee | Moves the furni away from users |
| Move furni to | Moves furni towards chosen target furni |
| Move furni to user | Moves furni onto a user's tile |
| Move furni to furni | Moves furni onto another furni |
| Relative move | Moves furni a set number of tiles on X and Y (up to 20) |
| Set furni altitude | Raises, lowers or sets the height of furni |
| Override height | Makes a tile act as if it has a certain height (0 to 8), for walking and stacking |
| Move as group | Moves a group of furni together, keeping their layout, to a user or furni |
| Move group one step *(extra)* | Shifts a group of furni one tile in a direction |
| Place furni | Places temporary copies of furni. They disappear when the room unloads and can't be picked up |
| Remove furni | Removes those temporary copies |
| Spawn furni *(extra)* | Creates real furni owned by the room owner |
| Pick up furni *(extra)* | Picks up furni back to the owner's inventory. Never picks up wired |

### Users

| Effect | What it does |
| --- | --- |
| Show message | Shows a message to the users, or to the whole room. Up to 400 characters and 8 lines. Placeholders like $(name) are filled in |
| Make user say *(extra)* | Makes the user say a text in a chat bubble |
| Teleport user to furni | Teleports users to a furni, with the teleport effect. **Fast** makes it quicker |
| Move user to furni | Makes users walk (or jump) to a furni |
| Move and rotate user | Moves users one tile and/or turns them |
| Walk to furni *(extra)* | Makes users walk to a furni, with fast or teleport options |
| Move user N tiles *(extra)* | Moves users 1 to 10 tiles |
| Make user sit / lay / fast walk *(extra)* | Puts users in that posture or speeds them up |
| Teleport to room | Sends users to another room, through a room link, a teleporter pair or a room number. Doorbell, password and bans still count |
| Join team / Leave team | Puts users in a team (chosen, the smallest, or random) or takes them out |
| Everyone leaves team *(extra)* | Takes everyone out of their team |
| Freeze user / Unfreeze user | Stops users from walking, with a freeze effect, or lets them walk again |
| Kick from room | Kicks users out with a message |
| Mute user | Mutes users for up to 24 hours, with a message |
| Give effect | Puts an avatar effect on users |
| Give hand item *(extra)* | Gives users a hand item |
| Click settings | Changes how clicking works for users: walk behind avatars or click through furni |

### Points, rewards and profile

| Effect | What it does |
| --- | --- |
| Give score / Give score to team | Gives points in a game (negative points take them away). You can limit how often per game |
| Give reward | Gives a badge, furni or points, once or a few times per day, hour or minute |
| Progress achievement | Adds progress to an achievement, if the hotel allows it in this room |
| Progress / reset reward track | Adds progress to a reward track, or resets it |
| Give credits, duckets, diamonds, experience, respect *(extra)* | Gives that currency or score, up to the hotel's maximum |
| Give currency of type *(extra)* | Gives any currency type |
| Give or take furni *(extra)* | Gives furni to users, or takes it away |
| Give / remove badge *(extra)* | Gives or removes a badge |
| Add / remove tag *(extra)* | Adds or removes a profile tag |
| Give look *(extra)* | Changes the user's clothes |
| Give achievement *(extra)* | Unlocks an achievement |
| Alert *(extra)* | Shows a pop-up alert |
| Open page *(extra)* | Opens a page in the client, for example the catalogue |
| Say command *(extra)* | Runs a chat command as the user (only safe commands) |

### Bots

| Effect | What it does |
| --- | --- |
| Bot teleport to furni | Teleports a bot to a furni |
| Bot walk to furni | Makes a bot walk to a furni |
| Bot talk | Makes a bot talk or shout |
| Bot talk to user | Makes a bot whisper or talk to a user |
| Bot gives hand item | A bot hands users an item |
| Bot follow user | A bot starts or stops following a user |
| Bot change figure | Changes a bot's clothes |
| Bot dance *(extra)* | Makes a bot dance or stop dancing |

### Room, clocks and media

| Effect | What it does |
| --- | --- |
| Reset timers | Restarts the *Execute once after X seconds* triggers |
| Control clock | Starts, stops, pauses, resumes or resets a game counter |
| Adjust clock | Adds time, takes time away, or sets the counter |
| Set roller speed *(extra)* | Changes how fast rollers move, or switches them off |
| Play YouTube *(extra)* | Plays a YouTube video on a TV |
| Jukebox play / stop *(extra)* | Plays or stops a jukebox track |
| Set room promotion *(extra)* | Changes the room's event text |
| Write to logs | Writes a line to the wired room log, for testing your wired |

### Linking stacks

| Effect | What it does |
| --- | --- |
| Call another stack | Runs the effects of other stacks, with their own conditions. Up to 10 calls deep |
| Send signal | Sends a signal to antennas, optionally with users and furni. See *Signals* |
| Give / change / remove variable | Works with variables. See *Variables* |
| Send Link message / dataframe | Sends text or values to other rooms. See *WIRED Link* |
| Give currency / furni from chest, Initiate / cancel transaction | Works with chests. See *Chests* |

### When conditions fail

Some effects run **only when the conditions fail**: *Show message (on fail)*, *Write to logs (on fail)*, *Negative call stack* and *Negative send signal*. Use them for an *else*: "if the user has no badge, tell them".

## Conditions: only if

A condition checks something before the effects run. Most conditions have a **negative** version that checks the opposite ("is **not** on furni"). When a condition checks several users or furni, you choose **all of them** or **at least one**.

### Furni

| Condition | Passes when | Negative version |
| --- | --- | --- |
| Furni matches snapshot | Furni have the state, direction, position or height they had when you saved | Yes |
| Furni have users on | There are users on the picked furni | Yes |
| Furni have furni on | Other furni stand on the picked furni | Yes |
| Triggering furni is of type | The triggering furni is the same type as a picked furni | Yes |
| Furni altitude is | Furni are lower than, equal to or higher than a height | No |
| Movements are valid | The stack's move effects would all succeed (nothing in the way) | No |
| Furni have same height *(extra)* | The furni are at the same height | Yes |
| Furni in range *(extra)* | Furni are within a distance of the triggering furni | Yes |
| Furni transparency is *(extra)* | Furni have a certain transparency | Yes |
| Next furni has state *(extra)* | A furni next to the triggering furni is in a certain state | No |

### Users

| Condition | Passes when | Negative version |
| --- | --- | --- |
| User is on furni | The triggering user stands on a picked furni | Yes |
| User on furni with state *(extra)* | The user stands on a furni in a certain state | No |
| User count in room | The number of users is between a minimum and a maximum | Yes |
| User is in team | The user is in a team (any, or one colour) | Yes |
| User is in group | The user is a member of the room's group | Yes |
| User wears badge | The user wears a certain badge | Yes |
| User owns badge *(extra)* | The user has a badge, worn or not | Yes |
| User wears effect | The user has a certain avatar effect on | Yes |
| User has hand item | The user holds a certain hand item | Yes |
| Triggering user is | The trigger was started by a user, bot or pet, or by one name | Yes |
| User performs action | The user is waving, dancing, sitting and so on | Yes |
| User faces direction | The user looks in a certain direction | No |
| User level is | The user's account level is above, equal to or below a number | No |
| User is frozen *(extra)* | The user is frozen by wired | Yes |
| User is male / female *(extra)* | The user's avatar gender | No |
| User has rights *(extra)* | The user has rights in the room | Yes |
| User has rank *(extra)* | The user has a certain hotel rank | Yes |
| User has credits / duckets / diamonds *(extra)* | The user has at least that much | Yes |
| User has at least X items *(extra)* | The user's inventory has at least that many furni | No |
| User owns furni *(extra)* | The user owns a furni of the picked type | Yes |
| User has tag *(extra)* | The user has a certain profile tag | Yes |
| Motto contains *(extra)* | The user's motto contains a text | No |
| User in range *(extra)* | The user is within a distance of a furni | Yes |
| User cooldown *(extra)* | The same user hasn't triggered it in the last X seconds | No |
| First time only *(extra)* | It's the first time this user triggers it | No |
| Once per day *(extra)* | The user hasn't triggered it yet today | No |
| User has highscore points *(extra)* | The user has at least X points on a highscore board | Yes |

### Time, games and selection

| Condition | Passes when |
| --- | --- |
| Time elapsed more / less than | More (or less) than X seconds have passed since the room loaded or the timers were reset |
| Date range active | Today is between two dates |
| Time matches / Date matches | The clock or the date matches what you set, in the room's time zone |
| Counter time matches | A game counter shows a certain time |
| Team has score / Team has rank | A team has a certain score, or is in a certain place |
| Battle Banzai is running *(extra)* | A Banzai game is going on (also a negative version) |
| Selection quantity | The selectors in the stack picked a certain number of users or furni |

### Variables and chests

| Condition | Passes when |
| --- | --- |
| Has variable | The user or furni has a variable (also a negative version) |
| Variable value matches | A variable is equal to, higher or lower than a number or another variable |
| Variable age matches | A variable was created or changed more or less than X time ago |
| Check array *(extra)* | A list variable contains what you check for |
| Chest has items | A chest holds at least an amount of coins or furni |
| Chest has item type | A chest holds a certain type of furni |

### Using more than one condition

Normally **all** conditions in a stack must pass. Put a **Condition evaluation** add-on in the stack to change that: at least one, not all, none, or a number of them.

## Selectors and filters: who or what

A selector picks users or furni **while the stack runs**, so you don't have to click them all in every box. In an effect, choose the **selector** source to use what the selectors picked.

Every selector has two switches:

- **Filter existing selection**: instead of adding to what other selectors picked, only keep the ones that also match this selector.
- **Invert**: pick everything that does **not** match.

Without *filter*, several selectors in one stack add up.

### Furni selectors

| Selector | Picks |
| --- | --- |
| Furni of the same type | All furni of the same type as the picked furni, optionally only in the same state |
| Picked furni | The furni you clicked (up to 20) |
| Furni on furni | Furni on top of, below, at the same height as, or on the same tile as other furni |
| Furni from signal | The furni that came with a signal |
| Furni in neighborhood | Furni in a pattern of tiles you draw around the user or a furni |
| Furni in area | Furni inside a rectangle of the room |
| Furni with altitude | Furni lower than, equal to or higher than a height |
| Furni with variable | Furni that have a variable, or a variable with a certain value |
| Chest scanner *(extra)* | Furni in the room of a type stored in a chest |

### User selectors

| Selector | Picks |
| --- | --- |
| Users by type | Users, pets or bots |
| Users in team | Users in any team, or one colour |
| Users on furni | Users standing on the picked furni |
| Users by action | Users who waved, danced, held a sign and so on in the last 5 seconds |
| Users from signal | The users that came with a signal |
| Users by name | Users with the names you type (up to 20, one per line) |
| Users in neighborhood | Users in a pattern of tiles you draw |
| Users in area | Users inside a rectangle of the room |
| Users with hand item | Users holding a certain hand item |
| Users in group | Members of the room's group, or another group |
| Users with variable | Users that have a variable, or a variable with a certain value |

### Remote selector

The **remote selector** runs selectors from another stack and uses their result. It can combine them (everything any of them picked, or only what all of them picked) and pick a random number from the result.

### Filters

Filters are add-ons that shrink a selection:

- **Furni filter / User filter**: keep only a number of them, at random or in order.
- **Filter by variable**: sort by a variable (highest, lowest, oldest, newest) and keep the first ones. Great for "the 3 players with the most points".

## Add-ons: changing how a stack runs

Add-ons don't do anything on their own. You put them in a stack, and they change how the other boxes in that stack behave.

### How effects are chosen and timed

| Add-on | What it changes |
| --- | --- |
| Random effect | Runs only a random few of the stack's effects, instead of all of them. It can also avoid repeating recent picks |
| Unseen effect | Runs one effect at a time, in turn, so nothing repeats until every effect has had its turn |
| Execute in order | Runs the effects one after the other, from bottom to top, instead of all at once |
| Execution limit | Lets the stack run at most a number of times (1 to 100) per few seconds. Stops spam |
| Condition evaluation | Changes how conditions count: all, at least one, not all, none, or fewer / exactly / more than a number |

### Movement

| Add-on | What it changes |
| --- | --- |
| No movement animation | Furni jump to their new place instantly instead of sliding |
| Animation time | How long a slide takes (0.05 to 2 seconds) |
| Movement physics | Whether moved furni keep their height, pass through furni or users, or are blocked by certain furni |
| Carry users | Users standing on a moved furni ride along with it |
| Jump strength / movement curve | Moves become a jump with an arc. Extra on this hotel: easings like bounce, elastic and drop |
| Projectile | Moves become a flying shot with a curve, a speed per tile and a shooter. You can read where the shot is and what it hit |

### Selections

| Add-on | What it changes |
| --- | --- |
| Furni filter / User filter | Keeps only a number of the picked furni or users, random or in order |
| Filter furni / users by variable | Sorts by a variable (highest, lowest, oldest, newest) and keeps the top ones |

### Texts in messages

These add-ons let you put names and numbers into **Show message**, **Bot talk** and other texts. You choose a placeholder name, for example `name`, and write `$(name)` in your text.

| Add-on | Fills in |
| --- | --- |
| Username placeholder | The name of one user, or all of them with commas in between |
| Furni name placeholder | The name of a furni |
| Variable placeholder | The value of a variable, as a number or as text |
| Global placeholder | A fixed text you use in all wired in the room, for example the name of your game |
| Variable capturer | The other way round: takes a word or number a user says (written as `#(name)` in the keyword) and stores it in a variable |
| Text connector | Turns numbers into words and back, with lines like `1=Red`, `2=Blue` |

The text of whatever started the stack is always available as `$(trigger_text)`.

### Variables, levels and displays

| Add-on | What it does |
| --- | --- |
| Level-up system | Turns a points variable into levels (manual, linear or exponential). You can read the level, the progress and the points still needed |
| Time utilities | Reads a variable as a date and time: the hour, day, month, year and so on, in the room's time zone |
| Variable leaderboard | Shows a ranking of a user variable on the screen, for example the top players by coins |
| Health / progress / level / status / boss bar, number display | Draws a bar or number above users or furni, showing the value of a variable |
| Achievement enabler | Names the achievements that wired in this room may give progress to |
| Variables web API | Lets an outside website read or write the room's variables, if the hotel has switched this on |
| Link speaker | Chooses which WIRED Links the stack's *Send Link* boxes send through |
| Custom contract | A contract that mixes payments and rewards freely (see *Chests*) |

## Variables: wired that remembers

A variable is a named value that wired can store, change and check: coins in a game, lives, a level, whether someone has a key. You create a variable with a **variable box**, then use effects to give, change or remove it, and conditions to check it.

### Kinds of variables

| Variable box | Who holds it | How long it lasts |
| --- | --- | --- |
| User variable | Each user has their own value | While they are in the room, forever (saved), or shared with your other rooms |
| Furni variable | Each furni has its own value | While the room is open, or forever (saved) |
| Room variable | One value for the whole room | While the room is open, forever, or shared with your other rooms |
| Context variable | Only exists while one stack runs | Passed along with signals and stack calls, then gone |
| Reference | A copy of a shared variable from another of your rooms | As long as the original exists. Can be read-only or writable |
| Echo | Follows another variable automatically | As long as the original exists |
| Pointer | Points to another variable box, chosen while the stack runs | Only while the stack runs |
| Quest / quest chain | Turns a counter into a quest with a target, progress and percent | Same as the counter |
| Daily task | Turns a counter into a daily task that resets every day | Resets at the start of each day in the room's time zone |

A variable can **have a value** (a number like 5 or -20) or just **exist** (a yes/no flag, like "has the red key").

### Working with variables

| Box | What it does |
| --- | --- |
| Give variable | Gives the variable to users or furni, with a starting value (typed in, or copied from another variable) |
| Change variable | Changes the value: set, add, subtract, multiply, divide, power, remainder, minimum, maximum, random, and more |
| Remove variable | Takes the variable away |
| Has variable *(condition)* | Checks that a user or furni has it |
| Variable value matches *(condition)* | Checks the value against a number or another variable |
| Variable age matches *(condition)* | Checks how long ago it was created or changed |
| Variable changed *(trigger)* | Starts a stack when the variable is created, changed or removed |
| Users / furni with variable *(selector)* | Picks everyone who has it, or a certain value |

In most of these boxes the value field accepts **a number or a variable name**. Click the small variable button next to the field to pick one. Tick **Use the same furni or user as the target variable** to compare a user with their own other variable.

### Built-in variables

The hotel already gives you many ready-made variables. They start with `@`. Some can also be changed with **Change variable**.

| About | Examples | Can be changed |
| --- | --- | --- |
| Users | `@position_x`, `@position_y`, `@direction`, `@altitude`, `@level`, `@effect_id`, `@handitem_id`, `@team_color`, `@team_score`, `@is_hc`, `@has_rights`, `@dance`, `@sign`, `@is_idle` | Position, direction, altitude, hand item, effect, scores |
| Furni | `@state`, `@position_x`, `@position_y`, `@rotation`, `@altitude`, `@height`, `@opacity`, `@owner_id`, `@can_move_freely` | State, position, rotation, altitude, opacity |
| Shots (projectile) | `@projectile.animation.tiles_traveled`, `@projectile.animation.user_collisions`, `@projectile.animation.is_traveling` | No |
| Room | `@user_count`, `@furni_count`, `@wired_timer`, team scores and sizes, `@current_time` with its parts (hour, day, month, year) | Team scores |
| While a stack runs | `@selector_user_count`, `@signal_furni_count`, `@antenna_id`, `@chat_type` | No |

### Seeing your variables

Type `:var` in the chat to open the **Variables** tab of the wired menu. It shows every variable, who holds it and the values, and lets you change or delete them. With `:inspect` you click a user or furni and see all their variables.

## Signals, WIRED Link, chests and contracts

### Signals: one stack starts another

A **signal** lets one stack start another stack in the same room. You need an **antenna** furni.

1. In the first stack, add **Send signal** and pick the antenna. You can send users and furni along with it.
2. In the second stack, use the trigger **Signal received** and pick the same antenna.
3. In the second stack, the users and furni that came with the signal are available as the **signal** source.

Rules: a signal chain reaches each antenna only once, so signals can't loop forever. The antenna flashes every time it receives a signal. *Negative send signal* only sends when the conditions fail.

### WIRED Link: talking between rooms

A **WIRED Link** furni connects your rooms. Click it to open the network window, create a network, and put a Link from each room in it (one Link per room per network). Only your own rooms can join your networks.

| Box | What it does |
| --- | --- |
| Send Link message | Sends a text to the other rooms on the network |
| Send Link dataframe | Sends up to 12 named values (numbers, variable values or texts) |
| Receive Link message *(trigger)* | Starts a stack when a message arrives. The text is in `$(trigger_text)` |
| Receive Link dataframe *(trigger)* | Starts a stack when values arrive, and puts them in placeholders or variables |
| Link speaker *(add-on)* | Chooses which Links the send boxes use |

The other room must be loaded (someone is in it) to receive. A message can travel at most 3 hops, and a room can send about 10 messages per second.

### Chests: wired storage

Chests store coins or furni for your wired games.

| Chest | Holds |
| --- | --- |
| Coin chest | 5,000 coins, upgradable up to 100,000 |
| Furni chest | 1,000 furni, upgradable up to 10,000 |
| Starter furni chest | 100 furni |

In a chest's settings you choose its name, who may open it or donate, and whether it locks automatically when you leave. If you delete a chest, its contents go back to you.

| Box | What it does |
| --- | --- |
| Give currency from chest | Pays users coins from a coin chest. Never more than the chest holds |
| Give furni from chest | Gives users furni from a furni chest: random, oldest or newest first |
| Chest has items *(condition)* | Checks that a chest holds enough |
| Chest has item type *(condition)* | Checks that a chest holds a certain furni |
| Chest scanner *(selector)* | Picks room furni of a type that is in the chest |

### Contracts and transactions: buying and selling with wired

A **contract** is a furni that describes a deal. Wired runs the deal with **Initiate transaction**.

| Contract | The user... |
| --- | --- |
| Payment contract | Pays coins or furni into your chests |
| Reward contract | Receives coins or furni from your chests |
| Trade contract | Pays something and receives something, both at once or not at all |
| Custom contract | Any mix of the above, as an add-on |

Click a contract to open its editor. A payment can have up to 3 options (for example "2 chairs and 5 coins" **or** "1 sofa").

1. Put **Initiate transaction** in a stack and pick one contract.
2. When it runs, the user gets a trade window. They put in what they pay, accept, and confirm.
3. On success the trigger **Transaction completed** fires; if something is missing, **Transaction failed** fires with the reason.
4. **Cancel transaction** stops a deal that is still open.

## The wired menu and tools

### Chat commands

| Command | Opens |
| --- | --- |
| `:wf` or `:wired` | The wired menu |
| `:var` | The Variables tab |
| `:inspect` | The Inspection tab |
| `:hidewired` | Hides the wired furni in the room, so your room looks clean |

### Tabs of the wired menu

| Tab | What you find there |
| --- | --- |
| Monitor | How busy your wired is, errors (for example a stack that runs too often), and the room log |
| Variables | All variables in the room, who holds them, and their values. You can edit or delete them |
| Inspection | Click a user or furni to see all their variables. The **highlight wireds** button lights up the boxes that use that furni |
| Chests | Your chests and their contents |
| Settings | Who may look at and change your wired, the room's time zone, reload and roll back |
| Share | Copy your whole wired setup as a code, or paste a code to import someone's setup |

### The box editor

When you open a wired box, the **≡** menu at the top lets you copy the box, paste settings into another box, clear the picked furni, reset the box to its defaults, or save without closing.

### Notes for builders

The **label** furni puts a small note bubble in your room, so you can write down what a stack does. You choose its colour and who can see it.

### Room settings furni

Some furni change the room itself while they are switched on.

| Furni | When it's on |
| --- | --- |
| Invisible furni control | Hides the furni layers marked invisible |
| Area hide | Hides an area of the room |
| Hand item block | Users can't give or drop hand items |
| Queue speed | Changes how fast users walk in a queue |
| Wired disable | Switches all wired in the room off |
| Hide wired | Hides the wired furni |
| Dice disable | Users can't roll dice |
| Door kick disable | Users aren't kicked when they walk on the door |
| Chooser disable | Visitors can't use `:chooser` |

## Connecting boxes: example stacks

Each example lists the boxes you stack on **one tile**, bottom to top. Open each box, set it up, and click the furni it should use.

### 1. A welcome message

1. Trigger **User enters room**.
2. Add-on **Username placeholder**, named `name`, set to the triggering user.
3. Effect **Show message**: `Welcome $(name)!`, shown to the triggering user.

### 2. A secret door

1. Trigger **User says keyword**: `open sesame`, exact match.
2. Condition **User wears badge**: your club badge.
3. Effect **Toggle furni state**: pick the gate.
4. Effect **Toggle furni state** with a delay of 5 seconds: pick the same gate, so it closes again.

### 3. A trap tile

1. Trigger **User walks on furni**: pick the trap tile.
2. Effect **Teleport user to furni**: pick the start tile, source *triggering user*.
3. Effect **Show message** (on the same tile): `Oops, back to the start!`

### 4. Coins for every round

1. A **User variable** box named `coins`, with a value, saved permanently.
2. In your game stack, add **Change variable**: `coins` + 10 for the triggering user.
3. Add-on **Variable placeholder** named `coins`, and **Show message**: `You now have $(coins) coins`.
4. Optional: add a **Variable leaderboard** add-on to show the top players.

### 5. Two stacks talking with a signal

Stack A (on the button):

1. Trigger **User clicks furni**: pick the button.
2. Effect **Send signal**: pick the antenna, and send the triggering user along.

Stack B (somewhere else):

1. Trigger **Signal received**: pick the same antenna.
2. Effect **Give effect** to the users from the signal.

### 6. A random prize

1. Trigger **User clicks furni**: pick the prize machine.
2. Condition **User cooldown** *(extra)*: 60 seconds.
3. Add-on **Random effect**: pick 1.
4. Three effects, for example **Give reward** (badge), **Give credits** and **Show message** `Better luck next time`. One of them is picked at random each time.

### 7. Only the top 3 players win

1. Trigger **Game ends**.
2. Selector **Users with variable**: `score`.
3. Add-on **Filter users by variable**: highest first, keep 3.
4. Effect **Give reward** to the *selector* users.

### 8. A shop with a chest

1. A **coin chest**, a **furni chest** with thrones in it, and a **trade contract**: "pay 50 coins, receive 1 throne".
2. Trigger **User clicks furni** on the shop counter.
3. Effect **Initiate transaction**: pick the contract.
4. Stack with trigger **Transaction completed**: **Show message** `Thanks for your purchase!`
5. Stack with trigger **Transaction failed**: **Show message** `You don't have enough`.

## Limits, timings and troubleshooting

### Timings

| What | How long |
| --- | --- |
| One wired step (delay unit) | 0.5 seconds |
| Precise delay | Steps of 0.05 seconds |
| Longest delay | 10 seconds (the hotel can change this) |
| Teleport to furni | Lands after 1.5 seconds, or 0.5 seconds with *fast* |
| Box flash | Boxes light up when they run. A delayed effect lights up when its stack starts, not when it runs |

### Limits

| Limit | Value |
| --- | --- |
| Picked furni per box | 20 |
| Names in *Users by name* | 20 |
| Show message | 400 characters, 8 lines, at most 10 messages per 5 seconds per room |
| Call another stack | 10 calls deep |
| Signals | Each antenna once per chain |
| WIRED Link | 3 hops, about 10 sends per second |
| Execution limit add-on | 1 to 100 runs per 0.5 to 10 seconds |

If a room's wired runs far too often or too heavily, the hotel slows it down or stops it for a while. The **Monitor** tab shows when this happens and why.

### Something doesn't work?

- **Nothing happens.** Check that all boxes are on the same tile, and that the trigger is really fired (open the Monitor tab).
- **The effect runs on the wrong users or furni.** Open the effect and check its source: picked furni, triggering user, selector or signal.
- **A condition never passes.** Try the stack without it. Check *all* versus *at least one*.
- **A message doesn't show.** You may have hit the limit of 10 messages per 5 seconds, or the text is longer than 400 characters.
- **Signals don't arrive.** Check that the sender and the receiver use the same antenna.
- **A WIRED Link message doesn't arrive.** The other room must be loaded, and both Links must be on the same network.
- **Others can't see or edit your wired.** The owner sets who may inspect or modify wired in the wired menu's Settings tab.
- **Testing.** Use *Write to logs* to see in the room log which stacks ran.
