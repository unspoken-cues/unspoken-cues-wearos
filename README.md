# Unspoken Cues — Wear OS App

Wear OS companion for Samsung Galaxy Watches running Wear OS 3 or newer (API 30+).
One full-screen status color at a time, profile QR sharing, and persistent color lock.
Older Samsung watches running Tizen are not supported.

Target package: `com.unspokencues.wear`

## Status
Initial Android app. Phone-to-watch sync is implemented; watch color changes are
local until a newer phone update arrives. Two-way backend synchronization is future work.

## Open and run

1. Open this folder as a project in Android Studio. Use JDK 21 or newer and install
   Android SDK 37 (the same compile SDK as the phone project). Let Gradle sync.
2. Pair a Wear OS emulator or Samsung Galaxy Watch with your Android phone using
   the Wear OS/Galaxy Wearable setup flow. Install Google Play services on both.
3. Build/install **debug** on the watch. Install the updated phone app from
   `unspoken-cues-phone` on the paired phone. Open it, sign in, and finish onboarding.
4. Open the watch app. It receives the phone's cue, display name, profile visibility,
   and card token. It caches them for disconnected use. No Supabase credentials are
   needed in this watch project.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

The second command requires a connected Wear OS device/emulator. The debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. This repository is on `luana-wearos`.

## Using the watch

- The status screen shows the selected cue and its meaning. Scroll to reach controls
  on smaller round screens. Swiping or turning the bezel never changes status.
- **Color locked → Unlock color → choose a cue** deliberately changes the watch
  color and immediately locks it again. Back/Cancel leaves the color unchanged.
- **Share profile** works while color is locked. Scan the QR with the **Unspoken
  Cues phone app**; the existing custom link is not a public web profile. Close,
  Back, or a 30-second timeout returns to the status screen.
- Private/missing profiles cannot be shared. Change privacy/profile on the phone.
  Sign-out clears the watch when the updated snapshot reaches it. While disconnected,
  the watch can only know the last received state; privacy changes cannot revoke an
  already cached QR remotely until reconnection. Server-side access rules still apply.
- Full brightness/color while the app is active is distinct from always-on display.
  This initial version uses normal system sleep behavior; it does not force the
  screen to remain illuminated or replace the watch face.

## Phone bridge and application identity

The companion patch in the phone repository publishes schema 1 DataItems at
`/unspoken-cues/state`: `active`, `status`, `name`, `token`, `public`, `revision`, and `publisher`.
It retries while the phone app is open. Play services transfers the latest DataItem
after reconnection; the watch background listener accepts only newer revisions.
Each phone installation has a publisher generation, allowing recovery after phone
data reset even if its clock changed. Retired generations cannot restore old data.
Receiving a phone update always relocks the watch. Duplicate snapshots do not
override a locally chosen color when reopening the app.

Data Layer requires **matching application IDs and signing certificates** on both
devices ([Android documentation](https://developer.android.com/training/wearables/data/overview)).
The watch debug variant uses `com.example.unspokenqueues`, matching the current
phone project; both must use the same debug signing key. Release stays
`com.unspokencues.wear` as documented here. **Production sync is not release-ready:**
the team must coordinate a shared release application ID and signing identity
across both projects before publishing. Do not ship mismatched release IDs.

## Manual acceptance checks

- Set each of the four cues on the phone: the watch shows that exact name/meaning/color.
- Swipe in every direction and rotate the bezel while locked: cue stays unchanged;
  system Back and navigation remain available.
- Cancel unlock, then confirm unlock and choose a new cue: only confirmed selection
  changes the color, and selection relocks it. Reopen/restart: retain the cue and lock.
- Share while locked: scan the whole QR on a small round watch with another phone;
  verify the existing S.W.A.P. flow and return to the same cue after 30 seconds.
- Make the profile private, sign out, change account, disconnect/reconnect, and
  restart the watch app: no stale account is shown after a newer snapshot arrives.

Design and implementation notes: [design](docs/design.md), [plan](docs/implementation-plan.md).

## Docs
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security/secret handling: [SECURITY.md](SECURITY.md)

## Product context
- Only ONE status color fills the screen at a time — never all four together.
- Swipe-down must remain normal Wear OS system behavior; don't intercept it.
- QR times out back to the previously active status.
- Final Play identity is `com.unspokencues.wear` — do not leave a `playtest` applicationId in a release build.
