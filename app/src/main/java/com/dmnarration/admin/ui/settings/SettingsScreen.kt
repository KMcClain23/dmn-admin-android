package com.dmnarration.admin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.SettingIssue
import com.dmnarration.admin.domain.SettingKeys
import com.dmnarration.admin.domain.SiteSettings
import com.dmnarration.admin.domain.acceptingProjectsLabel
import com.dmnarration.admin.domain.availableMonthsLabel
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/**
 * The seven numbers the app does arithmetic with, read-only.
 *
 * Read-only is the schema's decision rather than this screen's: `site_settings` has a
 * `Role read` policy and no update policy at all, so a write would return zero rows
 * rather than an error. There is no write path here and adding one needs a migration
 * that makes the refusal visible first.
 */
@Composable
fun SettingsScreen(
    settings: SiteSettings?,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val c = DmnTheme.colors

    Column {
        Text(
            "Settings",
            style = DmnType.TitleLg,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )

        if (error != null) {
            Text(
                error,
                style = DmnType.Body,
                color = c.alertRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        PullToRefreshSurface(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            content = ScrollableContent.list(
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (settings == null) {
                    // Still a lazy list, so it scrolls and pulls with nothing in it.
                    //
                    // "Loading…" is only honest while something is loading. The first
                    // cut showed it forever when the read had already failed and been
                    // swallowed, which is a screen asserting work is in progress when
                    // nothing is happening.
                    item {
                        Text(
                            when {
                                loading -> "Loading…"
                                error != null -> ""
                                else -> "Settings could not be read. Pull down to try again."
                            },
                            style = DmnType.Body,
                            color = c.textMuted,
                        )
                    }
                    return@list
                }
                body(settings)
            },
        )
    }
}

private fun LazyListScope.body(s: SiteSettings) {
    item {
        Group("Availability") {
            // A state, not "true".
            Setting("Status", acceptingProjectsLabel(s.acceptingProjects, s.acceptingProjectsRaw))
            Setting("Booking window", availableMonthsLabel(s.availableMonths, s.availableMonthsRaw))
        }
    }

    item {
        Group("Rates") {
            /*
             * These two are one word apart and mean different things, and the web's
             * own note records that the finished-hour divisor "was written down twice
             * and had already drifted once". The labels carry the distinction rather
             * than the key names, which differ only in the middle word.
             */
            SettingRow(
                "Words per hour at the mic",
                s.studio.settings.wordsPerNarrationHour?.toString(),
                s.studio.issueFor(SettingKeys.WORDS_PER_NARRATION_HOUR),
                "Drives every TIME figure — hours at the mic, days needed, capacity.",
            )
            SettingRow(
                "Words per finished hour",
                s.studio.settings.wordsPerFinishedHour?.toString(),
                s.studio.issueFor(SettingKeys.WORDS_PER_FINISHED_HOUR),
                "Drives every MONEY figure — earnings and PFH totals.",
            )
        }
    }

    item {
        Group("Capacity") {
            SettingRow(
                "A full day at the mic",
                s.studio.settings.dailyCapacityHours?.let { "$it hrs" },
                s.studio.issueFor(SettingKeys.DAILY_CAPACITY_HOURS),
            )
            SettingRow(
                "A heavy day starts at",
                s.studio.settings.heavyDayHours?.let { "$it hrs" },
                s.studio.issueFor(SettingKeys.HEAVY_DAY_HOURS),
            )
            SettingRow(
                "Books at the mic in one day",
                s.studio.settings.maxBooksPerDay?.toString(),
                s.studio.issueFor(SettingKeys.MAX_BOOKS_PER_DAY),
            )
        }
    }
}

/**
 * A setting, or why it could not be used.
 *
 * The rejection is shown against the offending value rather than the value being
 * silently replaced. A typo'd 500000 quietly becoming 9,200 is precisely the disease
 * W1 documents: a Settings page displaying a number the app does not use.
 */
@Composable
private fun SettingRow(label: String, value: String?, issue: SettingIssue?, note: String? = null) {
    val c = DmnTheme.colors
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(label, style = DmnType.Body, color = c.textMuted, modifier = Modifier.padding(end = 12.dp))
            Text(
                value ?: "Not usable",
                style = DmnType.Numeric,
                color = if (value == null) c.alertRed else c.textPrimary,
            )
        }
        issue?.let {
            Text(
                when (it) {
                    is SettingIssue.Missing -> "Not set in site_settings."
                    is SettingIssue.Unreadable -> "Stored value \"${it.raw}\" is not a number."
                    is SettingIssue.OutOfRange ->
                        "Stored value \"${it.raw}\" is outside ${it.allowed} and is not being used."
                },
                style = DmnType.Small,
                color = c.alertRed,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        note?.let {
            Text(it, style = DmnType.Small, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = DmnType.Label,
            color = DmnTheme.colors.textFaint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Setting(label: String, value: String, note: String? = null) {
    val c = DmnTheme.colors
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(label, style = DmnType.Body, color = c.textMuted, modifier = Modifier.padding(end = 12.dp))
            Text(value, style = DmnType.Numeric, color = c.textPrimary)
        }
        note?.let {
            Text(it, style = DmnType.Small, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
