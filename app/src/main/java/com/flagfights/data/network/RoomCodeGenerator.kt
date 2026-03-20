package com.flagfights.data.network

import kotlin.random.Random

class RoomCodeGenerator(
    private val alphabet: CharArray = DEFAULT_ALPHABET,
    private val codeLength: Int = DEFAULT_CODE_LENGTH,
    private val random: Random = Random.Default
) {
    init {
        require(alphabet.isNotEmpty()) { "Alphabet must not be empty." }
        require(codeLength > 0) { "Code length must be greater than zero." }
    }

    fun generate(): String = buildString(codeLength) {
        repeat(codeLength) {
            append(alphabet[random.nextInt(alphabet.size)])
        }
    }

    companion object {
        const val DEFAULT_CODE_LENGTH = 6
        private val DEFAULT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    }
}
