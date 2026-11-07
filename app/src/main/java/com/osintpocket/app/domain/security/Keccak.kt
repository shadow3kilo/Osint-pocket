package com.osintpocket.app.domain.security

/**
 * Keccak-512 ORIGINAL (padding 0x01), no el SHA3-512 de FIPS 202 (padding 0x06).
 * XposedOrNot usa esta variante para su comprobación de contraseñas por prefijo.
 */
object Keccak {

    private const val RATE = 72 // (1600 - 2·512) / 8

    private val RC = longArrayOf(
        0x0000000000000001uL.toLong(), 0x0000000000008082uL.toLong(), 0x800000000000808AuL.toLong(),
        0x8000000080008000uL.toLong(), 0x000000000000808BuL.toLong(), 0x0000000080000001uL.toLong(),
        0x8000000080008081uL.toLong(), 0x8000000000008009uL.toLong(), 0x000000000000008AuL.toLong(),
        0x0000000000000088uL.toLong(), 0x0000000080008009uL.toLong(), 0x000000008000000AuL.toLong(),
        0x000000008000808BuL.toLong(), 0x800000000000008BuL.toLong(), 0x8000000000008089uL.toLong(),
        0x8000000000008003uL.toLong(), 0x8000000000008002uL.toLong(), 0x8000000000000080uL.toLong(),
        0x000000000000800AuL.toLong(), 0x800000008000000AuL.toLong(), 0x8000000080008081uL.toLong(),
        0x8000000000008080uL.toLong(), 0x0000000080000001uL.toLong(), 0x8000000080008008uL.toLong(),
    )

    /** Desplazamientos de rotación, índice x + 5·y. */
    private val ROT = intArrayOf(
        0, 1, 62, 28, 27,
        36, 44, 6, 55, 20,
        3, 10, 43, 25, 39,
        41, 45, 15, 21, 8,
        18, 2, 61, 56, 14,
    )

    fun keccak512(message: ByteArray): ByteArray {
        val padLen = RATE - (message.size % RATE)
        val data = message.copyOf(message.size + padLen)
        data[message.size] = (data[message.size].toInt() xor 0x01).toByte()
        data[data.size - 1] = (data[data.size - 1].toInt() xor 0x80).toByte()

        val state = LongArray(25)
        var off = 0
        while (off < data.size) {
            for (i in 0 until RATE / 8) state[i] = state[i] xor leLong(data, off + 8 * i)
            permute(state)
            off += RATE
        }

        val out = ByteArray(64)
        for (i in 0 until 8) for (b in 0 until 8) out[8 * i + b] = (state[i] ushr (8 * b)).toByte()
        return out
    }

    fun keccak512Hex(text: String): String = Hashing.hex(keccak512(text.toByteArray(Charsets.UTF_8)))

    private fun permute(a: LongArray) {
        val c = LongArray(5)
        val b = LongArray(25)
        for (round in 0 until 24) {
            for (x in 0 until 5) c[x] = a[x] xor a[x + 5] xor a[x + 10] xor a[x + 15] xor a[x + 20]
            for (x in 0 until 5) {
                val d = c[(x + 4) % 5] xor java.lang.Long.rotateLeft(c[(x + 1) % 5], 1)
                for (y in 0 until 5) a[x + 5 * y] = a[x + 5 * y] xor d
            }
            for (x in 0 until 5) for (y in 0 until 5) {
                b[y + 5 * ((2 * x + 3 * y) % 5)] = java.lang.Long.rotateLeft(a[x + 5 * y], ROT[x + 5 * y])
            }
            for (x in 0 until 5) for (y in 0 until 5) {
                a[x + 5 * y] = b[x + 5 * y] xor (b[(x + 1) % 5 + 5 * y].inv() and b[(x + 2) % 5 + 5 * y])
            }
            a[0] = a[0] xor RC[round]
        }
    }

    private fun leLong(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }
}

/** Prefijo que se envía a XposedOrNot: 10 caracteres hex en minúscula del Keccak-512. */
object XonPassword {
    const val PREFIX_LEN = 10
    fun prefixFor(password: String): String = Keccak.keccak512Hex(password).take(PREFIX_LEN)
}
