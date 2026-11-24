package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Test

/** 13.10: reminder schedule (detection, +1 h, daily) and the daily cap. */
class WarnSchedulerTest {
    private val h = 3_600_000L
    private fun e(pkg: String, first: Long, last: Long, count: Int) = WarnScheduler.Entry(pkg, "t", "b", first, last, count)

    @Test fun schedule_is_now_then_one_hour_then_daily() {
        assertEquals(0L, WarnScheduler.nextDue(e("a", 0, 0, 0)))
        assertEquals(h, WarnScheduler.nextDue(e("a", 0, 0, 1)))
        assertEquals(h + 24 * h, WarnScheduler.nextDue(e("a", 0, h, 2)))
        assertEquals(10 * h + 24 * h, WarnScheduler.nextDue(e("a", 0, 10 * h, 5)))
    }

    @Test fun only_due_entries_and_at_most_three_a_day() {
        val now = 100 * h
        val list = listOf(e("a", 0, 0, 0), e("b", now - 2 * h, now - 2 * h, 1), e("c", 0, now - h, 2), e("d", 0, 0, 0), e("x", 0, 0, 0))
        val due = WarnScheduler.due(list, now, sentToday = 0)
        assertEquals(3, due.size)
        assertFalse("c was reminded 1 h ago: next one tomorrow", due.any { it.pkg == "c" })
        assertEquals(1, WarnScheduler.due(list, now, sentToday = 2).size)
        assertTrue(WarnScheduler.due(list, now, sentToday = 3).isEmpty())
    }
}
