package com.mmwtl.atlasmediaapi.media.bridge

data class RadioStation(
    val frequencyKHz: Int,
    val name: String,
    val band: String,
    val coverFileName: String = "",
)
