package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class BuiltInRadioPackTest {
    private lateinit var context: Context
    private lateinit var repository: RadioCatalogRepository

    private val minsk = "belarus-minsk-57e8660ca2"
    private val kostanay = "kazakhstan-kostanay-7372a66754"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        listOf("custom_radio", "radio_catalogs", "radio_covers").forEach { File(context.filesDir, it).deleteRecursively() }
        repository = RadioCatalogRepository(context)
    }

    @Test
    fun `the built-in catalog is the 6+ pack with Penza selected`() {
        val info = repository.getCatalogInfo()
        val builtIn = info.catalogs.first()

        assertEquals(RadioCatalogRepository.BUILT_IN_ID, builtIn.id)
        assertTrue(builtIn.builtIn && builtIn.isPack)
        assertEquals("atlas-radio-all", builtIn.packId)
        assertEquals(466, builtIn.cityCount)
        assertEquals(6740, builtIn.stationCount)
        assertEquals(RadioCatalogType.BUILT_IN, info.type)
        assertEquals(RadioCatalogRepository.DEFAULT_BUILT_IN_CITY, info.activeCityKey)
        assertEquals("Пенза", info.activeCityName)
        assertEquals("Пензенская область", info.activeRegion)
        assertEquals("Радио 7", repository.lookup(100100)?.name)
        assertNotNull(repository.openCoverStream(repository.lookup(100100)!!))
    }

    @Test
    fun `built-in cities are grouped by country`() {
        val cities = repository.packCities(RadioCatalogRepository.BUILT_IN_ID)

        assertEquals(listOf("Россия", "Казахстан", "Беларусь"), cities.map { it.country }.distinct())
        assertEquals(461, cities.count { it.country == "Россия" })
        assertEquals(4, cities.count { it.country == "Казахстан" })
        assertEquals(
            RadioPackCity(kostanay, "Костанай", "Костанайская область", 14, "Казахстан"),
            cities.single { it.key == kostanay },
        )
        assertEquals("Минск", cities.single { it.key == minsk }.region)
        assertTrue(cities.all { it.stationCount >= 6 })
    }

    @Test
    fun `Kazakhstan cities have current stations and usable artwork`() {
        val cities = repository.packCities(RadioCatalogRepository.BUILT_IN_ID)
        val expected = listOf(
            Triple("Астана", "Астана", 18),
            Triple("Кокшетау", "Акмолинская область", 9),
            Triple("Павлодар", "Павлодарская область", 13),
        )
        expected.forEach { (name, region, count) ->
            val city = cities.single { it.country == "Казахстан" && it.name == name }
            assertEquals(region, city.region)
            assertEquals(count, city.stationCount)
            repository.activateCatalog(RadioCatalogRepository.BUILT_IN_ID, city.key)
            assertEquals(count, repository.stations().size)
            repository.stations().forEach { station ->
                assertTrue(repository.openCoverStream(station)!!.use { it.readBytes().isNotEmpty() })
            }
            when (name) {
                "Астана" -> {
                    assertEquals("Радио Jibek Joly", repository.lookup(101400)?.name)
                    assertEquals("Той Думан", repository.lookup(103700)?.name)
                    assertEquals("LUX FM", repository.lookup(107700)?.name)
                }
                "Кокшетау" -> assertEquals(null, repository.lookup(101400)) // LUX is announced, not on air yet.
                "Павлодар" -> {
                    assertEquals("Радио Халык", repository.lookup(100500)?.name)
                    assertEquals("Ретро FM Казахстан", repository.lookup(101800)?.name)
                }
            }
        }
    }

    @Test
    fun `the built-in city is remembered across catalogs and restarts`() {
        repository.activateCatalog(RadioCatalogRepository.BUILT_IN_ID, minsk)
        val custom = repository.importCatalogZip(ByteArrayInputStream(single("90000,Custom,FM,\n"))).getOrThrow()
        assertEquals(custom.id, repository.getCatalogInfo().activeId)

        repository.restoreDefaultCatalog()
        assertEquals("Минск", repository.getCatalogInfo().activeCityName)

        repository.activateCatalog(custom.id)
        repository.deleteCatalog(custom.id)
        val restarted = RadioCatalogRepository(context).getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, restarted.activeId)
        assertEquals(minsk, restarted.activeCityKey)
    }

    @Test
    fun `an unknown built-in city is rejected and a dropped one falls back to Penza`() {
        assertTrue(runCatching { repository.activateCatalog(RadioCatalogRepository.BUILT_IN_ID, "nowhere") }.isFailure)

        val index = File(context.filesDir, "radio_catalogs/index.json")
        index.writeText(JSONObject(index.readText()).put("builtinCity", "removed-city").toString())
        val restarted = RadioCatalogRepository(context)

        assertEquals("Пенза", restarted.getCatalogInfo().activeCityName)
        assertEquals("Радио 7", restarted.lookup(100100)?.name)
    }

    @Test
    fun `the assets are the extracted 6+ pack and export rebuilds it`() {
        val archive = unzip(File("../docs/assets/radio-packs/atlas-radio-6plus.zip").readBytes())
        val output = ByteArrayOutputStream()
        repository.exportCatalogZip(output, RadioCatalogRepository.BUILT_IN_ID)
        val exported = unzip(output.toByteArray())

        assertEquals(archive.keys, exported.keys)
        archive.forEach { (name, bytes) -> assertArrayEquals(name, bytes, exported.getValue(name)) }
        assertEquals(
            "atlas-radio-pack",
            JSONObject(String(exported.getValue("pack.json"), StandardCharsets.UTF_8)).getString("format"),
        )
    }

    private fun single(rows: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("stations.csv"))
                zip.write(("frequency_khz,name,band,cover\n" + rows).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            output.toByteArray()
        }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) result[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return result
    }
}
