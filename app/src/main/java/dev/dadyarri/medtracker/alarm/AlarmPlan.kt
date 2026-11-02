package dev.dadyarri.medtracker.alarm

enum class AlarmStatus { WAITING, SNOOZED, RINGING, MUTED, CANCELLED, EXPIRED }

data class AlarmPlan(
    val token: String,
    val nextAt: Long?,
    val endAt: Long,
    val sound: Boolean,
    val status: AlarmStatus = AlarmStatus.WAITING,
    val snoozes: Int = 0,
    val repeatMinutes: Int = 15,
    val snoozeMinutes: Int = 5,
)

/** Pure timing policy. Recovery never converts a missed signal into a live delivery. */
object AlarmPolicy {
    const val MAX_SNOOZES = 3
    const val DELIVERY_GRACE_MS = 60_000L

    fun create(now: Long, delaySeconds: Int, sound: Boolean, token: String,
               repeatMinutes: Int = 15, windowMinutes: Int = 120,
               snoozeMinutes: Int = 5): AlarmPlan {
        require(delaySeconds in 10..3600)
        require(repeatMinutes in 1..60 && windowMinutes in 1..240 && snoozeMinutes in 1..30)
        val due = now + delaySeconds * 1000L
        return AlarmPlan(token, due, due + windowMinutes * 60_000L, sound,
            repeatMinutes = repeatMinutes, snoozeMinutes = snoozeMinutes)
    }

    fun deliver(plan: AlarmPlan, token: String, now: Long, nextToken: String): AlarmPlan? {
        val due = plan.nextAt ?: return null
        if (token != plan.token || plan.status in setOf(AlarmStatus.CANCELLED, AlarmStatus.EXPIRED) || now < due) return null
        if (now - due > DELIVERY_GRACE_MS || now >= plan.endAt) return expire(plan, nextToken)
        val next = (now + plan.repeatMinutes * 60_000L).takeIf { it < plan.endAt }
        return plan.copy(token = nextToken, nextAt = next, status = AlarmStatus.RINGING)
    }

    fun snooze(plan: AlarmPlan, now: Long, token: String): AlarmPlan? {
        if (plan.status !in setOf(AlarmStatus.RINGING, AlarmStatus.MUTED) || plan.snoozes >= MAX_SNOOZES) return null
        val due = now + plan.snoozeMinutes * 60_000L
        if (due >= plan.endAt) return null
        return plan.copy(token = token, nextAt = due, status = AlarmStatus.SNOOZED, snoozes = plan.snoozes + 1)
    }

    fun recover(plan: AlarmPlan, now: Long, token: String): AlarmPlan = when {
        plan.status in setOf(AlarmStatus.CANCELLED, AlarmStatus.EXPIRED) -> plan
        now >= plan.endAt || plan.nextAt == null || plan.nextAt <= now -> expire(plan, token)
        plan.status == AlarmStatus.RINGING -> plan.copy(status = AlarmStatus.MUTED)
        else -> plan
    }

    private fun expire(plan: AlarmPlan, token: String) =
        plan.copy(token = token, nextAt = null, status = AlarmStatus.EXPIRED)
}
