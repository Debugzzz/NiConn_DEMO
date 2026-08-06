package com.niconn.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraInfoParserTest {
    @Test
    fun `parses captured txt record`() {
        val info = CameraInfoParser.fromParts(
            instanceName = "Z50_2_8095684",
            port = 15740,
            txt = mapOf(
                "guid" to "04b00455-0000-1001-8001-3cbee158f492",
                "vid" to "A",
                "pid" to "455",
                "seq" to "64d504ea",
                "ver" to "1",
                "apps" to "PAIR",
            ),
        )
        assertEquals("Z50_2_8095684", info.instanceName)
        assertEquals(15740, info.port)
        assertEquals("04b00455-0000-1001-8001-3cbee158f492", info.guid)
        assertEquals("A", info.vid)
        assertEquals("455", info.pid)
        assertEquals("PAIR", info.apps)
    }

    @Test
    fun `defaults port when missing`() {
        val info = CameraInfoParser.fromParts("NIKON_1", 0, emptyMap())
        assertEquals(15740, info.port)
        assertNull(info.guid)
    }
}
