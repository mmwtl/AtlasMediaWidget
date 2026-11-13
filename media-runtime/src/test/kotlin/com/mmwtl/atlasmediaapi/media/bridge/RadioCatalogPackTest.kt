package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RadioCatalogPackTest {
    private lateinit var context: Context
    private lateinit var repository: RadioCatalogRepository
    private val radio7 = png(0xFF336699.toInt())
    private val vera = png(0xFF993366.toInt())

    private data class City(
        val key: String,
        val name: String,
        val region: String,
        val rows: String,
        val country: String = "",
    )

    private val penza = City("penza", "Пенза", "Пензенская область", "100100,Радио 7,FM,radio7.png\n94700,Радио Вера,FM,vera.png\n")
    private val kuznetsk = City("kuznetsk", "Кузнецк", "Пензенская область", "101500,Радио 7,FM,radio7.png\n")
    private val moscow = City("moscow", "Москва", "Москва и область", "104700,Радио 7,FM,radio7.png\n")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        listOf("custom_radio", "radio_catalogs", "radio_covers").forEach { File(context.filesDir, it).deleteRecursively() }
        repository = RadioCatalogRepository(context)
    }

    @Test
    fun `a pack is one entry with its cities and keeps the active catalog`() {
        val pack = repository.importZip(ByteArrayInputStream(pack(penza, kuznetsk, moscow))).getOrThrow().entry

        assertEquals("russia", pack.packId)
        assertEquals("Россия", pack.name)
        assertEquals(3, pack.cityCount)
        assertEquals(4, pack.stationCount)
        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertEquals(listOf(RadioCatalogRepository.BUILT_IN_ID, pack.id), info.catalogs.map { it.id })
        assertEquals(
            listOf(
                RadioPackCity("penza", "Пенза", "Пензенская область", 2),
                RadioPackCity("kuznetsk", "Кузнецк", "Пензенская область", 1),
                RadioPackCity("moscow", "Москва", "Москва и область", 1),
            ),
            repository.packCities(pack.id),
        )
        assertEquals(2, storedCovers().size)
    }

    @Test
    fun `activating a city of a pack serves its stations and covers`() {
        val pack = importPack(penza, moscow)

        repository.activateCatalog(pack.id, "moscow")

        val info = repository.getCatalogInfo()
        assertEquals(pack.id, info.activeId)
        assertEquals("moscow", info.activeCityKey)
        assertEquals("Москва", info.activeCityName)
        assertEquals("Москва и область", info.activeRegion)
        assertEquals(1, info.stationCount)
        assertTrue(info.description.contains("Россия"))
        assertArrayEquals(radio7, cover(104700))
        assertNull(repository.lookup(100100))

        val generation = repository.generation
        repository.activateCatalog(pack.id, "penza")
        assertTrue(repository.generation > generation)
        assertArrayEquals(vera, cover(94700))
    }

    @Test
    fun `a pack needs a known city and a single catalog takes none`() {
        val pack = importPack(penza)
        val single = repository.importCatalogZip(ByteArrayInputStream(single("90000,A,FM,\n")), name = "A").getOrThrow()

        assertTrue(runCatching { repository.activateCatalog(pack.id) }.isFailure)
        assertTrue(runCatching { repository.activateCatalog(pack.id, "moscow") }.isFailure)
        assertTrue(runCatching { repository.activateCatalog(single.id, "penza") }.isFailure)
        assertEquals(single.id, repository.getCatalogInfo().activeId)
    }

    @Test
    fun `updating a pack keeps the active city by key and the pack position`() {
        val own = repository.importCatalogZip(ByteArrayInputStream(single("90000,Своя,FM,\n")), name = "Свой").getOrThrow()
        val first = importPack(penza, kuznetsk)
        val tail = repository.importCatalogZip(ByteArrayInputStream(single("91000,Ещё,FM,\n")), name = "Ещё").getOrThrow()
        repository.activateCatalog(first.id, "kuznetsk")

        val updated = repository.importZip(
            ByteArrayInputStream(pack(kuznetsk.copy(rows = "101600,Радио 7,FM,radio7.png\n"), moscow)),
        ).getOrThrow().entry

        val info = repository.getCatalogInfo()
        assertEquals(updated.id, info.activeId)
        assertEquals("kuznetsk", info.activeCityKey)
        assertNull(repository.lookup(101500))
        assertNotNull(repository.lookup(101600))
        assertEquals(listOf(RadioCatalogRepository.BUILT_IN_ID, own.id, updated.id, tail.id), info.catalogs.map { it.id })
        assertEquals(listOf("kuznetsk", "moscow"), repository.packCities(updated.id).map { it.key })
        assertFalse(File(context.filesDir, "radio_catalogs/${first.id}").exists())
        assertEquals(1, storedCovers().size)
    }

    @Test
    fun `an update without the active city activates the built-in catalog`() {
        val first = importPack(penza, moscow)
        repository.activateCatalog(first.id, "penza")

        repository.importZip(ByteArrayInputStream(pack(moscow))).getOrThrow()

        assertEquals(RadioCatalogRepository.BUILT_IN_ID, repository.getCatalogInfo().activeId)
        assertEquals(RadioCatalogRepository.DEFAULT_BUILT_IN_CITY, repository.getCatalogInfo().activeCityKey)
    }

    @Test
    fun `an invalid pack is rejected without changing anything`() {
        val installed = importPack(penza)
        repository.activateCatalog(installed.id, "penza")
        val before = repository.getCatalogInfo()

        listOf(
            pack(moscow, manifest = { it.put("id", "Россия") }),
            pack(moscow, manifest = { it.put("format", "other") }),
            pack(moscow, moscow.copy(name = "Дубль")),
            pack(moscow.copy(rows = "104700,Радио 7,FM,missing.png\n")),
            pack(moscow, extra = mapOf("readme.txt" to byteArrayOf(1))),
            pack(moscow, manifest = { it.getJSONArray("catalogs").getJSONObject(0).put("file", "../x.csv") }),
            pack(moscow, manifest = { it.getJSONArray("catalogs").getJSONObject(0).put("file", "cities/none.csv") }),
        ).forEachIndexed { index, archive ->
            assertTrue("case $index", repository.importZip(ByteArrayInputStream(archive)).isFailure)
            assertEquals("case $index", before, repository.getCatalogInfo())
        }
        assertEquals("Радио 7", repository.lookup(100100)?.name)
    }

    @Test
    fun `a pack is renamed by its pack json and replaced only by a pack`() {
        val pack = importPack(penza, moscow)

        assertTrue(runCatching { repository.renameCatalog(pack.id, "Другое") }.isFailure)
        assertTrue(repository.importZip(ByteArrayInputStream(single("90000,A,FM,\n")), replaceId = pack.id).isFailure)
        assertTrue(repository.importZip(ByteArrayInputStream(pack(moscow)), replaceId = pack.id).isFailure)

        repository.activateCatalog(pack.id, "moscow")
        repository.deleteCatalog(pack.id)

        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertEquals(1, info.catalogs.size)
        assertTrue(storedCovers().isEmpty())
    }

    @Test
    fun `an exported pack imports back as an update of the same pack`() {
        val imported = importPack(penza, moscow)

        val output = ByteArrayOutputStream()
        repository.exportCatalogZip(output, imported.id)

        val entries = unzip(output.toByteArray())
        assertEquals(
            setOf("pack.json", "cities/penza.csv", "cities/moscow.csv", "covers/radio7.png", "covers/vera.png"),
            entries.keys,
        )
        val manifest = JSONObject(String(entries.getValue("pack.json"), StandardCharsets.UTF_8))
        assertEquals("Россия", manifest.getString("name"))
        assertEquals("Москва и область", manifest.getJSONArray("catalogs").getJSONObject(1).getString("region"))

        val again = repository.importZip(ByteArrayInputStream(output.toByteArray())).getOrThrow().entry
        assertEquals(listOf("penza", "moscow"), repository.packCities(again.id).map { it.key })
        assertEquals(2, repository.getCatalogInfo().catalogs.size)
    }

    @Test
    fun `a city's country survives import, restart and export`() {
        val minsk = City("minsk", "Минск", "Минск", "101200,Радио 7,FM,radio7.png\n", country = "Беларусь")
        val imported = importPack(penza.copy(country = "Россия"), minsk)

        val restarted = RadioCatalogRepository(context)

        assertEquals(
            listOf(
                RadioPackCity("penza", "Пенза", "Пензенская область", 2, "Россия"),
                RadioPackCity("minsk", "Минск", "Минск", 1, "Беларусь"),
            ),
            restarted.packCities(imported.id),
        )
        val output = ByteArrayOutputStream()
        restarted.exportCatalogZip(output, imported.id)
        val manifest = JSONObject(String(unzip(output.toByteArray()).getValue("pack.json"), StandardCharsets.UTF_8))
        assertEquals("Беларусь", manifest.getJSONArray("catalogs").getJSONObject(1).getString("country"))
    }

    @Test
    fun `the active city survives a restart`() {
        val imported = importPack(penza, moscow)
        repository.activateCatalog(imported.id, "penza")

        val restarted = RadioCatalogRepository(context)

        val info = restarted.getCatalogInfo()
        assertEquals(imported.id, info.activeId)
        assertEquals("Пенза", info.activeCityName)
        assertEquals(2, info.catalogs.last().cityCount)
        assertArrayEquals(vera, restarted.openCoverStream(restarted.lookup(94700)!!)!!.use { it.readBytes() })
    }

    @Test
    fun `a pack with thousands of cities and covers stays one small index entry`() {
        val covers = (0 until 500).associate { "logo$it.png" to png(0xFF000000.toInt() or it * 7919) }
        val cities = (0 until 3200).map { position ->
            val rows = (0 until 4).joinToString("") { station ->
                val logo = (position * 4 + station) % covers.size
                "${88000 + station * 100},Станция $logo,FM,logo$logo.png\n"
            }
            City("c$position", "Город $position", "Регион ${position % 85}", rows)
        }
        val archive = pack(*cities.toTypedArray(), coverFiles = covers)

        val started = System.nanoTime()
        val pack = repository.importZip(ByteArrayInputStream(archive)).getOrThrow().entry
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(3200, pack.cityCount)
        assertEquals(500, storedCovers().size)
        assertTrue("index.json stays small", File(context.filesDir, "radio_catalogs/index.json").length() < 4_096)
        repository.activateCatalog(pack.id, "c3199")
        assertEquals(4, repository.getCatalogInfo().stationCount)
        assertEquals(3200, repository.packCities(pack.id).size)
        println("3200-city pack imported in $elapsedMs ms")
    }

    private fun importPack(vararg cities: City): RadioCatalogEntry =
        repository.importZip(ByteArrayInputStream(pack(*cities))).getOrThrow().entry

    private fun pack(
        vararg cities: City,
        manifest: (JSONObject) -> Unit = {},
        extra: Map<String, ByteArray> = emptyMap(),
        coverFiles: Map<String, ByteArray> = mapOf("radio7.png" to radio7, "vera.png" to vera),
    ): ByteArray {
        val json = JSONObject().apply {
            put("format", "atlas-radio-pack")
            put("version", 1)
            put("id", "russia")
            put("name", "Россия")
            put("catalogs", JSONArray().apply {
                cities.forEach { city ->
                    put(JSONObject().apply {
                        put("key", city.key)
                        put("name", city.name)
                        if (city.country.isNotEmpty()) put("country", city.country)
                        put("region", city.region)
                        put("file", "cities/${city.key}.csv")
                    })
                }
            })
        }
        manifest(json)
        val files = linkedMapOf("pack.json" to json.toString().toByteArray(StandardCharsets.UTF_8))
        cities.forEach { city ->
            files["cities/${city.key}.csv"] = ("frequency_khz,name,band,cover\n" + city.rows).toByteArray(StandardCharsets.UTF_8)
        }
        coverFiles.forEach { (name, bytes) -> files["covers/$name"] = bytes }
        files.putAll(extra)
        return zip(files)
    }

    private fun single(rows: String): ByteArray =
        zip(mapOf("stations.csv" to ("frequency_khz,name,band,cover\n" + rows).toByteArray(StandardCharsets.UTF_8)))

    private fun zip(files: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun cover(frequencyKHz: Int): ByteArray =
        repository.openCoverStream(repository.lookup(frequencyKHz)!!)!!.use { it.readBytes() }

    private fun storedCovers(): List<String> =
        File(context.filesDir, "radio_covers").listFiles().orEmpty().map(File::getName)

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
