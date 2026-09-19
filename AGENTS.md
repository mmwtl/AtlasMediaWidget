# AtlasMediaWidget Repository Guide

## Scope

These instructions apply to the entire repository.

## Current project

AtlasMediaWidget is an Android media overlay for a portrait Geely OneOS head unit. The current
implementation uses a `TYPE_APPLICATION_OVERLAY` window and shows the card only while HOME is in
the foreground. The card is not a launcher `AppWidget`, and the repository must not describe it as
a drop-in third-party launcher widget.

The only supported distribution is the integrated Widget APK:

- package: `com.mmwtl.atlasmediawidget`;
- app module: `:app`;
- embedded runtime: `:media-runtime`;
- shared protocol/core: `:media-core`;
- vendor edges: `:vendor-oneos` and `:vendor-ecarx-stub`;
- private runtime process: `:media`.

The integrated APK contains the Media Bridge service, notification listener, diagnostics activity
and FileProvider from `:media-runtime`. The Bridge and diagnostics activity run in `:media`; the
Bridge is not exported. The separate Atlas Media API APK is not built or distributed. The
integrated flavor removes its launcher alias and opens diagnostics from the Widget application.

Do not change the package name or reintroduce a second distribution flavor without an explicit
migration request.

## Evidence and platform assumptions

- Keep confirmed device behavior, Android API facts and implementation assumptions visibly separate
  in documentation and reviews.
- The Media Bridge protocol v1 and its runtime implementation are maintained in this repository.
- Decompiled OEM classes and Binder behavior are firmware-specific evidence, not a stable public
  API. Keep them behind adapter/service edges.
- Target the tested Android 11 portrait head unit first. Do not generalize OneOS behavior to other
  firmware versions without a device test.
- Build and unit-test success does not prove correct OEM Binder behavior, source arbitration,
  cold-boot startup, dashboard output or sleep/wake recovery on the head unit.

## Overlay behavior

- Keep the overlay non-focusable and non-touch-modal so the screen outside the card remains
  interactive.
- Show the card only while HOME/the launcher is foreground unless the user explicitly enables a
  different mode.
- Preserve the stored position and appearance across ordinary upgrades.
- Treat overlay permission, usage access, accessibility service, notification-listener access,
  foreground-service behavior, boot start and OEM power restrictions as separate concerns. The app
  cannot grant these permissions to itself.
- Do not kill or force-stop the OEM launcher, media widget, MediaCenter, Bluetooth or radio
  processes during recovery.

## Media Bridge and state

- Consume the versioned Media Bridge through one Widget adapter. Bind to the explicit integrated
  component, register a reply Messenger, accept only a compatible protocol version and reconnect
  after Binder death with bounded backoff.
- Perform Binder calls off the main thread. Use bounded retries, generation/session IDs for
  asynchronous artwork and idempotent listener registration.
- Validate the media backend and notification-listener status during setup. Report a specific
  missing prerequisite instead of silently presenting cached data.
- A snapshot may include the selected and available sources, metadata, artwork URI, position,
  duration, speed and capability mask. Keep fields optional when the active source does not provide
  them; do not synthesize missing values from stale state.
- Extrapolate playing position locally from position, speed and
  `SystemClock.elapsedRealtime()`. Reconciliation is low-frequency and only supplements callbacks
  while the overlay is visible or playback is expected.
- Never retain stale playback state indefinitely. After bounded reconciliation failure, show an
  explicit unavailable/disconnected state.
- Send transport and source commands only through the explicit versioned service. Respect the
  capability mask and treat `OK` as delivery/pending until a newer snapshot confirms the result.
- Preserve the selected Online player when returning from native sources. For OneOS DIM, send full
  Online metadata once and use the isolated numeric progress facade for optional progress updates;
  do not redraw the full card on every progress tick.

## Settings and backups

- `MainActivity` is the single settings entry point with five sections: System, Media, Widget,
  Backup and Diagnostics. Keep settings ownership split as implemented: Widget preferences in the
  app process and media/runtime settings in `:media` behind the Media Bridge settings operations.
- Keep settings export/import and radio-catalog export/import separate. Preserve legacy JSON
  settings compatibility and the crash-recovery journal.
- Do not add another competing settings editor or expose runtime configuration through an unrelated
  external API.
- Do not expose raw third-party notification contents. Retain only the media fields needed for the
  card and diagnostics.

## Version and artifact naming

- Keep `appVersionCode` and `appVersionName` at the top of `app/build.gradle` as the single base
  version source.
- Increment the base version only for a completed release improvement from `main`, unless the user
  explicitly requests another release number. A single user-requested batch is one increment.
- Builds from non-`main` branches keep the base version and append a sanitized branch name to the
  effective `versionName`; never encode the branch in `versionCode`.
- Preserve the archive base name
  `<effectiveVersionName>[<versionCode>]AtlasMediaWidget`. The integrated release artifact must be
  `<effectiveVersionName>[<versionCode>]AtlasMediaWidget-release.apk` under
  `app/build/outputs/apk/integrated/release/`.
- The standard application release task is `:app:assembleRelease` (also available as
  `assembleRelease`). Do not allow Gradle to fall back to `app-*.apk` naming.

## Build and verification

Use the repository wrapper. Before handing off a completed application improvement, run:

```sh
sh gradlew --offline clean check assembleRelease
```

For a release artifact, inspect package/version metadata and run `apksigner verify` when signing
assets are available. Verify the integrated manifest: private Media Bridge and diagnostics in
`:media`, notification listener and Widget FileProvider present, correct Widget authority, no
`REQUEST_INSTALL_PACKAGES`, and no separate API APK assets.

Signing may be supplied by ignored local `secure.signing.gradle` and keystore files. If they are
absent, report the artifact as unsigned. Never commit keystores, credentials or signing files and
never call a debug-signed artifact a production release.

For overlay or media changes, use Android 11 at 1440×1920 portrait when an emulator is available,
then validate source switching and sleep/wake recovery on the real head unit. Unit tests should
cover session selection, stale-state expiry, source mapping, reconnect transitions and relevant
settings/import invariants.

## Source and UI guidelines

- Keep Android framework and OEM Binder behavior at adapter/service edges; keep arbitration and
  state reduction in pure, testable classes.
- Maintain the Atlas graphite palette: `#171717` background, `#262626` cards, `#333333` nested
  surfaces, `#F5F5F5` primary text, `#D4D4D4` secondary text and `#7893A0` accent.
- Avoid new dependencies and abstractions unless they materially simplify a required behavior.
- Do not add hypothetical fallbacks or silently force a source at startup. Preserve the head unit's
  selected/default source unless the explicit user setting requests an automatic switch.

## Documentation and repository hygiene

- `README.md` is the Russian product-facing documentation. `README.en.md` is its standalone English
  counterpart; do not mix both languages section by section in either file.
- Keep the root README limited to product description, screenshots, features, requirements, setup,
  build instructions and links to deeper documentation. Put research, plans, audits and maintainer
  constraints in `docs/` or this file.
- Label screenshot-only demo data as demo data and do not document temporary test hooks in the
  public README.
- Preserve unrelated user changes in a dirty worktree.
- Stage only files belonging to the current improvement. Do not amend, rebase, push or rewrite
  existing history unless explicitly requested.
- Never commit generated APKs, Gradle caches, local SDK paths, signing files, keystores, secrets,
  extracted firmware APKs or decompiler output.
- After completing and verifying each improvement, create a Git commit unless the user explicitly
  asks to leave it uncommitted.
