package com.mmwtl.atlasmediawidget;

final class MetadataLayout {
    private MetadataLayout() {}

    static int resolveTop(int requestedTop, int contentHeight,
            int corridorBottom, int safeBottom) {
        int centeredTop = requestedTop
                + Math.max(0, corridorBottom - requestedTop - contentHeight) / 2;
        return Math.min(centeredTop, safeBottom - contentHeight);
    }
}
