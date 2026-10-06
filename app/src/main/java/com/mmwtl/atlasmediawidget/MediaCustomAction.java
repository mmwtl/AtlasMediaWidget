package com.mmwtl.atlasmediawidget;

import android.os.Bundle;

/**
 * A button the active player publishes in its media session. The card shows the player's own icon
 * and sends {@link #action} back unchanged; what it does (like, shuffle, …) is up to the player.
 */
final class MediaCustomAction {
    final String action;
    final String name;
    final int iconResId;
    final String ownerPackage;

    MediaCustomAction(String action, String name, int iconResId, String ownerPackage) {
        this.action = action == null ? "" : action;
        this.name = name == null ? "" : name;
        this.iconResId = iconResId;
        this.ownerPackage = ownerPackage == null ? "" : ownerPackage;
    }

    static MediaCustomAction fromBundle(Bundle bundle) {
        return new MediaCustomAction(
                bundle.getString(MediaBridgeContract.K_CUSTOM_ACTION_ID),
                bundle.getString(MediaBridgeContract.K_CUSTOM_ACTION_NAME),
                bundle.getInt(MediaBridgeContract.K_CUSTOM_ACTION_ICON),
                bundle.getString(MediaBridgeContract.K_CUSTOM_ACTION_PACKAGE));
    }

    boolean isUsable() {
        return !action.isBlank() && iconResId != 0 && !ownerPackage.isBlank();
    }

    String label() {
        return name.isBlank() ? action : name;
    }
}
