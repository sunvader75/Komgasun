package com.sunvader.komgasun.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTypeTest {

    @Test
    fun testFromName_defaultToLocalOnNullOrUnknown() {
        assertEquals(ServerType.LOCAL, ServerType.fromName(null))
        assertEquals(ServerType.LOCAL, ServerType.fromName(""))
        assertEquals(ServerType.LOCAL, ServerType.fromName("invalid"))
    }

    @Test
    fun testFromName_caseInsensitive() {
        assertEquals(ServerType.LOCAL, ServerType.fromName("LOCAL"))
        assertEquals(ServerType.LOCAL, ServerType.fromName("local"))
        assertEquals(ServerType.REMOTE, ServerType.fromName("REMOTE"))
        assertEquals(ServerType.REMOTE, ServerType.fromName("remote"))
    }

    @Test
    fun testLabels() {
        assertEquals("Local (WiFi)", ServerType.LOCAL.label)
        assertEquals("Tailscale (Remoto)", ServerType.REMOTE.label)
    }
}
