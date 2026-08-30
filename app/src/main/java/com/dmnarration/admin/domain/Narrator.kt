package com.dmnarration.admin.domain

/**
 * Someone a pickup can be assigned to.
 *
 * This is the EDITOR's view: id, name, active. Email and notes are absent from
 * the return type of narrators_for_editor(), not nulled — the narrators table is
 * admin-only and column-level control cannot come from RLS or grants when both
 * roles are `authenticated`.
 */
data class Narrator(
    override val id: String,
    val displayName: String,
    val active: Boolean,
) : Identified
