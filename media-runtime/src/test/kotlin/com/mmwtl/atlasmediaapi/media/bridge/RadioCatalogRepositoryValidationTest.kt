package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.graphics.Bitmap
import org.junit.After
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
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RadioCatalogRepositoryValidationTest {

    private lateinit var context: Context
    private lateinit var repository: RadioCatalogRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        listOf("custom_radio", "custom_radio_prev", "custom_radio_next").forEach {
            File(context.filesDir, it).deleteRecursively()
        }
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("staging_radio_") }
            ?.forEach { it.deleteRecursively() }
        repository = RadioCatalogRepository(context)
    }

    @After
    fun tearDown() {
        listOf("custom_radio", "custom_radio_prev", "custom_radio_next").forEach {
            File(context.filesDir, it).deleteRecursively()
        }
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("staging_radio_") }
            ?.forEach { it.deleteRecursively() }
    }

    @Test
    fun `duplicate canonical zip entries leave the current catalog unchanged`() {
        installCatalog(
            "90000,Old Station,FM,old.png\n",
            listOf("old.png"),
        )

        val duplicateZip = zipOf(
            ZipItem("stations.csv", csv("91000,New Station,FM,new.png\n").toByteArray(StandardCharsets.UTF_8)),
            ZipItem("stations.csv/", directory = true),
        )

        assertTrue(repository.importCustomZip(ByteArrayInputStream(duplicateZip)).isFailure)
        assertOldCatalogStillActive()
    }

    @Test
    fun `unsafe zip entry names are rejected and leave the current catalog unchanged`() {
        installCatalog(
            "90000,Old Station,FM,old.png\n",
            listOf("old.png"),
        )

        listOf(
            "/stations.csv",
            "C:/stations.csv",
            "covers\\evil.png",
            "covers/../stations.csv",
        ).forEach { unsafeName ->
            val unsafeZip = zipOf(
                ZipItem(unsafeName, byteArrayOf(1, 2, 3)),
            )
            assertTrue(
                "Expected unsafe entry to be rejected: $unsafeName",
                repository.importCustomZip(ByteArrayInputStream(unsafeZip)).isFailure,
            )
            assertOldCatalogStillActive()
        }
    }

    @Test
    fun `unknown root and cover files are rejected before replacing the current catalog`() {
        installCatalog(
            "90000,Old Station,FM,old.png\n",
            listOf("old.png"),
        )

        val unknownRootZip = catalogZip(
            "91000,New Station,FM,new.png\n",
            listOf("new.png"),
            extraItems = listOf(ZipItem("unexpected.txt", byteArrayOf(1))),
        )
        val unknownCoverZip = catalogZip(
            "91000,New Station,FM,new.png\n",
            listOf("new.png"),
            extraItems = listOf(ZipItem("covers/unexpected.txt", byteArrayOf(1))),
        )

        listOf(unknownRootZip, unknownCoverZip).forEach { invalidZip ->
            assertTrue(repository.importCustomZip(ByteArrayInputStream(invalidZip)).isFailure)
            assertOldCatalogStillActive()
        }
    }

    @Test
    fun `valid old archive with covers directory entry fully replaces the current catalog`() {
        installCatalog(
            "90000,Old Station,FM,old.png\n91000,Old Station Two,FM,old-two.png\n",
            listOf("old.png", "old-two.png"),
        )

        val replacement = catalogZip(
            "92000,Replacement Station,FM,new.png\n",
            listOf("new.png"),
        )
        assertTrue(repository.importCustomZip(ByteArrayInputStream(replacement)).isSuccess)

        assertNull(repository.lookup(90000))
        assertNull(repository.lookup(91000))
        assertNotNull(repository.lookup(92000))
        assertFalse(File(context.filesDir, "custom_radio/covers/old.png").exists())
        assertFalse(File(context.filesDir, "custom_radio/covers/old-two.png").exists())
        assertTrue(File(context.filesDir, "custom_radio/covers/new.png").isFile)
    }

    @Test
    fun `legacy archive may contain additional valid artwork`() {
        val archive = catalogZip(
            "92000,Station,FM,new.png\n",
            listOf("new.png", "unused.png"),
        )
        assertTrue(repository.importCustomZip(ByteArrayInputStream(archive)).isSuccess)
        assertTrue(File(context.filesDir, "custom_radio/covers/unused.png").isFile)
    }

    @Test
    fun `repository startup removes abandoned radio staging directories`() {
        val abandonedDirectory = File(context.filesDir, "staging_radio_abandoned").apply {
            mkdirs()
            resolve("partial.bin").writeBytes(byteArrayOf(1, 2, 3))
        }
        val similarlyNamedFile = File(context.filesDir, "staging_radio_marker").apply {
            writeText("keep")
        }

        RadioCatalogRepository(context)

        assertFalse(abandonedDirectory.exists())
        assertTrue(similarlyNamedFile.isFile)
    }

    private fun installCatalog(stations: String, coverNames: List<String>) {
        val result = repository.importCustomZip(
            ByteArrayInputStream(catalogZip(stations, coverNames)),
        )
        assertTrue(result.isSuccess)
    }

    private fun assertOldCatalogStillActive() {
        assertNotNull(repository.lookup(90000))
        assertNull(repository.lookup(91000))
        assertTrue(File(context.filesDir, "custom_radio/covers/old.png").isFile)
    }

    private fun catalogZip(
        stations: String,
        coverNames: List<String>,
        extraItems: List<ZipItem> = emptyList(),
    ): ByteArray {
        val items = mutableListOf(
            ZipItem("stations.csv", csv(stations).toByteArray(StandardCharsets.UTF_8)),
            ZipItem("covers/", directory = true),
        )
        items += coverNames.map { ZipItem("covers/$it", pngBytes()) }
        items += extraItems
        return zipOf(*items.toTypedArray())
    }

    private fun csv(rows: String): String =
        "frequency_khz,name,band,cover\n$rows"

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class ZipItem(
        val name: String,
        val content: ByteArray = ByteArray(0),
        val directory: Boolean = false,
    )

    private fun zipOf(vararg items: ZipItem): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                items.forEach { item ->
                    zip.putNextEntry(ZipEntry(item.name))
                    if (!item.directory) zip.write(item.content)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
