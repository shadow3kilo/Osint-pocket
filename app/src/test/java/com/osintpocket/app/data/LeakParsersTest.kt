package com.osintpocket.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeakParsersTest {

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // Ejemplos tomados de la documentación de XposedOrNot (api_doc).
    @Test fun xonFound() {
        val r = LeakParsers.xon(json("""
            {"BreachesSummary":{"site":"SweClockers"},
             "ExposedBreaches":{"breaches_details":[{"breach":"SweClockers","xposed_records":254967,
               "xposed_date":"2015","domain":"sweclockers.com","xposed_data":"Email addresses;Passwords;Usernames",
               "password_risk":"easytocrack"}]},
             "BreachMetrics":{"risk":[{"risk_label":"Low","risk_score":3}]},
             "ExposedPastes":null,"PasteMetrics":null,"PastesSummary":{"cnt":0,"domain":"","tmpstmp":""}}
        """))
        assertTrue(r.found)
        assertEquals(1, r.items.size)
        assertEquals("SweClockers", r.items[0].name)
        assertTrue(r.items[0].fields.any { it.key == "Datos expuestos" && it.value == "Email addresses, Passwords, Usernames" })
        assertTrue(r.items[0].fields.any { it.key == "Contraseñas" && it.value == "Hash fácil de descifrar" })
    }

    @Test fun xonNotFound() {
        val r = LeakParsers.xon(json("""
            {"BreachMetrics":null,"BreachesSummary":{"site":""},"ExposedBreaches":null,
             "ExposedPastes":null,"PasteMetrics":null,"PastesSummary":{"cnt":0,"domain":"","tmpstmp":""}}
        """))
        assertFalse(r.found)
        assertTrue(r.items.isEmpty())
        assertFalse(LeakParsers.xon(null).found)
    }

    @Test fun xonPasswordCount() {
        assertEquals(11999477L, LeakParsers.xonPasswordCount(json(
            """{"SearchPassAnon":{"anon":"808d63ba47","char":"D:6;A:0;S:0;L:6","count":"11999477","wordlist":0}}""")))
        assertEquals(0L, LeakParsers.xonPasswordCount(json("""{"Error":"Not found"}""")))
    }

    @Test fun leakCheckFound() {
        val r = LeakParsers.leakCheck(json("""
            {"success":true,"found":3,"fields":["username","password"],
             "sources":[{"name":"Canva.com","date":"2019-05"},{"name":"Collection 1","date":""}]}
        """))
        assertTrue(r.found)
        assertEquals(listOf("Canva.com", "Collection 1"), r.items.map { it.name })
        assertTrue(r.summary.contains("username, password"))
    }

    @Test fun leakCheckNotFound() {
        assertFalse(LeakParsers.leakCheck(json("""{"success":false,"error":"Not found"}""")).found)
        assertFalse(LeakParsers.leakCheck(json("""{"success":true,"found":0,"fields":[],"sources":[]}""")).found)
    }

    @Test(expected = IllegalStateException::class)
    fun leakCheckOtherErrorThrows() {
        LeakParsers.leakCheck(json("""{"success":false,"error":"Invalid characters in query"}"""))
    }

    @Test fun hudsonRockFound() {
        val r = LeakParsers.hudsonRock(json("""
            {"message":"This email address is associated with a computer that was infected by an info-stealer",
             "stealers":[{"total_corporate_services":1,"total_user_services":9,"date_compromised":"2022-07-29T12:52:09.000Z",
               "stealer_family":"RedLine","computer_name":"DESKTOP-1","operating_system":"Windows 10 Pro",
               "top_passwords":["1******3","p*******d"]}]}
        """))
        assertTrue(r.found)
        assertEquals("RedLine", r.items[0].name)
        assertTrue(r.items[0].fields.any { it.key == "Infección" && it.value == "2022-07-29" })
        assertTrue(r.items[0].fields.any { it.key == "Credenciales" && it.value == "9 personales, 1 corporativas" })
    }

    @Test fun hudsonRockNotFound() {
        assertFalse(LeakParsers.hudsonRock(json("""{"message":"not associated","stealers":[]}""")).found)
        assertFalse(LeakParsers.hudsonRock(null).found)
    }
}
