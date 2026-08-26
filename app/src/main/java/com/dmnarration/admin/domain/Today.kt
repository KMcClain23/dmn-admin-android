package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The current local day, read fresh.
 *
 * This is deliberately a function and not a value, and it must never be held in
 * a ViewModel field. The web gets day-freshness for free because `new Date()`
 * sits inline inside `daysUntil`, so a board left open across midnight corrects
 * itself on the next React render. Injecting `today` — which is what makes the
 * domain functions testable — moves that responsibility here and makes it
 * possible to lose: a `val today = currentDay()` captured when a ViewModel is
 * constructed freezes every date, urgency colour and bucket at whatever day the
 * app happened to launch. A card due tomorrow would still say tomorrow, still
 * be amber, still sit in This Month, a week later.
 *
 * So call this once per load, store the result in the emitted state, and let
 * pull-to-refresh correct any staleness. That is the accepted bound for v1.
 *
 * The zone is the device's, never UTC, and that is not a detail. Dean is at
 * UTC-7: from 17:00 local until midnight the UTC date is already tomorrow, so a
 * UTC-derived "today" would be right for seventeen hours a day and silently a
 * day ahead for the other seven — shifting every urgency colour and every
 * bucket one step early, every evening. The clock is a parameter only so that
 * can be pinned in a test.
 */
fun currentDay(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    clock: Clock = Clock.System,
): LocalDate = clock.now().toLocalDateTime(timeZone).date
