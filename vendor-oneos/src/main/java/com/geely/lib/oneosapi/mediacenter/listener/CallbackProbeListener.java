package com.geely.lib.oneosapi.mediacenter.listener;

public interface CallbackProbeListener {
    void onMusicEvent(int source, String event);

    void onDeviceEvent(int source, String event, int value);

    void onRadioEvent(String event, int value);
}
