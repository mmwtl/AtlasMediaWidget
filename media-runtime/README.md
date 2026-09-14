# Встроенная среда выполнения AtlasMediaApi / Integrated AtlasMediaApi runtime

## Описание / Overview

Этот модуль и сопутствующие модули `media-core`, `vendor-oneos` и `vendor-ecarx-stub`
синхронизированы из `/Users/wital/dev/AtlasMediaApi` (коммит `4e9b640`).

This module and the sibling `media-core`, `vendor-oneos`, and `vendor-ecarx-stub` modules were
synchronized from `/Users/wital/dev/AtlasMediaApi` commit `4e9b640`.

## Интегрированный рантайм / Integrated runtime

Этот репозиторий собирает рантайм только как часть интегрированного Widget APK.
Автономный APK AtlasMediaApi больше не собирается и не распространяется.

This repository builds the runtime only as part of the integrated Widget APK.
The standalone AtlasMediaApi APK is no longer built or distributed.

- `MediaRuntime` управляет одним ленивым координатором на процесс вместо требования `AtlasMediaApiApp`;  
  `MediaRuntime` owns one lazy coordinator per process instead of requiring `AtlasMediaApiApp`;
- `MediaBridgeService`, `MediaNotificationListenerService` и `DiagnosticActivity` работают в процессе
  `:media` приложения-хоста;  
  `MediaBridgeService`, `MediaNotificationListenerService`, and `DiagnosticActivity` run in the
  host application's `:media` process;
- Bridge и активность диагностики являются приватными компонентами приложения-хоста; диагностика
  открывается из единого интерфейса Widget.
  The bridge and diagnostics activity are private components of the host application; diagnostics
  is opened from the unified Widget interface.
- Пользовательские разрешения медиа декларируются хостовым flavor'ом `integrated` и управляются из
  главной активности Widget; экран диагностики только отображает их текущий статус.  
  User-grantable media permissions are declared by the `integrated` host flavor and managed from
  the main Widget activity; diagnostics only reports their current state.

При переносе изменений бэкенда из AtlasMediaApi синхронизируйте все четыре модуля вместе и сохраняйте
эти хостовые различия. Не копируйте собранный APK AtlasMediaApi в этот модуль.

When bringing backend changes from AtlasMediaApi, synchronize all four modules together and keep
these host-specific differences. Do not copy a built AtlasMediaApi APK into this module.
