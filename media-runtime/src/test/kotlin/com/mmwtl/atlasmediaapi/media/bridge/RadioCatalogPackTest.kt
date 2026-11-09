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

    private data class City(val key: String, val name: String, val region: String, val rows: String)

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
    fun `a pack becomes one catalog per city with shared covers and keeps the active catalog`() {
        val imported = repository.importZip(ByteArrayInputStream(pack(penza, kuznetsk, moscow))).getOrThrow()

        assertEquals("russia", imported.packId)
        assertEquals("Россия", imported.packName)
        assertEquals(listOf("Пенза", "Кузнецк", "Москва"), imported.catalogs.map { it.name })
        assertEquals(4, imported.stationCount)
        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        val cities = info.catalogs.filter { it.packId == "russia" }
        assertEquals(listOf("Пензенская область", "Пензенская область", "Москва и область"), cities.map { it.region })
        assertTrue(cities.all { it.packName == "Россия" })
        assertEquals(2, storedCovers().size)

        repository.activateCatalog(cities.last().id)
        assertArrayEquals(radio7, cover(104700))
        assertNull(repository.lookup(100100))
        assertTrue(repository.getCatalogInfo().description.contains("Россия"))
    }

    @Test
    fun `updating a pack keeps the active city by key and the pack position`() {
        val own = repository.importCatalogZip(ByteArrayInputStream(single("90000,Своя,FM,\n")), name = "Свой").getOrThrow()
        val first = repository.importZip(ByteArrayInputStream(pack(penza, kuznetsk))).getOrThrow()
        val tail = repository.importCatalogZip(ByteArrayInputStream(single("91000,Ещё,FM,\n")), name = "Ещё").getOrThrow()
        repository.activateCatalog(first.catalogs.first { it.name == "Кузнецк" }.id)

        val updated = repository.importZip(
            ByteArrayInputStream(pack(kuznetsk.copy(rows = "101600,Радио 7,FM,radio7.png\n"), moscow)),
        ).getOrThrow()

        val info = repository.getCatalogInfo()
        val kuznetskId = updated.catalogs.first { it.name == "Кузнецк" }.id
        assertEquals(kuznetskId, info.activeId)
        assertNull(repository.lookup(101500))
        assertNotNull(repository.lookup(101600))
        assertEquals(
            listOf(RadioCatalogRepository.BUILT_IN_ID, own.id) + updated.catalogs.map { it.id } + tail.id,
            info.catalogs.map { it.id },
        )
        assertFalse(info.catalogs.any { it.name == "Пенза" })
        assertTrue(first.catalogs.none { old -> File(context.filesDir, "radio_catalogs/${old.id}").exists() })
        assertEquals(1, storedCovers().size)
    }

    @Test
    fun `an update without the active city activates the built-in catalog`() {
        val first = repository.importZip(ByteArrayInputStream(pack(penza, moscow))).getOrThrow()
        repository.activateCatalog(first.catalogs.first { it.name == "Пенза" }.id)

        repository.importZip(ByteArrayInputStream(pack(moscow))).getOrThrow()

        assertEquals(RadioCatalogRepository.BUILT_IN_ID, repository.getCatalogInfo().activeId)
    }

    @Test
    fun `an invalid pack is rejected without changing anything`() {
        val installed = repository.importZip(ByteArrayInputStream(pack(penza))).getOrThrow()
        repository.activateCatalog(installed.catalogs.single().id)
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
    fun `pack cities are only changed together with their pack`() {
        val city = repository.importZip(ByteArrayInputStream(pack(penza, moscow))).getOrThrow().catalogs.first()

        assertTrue(runCatching { repository.renameCatalog(city.id, "Другое") }.isFailure)
        assertTrue(runCatching { repository.deleteCatalog(city.id) }.isFailure)
        assertTrue(repository.importZip(ByteArrayInputStream(single("90000,A,FM,\n")), replaceId = city.id).isFailure)
        assertTrue(repository.importZip(ByteArrayInputStream(pack(moscow)), replaceId = city.id).isFailure)

        repository.activateCatalog(city.id)
        repository.deletePackOf(city.id)

        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertEquals(1, info.catalogs.size)
        assertTrue(storedCovers().isEmpty())
    }

    @Test
    fun `a pack city name may repeat a single catalog name`() {
        repository.importCatalogZip(ByteArrayInputStream(single("90000,A,FM,\n")), name = "Пенза").getOrThrow()

        val imported = repository.importZip(ByteArrayInputStream(pack(penza))).getOrThrow()

        assertEquals("Пенза", imported.catalogs.single().name)
    }

    @Test
    fun `the pack limit counts the cities of every catalog`() {
        repeat(RadioCatalogRepository.MAX_CUSTOM_CATALOGS - 1) {
            repository.importCatalogZip(ByteArrayInputStream(single("90000,S,FM,\n")), name = "C$it").getOrThrow()
        }

        assertTrue(repository.importZip(ByteArrayInputStream(pack(penza, moscow))).isFailure)
        assertTrue(repository.importZip(ByteArrayInputStream(pack(penza))).isSuccess)
    }

    @Test
    fun `an exported pack imports back as an update of the same pack`() {
        val imported = repository.importZip(ByteArrayInputStream(pack(penza, moscow))).getOrThrow()

        val output = ByteArrayOutputStream()
        repository.exportCatalogZip(output, imported.catalogs.last().id)

        val entries = unzip(output.toByteArray())
        assertEquals(
            setOf("pack.json", "cities/penza.csv", "cities/moscow.csv", "covers/radio7.png", "covers/vera.png"),
            entries.keys,
        )
        val manifest = JSONObject(String(entries.getValue("pack.json"), StandardCharsets.UTF_8))
        assertEquals("Россия", manifest.getString("name"))
        assertEquals("Москва и область", manifest.getJSONArray("catalogs").getJSONObject(1).getString("region"))

        val again = repository.importZip(ByteArrayInputStream(output.toByteArray())).getOrThrow()
        assertEquals(listOf("Пенза", "Москва"), again.catalogs.map { it.name })
        assertEquals(3, repository.getCatalogInfo().catalogs.size)
    }

    @Test
    fun `pack details survive a restart`() {
        val imported = repository.importZip(ByteArrayInputStream(pack(penza, moscow))).getOrThrow()
        repository.activateCatalog(imported.catalogs.first().id)

        val restarted = RadioCatalogRepository(context)

        val cities = restarted.getCatalogInfo().catalogs.filter { it.packId.isNotEmpty() }
        assertEquals(listOf("Пенза", "Москва"), cities.map { it.name })
        assertEquals(listOf("Пензенская область", "Москва и область"), cities.map { it.region })
        assertArrayEquals(vera, restarted.openCoverStream(restarted.lookup(94700)!!)!!.use { it.readBytes() })
    }

    private fun pack(
        vararg cities: City,
        manifest: (JSONObject) -> Unit = {},
        extra: Map<String, ByteArray> = emptyMap(),
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
        files["covers/radio7.png"] = radio7
        files["covers/vera.png"] = vera
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
