package net.corda.virtualnode

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ShortHashTest {
    @Test
    fun `can create short hash`() {
        assertDoesNotThrow {
            ShortHash.of("12345678901234567890")
        }
    }

    @Test
    fun `can create short hash from hex string in lower case`() {
        assertDoesNotThrow {
            ShortHash.of("1234567890abcdef")
        }
    }

    @Test
    fun `can create short hash from hex string in caps`() {
        assertDoesNotThrow {
            ShortHash.of("1234567890ABCDEF")
        }
    }

    @Test
    fun `cannot create short hash if too short`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("12345678901")
        }
    }

    @Test
    fun `cannot create short hash if not a hex string`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("fishfishfishfish")
        }
    }

    @Test
    fun `cannot create short hash if not a hex string after char 12`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("123456789012xyz")
        }
    }

    @Test
    fun `comparison`() {
        assertThat(ShortHash.of("1234567890ab")).isEqualTo(ShortHash.of("1234567890ab"))
        assertThat(ShortHash.of("1234567890ab")).isNotEqualTo(ShortHash.of("ab1234567890"))
    }

    @Test
    fun `hex strings are uppercase`() {
        assertThat(ShortHash.of("abcdefabcdef").value).isEqualTo("ABCDEFABCDEF")
        assertThat(ShortHash.of("ABCDEFABCDEF").value).isEqualTo("ABCDEFABCDEF")
    }
}
