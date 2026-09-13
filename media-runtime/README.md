# Встроенная среда выполнения AtlasMediaApi / Integrated AtlasMediaApi runtime

## Описание / Overview

Этот модуль и сопутствующие модули `media-core`, `vendor-oneos` и `vendor-ecarx-stub`
синхронизированы из `/Users/wital/dev/AtlasMediaApi` (коммит `4e9b640`).

This module and the sibling `media-core`, `vendor-oneos`, and `vendor-ecarx-stub` modules were
synchronized from `/Users/wital/dev/AtlasMediaApi` commit `4e9b640`.

## Отличия integrated-варианта / Integrated variant differences

Вариант `integrated` намеренно отличается от автономного приложения в следующих аспектах:

The integrated variant deliberately differs from the standalone application in these aspects:

- `MediaRuntime` управляет одним ленивым координатором на процесс вместо требования `AtlasMediaApiApp`;  
  `MediaRuntime` owns one lazy coordinator per process instead of requiring `AtlasMediaApiApp`;
- `MediaBridgeService`, `MediaNotificationListenerService` и `DiagnosticActivity` работают в процессе
  `:media` приложения-хоста;  
  `MediaBridgeService`, `MediaNotificationListenerService`, and `DiagnosticActivity` run in the
  host application's `:media` process;
- Bridge и активность диагностики являются приватными компонентами приложения-хоста, а экспортируемый
  launcher alias (`MediaApiLauncher`) предоставляет доступ к экрану диагностики как к отдельному
  пункту списка приложений «Atlas Media API» со своей иконкой (`@mipmap/ic_media_api_launcher`) и
  собственным `taskAffinity` (`com.mmwtl.atlasmediaapi`);  
  The bridge and diagnostics activity are private components of the host application, while an
  exported launcher alias (`MediaApiLauncher`) exposes diagnostics as the separate `Atlas Media API`
  app-list entry with its own standalone launcher icon (`@mipmap/ic_media_api_launcher`) and dedicated
  `taskAffinity` (`com.mmwtl.atlasmediaapi`);
- Пользовательские разрешения медиа декларируются хостовым flavor'ом `integrated` и управляются из
  главной активности Widget; экран диагностики только отображает их текущий статус.  
  User-grantable media permissions are declared by the `integrated` host flavor and managed from
  the main Widget activity; diagnostics only reports their current state.

При переносе изменений бэкенда из AtlasMediaApi синхронизируйте все четыре модуля вместе и сохраняйте
эти хостовые различия. Не копируйте собранный APK AtlasMediaApi в этот модуль.

When bringing backend changes from AtlasMediaApi, synchronize all four modules together and keep
these host-specific differences. Do not copy a built AtlasMediaApi APK into this module.
