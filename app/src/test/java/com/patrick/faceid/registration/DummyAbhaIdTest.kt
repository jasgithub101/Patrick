package com.patrick.faceid.registration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DummyAbhaIdTest {

    @Test fun isAlwaysMarkedAsDummySoItCannotPassAsARealAbhaNumber() {
        repeat(100) {
            val id = DummyAbhaId.generate()
            assertTrue("must carry the DUMMY- prefix: $id", DummyAbhaId.isDummy(id))
            assertTrue("must not be 14 bare digits: $id", id.any { !it.isDigit() })
        }
    }

    @Test fun hasFourteenDigitsAfterThePrefix() {
        val digits = DummyAbhaId.generate().removePrefix(DummyAbhaId.PREFIX)
        assertEquals(14, digits.length)
        assertTrue(digits.all { it.isDigit() })
    }

    @Test fun isReproducibleForAGivenSeed() {
        assertEquals(DummyAbhaId.generate(Random(42)), DummyAbhaId.generate(Random(42)))
    }

    @Test fun differentSeedsGiveDifferentIds() {
        assertTrue(DummyAbhaId.generate(Random(1)) != DummyAbhaId.generate(Random(2)))
    }

    @Test fun realLookingAbhaNumbersAreNotTreatedAsDummy() {
        assertFalse(DummyAbhaId.isDummy("91123456789012"))
    }
}
