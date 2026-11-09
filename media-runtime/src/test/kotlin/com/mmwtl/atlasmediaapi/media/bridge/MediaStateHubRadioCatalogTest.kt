package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class MediaStateHubRadioCatalogTest {
    @Test
    fun `switching catalogs drops the artwork resolved from the previous catalog`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RadioCatalogRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        listOf("radio_catalogs", "radio_covers").forEach { File(context.filesDir, it).deleteRecursively() }
        val catalogs = RadioCatalogRepository(context)
        val first = catalogs.importCatalogZip(ByteArrayInputStream(zip("100100,Радио 7,FM,\n")), name = "A")
            .getOrThrow()
        catalogs.importCatalogZip(ByteArrayInputStream(zip("100100,Радио 7,FM,\n")), name = "B").getOrThrow()
        catalogs.activateCatalog(first.id)

        val repository = MediaStateRepository()
        val hub = MediaStateHub(
            context = RadioTitleContext(context),
            repository = repository,
            artworkRepository = ArtworkRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
            radioCatalogRepository = catalogs,
        )
        hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )
        hub.onOneOsRadioState(frequency(), playing = true)
        // The cover first catalog A resolved for this station.
        repository.update { it.copy(artworkUri = "content://covers/a.png", artworkRevision = it.artworkRevision + 1) }

        hub.refreshRadioState()
        assertEquals("content://covers/a.png", repository.snapshot().artworkUri)

        catalogs.activateCatalog(catalogs.getCatalogInfo().catalogs.last().id)
        hub.refreshRadioState()

        assertEquals("", repository.snapshot().artworkUri)
        assertEquals("Радио 7", repository.snapshot().title)
    }

    /** Module unit tests run without Android resources; the hub only needs the source title. */
    private class RadioTitleContext(base: Context) : android.content.ContextWrapper(base) {
        @Suppress("DEPRECATION")
        private val titles = object : android.content.res.Resources(
            base.resources.assets,
            base.resources.displayMetrics,
            base.resources.configuration,
        ) {
            override fun getText(id: Int): CharSequence = "Радио"
        }

        override fun getResources(): android.content.res.Resources = titles
    }

    private fun frequency(): Frequency = Frequency(100_100, 1, "", "Радио 7", "", 0, 80, "")

    private fun zip(rows: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("stations.csv"))
                zip.write(("frequency_khz,name,band,cover\n$rows").toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            output.toByteArray()
        }
}
