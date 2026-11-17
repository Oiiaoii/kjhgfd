# LuminFish 2.4b - simple sign up / log in (no Discord, no Roblox)
- Sign-in with Discord / Roblox is gone. Sign up = username + password, Log in = the same. Kept on the phone, only a salted hash is saved.
- New sign-in screen: drifting colour glow, floating logo, sliding tabs, focus-glow fields, Show/Hide password, shake on errors.
- Blacklist now blocks by USERNAME. Owner = the reserved owner_username in auth.xml, only usable with the owner password (hash in owner_hash,
  BLACKLIST_SETUP.md). Settings rows are now soft gradient tiles. Old Discord/Roblox sessions are signed out once.
- Not compiled / not run here.

# LuminFish 2.4 - tap timing check, owner-only blacklist hardened, new look

## Tap timing (checked, one real fix)
Checked Tapper.kt, EdgeTapper.kt and the engine loop in MacroService.kt, and re-ran every simulation (edge, excursion, spam guard, drift).
The timing logic itself is sound in simulation (edge tapper 94-99% of the time in the zone). One real problem was found:
- **The tap brake cut the opening taps short.** The edge tapper sends up to 12 quick catch-up taps per second (80 ms apart) when the fill is
  below the zone (start of a reel, zone jumps away). The brake allowed only 10 per second, so about 50-80 taps per 30 s were held back and the
  fill arrived late. Brake is now 13 while the edge tapper runs (still 10 for the other tap modes). tests/brake_test.py:
  zone moving 120 px/s 89.5% -> 91.7% in the zone, delay 100 ms 79.1% -> 81.1%, steady zone 93.7% -> 93.8%, taps held back 50-80 -> 0.
- The edge tapper now accepts a measured tap delay up to 140 ms (it was cut at 110 ms while the ledger measures up to 140).
Not checkable here: the real game and your phone's real tap delay. After a reel the Debug log prints the real in-zone % and delay.

## Blacklist (owner only)
It already existed in 2.3; what was missing or wrong:
- **Nobody was owner** because auth.xml had no IDs. New: tap your account picture - your own ID is shown, tap it to copy, paste it into
  `owner_discord_id` or `owner_roblox_id` in app/src/main/res/values/auth.xml, rebuild. Only that account sees Blacklist.
- **A running macro was never stopped.** Now the macro checks the list every 2 s and stops (and shows the blocked screen) when the account
  is listed; the list is re-downloaded every 5 minutes while the macro runs.
- The Blacklist screen closes itself if the signed-in account is not the owner (also when coming back to it).
- Publish: plain Java refuses the PATCH method; the app now falls back to POST + X-HTTP-Method-Override. Not tested against GitHub. If
  Publish fails, Copy JSON and paste it into the Gist yourself.

## Design
- Page background: deep blue glow fading to black. Header line: glowing gradient.
- Cards: gradient fill, soft border, elevation, glowing divider under every title. Icon chips tinted in their own colour.
- Buttons: gradient primary / green / red, glass secondary, lifted. Stat tiles: gradient with a tinted icon chip, bigger numbers.
- Menu: gradient panel and gradient selection bar. Account sheet: gradient, owner gold frame, tap-to-copy ID.
- Home: "Welcome back, NAME" banner with a floating emblem (gold line for the owner). Panel cards also use the gradient.
Not compiled and not run on a phone (no Android SDK here): the GitHub build is the first compile.


<!-- ===== UPDATE_2.3 ===== -->
# SIGN-IN REQUIRED + BLACKLIST (latest)
- **Guest mode is removed.** Without a signed-in Discord or Roblox account the main screen does not open and the macro does not start
  (an old "guest" state from an earlier build does not count).
- **Sign-in buttons** are now just the brand mark and the name (Discord / Roblox), no "Sign up with ..." sentence, and no OAuth wording anywhere in the
  app. Sign up and Log in tabs stay. Discord and Roblox both use the real browser OAuth flow (SIGNIN_SETUP.md); the sign-in setup box only shows when
  nothing is configured (long-press the logo to open it later). The marks are simple drawings, not the official logos.
