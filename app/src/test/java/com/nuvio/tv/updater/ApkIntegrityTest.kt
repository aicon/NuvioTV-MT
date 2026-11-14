package com.nuvio.tv.updater

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkIntegrityTest {
    @Test
    fun `github sha256 prefix is normalized and a bare hex digest is accepted`() {
        val hex = "a".repeat(64)
        assertEquals(hex, ApkIntegrity.normalizeSha256("sha256:$hex"))
        assertEquals(hex, ApkIntegrity.normalizeSha256(hex.uppercase()))
    }

    @Test
    fun `size-looking or truncated digests are not treated as sha256`() {
        assertNull(ApkIntegrity.normalizeSha256(null))
        assertNull(ApkIntegrity.normalizeSha256("12345"))
        assertNull(ApkIntegrity.normalizeSha256("sha256:abcd"))
    }

    @Test
    fun `file hash matches the known sha256 of abc`() {
        val file = File.createTempFile("apk-integrity", ".bin")
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApkIntegrity.sha256Hex(file)
            )
        } finally {
            file.delete()
        }
    }
}
