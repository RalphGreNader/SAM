package com.samvedamanikam.sam.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlTest {

    @Test
    fun addsTrailingSlashAndKeepsHttps() {
        assertEquals("https://samvedamanikam.com/", DeviceAuthClient.normalizeServerUrl("https://samvedamanikam.com"))
        assertEquals("https://samvedamanikam.com/", DeviceAuthClient.normalizeServerUrl("  https://samvedamanikam.com/ "))
    }

    @Test
    fun rejectsPlainHttp() {
        assertThrows(DeviceAuthException::class.java) { DeviceAuthClient.normalizeServerUrl("http://samvedamanikam.com/") }
        assertThrows(DeviceAuthException::class.java) { DeviceAuthClient.normalizeServerUrl("samvedamanikam.com") }
    }
}
