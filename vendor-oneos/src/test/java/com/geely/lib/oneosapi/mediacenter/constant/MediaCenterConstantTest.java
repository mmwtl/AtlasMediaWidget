package com.geely.lib.oneosapi.mediacenter.constant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaCenterConstantTest {
    @Test
    public void audioSourceOrdinalsMatchOneOsWireValues() {
        assertEquals(0, MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN.ordinal());
        assertEquals(1, MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB.ordinal());
        assertEquals(2, MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT.ordinal());
        assertEquals(3, MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO.ordinal());
        assertEquals(4, MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE.ordinal());
        assertEquals(5, MediaCenterConstant.AudioSource.AUDIO_SOURCE_OTHER.ordinal());
        assertEquals(6, MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING.ordinal());
        assertEquals(7, MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA.ordinal());
    }

    @Test
    public void invalidValuesNormalizeToUnknown() {
        assertEquals(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
                MediaCenterConstant.getAudioSourceEnum(-1)
        );
        assertEquals(
                MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN,
                MediaCenterConstant.getAudioSourceEnum(100)
        );
        assertEquals(
                MediaCenterConstant.AppSource.UNKNOWN,
                MediaCenterConstant.getAppSourceEnum(-1)
        );
    }
}
