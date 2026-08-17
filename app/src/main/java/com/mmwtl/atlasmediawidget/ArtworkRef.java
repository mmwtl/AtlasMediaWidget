package com.mmwtl.atlasmediawidget;

import android.net.Uri;

final class ArtworkRef {
    enum Kind { NONE, CONTENT_URI, ASSET, FILE }

    static final ArtworkRef NONE = new ArtworkRef(Kind.NONE, "");

    final Kind kind;
    final String value;

    private ArtworkRef(Kind kind, String value) {
        this.kind = kind;
        this.value = value == null ? "" : value;
    }

    static ArtworkRef contentUri(String value) {
        return value == null || value.isBlank()
                ? NONE : new ArtworkRef(Kind.CONTENT_URI, value);
    }

    static ArtworkRef mediaUri(String value) {
        if (value == null || value.isBlank()) return NONE;
        Uri uri = Uri.parse(value);
        if (uri.getScheme() == null) return file(value);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return file(uri.getPath());
        }
        return contentUri(value);
    }

    static ArtworkRef asset(String value) {
        return value == null || value.isBlank() ? NONE : new ArtworkRef(Kind.ASSET, value);
    }

    static ArtworkRef file(String value) {
        return value == null || value.isBlank() ? NONE : new ArtworkRef(Kind.FILE, value);
    }

    boolean isEmpty() {
        return kind == Kind.NONE || value.isBlank();
    }

    String cacheKey() {
        return kind.name() + ':' + value;
    }
}
