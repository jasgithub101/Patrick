package com.patrick.faceid.registration

import kotlin.random.Random

/**
 * Generates PLACEHOLDER health identifiers for the prototype.
 *
 * Deliberately prefixed with "DUMMY-" so a generated value can never be mistaken for, or used
 * as, a real ABHA number. Real ABHA numbers are 14 digits; nothing here should ever be fed to an
 * ABDM system. Real linkage is out of scope for Phase 1 and must go through the authorised
 * ABDM workflow with the person's consent.
 */
object DummyAbhaId {

    const val PREFIX = "DUMMY-"

    fun generate(random: Random = Random.Default): String =
        PREFIX + (1..14).map { random.nextInt(10) }.joinToString("")

    fun isDummy(value: String): Boolean = value.startsWith(PREFIX)
}
