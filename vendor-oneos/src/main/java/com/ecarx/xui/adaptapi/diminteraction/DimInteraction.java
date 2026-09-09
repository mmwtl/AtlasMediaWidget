package com.ecarx.xui.adaptapi.diminteraction;

import android.content.Context;
import com.ecarx.xui.adaptapi.AdaptAPI;

public abstract class DimInteraction extends AdaptAPI {
    public static final int APP_TYPE_DEFAULT = 0;
    public static final int APP_TYPE_AMAP = 1;
    public static final int SHOW_PRESENTATION_ALWAYS = 2;
    public static final int SHOW_PRESENTATION_NAVI_ROUTE = 1;
    public static final int SHOW_PRESENTATION_NEVER = 3;

    public interface IInteractionCallback {
        void onShowPresentationOptionChanged(int option);
    }

    public static DimInteraction create(Context context) {
        return new DIMInteractionImpl(context);
    }

    public abstract IMediaInteraction getMediaInteraction();
    public abstract int getShowPresentationOption();
    public abstract int getSupportedRankingType();
    public abstract void registerInteractionCallback(IInteractionCallback callback);
    public abstract void unregisterInteractionCallback(IInteractionCallback callback);
}
