# AtlasMediaWidget Repository Guide

## Scope

These instructions apply to the entire repository.

## Project purpose

AtlasMediaWidget is intended to be an Android 11 media overlay for a portrait automotive head
unit. The first implementation should use a `TYPE_APPLICATION_OVERLAY` window, following the
proven shell and lifecycle approach from AtlasAppWidget. The standard release build is the
`integrated` Widget APK where the versioned AtlasMediaApi backend (`:media-runtime`) runs directly
inside a private `:media` process. The autonomous `com.mmwtl.atlasmediaapi` package can still be
built separately from `:api-app` when a standalone backend is needed.
The planned package name is `com.mmwtl.atlasmediawidget`; do not change it without an explicit
migration request.

The OEM card is a real `AppWidget`, but the head-unit launcher pins only its known Geely provider.
Do not describe AtlasMediaWidget as a drop-in launcher widget until placement of a third-party
provider has been demonstrated on the real head unit.

## Evidence and platform assumptions

- Keep confirmed device behavior, Android API facts, and implementation assumptions visibly
  separate in documentation and reviews.
- The Media Bridge protocol v1 and runtime implementation are maintained directly in this repository
  as modules `:media-core`, `:media-runtime`, `:vendor-oneos`, `:vendor-ecarx-stub`, and `:api-app`.
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

- Consume the versioned AtlasMediaApi Media Bridge service through one adapter. Bind using an
  explicit flavor-selected component, register a reply Messenger, accept only a compatible protocol
  version, and reconnect after Binder death with bounded backoff.
- Validate the required media backend and notification-listener status during setup. Report a
  specific missing prerequisite instead of silently showing cached data.
- The Media Bridge snapshot must include current and available sources, playback position,
  duration, speed, actions and a read-granted artwork URI. Keep fields optional where the active
  source genuinely does not provide them; do not synthesize missing data from stale values.
- Keep the GInputBridge adapter behind an interface. A direct `NotificationListenerService` plus
  optional OneOS adapter is a fallback only if the external API proves insufficient.
- Do not request or claim ordinary access to the privileged `MEDIA_CONTENT_CONTROL` permission.
- Perform Binder calls off the main thread. Use bounded retries with backoff, generation/session
  IDs for asynchronous artwork, and idempotent listener registration.
- Never keep stale playback state indefinitely. After a bounded reconciliation failure, show an
  explicit unavailable or disconnected state instead of silently presenting old metadata.
- Extrapolate a playing position locally from position, speed and `SystemClock.elapsedRealtime()`.
  Do not request one IPC update per second. A low-frequency reconciliation timer may run only while
  the overlay is visible or playback is expected, and must supplement rather than replace callbacks.
- Send transport/source commands only through the explicit versioned bound service. The autonomous
  v1 service is intentionally open on the isolated head unit; the integrated service is private to
  the Widget package. Do not add client-side identity assumptions.
  The UI must respect the capability mask and treat `OK` as command delivery, pending until a newer
  snapshot confirms the resulting state.

## Version and artifact naming

- Once the Android module exists, keep `appVersionCode` and `appVersionName` at the top of the app
  Gradle file as the single version source.
- Treat `main` as the only release-version branch. Increment `appVersionCode` and the semantic
  patch component of the base `appVersionName` only when the completed improvement is being built
  from `main`, unless the user requests a different release number.
- Builds from any other branch must keep the base `appVersionCode` and base `appVersionName`
  unchanged. Append a sanitized branch name to the effective visible `versionName` (for example,
  branch `seek` makes `1.0.26-seek`), and use that effective version in the APK/archive name too.
  Do not encode the branch name in `versionCode`.
- A single user-requested batch is one version increment even when it contains several related
  files or commits.
- Preserve the archive base name `<effectiveVersionName>[<versionCode>]AtlasMediaWidget`; do not
  allow Gradle to fall back to module-derived `app-*.apk` names. The standard integrated release
  build omits the flavor suffix and outputs `<effectiveVersionName>[<versionCode>]AtlasMediaWidget-release.apk`.
  Standalone API APKs from `:api-app` follow `<effectiveVersionName>[<versionCode>]AtlasMediaApi-release.apk`.
- The Widget application has only the `integrated` distribution flavor. Its standard release task
  `:app:assembleRelease` outputs the integrated Widget APK with the private `:media` process.
- The `integrated` variant must include `media-runtime`, bind its non-exported Media Bridge service
  in the `:media` process, and contain neither separate API APK assets nor
  `REQUEST_INSTALL_PACKAGES`.

## Build and verification

Use the repository wrapper. Before handing off a completed application improvement, run at minimum:

```sh
sh gradlew --offline clean check assembleRelease
```

This command builds the standard `release` for `:app` (integrated backend) and `release` for `:api-app`.
Individual targets can be built separately:
- Standard Widget (with integrated API): `sh gradlew assembleRelease` or `sh gradlew :app:assembleRelease`
- Standalone Media API APK: `sh gradlew :api-app:assembleRelease`

Verify release outputs under `app/build/outputs/apk/integrated/release/`
(`<effectiveVersionName>[<versionCode>]AtlasMediaWidget-release.apk`) and
`api-app/build/outputs/apk/release/`. Inspect package/version
metadata and run `apksigner verify` when the artifacts are signed.
For integrated builds, verify the local Media Bridge, notification listener, diagnostics activity,
and FileProvider are present only in that flavor; verify that the bridge and diagnostics activity
run in `:media`, the bridge is not exported, and the provider authority uses the Widget package.

Release signing may be supplied by the ignored local `secure.signing.gradle` and keystore files.
If they are absent, report unsigned artifacts explicitly; never disguise a debug-signed artifact
as a production release and never commit keystores or credentials.

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

## Public documentation

- Keep the root README product-facing: describe the app, screenshots, features, requirements,
  setup, usage, build instructions and links to deeper documentation.
- Do not leave design deliberation, implementation planning, completed-task checklists or internal
  comparisons in the README. Put architectural research in `docs/` and maintainer constraints in
  this file.
- Label screenshot-only demonstration media states as demo data without exposing temporary test
  hooks or capture procedures in the public README.

## Repository hygiene

- After completing and verifying each improvement, create a Git commit unless the user explicitly
  asks to leave it uncommitted.
- Stage only files belonging to the current improvement. Do not amend, rebase, push, or rewrite
  existing history unless explicitly requested.
- Never commit generated APKs, Gradle caches, local SDK paths, signing files, keystores, secrets,
  extracted firmware APKs, or decompiler output.
- Preserve unrelated user changes in a dirty worktree.
