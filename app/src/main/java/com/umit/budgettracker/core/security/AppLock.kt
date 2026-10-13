package com.umit.budgettracker.core.security

import android.os.SystemClock
import com.umit.budgettracker.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Salted PBKDF2 PIN hashing; the PIN itself is never stored. */
object PinHasher {
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(pin, salt).toByteArray()
        val expected = expectedHash.toByteArray()
        // Constant-time comparison.
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }
}

enum class LockState { CHECKING, LOCKED, UNLOCKED }

/**
 * Decides when the app shows the lock screen. The lock applies after the app has been in the
 * background for at least the configured delay; leaving for our own file pickers / camera does
 * not count, so backups and receipt photos don't force a re-unlock.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsDataStore
) {
    private val _state = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundedAt: Long? = null
    private var externalLaunchAt: Long? = null

    suspend fun onColdStart() {
        val prefs = settings.current()
        _state.value = if (prefs.lockEnabled && prefs.hasPin) LockState.LOCKED else LockState.UNLOCKED
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    suspend fun onForeground() {
        val left = backgroundedAt ?: return
        backgroundedAt = null
        val prefs = settings.current()
        if (!prefs.lockEnabled || !prefs.hasPin || _state.value != LockState.UNLOCKED) return
        val external = externalLaunchAt
        externalLaunchAt = null
        if (external != null && external <= left + EXTERNAL_GRACE_MS) return
        val awayMs = SystemClock.elapsedRealtime() - left
        if (awayMs >= prefs.lockDelaySeconds * 1000L) _state.value = LockState.LOCKED
    }

    /** Called right before the app starts a picker / camera activity for a result. */
    fun onExternalActivityLaunched() {
        externalLaunchAt = SystemClock.elapsedRealtime()
    }

    suspend fun tryPin(pin: String): Boolean {
        val prefs = settings.current()
        val salt = prefs.pinSalt ?: return false
        val hash = prefs.pinHash ?: return false
        val ok = PinHasher.verify(pin, salt, hash)
        if (ok) _state.value = LockState.UNLOCKED
        return ok
    }

    suspend fun pinLength(): Int = settings.current().pinLength

    fun unlockWithBiometric() {
        _state.value = LockState.UNLOCKED
    }

    suspend fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        settings.update { it.copy(pinSalt = salt, pinHash = hash, pinLength = pin.length) }
    }

    suspend fun disableLock() {
        settings.update { it.copy(lockEnabled = false, pinHash = null, pinSalt = null) }
        _state.value = LockState.UNLOCKED
    }

    private companion object {
        const val EXTERNAL_GRACE_MS = 2_000L
    }
}
