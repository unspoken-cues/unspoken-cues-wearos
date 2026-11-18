# Two-way cue status sync

A signed-in participant must see the same committed global cue on the phone, the paired watch and their Supabase profile. The phone owns the authenticated write. Event-specific overrides remain separate. Keep the existing privacy-filtered snapshot compatible with older companion versions.

## Interaction and transport

A local watch cue selection records the latest pending choice before sending it. Associate it with an opaque phone/account generation, a base revision and an operation identity. The normal watch selection, reconnect, and the phone’s existing Sync now action must all reach the reconciliation path. A pending operation survives a process restart. Transport only the cue and coordination metadata. Sign-out or account replacement invalidates the old generation even if the new account has identical visible fields. Legacy or malformed input fails closed.

Reuse Wear Data Layer and the signed-in phone’s profile repository. Serialize local phone edits and watch requests, or use an equivalent conditional update. A fresh explicit phone choice supersedes an older watch edit. A retry after a lost receipt must converge without reverting a newer choice. Acknowledge a watch edit after Supabase confirms it. On backend failure preserve a retryable pending state and show truthful feedback. A current authoritative snapshot restores a stale watch. Sync now must actually reconcile pending input and committed status, including unchanged cues. Reflect the result in the phone UI and republish to the watch. Document disconnected/background behavior and the package/signing requirements.

## Acceptance tests

- S1. Each valid watch cue goes through the phone to the authenticated profile, then updates the phone UI and watch receipt. A phone edit follows the other direction.
- S2. A pending offline choice survives restart. Reconnect or Sync now retries. Network failure cannot produce a success indication.
- S3. Duplicate requests, delayed receipts and stale base revisions cannot overwrite a newer phone or watch choice. A same-value retry terminates.
- S4. Sign-out, account switch and malformed payloads never update another account. No credentials or profile identifiers travel to the watch.
- S5. Old snapshot consumers still render existing cues and sharing privacy remains intact. UI distinguishes connection from successful reconciliation.
- S6. Run both debug builds, unit suites, Android test compilation and lint. Exercise real entry points with injected backend/transport in integration tests or connected emulators. Record commands, outcomes and any physical pairing or authenticated server verification still required.

## Paired builds

Install the phone APK on the phone and the Wear APK on the watch. Both debug variants already use the same application ID. For a paired release using the current phone ID, build the watch with `sh gradlew :app:assembleRelease -PcompanionApplicationId=com.example.unspokenqueues`. Sign both artifacts with the same release certificate. An existing store listing keeps its original package identity. A release with a different ID from the installed phone cannot use this transport.

## Watch verification

On November 18, 2026, `bash gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug --max-workers=2` passed using Android Studio's JDK and SDK 37. All 16 JVM tests passed. Lint reported 13 warnings and zero errors. The device suite `com.unspokencues.wear.WatchCueSyncTest` drives the normal picker, durable store, serialized Data Layer payloads and receipts using an injected transport. The remaining physical checks cover paired delivery, signed-in Supabase writes and matching release signatures.

## Device verification

All three WatchCueSyncTest cases and all four existing WatchAppTest cases passed on the API 37 Wear emulator with Espresso 3.7.0. The tests exercised the real cue picker, pending store, DataMap and receipt handling using an injected transport. A physical paired-device run remains separate.
