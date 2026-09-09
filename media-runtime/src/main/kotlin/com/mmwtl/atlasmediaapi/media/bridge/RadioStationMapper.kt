package com.mmwtl.atlasmediaapi.media.bridge

import com.geely.lib.oneosapi.mediacenter.bean.Frequency

private const val MAX_VENDOR_RADIO_TEXT_LENGTH = 128

internal fun mergeRadioStations(stations: List<Frequency>): List<Frequency> {
    val merged = mutableListOf<Frequency>()
    stations.forEach { candidate ->
        val existingIndex = merged.indexOfFirst { sameRadioStation(it, candidate) }
        if (existingIndex < 0) {
            merged += candidate
        } else if (radioMetadataScore(candidate) > radioMetadataScore(merged[existingIndex])) {
            merged[existingIndex] = candidate
        }
    }
    return merged
        .filter { isSupportedRadioFrequency(it.frequency, it.band) }
        .sortedWith(compareBy<Frequency> { it.band }.thenBy { it.frequency })
}

internal fun sameRadioStation(first: Frequency, second: Frequency): Boolean {
    if (first.band != second.band || first.frequency != second.frequency) return false
    val firstSelector = first.selector.safeRadioText()
    val secondSelector = second.selector.safeRadioText()
    if (firstSelector.isNotBlank() && secondSelector.isNotBlank()) {
        return firstSelector == secondSelector
    }
    val firstService = first.serviceName.safeRadioText()
    val secondService = second.serviceName.safeRadioText()
    return firstService.isBlank() || secondService.isBlank() || firstService == secondService
}

internal fun Frequency.toRadioStationSnapshot(
    catalogStation: RadioStation?,
    favorite: Boolean,
    artworkUri: String,
): RadioStationSnapshot {
    val formatted = formatRadioFrequency(frequency)
    val vendorServiceName = serviceName.safeRadioText()
    val vendorEnsembleName = ensembleName.safeRadioText()
    val displayName = catalogStation?.name?.trim().orEmpty().ifBlank {
        vendorServiceName.ifBlank { vendorEnsembleName.ifBlank { formatted } }
    }
    val safeSelector = selector.safeRadioText()
    return RadioStationSnapshot(
        id = "radio:$band:$frequency:${safeSelector.take(48)}:${vendorServiceName.take(48)}",
        frequencyKHz = frequency,
        formattedFrequency = formatted,
        band = band,
        bandName = catalogStation?.band ?: resolveRadioBandName(frequency, band),
        name = displayName,
        ensembleName = vendorEnsembleName,
        serviceName = vendorServiceName,
        genre = ensembleRdsPty.safeRadioText(),
        iconId = iconId,
        signalQuality = signalQuality,
        selector = safeSelector,
        favorite = favorite || isCollection,
        artworkUri = artworkUri,
    )
}

internal fun RadioStationTarget.toOneOsFrequency(): Frequency = Frequency(
    frequencyKHz,
    band,
    ensembleName,
    serviceName,
    genre,
    iconId,
    signalQuality,
    selector,
)

private fun radioMetadataScore(frequency: Frequency): Int =
    listOf(
        frequency.serviceName,
        frequency.ensembleName,
        frequency.ensembleRdsPty,
        frequency.selector,
    ).count { !it.isNullOrBlank() } + if (frequency.isCollection) 1 else 0

private fun String?.safeRadioText(): String =
    this?.trim()?.take(MAX_VENDOR_RADIO_TEXT_LENGTH).orEmpty()
