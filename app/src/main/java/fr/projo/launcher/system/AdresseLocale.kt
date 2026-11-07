package fr.projo.launcher.system

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Adresse IPv4 de la TV sur le réseau local (Wi-Fi ou Ethernet de la maison) : liens des puces
 * NFC, raccourci iOS « Envoyer sur la TV », connexion adb. Jamais celle d'un VPN (interface
 * tun0, wg0… point à point), qui n'est pas joignable depuis le téléphone.
 */
object AdresseLocale {

    /** Une interface réseau : nom, adresses IPv4, lien point à point (VPN, tunnel). */
    class Interface(val nom: String, val ipv4: List<String>, val pointAPoint: Boolean = false)

    /** Préfixes des interfaces de VPN et de tunnel. */
    private val TUNNELS = listOf("tun", "tap", "ppp", "wg", "ipsec", "utun", "vpn", "l2tp", "gre", "dummy")

    /**
     * Adresse choisie parmi [interfaces] (null si aucune) : ni VPN ni tunnel, ni boucle locale
     * (127.x), ni adresse automatique (169.254.x) ; Wi-Fi et Ethernet d'abord, adresses privées
     * (10.x, 172.16-31.x, 192.168.x) d'abord.
     */
    fun choisir(interfaces: List<Interface>): String? {
        val candidates = interfaces
            .filter { i -> !i.pointAPoint && TUNNELS.none { i.nom.lowercase().startsWith(it) } }
            .flatMap { i -> i.ipv4.map { i.nom.lowercase() to it } }
            .filter { (_, ip) -> ipv4(ip) != null && !ip.startsWith("127.") && !ip.startsWith("169.254.") && ip != "0.0.0.0" }
        return candidates.sortedWith(
            compareBy<Pair<String, String>>(
                { (nom, _) -> if (nom.startsWith("wlan") || nom.startsWith("eth") || nom.startsWith("en")) 0 else 1 },
                { (_, ip) -> if (privee(ip)) 0 else 1 },
            ),
        ).firstOrNull()?.second
    }

    /** Adresse de cet appareil (null : pas de réseau local). */
    fun lire(): String? = try {
        choisir(
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .map { ni ->
                    Interface(
                        ni.name ?: "",
                        ni.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                        pointAPoint = runCatching { ni.isPointToPoint }.getOrDefault(false),
                    )
                },
        )
    } catch (e: Exception) {
        null
    }

    private fun ipv4(ip: String): List<Int>? {
        val m = ip.split('.')
        if (m.size != 4) return null
        val n = m.map { it.toIntOrNull() ?: return null }
        return if (n.all { it in 0..255 }) n else null
    }

    private fun privee(ip: String): Boolean {
        val n = ipv4(ip) ?: return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168)
    }
}
