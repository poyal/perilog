package com.poyal.perilog

import com.poyal.perilog.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PrivacyPreferencesTest {
    @Test fun missingProtectionPreservesLegacyLockInSettingsAndBackups() {
        assertFalse(Preferences().lock)
        assertFalse(Preferences().screenProtection)
        for (locked in listOf(false, true)) {
            val old = """{"lock":$locked}"""
            val preferences = codec.decodeFromString<Preferences>(old)
            assertEquals(locked, preferences.screenProtection)
            val backup = codec.decodeFromString<Snapshot>("""{"preferences":$old}""")
            assertEquals(preferences, backup.preferences)
            val saved = codec.encodeToString(backup)
            assertTrue(saved.contains("\"screenProtection\":$locked"))
            assertEquals(locked, codec.decodeFromString<Snapshot>(saved).preferences.screenProtection)
        }
    }
    @Test fun explicitProtectionIsIndependentOfLockAndSurvivesRoundTrip() {
        for (lock in listOf(false, true)) for (protection in listOf(false, true)) {
            val preferences = codec.decodeFromString<Preferences>("""{"lock":$lock,"screenProtection":$protection}""")
            assertEquals(protection, preferences.screenProtection)
            val changedLock = preferences.copy(lock = !lock)
            assertEquals(protection, changedLock.screenProtection)
            val backup = Snapshot(preferences = changedLock)
            assertEquals(backup, codec.decodeFromString<Snapshot>(codec.encodeToString(backup)))
        }
    }
}
