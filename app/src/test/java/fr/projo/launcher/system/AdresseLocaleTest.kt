package fr.projo.launcher.system

import fr.projo.launcher.system.AdresseLocale.Interface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Adresse de la TV sur le réseau local : jamais celle d'un VPN (raccourci iOS, puces NFC). */
class AdresseLocaleTest {

    @Test
    fun wifiPlutotQueLeVpn() {
        // VPN allumé : tun0 apparaît souvent avant wlan0
        val avecVpn = listOf(
            Interface("tun0", listOf("10.2.0.2"), pointAPoint = true),
            Interface("wg0", listOf("10.64.0.1")),
            Interface("wlan0", listOf("192.168.1.42")),
        )
        assertEquals("192.168.1.42", AdresseLocale.choisir(avecVpn))
        assertEquals("192.168.1.18", AdresseLocale.choisir(listOf(Interface("eth0", listOf("192.168.1.18")))))
    }

    @Test
    fun adressesEcartees() {
        assertNull(AdresseLocale.choisir(emptyList()))
        assertNull(AdresseLocale.choisir(listOf(Interface("tun0", listOf("10.8.0.2")), Interface("ppp0", listOf("10.0.0.5")))))
        assertNull(AdresseLocale.choisir(listOf(Interface("wlan0", listOf("169.254.10.3", "127.0.0.1", "0.0.0.0", "pas une ip")))))
        // Une interface point à point au nom inconnu reste un tunnel
        assertNull(AdresseLocale.choisir(listOf(Interface("xyz0", listOf("10.1.1.1"), pointAPoint = true))))
    }

    @Test
    fun wifiEtAdressePriveeDAbord() {
        val l = listOf(
            Interface("rmnet0", listOf("100.64.3.2")),
            Interface("wlan0", listOf("82.12.4.5", "10.0.0.7")),
        )
        assertEquals("10.0.0.7", AdresseLocale.choisir(l))
        assertEquals("100.64.3.2", AdresseLocale.choisir(listOf(Interface("rmnet0", listOf("100.64.3.2")))))
    }
}
