package com.mmwtl.atlasmediaapi.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneOsProbeReportTest {
    @Test
    fun defaultReportDoesNotExposeMediaPayload() {
        val report = OneOsProbeReport(
            status = OneOsProbeReport.Status.IDLE,
            elapsedMillis = 0L
        )

        assertEquals(OneOsProbeReport.Status.IDLE, report.status)
        assertEquals("none", report.lastSourceCallback)
        assertNull(report.errorCode)
    }
}
