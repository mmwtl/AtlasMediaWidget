# Integrated AtlasMediaApi runtime

This module and the sibling `media-core`, `vendor-oneos`, and `vendor-ecarx-stub` modules were
synchronized from `/Users/wital/dev/AtlasMediaApi` commit `4e9b640`.

The integrated variant deliberately differs from the standalone application in three places:

- `MediaRuntime` owns one lazy coordinator per process instead of requiring `AtlasMediaApiApp`;
- `MediaBridgeService`, `MediaNotificationListenerService`, and `DiagnosticActivity` run in the
  host application's `:media` process;
- the bridge and diagnostics activity are private components of the host application, while an
  exported launcher alias exposes diagnostics as the separate `Atlas Media API` app-list entry.
- user-grantable media permissions are declared by the `integrated` host flavor and managed from
  the main Widget activity; diagnostics only reports their current state.

When bringing backend changes from AtlasMediaApi, synchronize all four modules together and keep
these host-specific differences. Do not copy a built AtlasMediaApi APK into this module.
