# Setup, testing and troubleshooting

Return to the [handoff index](README.md).

## Build prerequisites

Open this repository separately in Android Studio. Checked-in versions: Gradle 9.6.0, AGP 9.4.0, Kotlin Compose plugin 2.2.10, Compose BOM 2026.02.01, Wear Compose material/foundation 1.5.0, Wearable Play services 19.0.0 and ZXing 3.5.3. Java source/target is 11; current local daemon criteria request Java 25 through Foojay. Install Android SDK 37; minSdk is 30 and targetSdk is 37. Initial app version is 0.1.0/code 1.

Supply an untracked `local.properties` SDK path if needed. No Supabase URL/key, Firebase JSON or `.env` is required by this app. First sync may need network access for Gradle/JDK/dependencies. The old root README's JDK guidance should be read alongside current daemon criteria.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
adb devices -l
adb -s <watch-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <watch-serial> shell am start -n com.example.unspokenqueues/com.unspokencues.wear.MainActivity
```

On Windows use `.\gradlew.bat`. If the wrapper is not executable locally, invoke `sh ./gradlew` or correct its executable bit. Pair the watch with the phone and install the matching phone debug build on the **phone**, complete sign-in/profile/tutorial and trigger Settings → Sync now. Google Play services must be available. Compare both `./gradlew signingReport` outputs if pairing fails: matching IDs without matching certificates is insufficient.

## Automated tests

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Select the Wear OS device explicitly when multiple targets are present, for example with `ANDROID_SERIAL=<watch-serial>`. Device instrumentation requires a suitable watch target; pure JVM tests do not. These commands were documented, not executed during this documentation-only change.

| Source | Coverage |
| --- | --- |
| `app/src/test/java/com/unspokencues/wear/WatchStateTest.kt` | Lock/select/relock rules, card/profile validation, inactive clearing, snapshot order/new and retired publishers |
| `app/src/test/java/com/unspokencues/wear/QrTest.kt` | Card QR module encoding/ZXing decode |
| `app/src/androidTest/java/com/unspokencues/wear/WatchAppTest.kt` | Accessible cue identity, guide/options, accidental gesture protection, unlock/selection, locked sharing and timeout |

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html`, and connected results under `app/build/reports/androidTests/connected/` / `app/build/outputs/androidTest-results/connected/`. A UI/model test does not prove paired-device delivery, WatchStore persistence/stale-callback protection, receiver-driven screen changes or backend authorization. No checked-in Actions workflow or automated cross-device transport suite exists.

## Device acceptance

1. Start with empty watch data: Waiting for phone, no fabricated identity or QR.
2. Send each phone cue and check name/meaning/symbol/color. Inspect Options last-update time.
3. Swipe and use bezel/rotary while locked; cue must stay stable while normal system navigation remains usable.
4. Cancel Unlock; then confirm unlock, choose a cue, verify relock and persistence after restart. It must not change the phone.
5. Retry unchanged phone state/Sync now: local cue remains. Change phone content: accepted newer snapshot restores phone cue and relocks.
6. Share while locked, scan with another Unspoken Cues phone, test Close, Back and 30-second timeout. Use a small round display and large system text; verify the whole QR/quiet zone is visible.
7. Make profile private, sign out, change A→B, restart apps and disconnect/reconnect. After newer delivery, stale QR/identity closes/clears. While offline the watch can only display its old cache.
8. Reset/reinstall phone with changed clock; new generation must recover. Deliver an older/equal/retired snapshot in controlled tests and ensure it cannot overwrite the accepted state.
9. Exercise malformed cue, schema, token, publisher and revision. Active invalid cue/unsupported schema must not replace state. Missing public token must prevent sharing while preserving valid cue.
10. Check TalkBack cue announcements, text scaling, all four cues without relying on color, timeout/sleep and system Back. Always-on full illumination is not a supported claim.

Record commit/build/environment/device/OS and actual outcomes. Test accounts/tokens should be synthetic and kept out of public logs/screenshots.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No received state | Phone sign-in/onboarding/profile load, paired nodes/Play services, installed debug ID and certificate match; phone Sync now |
| Phone says Connected but watch unchanged | Indicator is connected nodes, not app acknowledgment; check installed companion, schema/path and accepted revision |
| Local color survives retry | Expected when phone content/revision unchanged; select a different phone cue to make a new snapshot |
| QR unavailable | Last accepted profile must be public with a valid token; watch cannot query the backend itself |
| Old identity while offline | Cache cannot be cleared until delivery; keep phone open/reconnected long enough to publish/accept newer snapshot |
| New state ignored | Unsupported schema, invalid cue, nonpositive revision, blank/retired publisher, equal/older current-generation revision |
| Can't launch with adb | Debug package is `com.example.unspokenqueues`, activity is `com.unspokencues.wear.MainActivity` |
| Gradle/JDK failure | SDK 37, current Java 25 criteria, wrapper/checksum, Foojay/Maven network access; Java source target 11 is not daemon version |
| Release sync fails | Current release IDs mismatch; shared ID/signing release strategy is unresolved |

Do not delete app data as the first debugging step: it removes local choices and ordering/retirement history. Inspect state/revisions first using authorized development devices. There is no production signing/release pipeline here; obtain publishing access/key custody and coordinate with the phone owner. Existing SECURITY.md governs secret handling.
