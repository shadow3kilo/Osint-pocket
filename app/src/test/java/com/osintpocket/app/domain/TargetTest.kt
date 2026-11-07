package com.osintpocket.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetTest {

    @Test fun plainDomain() = assertEquals(Target.Domain("example.com"), Target.parse("Example.COM"))

    @Test fun urlWithPathAndPort() =
        assertEquals(Target.Domain("sub.example.com"), Target.parse("https://sub.example.com:8443/a?b=c"))

    @Test fun trailingDot() = assertEquals(Target.Domain("example.com"), Target.parse("example.com."))

    @Test fun idn() = assertEquals(Target.Domain("xn--espaa-rta.es"), Target.parse("españa.es"))

    @Test fun ipv4() = assertEquals(Target.Ip("1.1.1.1", isV6 = false), Target.parse(" 1.1.1.1 "))

    @Test fun ipv4WithPort() = assertEquals(Target.Ip("8.8.8.8", isV6 = false), Target.parse("8.8.8.8:53"))

    @Test fun ipv6() = assertEquals(Target.Ip("2606:4700::1111", isV6 = true), Target.parse("2606:4700::1111"))

    @Test fun ipv6Bracketed() =
        assertEquals(Target.Ip("2001:db8::1", isV6 = true), Target.parse("http://[2001:db8::1]:8080/"))

    @Test fun invalidOctet() = assertNull(Target.parse("300.1.1.1"))

    @Test fun garbage() = assertNull(Target.parse("no es un dominio"))

    @Test fun empty() = assertNull(Target.parse("   "))
}
