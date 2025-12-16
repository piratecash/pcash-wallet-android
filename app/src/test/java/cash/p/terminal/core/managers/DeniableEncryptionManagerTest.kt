package cash.p.terminal.core.managers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeniableEncryptionManagerTest {

    // Valid size buckets for container (must match DeniableEncryptionManager)
    private val SIZE_BUCKETS = listOf(50_000, 100_000, 200_000, 500_000, 1_000_000, 5_000_000, 10_000_000)

    private fun isValidBucketSize(sizeInBytes: Int): Boolean {
        return sizeInBytes in SIZE_BUCKETS
    }

    // region Salt Generation

    @Test
    fun `generateSalt returns 32 bytes`() {
        val salt = DeniableEncryptionManager.generateSalt()
        assertEquals(32, salt.size)
    }

    @Test
    fun `generateSalt returns different values each call`() {
        val salt1 = DeniableEncryptionManager.generateSalt()
        val salt2 = DeniableEncryptionManager.generateSalt()
        assertTrue(!salt1.contentEquals(salt2))
    }

    // endregion

    // region Key Derivation

    @Test
    fun `deriveKey returns 32 bytes`() {
        val salt = DeniableEncryptionManager.generateSalt()
        val key = DeniableEncryptionManager.deriveKey("password", salt)
        assertEquals(32, key.size)
    }

    @Test
    fun `deriveKey is deterministic for same password and salt`() {
        val salt = DeniableEncryptionManager.generateSalt()
        val key1 = DeniableEncryptionManager.deriveKey("password", salt)
        val key2 = DeniableEncryptionManager.deriveKey("password", salt)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        val salt = DeniableEncryptionManager.generateSalt()
        val key1 = DeniableEncryptionManager.deriveKey("password1", salt)
        val key2 = DeniableEncryptionManager.deriveKey("password2", salt)
        assertTrue(!key1.contentEquals(key2))
    }

    @Test
    fun `deriveKey produces different keys for different salts`() {
        val salt1 = DeniableEncryptionManager.generateSalt()
        val salt2 = DeniableEncryptionManager.generateSalt()
        val key1 = DeniableEncryptionManager.deriveKey("password", salt1)
        val key2 = DeniableEncryptionManager.deriveKey("password", salt2)
        assertTrue(!key1.contentEquals(key2))
    }

    // endregion

    // region Single Message Container

    @Test
    fun `createContainerBytes with single message returns valid byte array`() {
        val message = "Hello, World!".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        assertNotNull(container)
        assertTrue(container.isNotEmpty())
        // Container size is dynamic, rounded to bucket (minimum 50KB)
        assertTrue(container.size >= 50_000)
        assertTrue(isValidBucketSize(container.size))
    }

    @Test
    fun `extractMessageFromBytes returns correct data for single message`() {
        val originalMessage = "Hello, World!".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = originalMessage,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "password")

        assertNotNull(extracted)
        assertArrayEquals(originalMessage, extracted)
    }

    @Test
    fun `extractMessageFromBytes returns null for wrong password`() {
        val message = "Secret message".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "correctPassword",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "wrongPassword")

        assertNull(extracted)
    }

    // endregion

    // region Dual Message Container

    @Test
    fun `createContainerBytes with two messages succeeds`() {
        val message1 = "Public message".toByteArray()
        val message2 = "Hidden message".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message1,
            password1 = "password1",
            message2 = message2,
            password2 = "password2",
            salt = salt
        )

        assertNotNull(container)
        assertTrue(container.size >= 50_000) // Minimum 50KB
        assertTrue(isValidBucketSize(container.size))
    }

    @Test
    fun `extractMessageFromBytes returns correct data for each password in dual container`() {
        val message1 = "First message".toByteArray()
        val message2 = "Second hidden message".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message1,
            password1 = "password1",
            message2 = message2,
            password2 = "password2",
            salt = salt
        )

        val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, "password1")
        val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, "password2")

        assertNotNull(extracted1)
        assertNotNull(extracted2)
        assertArrayEquals(message1, extracted1)
        assertArrayEquals(message2, extracted2)
    }

    @Test
    fun `dual container - wrong password for either returns null`() {
        val message1 = "Public".toByteArray()
        val message2 = "Hidden".toByteArray()
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message1,
            password1 = "pass1",
            message2 = message2,
            password2 = "pass2",
            salt = salt
        )

        val wrongExtract = DeniableEncryptionManager.extractMessageFromBytes(container, "wrongPassword")
        assertNull(wrongExtract)
    }

    // endregion

    // region Password Collision Detection

    @Test(expected = DeniableEncryptionManager.PasswordCollisionException::class)
    fun `createContainerBytes throws PasswordCollisionException for identical passwords`() {
        val salt = DeniableEncryptionManager.generateSalt()

        DeniableEncryptionManager.createContainerBytes(
            message1 = "msg1".toByteArray(),
            password1 = "samePassword",
            message2 = "msg2".toByteArray(),
            password2 = "samePassword",
            salt = salt
        )
    }

    @Test
    fun `large payloads with two passwords - collision detection works`() {
        // Large payloads (~30KB each) increase collision probability
        val largeMessage1 = ByteArray(30_000) { 'A'.code.toByte() }
        val largeMessage2 = ByteArray(30_000) { 'B'.code.toByte() }

        var collisionCount = 0
        var successCount = 0
        val attempts = 20

        // Try multiple times with different salts to observe collision behavior
        repeat(attempts) {
            val salt = DeniableEncryptionManager.generateSalt()
            try {
                val container = DeniableEncryptionManager.createContainerBytes(
                    message1 = largeMessage1,
                    password1 = "password1",
                    message2 = largeMessage2,
                    password2 = "password2",
                    salt = salt
                )
                // Verify both messages can be extracted
                val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, "password1")
                val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, "password2")
                assertNotNull(extracted1)
                assertNotNull(extracted2)
                assertArrayEquals(largeMessage1, extracted1)
                assertArrayEquals(largeMessage2, extracted2)
                successCount++
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                collisionCount++
            }
        }

        // With large payloads, we expect some collisions but also some successes
        // This test verifies collision detection is working
        assertTrue("Expected at least one success in $attempts attempts", successCount > 0)
    }

    @Test
    fun `changing salt can resolve collision`() {
        val message1 = ByteArray(20_000) { 'X'.code.toByte() }
        val message2 = ByteArray(20_000) { 'Y'.code.toByte() }
        val password1 = "firstPassword"
        val password2 = "secondPassword"

        var resolved = false
        var attempts = 0
        val maxAttempts = 50

        // Keep trying with different salts until we find one without collision
        while (!resolved && attempts < maxAttempts) {
            val salt = DeniableEncryptionManager.generateSalt()
            try {
                val container = DeniableEncryptionManager.createContainerBytes(
                    message1 = message1,
                    password1 = password1,
                    message2 = message2,
                    password2 = password2,
                    salt = salt
                )
                // Success - verify data integrity
                val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, password1)
                val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, password2)
                assertArrayEquals(message1, extracted1)
                assertArrayEquals(message2, extracted2)
                resolved = true
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                attempts++
            }
        }

        assertTrue("Should resolve collision within $maxAttempts attempts by changing salt", resolved)
    }

    @Test
    fun `two passwords with moderate payloads succeed`() {
        // ~10KB payloads should have low collision probability
        val message1 = ByteArray(10_000) { (it % 256).toByte() }
        val message2 = ByteArray(10_000) { ((it + 128) % 256).toByte() }

        // May need a few attempts due to random offset collision
        var success = false
        repeat(10) {
            if (success) return@repeat
            val testSalt = DeniableEncryptionManager.generateSalt()
            try {
                val container = DeniableEncryptionManager.createContainerBytes(
                    message1 = message1,
                    password1 = "alpha",
                    message2 = message2,
                    password2 = "beta",
                    salt = testSalt
                )

                val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, "alpha")
                val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, "beta")

                assertNotNull(extracted1)
                assertNotNull(extracted2)
                assertArrayEquals(message1, extracted1)
                assertArrayEquals(message2, extracted2)
                success = true
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                // Try again with different salt
            }
        }

        assertTrue("Two 10KB payloads should succeed within 10 attempts", success)
    }

    @Test
    fun `collision exception message is user-friendly`() {
        val salt = DeniableEncryptionManager.generateSalt()

        try {
            DeniableEncryptionManager.createContainerBytes(
                message1 = "msg1".toByteArray(),
                password1 = "samePassword",
                message2 = "msg2".toByteArray(),
                password2 = "samePassword",
                salt = salt
            )
            assertTrue("Should have thrown PasswordCollisionException", false)
        } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
            // Verify exception message is helpful
            assertTrue(e.message?.contains("password") == true || e.message?.contains("Password") == true)
        }
    }

    @Test
    fun `very large payloads have high collision probability`() {
        // ~45KB payloads - very likely to collide with OFFSET_MODULO = 100,000
        val veryLargeMessage1 = ByteArray(45_000) { 'A'.code.toByte() }
        val veryLargeMessage2 = ByteArray(45_000) { 'B'.code.toByte() }

        var collisionCount = 0
        val attempts = 10

        repeat(attempts) {
            val salt = DeniableEncryptionManager.generateSalt()
            try {
                DeniableEncryptionManager.createContainerBytes(
                    message1 = veryLargeMessage1,
                    password1 = "pass1",
                    message2 = veryLargeMessage2,
                    password2 = "pass2",
                    salt = salt
                )
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                collisionCount++
            }
        }

        // With two 45KB blocks in 100KB offset space, collisions should be frequent
        // This documents expected behavior, not a bug
        assertTrue("Expected high collision rate with very large payloads, got $collisionCount/$attempts",
            collisionCount >= attempts / 2)
    }

    // endregion

    // region Invalid Container Handling

    @Test
    fun `extractMessageFromBytes returns null for empty container`() {
        val result = DeniableEncryptionManager.extractMessageFromBytes(ByteArray(0), "password")
        assertNull(result)
    }

    @Test
    fun `extractMessageFromBytes returns null for too short container`() {
        val result = DeniableEncryptionManager.extractMessageFromBytes(ByteArray(1000), "password")
        assertNull(result)
    }

    @Test
    fun `extractMessageFromBytes returns null for too large container`() {
        val tooLarge = ByteArray(15_000_000) // More than MAX_CONTAINER_SIZE
        val result = DeniableEncryptionManager.extractMessageFromBytes(tooLarge, "password")
        assertNull(result)
    }

    // endregion

    // region Round-trip Tests with Various Data

    @Test
    fun `round-trip with empty message`() {
        val message = ByteArray(0)
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "password")

        assertNotNull(extracted)
        assertEquals(0, extracted!!.size)
    }

    @Test
    fun `round-trip with binary data`() {
        val message = ByteArray(256) { it.toByte() }
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "password")

        assertNotNull(extracted)
        assertArrayEquals(message, extracted)
    }

    @Test
    fun `round-trip with JSON payload`() {
        val jsonPayload = """{"wallets":[{"name":"Main","type":"mnemonic"}],"version":4}"""
        val message = jsonPayload.toByteArray(Charsets.UTF_8)
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "backupPassword123",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "backupPassword123")

        assertNotNull(extracted)
        assertEquals(jsonPayload, String(extracted!!, Charsets.UTF_8))
    }

    @Test
    fun `round-trip with unicode characters`() {
        val unicodeMessage = "Hello 世界 🔐 Привет مرحبا"
        val message = unicodeMessage.toByteArray(Charsets.UTF_8)
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "пароль密码",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "пароль密码")

        assertNotNull(extracted)
        assertEquals(unicodeMessage, String(extracted!!, Charsets.UTF_8))
    }

    @Test
    fun `round-trip with mnemonic-like payload`() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val message = mnemonic.toByteArray(Charsets.UTF_8)
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "walletPassword",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "walletPassword")

        assertNotNull(extracted)
        assertEquals(mnemonic, String(extracted!!, Charsets.UTF_8))
    }

    // endregion

    // region Salt Validation

    @Test(expected = IllegalArgumentException::class)
    fun `createContainerBytes throws for wrong salt size`() {
        val wrongSizeSalt = ByteArray(16) // Should be 32

        DeniableEncryptionManager.createContainerBytes(
            message1 = "test".toByteArray(),
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = wrongSizeSalt
        )
    }

    // endregion

    // region Container Properties

    @Test
    fun `container size is rounded to valid bucket`() {
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = "test".toByteArray(),
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        // Container size should be a valid bucket size
        assertTrue("Container size ${container.size} should be a valid bucket", isValidBucketSize(container.size))
    }

    @Test
    fun `larger payload produces larger container bucket`() {
        val salt = DeniableEncryptionManager.generateSalt()

        // Small message
        val smallContainer = DeniableEncryptionManager.createContainerBytes(
            message1 = "tiny".toByteArray(),
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        // Large message (~50KB)
        val largeMessage = ByteArray(50_000) { 'X'.code.toByte() }
        val largeContainer = DeniableEncryptionManager.createContainerBytes(
            message1 = largeMessage,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        // Both should be valid bucket sizes
        assertTrue(isValidBucketSize(smallContainer.size))
        assertTrue(isValidBucketSize(largeContainer.size))

        // Large container should be bigger or equal (depending on offset)
        assertTrue(largeContainer.size >= smallContainer.size)
    }

    @Test
    fun `different containers have different content due to random noise`() {
        val salt = DeniableEncryptionManager.generateSalt()

        val container1 = DeniableEncryptionManager.createContainerBytes(
            message1 = "test".toByteArray(),
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val container2 = DeniableEncryptionManager.createContainerBytes(
            message1 = "test".toByteArray(),
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        // Containers should differ due to random padding and nonces
        assertTrue(!container1.contentEquals(container2))
    }

    // endregion

    // region Plausible Deniability

    @Test
    fun `containers with same size bucket are indistinguishable`() {
        val salt = DeniableEncryptionManager.generateSalt()

        val singleContainer = DeniableEncryptionManager.createContainerBytes(
            message1 = "public".toByteArray(),
            password1 = "pass1",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val dualContainer = DeniableEncryptionManager.createContainerBytes(
            message1 = "public".toByteArray(),
            password1 = "pass1",
            message2 = "secret".toByteArray(),
            password2 = "pass2",
            salt = salt
        )

        // Both should have same salt (first 32 bytes)
        assertTrue(singleContainer.copyOfRange(0, 32).contentEquals(dualContainer.copyOfRange(0, 32)))

        // Both should be valid bucket sizes
        assertTrue(isValidBucketSize(singleContainer.size))
        assertTrue(isValidBucketSize(dualContainer.size))

        // Size buckets provide deniability - can't tell from size alone
        // if there's one message or two (both fall in same or similar buckets)
    }

    @Test
    fun `container size uses buckets for deniability`() {
        val salt = DeniableEncryptionManager.generateSalt()

        // Create multiple containers with slightly different payload sizes
        // All should round to same bucket if within range
        val payloads = listOf(10, 50, 100, 200, 500)

        val containers = payloads.map { size ->
            val message = ByteArray(size) { 'X'.code.toByte() }
            DeniableEncryptionManager.createContainerBytes(
                message1 = message,
                password1 = "password",
                message2 = null,
                password2 = null,
                salt = salt
            )
        }

        // All should be valid bucket sizes
        containers.forEach { container ->
            assertTrue(isValidBucketSize(container.size))
        }
    }

    // endregion

    // region Retry Mechanism Tests

    @Test
    fun `retry with new salt resolves collision for large payloads`() {
        // Simulate the retry mechanism used in BackupProvider.createFullBackupV4Binary
        // Large payloads (~25KB each) have significant collision probability
        val payload1 = ByteArray(25_000) { 'A'.code.toByte() }
        val payload2 = ByteArray(25_000) { 'B'.code.toByte() }
        val password1 = "mainPassword"
        val password2 = "duressPassword"

        val maxRetries = 10
        var lastException: DeniableEncryptionManager.PasswordCollisionException? = null
        var successfulContainer: ByteArray? = null

        repeat(maxRetries) {
            val salt = DeniableEncryptionManager.generateSalt()
            try {
                successfulContainer = DeniableEncryptionManager.createContainerBytes(
                    message1 = payload1,
                    password1 = password1,
                    message2 = payload2,
                    password2 = password2,
                    salt = salt
                )
                return@repeat // Success, exit the loop
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                lastException = e
                // Continue to next retry with new salt
            }
        }

        // Should have succeeded within maxRetries
        assertNotNull("Retry mechanism should succeed within $maxRetries attempts", successfulContainer)

        // Verify both messages can be extracted
        val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(successfulContainer!!, password1)
        val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(successfulContainer!!, password2)

        assertNotNull(extracted1)
        assertNotNull(extracted2)
        assertArrayEquals(payload1, extracted1)
        assertArrayEquals(payload2, extracted2)
    }

    @Test
    fun `retry mechanism succeeds for realistic backup sizes`() {
        // Simulate realistic full backup JSON payload (~15KB typical)
        val realisticPayload1 = buildRealisticBackupPayload(15_000)
        val realisticPayload2 = buildRealisticBackupPayload(10_000)

        var successCount = 0
        val totalRuns = 5

        repeat(totalRuns) {
            var succeeded = false
            repeat(10) { // Max 10 retries per run
                if (succeeded) return@repeat
                val salt = DeniableEncryptionManager.generateSalt()
                try {
                    val container = DeniableEncryptionManager.createContainerBytes(
                        message1 = realisticPayload1,
                        password1 = "myMainPassword123",
                        message2 = realisticPayload2,
                        password2 = "myDuressPassword456",
                        salt = salt
                    )

                    // Verify round-trip
                    val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, "myMainPassword123")
                    val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, "myDuressPassword456")

                    assertArrayEquals(realisticPayload1, extracted1)
                    assertArrayEquals(realisticPayload2, extracted2)
                    succeeded = true
                    successCount++
                } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                    // Retry with new salt
                }
            }
        }

        // All runs should succeed with retry mechanism
        assertEquals("All $totalRuns runs should succeed with retry mechanism", totalRuns, successCount)
    }

    @Test
    fun `collision probability decreases with smaller payloads`() {
        // Small payloads (~5KB) should rarely collide
        val smallPayload1 = ByteArray(5_000) { 'X'.code.toByte() }
        val smallPayload2 = ByteArray(5_000) { 'Y'.code.toByte() }

        var collisionCount = 0
        val attempts = 20

        repeat(attempts) {
            val salt = DeniableEncryptionManager.generateSalt()
            try {
                DeniableEncryptionManager.createContainerBytes(
                    message1 = smallPayload1,
                    password1 = "password1",
                    message2 = smallPayload2,
                    password2 = "password2",
                    salt = salt
                )
            } catch (e: DeniableEncryptionManager.PasswordCollisionException) {
                collisionCount++
            }
        }

        // With 5KB payloads and 100KB offset space, collision rate should be low
        assertTrue("Collision rate should be low for small payloads: $collisionCount/$attempts",
            collisionCount < attempts / 2)
    }

    private fun buildRealisticBackupPayload(targetSize: Int): ByteArray {
        // Build a realistic JSON-like payload
        val sb = StringBuilder()
        sb.append("""{"wallets":[""")
        while (sb.length < targetSize - 100) {
            sb.append("""{"name":"Wallet","type":"mnemonic","crypto":{"cipher":"aes-128-ctr"}},""")
        }
        sb.append("""{"name":"Last"}],"version":4,"align_payload":"""")
        // Pad to target size
        while (sb.length < targetSize - 2) {
            sb.append('X')
        }
        sb.append("\"}")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `extractMessageFromBytes with moderately sized payload`() {
        // ~1KB payload
        val message = ByteArray(1024) { (it % 256).toByte() }
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = message,
            password1 = "password",
            message2 = null,
            password2 = null,
            salt = salt
        )

        val extracted = DeniableEncryptionManager.extractMessageFromBytes(container, "password")

        assertNotNull(extracted)
        assertArrayEquals(message, extracted)
    }

    @Test
    fun `dual container with different sized messages`() {
        val smallMessage = "small".toByteArray()
        val largerMessage = ByteArray(500) { 'X'.code.toByte() }
        val salt = DeniableEncryptionManager.generateSalt()

        val container = DeniableEncryptionManager.createContainerBytes(
            message1 = smallMessage,
            password1 = "pass1",
            message2 = largerMessage,
            password2 = "pass2",
            salt = salt
        )

        val extracted1 = DeniableEncryptionManager.extractMessageFromBytes(container, "pass1")
        val extracted2 = DeniableEncryptionManager.extractMessageFromBytes(container, "pass2")

        assertNotNull(extracted1)
        assertNotNull(extracted2)
        assertArrayEquals(smallMessage, extracted1)
        assertArrayEquals(largerMessage, extracted2)
    }

    // endregion
}
