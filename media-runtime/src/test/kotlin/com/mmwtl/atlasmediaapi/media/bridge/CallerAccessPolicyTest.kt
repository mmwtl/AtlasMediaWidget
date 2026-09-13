package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertTrue
import org.junit.Test

class CallerAccessPolicyTest {
    @Test
    fun `open access policy allows any uid and packages`() {
        val policy = OpenCallerAccessPolicy()
        assertTrue(policy.isAllowed(1000, setOf("com.example.client")))
        assertTrue(policy.isAllowed(10123, setOf("com.mmwtl.atlasmediawidget")))
        assertTrue(policy.isAllowed(0, emptySet()))
    }
}
