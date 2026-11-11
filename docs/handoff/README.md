# Wear OS engineering handoff

Reviewed 2026-11-11 against watch commit `7cbdc31` on `feature/glanceable-accessible-cues` plus the existing local changes. This describes the current working tree, not verified production deployment state.

Read [architecture and protocol](architecture.md), then [setup, testing and troubleshooting](testing.md). The phone repository contains the broader [system handoff](../../../UnspokenQueues/docs/handoff/README.md), [backend schema/API documentation](../../../UnspokenQueues/docs/handoff/database.md) and [ownership checklist](../../../UnspokenQueues/docs/handoff/known-issues.md). Cross-repository links assume sibling checkout names shown here; this companion guide is otherwise self-contained.

## Purpose and boundaries

The watch displays one Unspoken Cues status with a distinct symbol, name and meaning. A paired phone supplies cue, display name and a shareable card token through Google Play services Data Layer. The watch persists its last accepted state for disconnected use. Options contains profile QR sharing, deliberate color unlocking, a cue guide and last-update time.

The application supports Wear OS API 30+; it is a normal activity, not a watch-face service. It does not force always-on illumination. It has no Supabase client, account login, backend credential, camera, event browser or binder. Local watch color choices do not update the phone/server; newer accepted phone content replaces them.

## Read this before publishing

Debug application ID is `com.example.unspokenqueues` through an Android variant override; namespace/activity package remains `com.unspokencues.wear`. The current phone debug ID matches. Pairing also requires matching signing certificates. Release watch ID remains `com.unspokencues.wear`, while the current phone release ID differs. Resolve a shared installed identity/signing strategy and existing Play listing constraints before release. Historical docs mention a `luana-wearos` branch and different phone checkout name; use actual checkout/build state.

## Source map

Kotlin root: [`app/src/main/java/com/unspokencues/wear`](../../app/src/main/java/com/unspokencues/wear).

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Creates WatchStore, polls current DataItems while STARTED every 30 seconds, registers preference listener, renders WatchApp |
| `model/WatchState.kt` | Cue enum/colors/copy, SharedProfile/card link, PhoneSnapshot validation, lock/select/receive rules, publisher ordering |
| `data/WatchStore.kt` | Synchronized SharedPreferences persistence, receive deduplication/retirement, stale local write protection |
| `sync/WatchSyncService.kt` | Manifest Data Layer listener and exact path/schema parsing; forwards validated snapshots to store |
| `ui/WatchApp.kt` | Status/Options/Guide/Unlock/Select/Share navigation; back/relock, QR timeout, accessible cue semantics |
| `ui/CueSymbol.kt` | Canvas check/triangle/stop/question shapes and labels; no reliance on emoji fonts |
| `ui/ProfileQr.kt` | ZXing error-correction-M modules and integer-cell QR rendering with quiet zone |
| `app/src/main/AndroidManifest.xml` | Required watch hardware/library, non-standalone metadata, launcher, exported Data Layer service |
| `app/src/main/res/xml/data_extraction_rules.xml` | Excludes shared preferences from cloud backup and device transfer |
| `app/build.gradle.kts` | SDKs, application ID/debug override, Java compatibility, dependencies |
| `build.gradle.kts`, `settings.gradle.kts` | Plugin versions, repositories, Foojay and single app module |
| `gradle/*`, `gradle.properties`, wrappers | Gradle 9.6.0 checksum, Java 25 daemon criteria in local tree and runtime settings |
| `app/src/test` | State/order/privacy and QR unit tests |
| `app/src/androidTest` | Compose behavior/device tests |
| `.github` | CODEOWNERS/issue/PR templates; no checked-in Actions workflow found |

Existing [design](../design.md) explains product intent; [implementation plan](../implementation-plan.md) is historical planning. [CONTRIBUTING.md](../../CONTRIBUTING.md) and [SECURITY.md](../../SECURITY.md) retain team review/secret rules. There is no separate server code here.

## Maintenance rules

Keep cue values/colors/meanings, custom card URI, DataMap fields/types, revision semantics and schema coordinated with the phone. Tests exist independently in both repositories; no shared source module prevents drift. Change protocol versions with a mixed-version plan because unsupported schema numbers are ignored. Update these docs when modifying persistence, navigation, SDKs, identity or synchronization. Never include real card tokens, account sessions, signing keys or credentials in examples.
