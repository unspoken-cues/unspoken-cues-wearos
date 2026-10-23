# Initial Galaxy Watch companion

The phone remains the account/profile editor. The watch shows one of the same four
statuses and the exact same QR card link. The initial app runs on Wear OS 3+ (API 30+).

## Behavior
- Full-screen cue with its name and meaning; no swipe or rotary status cycling.
- Start color-locked. Unlock requires a separate confirmation screen; choosing a
  new color locks it again. Status and lock persist across process restarts.
- A deliberate phone update can replace the watch cue even while locally locked.
- Share stays available while locked, uses a square black/white QR with four-module
  quiet zone, and returns to status after 30 seconds or Back.
- No fake profile: before sync, explain how to connect. Private/missing profiles
  cannot be shared. A signed-out phone clears identity and status on the watch.
- Cache synced data for disconnected use; display when it was last received.

## Boundaries
WatchState is pure Kotlin state logic. WatchStore persists a validated snapshot and
lock. WatchSyncService receives phone DataItems even with the UI closed. Compose UI
is separate from QR generation. A small phone publisher delivers a versioned DataItem
when authenticated profile state changes. No account credentials cross the bridge.
Confirmed account transitions clear the previous identity even if the new profile
fails to load. Publisher generations recover from reinstall/clock correction;
retired generations cannot restore stale data.

The initial bridge is phone-to-watch. Watch color selection affects the wearable
only; a subsequent phone snapshot replaces it. Two-way backend writes are deferred
to avoid inventing conflict handling or weakening phone account ownership.

For development only, the watch debug applicationId matches the phone's existing
com.example.unspokenqueues. Release identity stays com.unspokencues.wear as required
by this repository. Production Data Layer pairing requires a coordinated matching
phone/watch applicationId and signing identity before release.

## Validation
JVM tests cover locked edits, unlock confirmation, auto-lock, profile validation,
privacy/account clearing, snapshot validation and QR decode. Compose device tests
cover swipe protection, sharing while locked, deliberate unlock and QR timeout.
Build and lint both touched projects. Round-watch scan and ambient checks require
a paired Wear OS emulator or Galaxy Watch; full-color always-on is not promised.
