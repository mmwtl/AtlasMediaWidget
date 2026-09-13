package com.ecarx.xui.adaptapi.diminteraction;

import android.content.Context;

/** Minimal DIM implementation used by AtlasMediaApi's media bridge. */
public class DIMInteractionImpl extends DimInteraction {
    private final MediaInteraction mediaInteraction;

    public DIMInteractionImpl(Context context) {
        this.mediaInteraction = new MediaInteraction(context);
    }

    @Override
    public IMediaInteraction getMediaInteraction() {
        return mediaInteraction;
    }

    @Override
    public int getShowPresentationOption() {
        return SHOW_PRESENTATION_NAVI_ROUTE;
    }

    @Override
    public int getSupportedRankingType() {
        return 0;
    }

    @Override
    public void registerInteractionCallback(IInteractionCallback callback) {
    }

    @Override
    public void unregisterInteractionCallback(IInteractionCallback callback) {
    }

}
