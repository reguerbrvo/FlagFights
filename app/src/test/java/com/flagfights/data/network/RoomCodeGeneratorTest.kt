package com.flagfights.data.network

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCodeGeneratorTest {

    @Test
    fun `generate returns code with configured length and alphabet`() {
        val generator = RoomCodeGenerator(
            alphabet = charArrayOf('A', 'B', 'C'),
            codeLength = 8,
            random = Random(1234)
        )

        val code = generator.generate()

        assertEquals(8, code.length)
        assertTrue(code.all { it in setOf('A', 'B', 'C') })
    }
}
