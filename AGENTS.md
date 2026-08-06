# AtlasMediaWidget Repository Guide

## Scope

These instructions apply to the entire repository.

## Project purpose

AtlasMediaWidget is intended to be an Android 11 media overlay for a portrait automotive head
unit. The first implementation should use a `TYPE_APPLICATION_OVERLAY` window, following the
proven shell and lifecycle approach from AtlasAppWidget. GInputBridge is installed on the target
head unit and its broadcast API is the primary media-data source for the first implementation.
The planned package name is `com.mmwtl.atlasmediawidget`; do not change it without an explicit
migration request.

The OEM card is a real `AppWidget`, but the head-unit launcher pins only its known Geely provider.
Do not describe AtlasMediaWidget as a drop-in launcher widget until placement of a third-party
provider has been demonstrated on the real head unit.

## Evidence and platform assumptions

- Keep confirmed device behavior, Android API facts, and implementation assumptions visibly
  separate in documentation and reviews.
- Treat `../GInputBridge` as the source of truth for its external broadcast contract. Do not infer
  extras or behavior from UI text alone; verify them against the sender/receiver implementation.
- Do not copy GInputBridge's `MediaSessionManager` collectors or its entire `com_geely` module into
  this repository while its installed API satisfies the requirement.
- Treat the decompiled OEM APKs as firmware-specific evidence, not as a stable public API.
- Target the tested Android 11 head unit first. Do not generalize OEM Binder behavior to other
  firmware versions without a device test.

## Required overlay behavior

- Keep the screen outside the overlay window interactive by preserving non-focusable,
  non-touch-modal window flags.
- Show the media surface only while a HOME/launcher activity is foreground unless the user
  explicitly enables another mode.
- Preserve overlay position and appearance across ordinary upgrades.
- Treat overlay permission, usage access, notification-listener access, foreground-service
  behavior, boot start, and OEM power restrictions as separate concerns. The app cannot grant
  these permissions to itself.
- Never kill or force-stop the OEM launcher, media widget, MediaCenter, Bluetooth, or radio
  processes as part of normal recovery.

## Media-state behavior

- Consume GInputBridge `PLAYBACK_METADATA`, `PLAYBACK_STATE`, and `AUDIO_SOURCE_CHANGED` events
  through one adapter. Send a package-targeted `REQUEST_PLAYBACK_INFO` on process start, after wake,
  and when the overlay becomes visible.
- Validate the required GInputBridge settings during setup: Media runtime, External API runtime,
  Send media session data, Broadcast intents, and notification access. Report a specific missing
  prerequisite instead of silently showing cached data.
- The current `REQUEST_PLAYBACK_INFO` response does not include current audio source, playback
  position, duration, actions, or guaranteed-readable artwork. Keep these fields optional and do
  not synthesize them from stale values.
- Keep the GInputBridge adapter behind an interface. A direct `NotificationListenerService` plus
  optional OneOS adapter is a fallback only if the external API proves insufficient.
- Do not request or claim ordinary access to the privileged `MEDIA_CONTENT_CONTROL` permission.
- Perform Binder calls off the main thread. Use bounded retries with backoff, generation/session
  IDs for asynchronous artwork, and idempotent listener registration.
- Never keep stale playback state indefinitely. After a bounded reconciliation failure, show an
  explicit unavailable or disconnected state instead of silently presenting old metadata.
- Do not poll aggressively. A low-frequency reconciliation timer may run only while the overlay is
  visible or playback is expected, and must supplement rather than replace callbacks.

## Version and artifact naming

- Once the Android module exists, keep `appVersionCode` and `appVersionName` at the top of the app
  Gradle file as the single version source.
- For every completed application, resource, or build-system improvement, increment
  `appVersionCode`. Increment the semantic patch component of `appVersionName` unless the user
  requests a different release number.
- A single user-requested batch is one version increment even when it contains several related
  files or commits.
- Preserve the archive base name `<versionName>[<versionCode>]AtlasMediaWidget`; do not allow
  Gradle to fall back to module-derived `app-*.apk` names.

## Build and verification

Use the repository wrapper after the Android project is scaffolded. Before handing off a completed
application improvement, run at minimum:

```sh
sh gradlew --offline clean check assembleRelease
```

Verify the release output under `app/build/outputs/apk/release/`, inspect its package/version
metadata, and run `apksigner verify` when the artifact is signed. Release signing may be supplied
by the ignored local `secure.signing.gradle` and keystore files. If they are absent, report the
unsigned artifact explicitly; never disguise a debug-signed artifact as a production release and
never commit keystores or credentials.

For overlay or media changes, validate on Android 11 at 1440x1920 portrait when an emulator is
available, then validate source arbitration and sleep/wake recovery on the real head unit. Unit
tests must cover session selection, stale-state expiry, source mapping, and reconnect state
transitions.

## Source and UI guidelines

- Keep Android framework and OEM Binder behavior at adapter/service edges. Keep arbitration and
  state reduction in pure, testable classes.
- Maintain the Atlas graphite visual system: `#171717` background, `#262626` cards, `#333333`
  nested surfaces, `#F5F5F5` primary text, `#D4D4D4` secondary text, and `#7893A0` accent.
- Avoid dependencies unless they materially simplify behavior that cannot remain small and local.
- Do not expose raw third-party notification contents. Retain only the media fields needed for the
  visible card and diagnostics.

## Repository hygiene

- After completing and verifying each improvement, create a Git commit unless the user explicitly
  asks to leave it uncommitted.
- Stage only files belonging to the current improvement. Do not amend, rebase, push, or rewrite
  existing history unless explicitly requested.
- Never commit generated APKs, Gradle caches, local SDK paths, signing files, keystores, secrets,
  extracted firmware APKs, or decompiler output.
- Preserve unrelated user changes in a dirty worktree.