- **Blacklist, owner only** (BLACKLIST_SETUP.md): a list file you host (Gist). The app downloads it at sign-up / log-in, at app start and before the macro
  starts; a blocked account gets an animated "Access blocked" screen (lock drops in, snaps shut, shakes, red ring, Check again / Sign out).
  Only the owner (the Discord / Roblox ID in auth.xml, proven by signing in) sees the Blacklist screen: switch Discord / Roblox, add by user ID with name
  and reason, remove with a slide-out animation, Copy JSON, Publish to the Gist, Refresh. The owner can not be blacklisted.
  Animations: sheet pops, rows rise in, added rows pop, removed rows slide away and collapse, owner badge breathes, gold ring on the owner's picture.
- Limits: no server of ours, so a patched APK can skip the check; a ban follows the Discord / Roblox ID, not the person; not tested against GitHub;
  a macro that is already running is blocked at its next start. tests/blacklist_test.py checks the list rules (13 cases pass).

# FIXES AFTER REAL TESTING (read this first)
You reported: tapping spams and never stops; after 1 minigame it stops; the X button does not click; and you do not want the AI.
What I found in the code, and what changed:
1. **Endless spam (cause found).** When the blue fill was not read, the old engine assumed "fill far below the zone" and tapped at the fastest rate for as
   long as the bar showed. My own colour-balance step (added for dim screens) made it worse: in the tight box around the bar there are no white pixels, so it
   turned the left half of the fill white and the fill was no longer recognised (tests/roi_test.py reproduces it on your 3rd picture). **Removed.**
   New rules: no fill seen = climb only for the first 2.5 s of a reel, then one probing tap per second and a log line; **tap brake** = never more than
   10 taps in one second; **no-lift check** = 14+ taps in 2.5 s without the fill rising (taps not reaching the game) pauses taps, and 3 times in a row
   stops the macro with a message (tests/spam_guard_sim.py: unreadable fill 218 -> 35 taps in 20 s; taps doing nothing: stops after 10 s).
2. **AI is off.** The AI autopilot (it changed aim, timing and recovery) is off by default and switched off once on update; what it learned is erased. Taps,
   timing and the result panel now come from the screen detection only. (Settings still has the AI switch if you ever want it back.)
3. **X button / macro stopping after one minigame.** Before, the macro waited with timers, then tapped a fixed spot. Now the result panel is driven by the screen:
   it watches for the X (picture recognition, white strokes, tested on your 4th and 5th pictures), taps it where it is, keeps looking and taps again (up to 5 times)
   until the X is gone, and waits up to 6 s longer for it before using the saved Close point. It also works WITHOUT fast capture on Android 11+ (screenshots).
   The same is true for the Fish / Exit button when casting.
4. **Test button**: Settings > Detection (app) or the panel: "Test buttons (Fish / Exit / X)" shows what the screen shows right now (match % and position for each
   button) and saves the screen picture to Android/data/com.solmacro/files/debug. If the X still does not click, press it on the result panel and send me
   the Debug log lines ("X button seen ...", "X button not recognised (best match NN%)") and that picture.
Honest limits: not compiled, not run on a phone. The tests cover your 5 pictures and the logic of the guards, not your phone's real frames.
The colour-balance claims below about the bar (dim screens / night filter) are RETRACTED for the bar reading: only plain colours (dim 60% still tested OK) are used now.

# LuminFish 2.3 - picture recognition of the fishing buttons, any zone colour, tap circles

Built on 2.2: nothing was removed or replaced. The tap ledger engine (Tapper.kt) is unchanged.

