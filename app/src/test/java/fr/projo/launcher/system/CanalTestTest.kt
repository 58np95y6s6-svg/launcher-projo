package fr.projo.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Test

/** Même code sur le Fire TV et le projecteur : chacun suit sa propre release de test. */
class CanalTestTest {

    @Test
    fun fireTvSuitSaRelease() {
        val c = CanalTest.pour(fireTv = true)
        assertEquals("firetv", c.tag)
        assertEquals("launcher-firetv.apk", c.apk)
        assertEquals("Fire TV", c.nom)
    }

    @Test
    fun projecteurSuitLaReleaseTest() {
        val c = CanalTest.pour(fireTv = false)
        assertEquals("test", c.tag)
        assertEquals("launcher-test.apk", c.apk)
        assertEquals("test", c.nom)
    }
}
