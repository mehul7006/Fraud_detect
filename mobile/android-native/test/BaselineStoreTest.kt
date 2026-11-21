package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Test

/** Roadmap 5.5: existing files ignored, new and modified files reported. */
class BaselineStoreTest {
    private class Mem(var data: List<String>? = null) {
        var saves = 0
        fun store(cap: Int = 50_000) = BaselineStore({ data }, { data = it; saves++ }, cap)
    }

    private fun id(p: String, m: Long = 1L) = BaselineStore.idOf(p, m)

    @Test fun firstRunReturnsNothingEvenWithExistingFiles() {
        val m = Mem()
        val existing = (1..60).map { id("/d/f$it") }
        assertTrue(m.store().newOnly(existing).isEmpty())
        assertEquals(60, m.data!!.size)
    }

    @Test fun newFileAfterBaselineIsReturned() {
        val m = Mem()
        val s = m.store()
        val old = (1..50).map { id("/d/f$it") }
        s.newOnly(old)
        val fresh = id("/d/new.apk")
        assertEquals(listOf(fresh), s.newOnly(old + fresh))
        assertTrue("reported only once", s.newOnly(old + fresh).isEmpty())
    }

    @Test fun modifiedFileGetsANewIdAndIsReturned() {
        val m = Mem()
        val s = m.store()
        s.newOnly(listOf(id("/d/a.pdf", 100)))
        assertEquals(listOf(id("/d/a.pdf", 200)), s.newOnly(listOf(id("/d/a.pdf", 200))))
    }

    @Test fun emptyFolderOnFirstRunStillSavesTheBaseline() {
        val m = Mem()
        m.store().newOnly(emptyList())
        assertNotNull("baseline must exist so the next new file is flagged", m.data)
        assertEquals(listOf(id("/d/x")), m.store().newOnly(listOf(id("/d/x"))))
    }

    @Test fun baselineSurvivesARestart() {
        val m = Mem()
        val ids = (1..10).map { id("/d/f$it") }
        m.store().newOnly(ids)
        assertTrue(m.store().newOnly(ids).isEmpty())
    }

    @Test fun bigLibraryDoesNotPushBaselineOut() {
        val m = Mem()
        val lib = (1..6000).map { id("/lib/f$it") } // old cap was 5000
        val s = m.store()
        s.newOnly(lib)
        assertTrue(s.newOnly(lib).isEmpty())
    }

    @Test fun capDropsOldestOnly() {
        val m = Mem()
        val s = m.store(cap = 3)
        s.newOnly(listOf("a", "b", "c"))
        s.newOnly(listOf("a", "b", "c", "d"))
        assertEquals(listOf("b", "c", "d"), m.data)
    }
}