## BUG FIXES in this build
**Tap spam after 1-2 minigames (found and fixed).** The 2.2 ledger learned "how far one tap lifts the fill" during a reel. When the
fill rises smoothly (an animation) instead of in one jump, every credited tap pulled that estimate down a little. In my simulation it
fell from 58 px to about 26 px in 25 s, so each tap looked weak and the macro tapped more and more, and the value was then SAVED
(Ai.savePhys) and used as the start of the next reel. Fixes: (1) Tapper.kt keeps learned lift/drain within 0.8-1.25x / 0.75-1.3x of the
start values, (2) Ai.kt only saves/loads lift and drain within +-15% of the measured nominal values, so an already drifted saved value
is pulled back at the next start, (3) the new edge tapper (below) does not learn while reeling at all.

**Script-like tapping (new, default: "One tap at zone edge").** The blue fill reaches the LEFT edge of the green zone -> exactly one
tap; it falls back to the edge -> one more tap. If the zone moves away, a few quick catch-up taps (about 90 ms apart) until the fill is
back, then single taps again. Only the start of a reel (fill far left of the zone) is a short burst. The zone colour does not matter
(any-colour zone, section 2). Simulation (tests/edge_sim.py, smooth lift, 3 seeds x 40 s, % of time the fill is in the zone):
v2.2 ledger 74.0 / moving zone 78.4 / 90 ms delay 63.5 / zone 60 px 60.9  ->  Edge 99.0 / 97.7 / 94.1 / 94.6, about 4 taps per second.
It stays at 95-96% even when its lift or drain numbers are wrong by -50% / +40% / -30%. No tapper can GUARANTEE 100%: that depends on the
real game's lift, delay and capture speed, so the Debug log prints the real in-zone percentage after every reel.

**X button did not work.** The X is now found by its WHITE strokes only (the box is see-through, so a picture of the X with its
background did not match on other backgrounds). Tested on both result screenshots, at 1.0x, 0.5x, 0.3x and 0.2x size: score 0.82-0.97,
other screens at most 0.61 (limit 0.80). If it is still not found, the log says "X button not recognised (best match NN%)", a picture of
the screen is saved to Android/data/com.solmacro/files/debug/ and the saved Close point is used. If the X is seen the log says
"X button seen (NN%) at x,y". Send me those lines or that picture and I can fix the exact cause.

## Also added
- **Cycle log**: after every fish "Cycle: bite N ms, reel N ms, result N ms, in the zone N%".
- **Learn my buttons**: the first strong match of the Fish / Exit button is saved from your own screen and used as an extra picture.
- **Save pictures when unsure**: Fish/Exit/X not recognised -> screen picture saved (max 1 per 25 s, 15 files).
- **5 failed reels in a row** -> webhook message (and the log), so you notice a stuck setup.
- Not added: auto-sell by inventory fullness (I do not know what the game shows for it; send a picture of the inventory if you want it).

## TAP RULES CHECKED (zone moves left / right, "does it stop after one tap?")
Simulated with tests/excursion_sim.py: the zone jumps 160 px left, 380 px right, 260 px left, then drifts; smooth lift; delay 45-140 ms; 3-50% lost taps.
- **No stall after the first tap**: 27-28 taps in the first 3 s, then about 4.6 taps/s; the longest time the fill was below the zone without a tap was
  110-160 ms (300 ms with the old rule).
- **Fill ABOVE the zone (zone moved left): 0 taps** in every case until it drained back. **Fill BELOW the zone (zone moved right): quick taps
  (about every 90 ms) until the fill is in the zone**, then one tap per arrival at the left edge. (Before, the quick taps stopped half a lift
  early; now they run until the predicted fill is inside the zone.)
- Watchdog added: clearly below the zone and 260 ms without a tap -> tap again (it never fired in the simulation, it only protects a lost tap).
- The delay it assumes now comes from the ledger's live measurement once it has 6 samples. Measured effect: real delay 90 ms 79.8% -> 84.0% in the
  zone, 140 ms 73.2% -> 79.3% (45 ms: 86.2% -> 85.2%).
