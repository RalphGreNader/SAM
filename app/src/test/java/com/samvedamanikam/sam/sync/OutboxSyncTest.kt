package com.samvedamanikam.sam.sync

import com.samvedamanikam.sam.data.ApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxSyncTest {

    @Test
    fun offlineUnauthorizedAndServerErrorsAreRetried() {
        assertTrue(OutboxSync.isTransient(ApiException("offline", offline = true)))
        listOf(401, 408, 429, 500, 503).forEach { assertTrue("$it", OutboxSync.isTransient(ApiException("x", code = it))) }
    }

    @Test
    fun rejectedRequestsAreNotRetried() {
        listOf(400, 404, 409).forEach { assertFalse("$it", OutboxSync.isTransient(ApiException("x", code = it))) }
    }
}
