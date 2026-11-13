package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class RadioCatalogCollectionTest {
    private lateinit var context: Context
    private lateinit var repository: RadioCatalogRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        listOf("custom_radio", "custom_radio_prev", "custom_radio_next", "radio_catalogs", "radio_covers")
            .forEach { File(context.filesDir, it).deleteRecursively() }
        repository = RadioCatalogRepository(context)
    }

    @Test
    fun `catalogs sharing a cover store it once and each resolves it`() {
        val shared = png(0xFF336699.toInt())
        val penza = import("Пенза", "100100,Радио 7,FM,radio7.png\n", "radio7.png" to shared)
        val moscow = import("Москва", "104700,Радио 7,FM,r7.png\n", "r7.png" to shared)

        assertEquals(1, storedCovers().size)
        assertEquals(moscow.id, repository.getCatalogInfo().activeId)
        assertArrayEquals(shared, cover(104700))

        repository.activateCatalog(penza.id)
        assertNull(repository.lookup(104700))
        assertArrayEquals(shared, cover(100100))
    }

    @Test
    fun `deleting a catalog keeps covers that another catalog still uses`() {
        val shared = png(0xFF112233.toInt())
        val own = png(0xFF445566.toInt())
        val first = import("A", "90000,A1,FM,a.png\n91000,A2,FM,own.png\n", "a.png" to shared, "own.png" to own)
        import("B", "92000,B1,FM,b.png\n", "b.png" to shared)
        assertEquals(2, storedCovers().size)

        repository.deleteCatalog(first.id)

        assertEquals(1, storedCovers().size)
        assertArrayEquals(shared, cover(92000))
        assertFalse(File(context.filesDir, "radio_catalogs/${first.id}").exists())
    }

    @Test
    fun `deleting the active catalog activates the built-in one`() {
        val only = import("Only", "90000,Only,FM,\n")
        val generation = repository.generation

        repository.deleteCatalog(only.id)

        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogType.BUILT_IN, info.type)
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertEquals(listOf(RadioCatalogRepository.BUILT_IN_ID), info.catalogs.map { it.id })
        assertTrue(repository.generation > generation)
        assertTrue(storedCovers().isEmpty())
    }

    @Test
    fun `restoring the default catalog keeps imported catalogs`() {
        val custom = import("Custom", "90000,Custom,FM,\n")

        repository.restoreDefaultCatalog()

        val info = repository.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertTrue(info.catalogs.any { it.id == custom.id })
        repository.activateCatalog(custom.id)
        assertEquals("Custom", repository.lookup(90000)?.name)
    }

    @Test
    fun `replacing a catalog keeps its name, position and active state`() {
        val first = import("Первый", "90000,Old,FM,\n")
        val second = import("Второй", "91000,Second,FM,\n")
        repository.activateCatalog(first.id)

        val replaced = repository.importCatalogZip(
            ByteArrayInputStream(zip("92000,New,FM,\n")),
            replaceId = first.id,
            activate = false,
        ).getOrThrow()

        val info = repository.getCatalogInfo()
        assertEquals("Первый", replaced.name)
        assertNotEquals(first.id, replaced.id)
        assertEquals(listOf(RadioCatalogRepository.BUILT_IN_ID, replaced.id, second.id), info.catalogs.map { it.id })
        assertEquals(replaced.id, info.activeId)
        assertNull(repository.lookup(90000))
        assertEquals("New", repository.lookup(92000)?.name)
    }

    @Test
    fun `a failed replacement leaves the catalog untouched`() {
        val first = import("Первый", "90000,Old,FM,\n")

        val result = repository.importCatalogZip(
            ByteArrayInputStream(zip("92000,New,FM,missing.png\n")),
            replaceId = first.id,
        )

        assertTrue(result.isFailure)
        assertEquals(first.id, repository.getCatalogInfo().activeId)
        assertEquals("Old", repository.lookup(90000)?.name)
    }

    @Test
    fun `duplicate import names get a suffix and renaming rejects a taken name`() {
        val first = import("Москва", "90000,A,FM,\n")
        val second = import("москва", "91000,B,FM,\n")
        assertEquals("москва (2)", second.name)

        val rejected = runCatching { repository.renameCatalog(second.id, "МОСКВА") }
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)

        repository.renameCatalog(first.id, "  Москва\tцентр  ")
        assertEquals("Москва центр", repository.getCatalogInfo().catalogs.first { it.id == first.id }.name)
    }

    @Test
    fun `the built-in catalog cannot be renamed, replaced or deleted`() {
        assertTrue(runCatching { repository.renameCatalog(RadioCatalogRepository.BUILT_IN_ID, "X") }.isFailure)
        assertTrue(runCatching { repository.deleteCatalog(RadioCatalogRepository.BUILT_IN_ID) }.isFailure)
        assertTrue(
            repository.importCatalogZip(
                ByteArrayInputStream(zip("90000,A,FM,\n")),
                replaceId = RadioCatalogRepository.BUILT_IN_ID,
            ).isFailure,
        )
    }

    @Test
    fun `the number of imported catalogs is bounded`() {
        repeat(RadioCatalogRepository.MAX_CUSTOM_CATALOGS) { import("C$it", "90000,S,FM,\n") }

        val result = repository.importCatalogZip(ByteArrayInputStream(zip("90000,S,FM,\n")), name = "extra")

        assertTrue(result.isFailure)
        assertEquals(RadioCatalogRepository.MAX_CUSTOM_CATALOGS + 1, repository.getCatalogInfo().catalogs.size)
    }

    @Test
    fun `catalogs and the active one survive a restart`() {
        val first = import("A", "90000,A,FM,a.png\n", "a.png" to png(0xFF010203.toInt()))
        import("B", "91000,B,FM,\n")
        repository.activateCatalog(first.id)

        val restarted = RadioCatalogRepository(context)

        assertEquals(first.id, restarted.getCatalogInfo().activeId)
        assertEquals(
            listOf("Россия, Казахстан, Беларусь — 6+ станций", "A", "B"),
            restarted.getCatalogInfo().catalogs.map { it.name },
        )
        assertNotNull(restarted.openCoverStream(restarted.lookup(90000)!!))
    }

    @Test
    fun `startup removes directories and covers the index does not reference`() {
        import("A", "90000,A,FM,\n")
        val orphanDirectory = File(context.filesDir, "radio_catalogs/${"0".repeat(32)}").apply {
            mkdirs()
            resolve("stations.csv").writeText("frequency_khz,name,band,cover\n")
        }
        val partialDirectory = File(context.filesDir, "radio_catalogs/.tmp-partial").apply { mkdirs() }
        val orphanCover = File(context.filesDir, "radio_covers/${"a".repeat(64)}.png").apply { writeBytes(byteArrayOf(1)) }

        RadioCatalogRepository(context)

        assertFalse(orphanDirectory.exists())
        assertFalse(partialDirectory.exists())
        assertFalse(orphanCover.exists())
        assertTrue(File(context.filesDir, "radio_catalogs/index.json").isFile)
    }

    @Test
    fun `export restores the original archive layout and cover names`() {
        val bytes = png(0xFF778899.toInt())
        val catalog = import("A", "90000,A,FM,logo.png\n", "logo.png" to bytes)

        val output = ByteArrayOutputStream()
        repository.exportCatalogZip(output, catalog.id)

        val entries = unzip(output.toByteArray())
        assertEquals(setOf("stations.csv", "covers/logo.png"), entries.keys)
        assertArrayEquals(bytes, entries.getValue("covers/logo.png"))
        val reimported = repository.importCatalogZip(ByteArrayInputStream(output.toByteArray()), name = "B")
        assertTrue(reimported.isSuccess)
        assertEquals(1, storedCovers().size)
    }

    @Test
    fun `an active legacy catalog migrates as the active Мой каталог`() {
        val legacy = File(context.filesDir, "custom_radio").apply {
            resolve("covers").mkdirs()
            resolve("stations.csv").writeText("frequency_khz,name,band,cover\n90000,Legacy,FM,old.png\n")
            resolve("covers/old.png").writeBytes(png(0xFF0000FF.toInt()))
        }
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(RadioCatalogRepository.KEY_CUSTOM_CATALOG_ACTIVE, true).commit()
        File(context.filesDir, "radio_catalogs").deleteRecursively()
        File(context.filesDir, "radio_covers").deleteRecursively()

        val migrated = RadioCatalogRepository(context)

        val info = migrated.getCatalogInfo()
        assertEquals(RadioCatalogType.CUSTOM, info.type)
        assertEquals(RadioCatalogRepository.MIGRATED_CATALOG_NAME, info.catalogs.first { it.id == info.activeId }.name)
        assertEquals("Legacy", migrated.lookup(90000)?.name)
        assertNotNull(migrated.openCoverStream(migrated.lookup(90000)!!))
        assertFalse(legacy.exists())
    }

    @Test
    fun `an inactive legacy catalog migrates without becoming active`() {
        File(context.filesDir, "custom_radio").apply {
            mkdirs()
            resolve("stations.csv").writeText("frequency_khz,name,band,cover\n90000,Legacy,FM,\n")
        }
        File(context.filesDir, "radio_catalogs").deleteRecursively()

        val migrated = RadioCatalogRepository(context)

        val info = migrated.getCatalogInfo()
        assertEquals(RadioCatalogRepository.BUILT_IN_ID, info.activeId)
        assertEquals(2, info.catalogs.size)
    }

    @Test
    fun `catalog names are normalized and made unique`() {
        assertEquals("a b", RadioCatalogRepository.normalizeName("  a\u0000  b \n"))
        assertEquals(40, RadioCatalogRepository.normalizeName("x".repeat(60)).length)
        assertEquals("A (3)", RadioCatalogRepository.uniqueName("A", listOf("a", "A (2)")))
        assertTrue(RadioCatalogRepository.isValidCatalogId(RadioCatalogRepository.BUILT_IN_ID))
        assertFalse(RadioCatalogRepository.isValidCatalogId("../x"))
    }

    private fun import(name: String, rows: String, vararg covers: Pair<String, ByteArray>): RadioCatalogEntry =
        repository.importCatalogZip(ByteArrayInputStream(zip(rows, *covers)), name = name).getOrThrow()

    private fun cover(frequencyKHz: Int): ByteArray =
        repository.openCoverStream(repository.lookup(frequencyKHz)!!)!!.use { it.readBytes() }

    private fun storedCovers(): List<String> =
        File(context.filesDir, "radio_covers").listFiles().orEmpty().map(File::getName)

    private fun zip(rows: String, vararg covers: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("stations.csv"))
                zip.write(("frequency_khz,name,band,cover\n$rows").toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
                covers.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry("covers/$name"))
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
