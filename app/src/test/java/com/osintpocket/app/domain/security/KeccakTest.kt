package com.osintpocket.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class KeccakTest {

    @Test fun emptyString() = assertEquals(
        "0eab42de4c3ceb9235fc91acffe746b29c29a8c366b7c60e4e67c466f36a4304c00fa9caf9d87976ba469bcbe06713b435f091ef2769fb160cdab33d3670680e",
        Keccak.keccak512Hex(""),
    )

    @Test fun abc() = assertEquals(
        "18587dc2ea106b9a1563e32b3312421ca164c7f1f07bc922a9c83d77cea3a1e5d0c69910739025372dc14ac9642629379540c17e2a65b19d77aa511a9d00bb96",
        Keccak.keccak512Hex("abc"),
    )

    @Test fun multiBlockInput() {
        // 200 bytes: cruza varios bloques de 72 bytes y el caso de relleno de un solo byte (71).
        assertEquals(128, Keccak.keccak512Hex("a".repeat(200)).length)
        assertEquals(128, Keccak.keccak512Hex("b".repeat(71)).length)
    }

    /** Coincide con el ejemplo de la documentación oficial de XposedOrNot (anon = 808d63ba47). */
    @Test fun xonPrefixMatchesOfficialExample() = assertEquals("808d63ba47", XonPassword.prefixFor("123456"))
}
