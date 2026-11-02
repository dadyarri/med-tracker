package dev.dadyarri.medtracker.alarm

import org.junit.Assert.*
import org.junit.Test

class AlarmPolicyTest {
    private val now = 1_000_000L
    private fun plan(sound: Boolean = true) = AlarmPolicy.create(now, 30, sound, "first")

    @Test fun defaultsAndFirstDelivery() {
        val waiting = plan()
        assertEquals(now + 30_000, waiting.nextAt)
        assertEquals(now + 30_000 + 120 * 60_000, waiting.endAt)
        val ringing = AlarmPolicy.deliver(waiting, "first", waiting.nextAt!!, "second")!!
        assertEquals(AlarmStatus.RINGING, ringing.status)
        assertEquals(waiting.nextAt!! + 15 * 60_000, ringing.nextAt)
    }
    @Test fun duplicateAndStaleDeliveryAreIgnored() {
        val waiting = plan()
        val ringing = AlarmPolicy.deliver(waiting, "first", waiting.nextAt!!, "second")!!
        assertNull(AlarmPolicy.deliver(ringing, "first", waiting.nextAt!!, "third"))
        assertNull(AlarmPolicy.deliver(waiting, "old", waiting.nextAt!!, "third"))
        assertNull(AlarmPolicy.deliver(waiting, "first", waiting.nextAt!! - 1, "third"))
    }
    @Test fun replacementRejectsOldPendingIntent() {
        val replacement = AlarmPolicy.create(now, 60, true, "replacement")
        assertNull(AlarmPolicy.deliver(replacement, "first", now + 60_000, "next"))
    }
    @Test fun snoozeReplacesAutoRepeatAndHasLimit() {
        var ringing = AlarmPolicy.deliver(plan(), "first", now + 30_000, "ring")!!
        var at = now + 30_000
        repeat(3) { index ->
            val snoozed = AlarmPolicy.snooze(ringing, at, "snooze$index")!!
            assertEquals(at + 5 * 60_000, snoozed.nextAt)
            assertEquals(index + 1, snoozed.snoozes)
            assertNull(AlarmPolicy.deliver(snoozed, ringing.token, at + 15 * 60_000, "stale"))
            at = snoozed.nextAt!!
            ringing = AlarmPolicy.deliver(snoozed, snoozed.token, at, "ring$index")!!
        }
        assertNull(AlarmPolicy.snooze(ringing, at, "fourth"))
    }
    @Test fun cancelledPlanNeverDeliversOrSnoozes() {
        val cancelled = plan().copy(status = AlarmStatus.CANCELLED, nextAt = null)
        assertNull(AlarmPolicy.deliver(cancelled, "first", now + 30_000, "next"))
        assertNull(AlarmPolicy.snooze(cancelled, now, "next"))
    }
    @Test fun futureReconciliationIsIdempotent() {
        val waiting = plan()
        val restored = AlarmPolicy.recover(waiting, now, "unused")
        assertEquals(waiting, restored)
        assertEquals(restored, AlarmPolicy.recover(restored, now, "also-unused"))
    }
    @Test fun rebootDoesNotPlayMissedAlarm() {
        val expired = AlarmPolicy.recover(plan(), now + 30_000, "expired")
        assertEquals(AlarmStatus.EXPIRED, expired.status)
        assertNull(expired.nextAt)
        assertNull(AlarmPolicy.deliver(expired, "first", now + 35_000, "late"))
    }
    @Test fun lateDeliveryExpiresWithoutAudio() {
        val expired = AlarmPolicy.deliver(plan(), "first", now + 30_000 + AlarmPolicy.DELIVERY_GRACE_MS + 1, "late")!!
        assertEquals(AlarmStatus.EXPIRED, expired.status)
        assertNull(expired.nextAt)
    }
    @Test fun repeatAndSnoozeCannotExceedWindow() {
        val short = AlarmPolicy.create(now, 30, true, "first", windowMinutes = 1)
        val ringing = AlarmPolicy.deliver(short, "first", now + 30_000, "ring")!!
        assertNull(ringing.nextAt)
        assertNull(AlarmPolicy.snooze(ringing, now + 30_000, "snooze"))
        assertEquals(AlarmStatus.EXPIRED, AlarmPolicy.recover(ringing, short.endAt, "expired").status)
    }
    @Test fun quietAndSoundShareTimingPolicy() {
        val quiet = plan(false)
        val delivered = AlarmPolicy.deliver(quiet, "first", quiet.nextAt!!, "next")!!
        assertFalse(delivered.sound)
        assertEquals(AlarmPolicy.deliver(plan(), "first", quiet.nextAt!!, "next")!!.nextAt, delivered.nextAt)
    }
    @Test fun rebootMutesPreviouslyActiveSoundButRetainsFutureRepeat() {
        val ringing = AlarmPolicy.deliver(plan(), "first", now + 30_000, "next")!!
        val restored = AlarmPolicy.recover(ringing, now + 40_000, "unused")
        assertEquals(AlarmStatus.MUTED, restored.status)
        assertEquals(ringing.nextAt, restored.nextAt)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidInputRejected() {
        AlarmPolicy.create(now, 0, true, "invalid")
    }
}
