# Companion architecture and protocol

Return to the [handoff index](README.md).

## Data flow

```mermaid
flowchart TD
    Phone[Authenticated phone composition] --> Data[Wearable DataItem]
    Data --> Service[WatchSyncService: DATA_CHANGED]
    Data --> Poll[MainActivity: 30s polling while STARTED]
    Service --> Parse[receiveDataItem]
    Poll --> Parse
    Parse --> Validation[PhoneSnapshot.fromWire]
    Validation --> Store[WatchStore.receive]
    Store --> Prefs[watch_state SharedPreferences]
    Prefs --> Listener[Activity preference listener]
    Listener --> UI[WatchApp]
    UI --> Local[WatchStore.select]
    Local --> Prefs
    Local --> Outbox[CueOutbox]
    Outbox --> Request[Wear cue-request DataItem]
    Request --> PhoneSync[Signed-in phone reconciliation]
    PhoneSync --> Profile[Supabase profile]
    Profile --> PhoneSync
    PhoneSync --> Data
```

The app is Kotlin/Compose with one Android application module. There are no ViewModels, navigation framework, Room database, HTTP repository, WorkManager jobs or backend synchronization credentials. `WatchState` is pure Kotlin rules; `WatchStore` owns persisted state; the service and activity reconcile incoming snapshots; Compose owns transient screen selection.

The service receives changed DataEvents even without an open activity. The activity polls already-stored DataItems every 30 seconds inside `repeatOnLifecycle(STARTED)` and releases buffers. Retrieval failures leave the cache available. Its SharedPreferences listener reloads the entire store; it reads once after listener registration to close the initialization gap. The service ignores DataItem deletions; clearing is an explicit inactive snapshot.

## Schema 1 message

The DataItem path must be exactly `/unspoken-cues/state`. Manifest filtering uses a path prefix, but the parser performs exact equality. The sender uses DataClient `putDataItem` with urgency; it is latest state, not a complete event stream.

| Field | Type | Validation/meaning |
| --- | --- | --- |
| schema | Int | Must equal 1 |
| active | Boolean | false → clear cue and profile |
| status | String | Active must be GREEN/YELLOW/RED/PURPLE; unknown rejects snapshot |
| name | String | At most 80 characters retained |
| token | String | SharedProfile link only exists for exactly 32 hex characters |
| public | Boolean | Share UI requires true and a valid link |
| revision | Long | Must be positive and pass generation ordering |
| publisher | String | Must be nonblank, not retired |

Phone sends no email, session, Auth credential, profile preferences/boundaries, image or account UUID. The card token is still sensitive as a sharing capability. `SharedProfile.link` lowercases it into `unspokencues://card/<token>`. Public active data without a valid token retains the cue but discards the profile; private data may retain a name but cannot produce `WatchState.shareLink`. Inactive data clears both irrespective of other fields.

The phone gates sharing on a confirmed session, loaded profile and completed onboarding. An updated phone reads staged watch requests on delivery, while open and when the participant chooses Sync now. A definite account change or sign-out invalidates prior requests. Connected node count and profile save feedback are independent.

The active snapshot adds `generation` (opaque session UUID), `cueRevision` (positive Long), `receipt` and `receiptWriter` (operation and installation UUID strings), and `receiptBase` and `receiptSequence` (Long). Empty strings and zeros indicate no receipt. All six extension fields must be present with valid types for two-way operation. A schema-1 snapshot with no extension remains readable and supports local-only watch selection.

The watch sends schema 1 at `/unspoken-cues/cue-request` with only `generation`, `baseRevision`, `operation`, `status`, `writer` and `sequence`. Each selection replaces the previous DataItem at this path. The watch persists its writer UUID, increasing sequence, operation UUID, selected cue and base before attempting delivery. A successful Data Layer put only queues transport. The pending indicator stays visible until an authoritative phone snapshot arrives.

## Ordering and persistence

Phone keeps `watch_publisher` preferences containing current fields, revision and random generation UUID. Changed content gets `max(currentTimeMillis, previous + 1)`; unchanged retries keep the same revision. Reset/reinstall creates a new publisher because those preferences are excluded from backup/transfer.

Watch `watch_state` stores status, has_profile/name/token/public, locked, received, revision, publisher and retired_publishers. Store operations synchronize on a common monitor. `SnapshotOrder` accepts a new unseen non-retired generation even at a lower revision; it accepts the current generation only at a strictly higher revision. A generation switch retires the previous one permanently in this cache. Delayed retired data cannot restore an old identity. This assumes one logical phone owner; simultaneous publisher arbitration is not implemented.

Accepted snapshots persist the full state, update the local receipt time and relock the cue. An explicit selection uses the current committed cue revision or keeps the base of an outstanding request. A new profile-only snapshot preserves the selection. An older receipt from the same writer and base also preserves it. An exact receipt clears pending and reports the save. A newer independent phone choice restores the authoritative color. The phone can advance the transport revision independently of the cue revision, including when Sync now republishes an unchanged cue.

Watch backups are disabled and shared preferences additionally excluded from extraction/transfer. Clearing watch app data loses the cache, retired-generation history and local color. The activity can recover current DataItems on restart if available; it cannot obtain a fresh backend state independently.

## UI state machine

`WatchApp` saves a private Screen enum: STATUS, OPTIONS, GUIDE, UNLOCK, SELECT, SHARE. A changed publisher/revision returns to STATUS. New phone data also relocks via the store.

- STATUS fills the display with one cue and shows symbol/name/meaning plus Options. Before sync it explains Waiting for phone.
- OPTIONS offers sharing, Color locked/Change color, Cue guide and last received local time. No accidental swipe/bezel cycling is implemented.
- UNLOCK requires a separate confirmation. Selecting a cue is ignored while locked; valid unlocked selection immediately relocks and persists locally.
- SELECT uses neutral buttons rather than displaying all four saturated cues together. Cancel and system Back relock and return to status.
- SHARE is available while locked when a public valid token exists. QR closes after 30 seconds, Close or Back. A new phone revision closes obsolete QR immediately.
- GUIDE shows all cue names/meanings/symbols on neutral background. System navigation is not intercepted for status-changing gestures.

Cue definitions mirror the phone: Green `#2E9E5B`/check, Yellow `#E8B517`/triangle, Red `#CF3A2D`/stop, Purple `#7B4FC9`/question. Foreground is dark on Green/Yellow and near-white on Red/Purple. Screen reader semantics combine cue label, full meaning and symbol; decorative shapes avoid duplicate announcements. Columns scroll for large text/round displays.

QR uses ZXing error correction M, black on white, four quiet modules per edge and integer-sized cells. It opens a custom link understood by the phone app, not a public web profile. No full-brightness override or always-on watch face is implemented.

## Privacy and reliability limits

Disconnected cache remains until a newer clear/private snapshot arrives. Setting Private on the phone withholds the token in new snapshots but does not remotely erase an offline device. The backend swap RPC accepts existing valid tokens even for private profiles, and tokens have no expiry/revocation flow. Existing swaps can read private profile rows. Do not promise immediate revocation.

Play services can deliver an already staged request when connectivity returns. Opening the watch app, its 30-second foreground poll and incoming phone state also retry a persisted request. If local Play services could not stage the initial request and both apps are closed, reopen the watch or trigger Sync now on the phone. If the signed-in phone is offline, the watch keeps its pending indication until backend reconciliation or a superseding edit. [Testing and troubleshooting guide](testing.md)
