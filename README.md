# Unspoken Cues — Wear OS App

Wear OS companion: one full-screen status color at a time (Green/Yellow/Red/Purple), tap/swipe to change, swipe up for QR, two-way sync with the phone app.

Target package: `com.unspokencues.wear`

## Status
Foundation phase — see [Issues](../../issues) and the org [Project board](https://github.com/orgs/unspoken-cues/projects) for current sprint work.

## Docs
- Contribution workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security/secret handling: [SECURITY.md](SECURITY.md)

## Product context
- Only ONE status color fills the screen at a time — never all four together.
- Swipe-down must remain normal Wear OS system behavior; don't intercept it.
- QR times out back to the previously active status.
- Final Play identity is `com.unspokencues.wear` — do not leave a `playtest` applicationId in a release build.
