package com.mmwtl.atlasmediaapi.media.bridge

/**
 * Bridge allowlist for the raw integer carried by OneOS Frequency.
 *
 * The decompiled vendor class provides no unit or range constants. The wire contract uses kHz;
 * existing AtlasMediaApi behavior also supports the OneOS FM representation where 8_755 means
 * 87.55 MHz. Keeping each representation explicit prevents the exported service from forwarding
 * arbitrary positive integers to the OEM backend.
 */
private enum class RadioFrequencyKind(
    val bandName: String,
    val oneOsBand: Int,
) {
    AM_LONG_WAVE("AM", 2),
    AM_MEDIUM_WAVE("AM", 2),
    FM_LEGACY_HUNDREDTHS_MHZ("FM", 1),
    FM_OIRT_KHZ("FM", 1),
    FM_KHZ("FM", 1),
    DAB_KHZ("DAB", 3),
}

private fun radioFrequencyKind(frequency: Int): RadioFrequencyKind? = when (frequency) {
    in 149..284 -> RadioFrequencyKind.AM_LONG_WAVE
    in 500..1_800 -> RadioFrequencyKind.AM_MEDIUM_WAVE
    in 8_750..10_800 -> RadioFrequencyKind.FM_LEGACY_HUNDREDTHS_MHZ
    in 65_800..74_000 -> RadioFrequencyKind.FM_OIRT_KHZ
    in 87_500..108_000 -> RadioFrequencyKind.FM_KHZ
    in 174_000..240_000 -> RadioFrequencyKind.DAB_KHZ
    else -> null
}

fun isSupportedRadioFrequency(frequency: Int): Boolean = radioFrequencyKind(frequency) != null

fun isSupportedRadioFrequency(frequency: Int, oneOsBand: Int): Boolean =
    radioFrequencyKind(frequency)?.oneOsBand == oneOsBand

fun supportedRadioBandName(frequency: Int): String? = radioFrequencyKind(frequency)?.bandName

fun formatRadioFrequency(frequency: Int): String = when (radioFrequencyKind(frequency)) {
    RadioFrequencyKind.AM_LONG_WAVE,
    RadioFrequencyKind.AM_MEDIUM_WAVE -> "$frequency kHz"

    RadioFrequencyKind.FM_LEGACY_HUNDREDTHS_MHZ ->
        "${formatScaledRadioFrequency(frequency, 100)} MHz"

    RadioFrequencyKind.FM_OIRT_KHZ,
    RadioFrequencyKind.FM_KHZ,
    RadioFrequencyKind.DAB_KHZ -> "${formatScaledRadioFrequency(frequency, 1_000)} MHz"

    null -> ""
}

fun resolveRadioBandName(frequency: Int, oneOsBand: Int): String =
    supportedRadioBandName(frequency) ?: when (oneOsBand) {
        1 -> "FM"
        2 -> "AM"
        3 -> "DAB"
        else -> "BAND_$oneOsBand"
    }

private fun formatScaledRadioFrequency(value: Int, divisor: Int): String {
    val whole = value / divisor
    val remainder = value % divisor
    if (remainder == 0) return whole.toString()
    val fraction = remainder.toString()
        .padStart(divisor.toString().length - 1, '0')
        .trimEnd('0')
    return "$whole.$fraction"
}
