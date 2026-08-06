package com.mmwtl.atlasmediawidget;

import android.os.Bundle;

final class MediaSource {
    enum Id {
        UNKNOWN, USB, BT, RADIO, ONLINE, OTHER, YUNTING, CPAA;

        static Id fromWire(String value) {
            if (value == null) return UNKNOWN;
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }

        String label() {
            return switch (this) {
                case USB -> "USB";
                case BT -> "Bluetooth";
                case RADIO -> "Радио";
                case ONLINE -> "Онлайн";
                case OTHER -> "Другое";
                case YUNTING -> "Yunting";
                case CPAA -> "CarPlay";
                default -> "Неизвестно";
            };
        }
    }

    final Id id;
    final boolean connected;
    final boolean available;
    final boolean selected;
    final long capabilities;

    MediaSource(Id id, boolean connected, boolean available, boolean selected, long capabilities) {
        this.id = id;
        this.connected = connected;
        this.available = available;
        this.selected = selected;
        this.capabilities = capabilities;
    }

    static MediaSource fromBundle(Bundle bundle) {
        return new MediaSource(
                Id.fromWire(bundle.getString(MediaBridgeContract.K_SOURCE_ID)),
                bundle.getBoolean(MediaBridgeContract.K_SOURCE_CONNECTED),
                bundle.getBoolean(MediaBridgeContract.K_SOURCE_AVAILABLE),
                bundle.getBoolean(MediaBridgeContract.K_SOURCE_SELECTED),
                bundle.getLong(MediaBridgeContract.K_SOURCE_CAPABILITIES)
        );
    }
}
