package com.mmwtl.atlasmediaapi.media.bridge

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.util.Locale

object RadioCatalogCsv {
    private const val HEADER = "frequency_khz,name,band,cover"
    private const val MAX_STATIONS = 256
    private const val MAX_NAME_LENGTH = 80

    @Throws(IOException::class)
    fun read(source: Reader): List<RadioStation> {
        val reader = if (source is BufferedReader) source else BufferedReader(source)
        val rawHeader = reader.readLine()
            ?: throw IOException("Каталог пуст")
        val header = rawHeader.replace("\uFEFF", "").trim()
        if (header != HEADER) {
            throw IOException("Ожидается заголовок $HEADER, получено: $header")
        }

        val stations = ArrayList<RadioStation>()
        val seenFrequencies = HashSet<Int>()
        var line: String?
        var lineNumber = 1

        while (reader.readLine().also { line = it } != null) {
            lineNumber++
            val currentLine = line ?: continue
            if (currentLine.isBlank()) continue

            val fields = parseLine(currentLine)
            if (fields.size != 4) {
                throw IOException("Строка $lineNumber: требуется 4 поля (frequency_khz,name,band,cover)")
            }

            val frequency = fields[0].trim().toIntOrNull()
                ?: throw IOException("Строка $lineNumber: неверная частота '${fields[0]}'")

            val name = fields[1].trim()
            val band = fields[2].trim().uppercase(Locale.ROOT)
            val cover = fields[3].trim()

            if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
                throw IOException("Строка $lineNumber: название должно быть от 1 до $MAX_NAME_LENGTH символов")
            }

            val expectedBand = supportedRadioBandName(frequency)
                ?.takeIf { it == "FM" || it == "AM" }
                ?: throw IOException(
                    "Строка $lineNumber: частота $frequency вне поддерживаемых аналоговых диапазонов",
                )
            if (band != expectedBand) {
                throw IOException("Строка $lineNumber: для частоты $frequency диапазон должен быть $expectedBand, указано '$band'")
            }

            if (!seenFrequencies.add(frequency)) {
                throw IOException("Строка $lineNumber: дубликат частоты $frequency кГц")
            }

            stations.add(RadioStation(frequency, name, band, cover))
        }

        if (stations.isEmpty()) {
            throw IOException("Каталог не содержит радиостанций")
        }
        if (stations.size > MAX_STATIONS) {
            throw IOException("Превышен лимит станций ($MAX_STATIONS): в каталоге ${stations.size}")
        }
        return stations
    }

    private fun parseLine(line: String): List<String> {
        val result = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < line.length && line[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(char)
                }
            } else when (char) {
                ',' -> {
                    result.add(field.toString())
                    field.setLength(0)
                }
                '"' -> {
                    if (field.isEmpty()) {
                        quoted = true
                    } else {
                        field.append(char)
                    }
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) {
            throw IOException("Незакрытая кавычка в строке CSV: $line")
        }
        result.add(field.toString())
        return result
    }
}
