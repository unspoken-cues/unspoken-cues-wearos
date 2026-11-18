# Unspoken Cues — Wear OS App

Wear OS companion for Samsung Galaxy Watches running Wear OS 3 or newer (API 30+).
One full-screen status color at a time, profile QR sharing, and persistent color lock.
Older Samsung watches running Tizen are not supported.

Target package: `com.unspokencues.wear`

## Status
With an updated, signed-in companion phone, choosing a watch cue updates your global
profile cue. The watch shows “Waiting for phone to save” until the phone confirms the
profile write. Older phone snapshots still support local watch colors.

## Open and run

1. Open this folder as a project in Android Studio and install Android SDK 37
   (the same compile SDK as the phone project). The current daemon criteria request
   Java 25 through the toolchain resolver; see the handoff for build details. Let Gradle sync.
2. Pair a Wear OS emulator or Samsung Galaxy Watch with your Android phone using
   the Wear OS/Galaxy Wearable setup flow. Install Google Play services on both.
3. Build/install **debug** on the watch. Install the updated phone app from the
   sibling phone checkout (`../UnspokenQueues` in this workspace) on the paired phone.
   Open it, sign in, and finish onboarding.
4. Open the watch app. It receives the phone's cue, display name, profile visibility,
   and card token. It caches them for disconnected use. No Supabase credentials are
   needed in this watch project.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

The second command requires a connected Wear OS device/emulator. The debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. The original implementation plan referred
to `luana-wearos`; check your actual branch rather than assuming that historical name.

## Using the watch

- The cue face shows a large symbol, color name, and short meaning. Color is never
  the only signal: Green = check mark, Yellow = caution triangle, Red = stop octagon,
  Purple = question mark. **Options → Cue guide** explains every cue without color.
- **Options** contains sharing, color lock, and last phone update. Swiping or turning
  the bezel never changes status. Scroll when using larger text or a smaller watch.
- **Options → Color locked → Unlock color → choose a cue** deliberately changes the watch
  color and immediately locks it again. With two-way sync, **Options** shows the save
  result. Your latest pending choice survives closing the app and restarting the watch.
- **Options → Share profile** works while color is locked. Scan the QR with the **Unspoken
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
after reconnection. The watch background listener accepts newer revisions.
Each phone installation has a publisher generation, allowing recovery after phone
data reset even if its clock changed. Retired generations cannot restore old data.
The updated phone includes a session generation, committed cue revision and watch
receipt. The watch publishes its latest selection to `/unspoken-cues/cue-request`.
The phone reconciles the request on delivery, while open, and through **Sync now**.
An explicit phone edit supersedes an older pending watch choice. A pending watch
choice survives unrelated profile updates and superseded receipts. Account changes
and sign-out clear the old choice when the new snapshot arrives.

Data Layer requires **matching application IDs and signing certificates** on both
devices ([Android documentation](https://developer.android.com/training/wearables/data/overview)).
The watch debug variant uses `com.example.unspokenqueues`, matching the current
phone project; both must use the same debug signing key. The default release application ID is
`com.unspokencues.wear`. Configure a paired release with the installed phone's
application ID and signing certificate. [Paired build command](docs/design/two-way-cue-sync.md#paired-builds)

## Manual acceptance checks

- Set each of the four cues on both devices. Confirm the same cue on the watch, the phone
  and the authenticated profile row. Queue a watch choice while disconnected, reconnect,
  then use **Sync now** and check that the pending message clears.
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
- **Start here for engineering takeover:** [Wear OS handoff](docs/handoff/README.md) — source map, architecture/protocol, setup, testing, limitations, and troubleshooting; links to the phone/backend handoff.
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security/secret handling: [SECURITY.md](SECURITY.md)

## Product context
- Only ONE status color fills the screen at a time — never all four together.
- Swipe-down must remain normal Wear OS system behavior; don't intercept it.
- QR times out back to the previously active status.
- Final Play identity is `com.unspokencues.wear` — do not leave a `playtest` applicationId in a release build.
