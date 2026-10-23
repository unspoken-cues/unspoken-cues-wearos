# Initial watch app implementation plan

Goal: deliver the three user stories on branch luana-wearos using the phone's cue
values and QR payload. Execution: inline, as requested by the user.
Spec: design.md. Kotlin/Compose, Wear OS API 30+, Gradle/AGP matching the phone.

1. Scaffold the Android watch build using the phone Gradle wrapper and versions.
   Add JVM behavior tests before state/QR implementations; run red, then green.
2. Implement persistent state, validated Data Layer snapshots and background receiver.
   Retain cached identity offline; clear it on sign-out; never share private profiles.
3. Implement round-screen status, deliberate unlock, single-cue selector, and QR
   timeout. Add device interaction tests for accidental swipes and lock behavior.
4. Add a phone publisher tied to authenticated profile/status changes. Keep account
   management on the phone and document the initial one-way sync limitation.
5. Run JVM suites, debug builds, lint and device tests when a suitable target exists.
   Review both diffs for leaked configuration, missing setup and user-story gaps.

Review focus: account switching, private profiles, malformed card tokens, gesture
changes while locked, and QR readability/clipping on a small round watch.