- What I could NOT check: the real game and your phone's real tap delay. The one real-device way to stall is the bar not being read for more than
  0.3 s (no picture of the fill = no taps, that is by design): the Debug log shows "marker hidden" / Click recovery lines then.

## LOG IN + ANIMATIONS (new)
- The sign-in screen now has two tabs: **Sign up** (new account) and **Log in** (you already have one). Both use Discord or Roblox.
  - Sign up creates a LuminFish account on this phone. Doing it again with the same Discord / Roblox just logs you in.
  - Log in needs an account that was signed up on this phone; if there is none for that Discord / Roblox it says so and moves you to Sign up.
  - Log in also lists your saved accounts (picture + name): one tap and you are in, no browser. The ✕ removes an account from the phone.
  - Sign out keeps the account saved, so you can log in again. "Remove account" in the account menu (tap the picture) deletes it.
  - Accounts are kept on this phone only (there is no server). They are not a password login: the provider's own sign-in proves who you are.
- Animations (all switchable: Settings > Detection > "Animations", they also stop when Android's animation scale is 0 / battery saver):
  - sign-in screen: slowly drifting colour glow background, logo pops in then floats with a spreading ring, title, tabs, card and buttons rise in one
    after another, the tab highlight slides, texts fade-swap between Sign up and Log in, saved accounts slide in, wrong input shakes the card, success shows
    a check and the screen lifts away into the main screen; press feedback on every button.
  - main screen: account picture pops in; screens fade between sign-in and main.
  - tap circles: the ring now spreads while it fades; auto-clicker circles pop in one after another.
  - already in 2.2 and unchanged: menu slide, page stagger, button press, counters bump, toggles.
- Not animated: system dialogs (Android's own), and the overlay panel's contents beyond what 2.2 already had.

## SIGN IN (new): Discord or Roblox
- First start shows a sign-in screen: **Continue with Discord**, **Continue with Roblox**, or **Continue as guest**. After that the main
  screen has an account picture (top right, your avatar or your initial); tap it to see the account, sign out, or switch.
- Real OAuth 2.0 + PKCE in the browser (Auth.kt, LoginActivity.kt). Needs the public client ID of a Discord and a Roblox app: see
  SIGNIN_SETUP.md (and docs/auth.html, the https bounce page for Roblox if it does not take `luminfish://auth`).
- Only the public name and picture are read; no password is typed in the app, no secret and no token are stored.
- NOT tested against the real servers (no network here), so the first sign-in is the real test. It does not lock anything on a server.

## SCREEN DETECTION: what was improved, and what the tests showed
Tests in tests/ (python3 + opencv-python + numpy), run on your screenshots and on changed copies of them:
1. **Dim screens and night / blue-light filters broke the zone detection** (tests/robust_test.py). With 2.3's first fixed colour floors the
   any-colour zone was half-lost at 70% brightness and fully lost at 50% and under any warm filter. Now: colour balance (the
   channels are scaled to the same white level) + brightness floors that follow the picture's own white level. Result: zone found at
   the same place at 70 / 50 / 40% brightness, a warm filter and a strong warm filter, blur, JPEG q40 and half size.
2. **Blue fill in any shade** (light blue to violet) is added to your picked fill colour, so a gradient fill still reads.
3. **Other screen layouts** (tests/layout_test.py): the old search assumed a landscape screen with the bar about 0.77 x the screen
   height wide. A third search pass now finds the bar anywhere (checks that the row is a bar: dark track / blue fill / zone fill a
   width at least 3.5x the zone). Portrait 1080x2400 (two bar sizes): not found before, found now; landscape and tablet cases unchanged.
   Thin lines (bar outline, text strokes) are removed first so they can not glue to the zone.
4. **Fish / Exit buttons, fine size search** (tests/scene_robust_test.py): the 14% steps of the size search lost up to 20 points of score on
   text (Fish 0.71 at one size), now a fine +-8% search follows. The Exit button at 25% screenshot size was missed before, found now.
5. **The X under a dim screen or night filter** was missed (threshold fixed at white 215); the X search now uses the same colour balance
   and a limit that follows the picture's white level. All 5 screenshots x 7 changes (plain, dim 50%, night filter, blur, JPEG q40,
   0.4x, 0.25x) are classified correctly (35 of 35); before: 30 of 35.
6. The sizes that worked are remembered between app starts (first look is fast).
Not changed: the tap ledger, capture speed code, your picked colours (they are still tried first, colour balance only helps the any-colour part).
Honest limits: tests use your 5 screenshots (re-colored / resized copies), not a live game; the Kotlin is not compiled; a blue or violet zone
(hue 95-140) is still not told apart from the fill; the relaxed layout pass needs a bar whose row is dark track + fill + zone.

## 1. The fishing buttons are recognised on the screen (new Scene.kt, pictures in assets/scene)
- **"Fish" button** seen -> it is tapped where it really is (no fixed Cast point needed).
- **"Exit" button** seen -> fishing is running, the macro waits for the bar and does NOT tap there (that would leave the fishing).
  If the bar never came (Bar not found), it taps the real Exit button once to leave, then casts again.
- **Bar mini-game** -> read by the existing OpenCV scan + tap ledger.
- **"Fishing Failed" / "Fish Caught!"** -> the X in the top-right corner of that box is found and tapped.
- If a button is not recognised, the old saved Cast / Close points are used, so nothing gets worse.
- Settings > Detection > "Find the Fish / Exit / X buttons on the screen" (panel: "Find buttons on screen"). Needs fast capture.
- Matching is done at many sizes and remembers the size that worked. Check Debug log lines: "Fish button seen (NN%)".

## 2. Any zone colour (Cv.kt)
The zone can change colour, and in the pictures it is made of two shades (teal + green). With one picked colour only one
shade was found, so the zone centre was off. Now any bright coloured area that is not the blue fill and not white text counts
as zone (green, teal, yellow, orange, red, pink, purple; two shades merge). Whole-screen search tries your picked colour first,
then any colour, and only accepts a blob whose row really looks like the bar (blue fill / dark track / zone along its width).
Setting: "Any zone colour" (on by default). A zone that is blue or violet (hue 95-140) is still not covered.

## 3. Small circles where it taps
- **Auto clickers**: every draggable circle now has a small black-and-white dot in its exact middle = the spot that gets tapped.
- **While running**: a short-lived small circle appears at every tap (Settings: "Show small circles where the macro taps").

## Tapping accuracy
The 2.2 tap ledger already compensates delay, lift and drain and follows a moving zone, so it was not replaced. What improves its
accuracy in 2.3 is the correct zone centre (both shades, any colour).

## Tested here
tests/excursion_sim.py, tests/scene_test.py, tests/xbtn_test.py, tests/edge_sim.py, tests/kt_tapper.py, tests/drift_test.py (python3 + opencv-python + numpy; images in tests/fixtures): Fish / Exit / mini-game / Failed / Caught screenshots are
all classified correctly, and the zone is found at the same place after recolouring it orange, yellow, red, pink, purple and cyan.
NOT compiled and NOT run on a phone (no Android SDK here). The GitHub build is the first compile: send the error line if it fails.
Template matching sizes were not measured on a real phone screen; the screenshots you sent are crops, so the first run needs a look at the Debug log.

<!-- ===== UPDATE_2.2 ===== -->
# LuminFish 2.2 - precision tapping

## What changed in the tapping
- **Tap ledger (new, on by default: Settings > Precision taps).** Every tap is entered in a ledger and counts as "in flight"
  until a picture of the screen shows its lift. The v2.0 engine assumed a tap had landed after a fixed 35 ms; on a slower
  phone the lift was still missing from the picture, the marker looked too low and an extra tap went out.
- The tap-to-game delay, the lift of one tap and the drain speed are measured live from the pictures and remembered for the
  next reel (Stats > Tapping shows them).
- A tap that Android cancels or refuses leaves the ledger immediately, so it is not counted as a lift.
- The zone is followed ahead of where it was seen (speed over the last 100-260 ms), so a moving zone is tracked.
- Needs fast capture. Without it (screenshots) the older timed engine is used. The old engine is still there (Timed tapping).

## Detection
- OpenCV box scan: once the bar is found, only a small box around it is read. The box goes from the ImageReader buffer
  straight into an OpenCV Mat (one copy per row), is converted to HSV and matched by colour (zone and blue fill) with
  OpenCV, then the columns are counted. No per-pixel Kotlin loop. The built-in scan is only a fallback.
- The marker position has decimals (the two columns at the edge of the blue fill are counted partly).
- Capture is unchanged: MediaProjection, 3-buffer ImageReader on GPU-friendly hardware buffers, frames taken the moment they
  arrive with Android's time stamp.
- GPU note: the screen is composited and captured by the GPU. The OpenCV step runs on the CPU (NEON) because the box is tiny;
  uploading it to a GPU would take longer than the scan.

## Tested here (desktop simulator, tests/reel_sim.py + sim6.py; 80 px zone, 5 runs x 40 s, % of time the marker is in the zone)
| case                                   | v2.0 timed | v2.2 ledger |
|----------------------------------------|-----------:|------------:|
| exact numbers                          | 98.8       | 99.9        |
| zone moving 120 px/s                   | 92.1       | 98.8        |
| tap delay 80 ms instead of 35 ms       | 91.6       | 99.9        |
| lift +18 %, drain -15 % off            | 88.4       | 99.8        |
| 20 fps capture                         | 96.8       | 99.7        |
| delay, lift/drain off and moving zone  | 68.6       | 99.5        |
| 15 % of taps cancelled by Android      | 90.5       | 98.6        |
The simulator is my model of the bar (one tap = +58 px after a delay, drain 215 px/s), not the real game. It shows the
logic is sound and much less sensitive to wrong guesses; it does not prove the numbers on your phone.

Not compiled or run on a phone here (no Android SDK). The GitHub build is the first compile: send the error line if it fails.

<!-- ===== UPDATE_2.1 ===== -->
# LuminFish 2.1

- App name is now spelled LuminFish everywhere (launcher, app header, panel, notification, Discord webhook name).
- New icon: your LuminFish logo is the launcher icon (adaptive, sits inside the safe zone), the floating button, the app header and the big start emblem.
- App screen: the permanent icon column is gone. Tap the menu button (top left) and the section list slides in as a panel with icons and names; tap a section or outside the panel to close it. Back also closes it.
- App header is centered (menu button | logo + name | empty slot of the same width) and pages have equal left/right margins.
- Floating panel: portrait panel is taller and the start emblem resizes to the free height, so Tap to Start, the buttons and the Positions / Capture / Roblox chips are no longer cut off.

Not compiled here (no Android SDK). The GitHub build is the first compile: send the error line if it fails.

<!-- ===== UPDATE_2.0 ===== -->
# LuminFish 2.0 (was Sol Macro / Brry)

## Design (features unchanged)
- New night-blue LuminFish look in the panel AND the app: glowing animated border, twinkling stars, drifting glow, emblem start button.
- Panel is now 16:9 and smaller. App > Look > Panel size: Small / Normal / Big.
- App > Look > Panel background: pick a photo or GIF (GIFs move on Android 9+), and how dark it is.
- App icon + floating icon = LuminFish emblem. Floating icon: breathing glow, pulse rings, orbiting spark, bobbing, green spinning ring while running, amber when paused.
- Panel closes by itself when Roblox is closed or left.
- Credits: creator's Roblox avatar loaded from the internet (shows the logo offline).

## Bugs
- Cast: the panel was still on screen for 450 ms when Start was pressed, so the first Cast tap hit the panel. Now removed instantly, first cast waits 0.28 s.
- Cast / X / Sell taps are re-sent up to 3x when Android cancels or refuses them; anti-AFK no longer interrupts a running press.
- Clicker circles: Android could shift the overlay window (camera notch, bars) so the saved spot differed from the circle. Now the real on-screen position is measured, corrected and saved.

## Detection
- ImageReader: 3 buffers, GPU-friendly hardware buffers on Android 10+, capture fps meter.
- OpenCV: bulk Mat load (no per-pixel loop), Mats reused between scans.
- Adaptive speed (panel toggle): scan detail follows capture fps, scan time and hit rate. ROI box grows before falling back to a full scan.
- Stats page shows capture fps and scan detail.

Not compiled or run on a phone here (no Android SDK). The GitHub build is the first compile: send the error line if it fails.

## Timed tapping (new)
- Cause of the spam: when the blue fill was not visible the engine assumed the marker was far below the zone and tapped at the maximum rate, and the proportional gain (x8) also saturated the rate on small errors.
- New engine: a tap goes out only when the predicted marker (with tap latency) is about to fall under a trigger line; steady state is one tap every gain/drain (about 270 ms). Spacing, a flood guard and an emergency catch-up stop runaway tapping.
- Marker hidden: dead-reckons from the last real position for 0.7 s, then climbs at 2x for the first 1.5 s or keeps time at the steady interval.
- Settings: Timed tapping (on by default, off = old engine) and Tap precision Normal / High / Max (panel and app Detection). Stats shows the current tap interval.

<!-- ===== UPDATE_1.9 ===== -->
# Sol Macro 1.9

## Screen detection only (no screenshots)
- The macro now needs the **live screen capture** (the "Screen capture" switch). Start is refused with a clear message when it is off,
  and the panel sends you straight to the permission screen.
- The reeling bar is read only from the live capture. The slow screenshot fallback is gone (also for calibration and the biome reader).
- If the capture is turned off while fishing, the macro stops cleanly, tells you (and the webhook) and opens the capture screen.

## New
- **Smart auto-fishing**: learns the bite time (min / average / max), scans less often until the earliest bite ever seen (saves battery),
  all reel and result timing comes from the screen (v1.8 detection) instead of fixed waits.
- **Click recovery**: 8 cancelled or refused taps in a row, an open panel blocking the screen for 8 s, or no tap for 1.2 s while the marker
  is low -> clears the blockers, gives taps more room, restarts the tap engine and keeps reeling.
- **Anti-freeze**: the screen did not change at all for N seconds (default 30), or nothing progressed for 3x that -> step 1 clear panels + camera
  nudge + recast, step 2 stronger nudge, step 3 rejoin (if rejoin is on) or stop. Webhook message on every step.
- **Catch verification**: a catch counts only after the result panel was really seen. A reel with no panel is listed as "Not verified" and not counted.
  If the panel is never seen on a phone (4 reels in a row at the start) verification switches itself off so catches are not lost.
- **Customizable auto-sell**: sell every N catches, every N minutes, or whichever comes first; choose whether Confirm is tapped. (Settings > Selling, and the panel.)
- **Fishing statistics**: successful catches, failed attempts, not verified, success rate, fishing time (pauses not counted), catches per hour,
  time per catch, bite min/avg/max, sells, recoveries. This session and all time (saved), with a reset button. App home screen and panel Stats.
- Keeps the screen on while the macro runs.

## Tested here (desktop mirror of the rules, tests/logic_tests.py + tests/run_tests.py)
auto-sell triggers, catch verification states, freeze detection (frozen / live / lone clock), click-recovery streaks, bar detector (100 synthetic frames).

## Not tested here
Not compiled and not run on a phone (no Android SDK or internet in the sandbox). The GitHub build is the first compile; send the error line if it fails.
The result-panel and freeze thresholds are tuned on synthetic frames, not on real Sol's RNG.

<!-- ===== UPDATE_1.8 ===== -->
# Sol Macro 1.8

## Fixed
- **Auto click did nothing after "Tap to Start".** The control panel stayed open in the middle of the screen and caught
  the taps meant for Roblox (and covered the reeling bar in the capture). Start now closes the panel by itself.
- **OpenCV detection was switched off** (Cv.kt was an empty stub, the "OpenCV detect" toggle did nothing). Cv.kt is now a real
  OpenCV detector (HSV colour match, shape check, bar memory). If OpenCV cannot load or finds nothing, the old scanner still runs.
  Panel > Stats shows "Detect  OpenCV / Built-in".
- **Blind clicking.** The tap engine kept tapping up to 0.8 s after it stopped seeing the bar. It now taps only while the bar was seen in the last 0.3 s.

## New
- **Show auto clickers** (Macro page button, Settings, Macro Settings switch, and the app's Button positions card).
  Circles appear for Cast, Reel, Inv, Sell, OK, Move, Cam, Shift and X. Drag them onto your buttons, tap DONE.
  Positions save automatically. They hide when the macro starts so they never block a tap.
- **Result panel timed by the screen, not by a timer.** After the bar is gone the macro compares the screen with the picture taken
  while the bar showed, waits until the caught/failed panel has slid in and stopped, taps X, then waits until the panel is really gone
  (and taps X again only if it is still there) before it casts. If no panel is ever seen it falls back to the old waits.

## Tested here
- Detector (same algorithm as Cv.kt) on 100 synthetic frames with brightness changes, noise and green/blue decoys: 0 missed, tip error <= 4 px,
  0 false bars. See tests/run_tests.py.
- Result-panel timing simulated: X tapped about 100 ms after the panel settles, cast again about 165 ms later.

## Not tested here
- Not compiled and not run on a phone (no Android SDK or internet in the sandbox). The GitHub build is the first compile.
  If it fails, send the error line. Real Sol's RNG colours and the real result panel were not available, only synthetic frames.

<!-- ===== UPDATE_1.7 ===== -->
# Sol Macro 1.7 (UI overhaul on top of 1.6)

## What changed
- Emoji icons are gone. The menu, cards and buttons use real vector icons (res/drawable/ic_*.xml, Material-style paths, tinted in code).
- The Panel check list no longer uses the check / cross / bullet characters, it draws icons too.
- The blue bar on top is gone: new dark theme (res/values/styles.xml, set in AndroidManifest.xml) and the status bar and navigation bar are painted the app's black.
- Cranked text fixed: the app clamps the phone's font size to 100% and all text is sized in dp (app screen, floating panel, position picker prompt), so it no longer grows or gets cut off.
- Buttons rebuilt: 48dp minimum height, icon + text, no clipped labels ("Turn on service" fits).
- Home screen: status pill with dot, 4 stat tiles (Catches, Failed, Sells, Run time), tidy capture/taps/checks/latency/bite/AI aim grid, button positions as a list with a clear "not set yet" warning, numbered How to use steps.
- Switch rows, focus-highlighted input boxes, card icons, boxed log and monitor, keyboard no longer covers fields (adjustResize).
- Screen refresh only rewrites text that changed (less flicker, log text stays selectable).
- Version 1.7 (versionCode 6).

## Files changed
app/build.gradle.kts, AndroidManifest.xml, res/values/styles.xml (new), res/drawable/ic_*.xml (new, 27 icons),
MainActivity.kt (rewritten UI), Panel.kt (dp text, check list icons, version), MacroService.kt (dp text in two overlay prompts).
Everything else is unchanged.

## Honest status
Not compiled or run on a phone here (no Android SDK in this sandbox). The GitHub build is the first compile. If it fails, open the build-reports artifact or send me the error line.

## Added after 1.7 first build
- Home: "Turn off service" button next to "Turn on service" (stops the macro, closes the panel, switches the accessibility service off). "Open Roblox" moved to its own full-width button below.
- Brry panel > Credits: tappable green text "Follow: chromeplated12301 for more update" that opens the Roblox profile link.
