package com.fraudshield.app

import com.fraudshield.app.SelfLaunchRule.Ev
import com.fraudshield.app.SelfLaunchRule.Kind.*
import org.junit.Assert.*
import org.junit.Test

/** Roadmap 6.6: rule behind selfLaunchCount. */
class SelfLaunchRuleTest {
    private val launcher = "com.launcher"
    private val neutral = setOf(launcher, "com.android.systemui")
    private fun r(t: Long, p: String) = Ev(t, RESUMED, p)
    private fun touch(t: Long, p: String) = Ev(t, USER_INTERACTION, p)

    @Test fun openingFromTheLauncherIsNotASelfLaunch() {
        val ev = listOf(r(0, launcher), r(1_000, "com.app"))
        assertTrue(SelfLaunchRule.count(ev, neutral).isEmpty())
    }

    @Test fun appJumpingInFrontOfAnotherAppWithoutATouchIsCounted() {
        val ev = listOf(r(0, launcher), r(1_000, "com.chrome"), touch(2_000, "com.chrome"),
            r(60_000, "com.adware"))
        assertEquals(mapOf("com.adware" to 1), SelfLaunchRule.count(ev, neutral))
    }

    @Test fun aTapInThePreviousAppThatOpensTheNextIsUserAction() {
        val ev = listOf(r(0, "com.chrome"), touch(10_000, "com.chrome"), r(11_000, "com.youtube"))
        assertTrue(SelfLaunchRule.count(ev, neutral).isEmpty())
    }

    @Test fun appLightingUpTheScreenByItselfIsCounted() {
        val ev = listOf(r(0, launcher), Ev(5_000, SCREEN_OFF, "android"), r(60_000, "com.adware"))
        assertEquals(mapOf("com.adware" to 1), SelfLaunchRule.count(ev, neutral))
    }

    @Test fun repeatedSelfLaunchesAreCountedAndStayingInFrontIsNot() {
        val ev = listOf(r(0, "com.chrome"),
            r(100_000, "com.adware"), r(100_500, "com.adware"),     // same app resumed again: not a new launch
            r(200_000, "com.chrome"), touch(200_100, "com.chrome"),
            r(300_000, "com.adware"),
            r(400_000, "com.chrome"), touch(400_100, "com.chrome"),
            r(500_000, "com.adware"))
        assertEquals(3, SelfLaunchRule.count(ev, neutral)["com.adware"])
    }

    @Test fun neutralPackagesAreNeverCounted() {
        val ev = listOf(r(0, "com.chrome"), r(60_000, "com.android.systemui"), r(61_000, launcher))
        assertTrue(SelfLaunchRule.count(ev, neutral).isEmpty())
    }

    @Test fun eventsOutOfOrderAreSortedFirst() {
        val ev = listOf(r(60_000, "com.adware"), r(1_000, "com.chrome"))
        assertEquals(mapOf("com.adware" to 1), SelfLaunchRule.count(ev, neutral))
    }
}
