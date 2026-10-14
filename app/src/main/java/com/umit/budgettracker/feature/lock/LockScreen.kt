package com.umit.budgettracker.feature.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: AppLockManager
) : ViewModel() {
    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var failures = 0
    private val _pinLength = MutableStateFlow(4)
    val pinLength: StateFlow<Int> = _pinLength.asStateFlow()

    init {
        viewModelScope.launch { _pinLength.value = lockManager.pinLength() }
    }

    fun onDigit(digit: Char) {
        if (_pin.value.length >= _pinLength.value) return
        _error.value = null
        _pin.value += digit
    }

    fun onBackspace() {
        _pin.value = _pin.value.dropLast(1)
    }

    fun submit() {
        val entered = _pin.value
        if (entered.length < _pinLength.value) return
        viewModelScope.launch {
            if (lockManager.tryPin(entered)) {
                failures = 0
                _pin.value = ""
            } else {
                failures++
                _pin.value = ""
                _error.value = if (failures >= 3) "PIN hatalı ($failures. deneme). Parmak izini deneyebilirsin." else "PIN hatalı."
            }
        }
    }

    fun onBiometricSuccess() = lockManager.unlockWithBiometric()
}

/** Full-screen lock: biometric prompt on open, PIN pad as fallback. Content behind stays hidden. */
@Composable
fun LockScreen(activity: FragmentActivity, viewModel: LockViewModel = hiltViewModel()) {
    val pin by viewModel.pin.collectAsState()
    val error by viewModel.error.collectAsState()
    val pinLength by viewModel.pinLength.collectAsState()
    val canUseBiometric = remember {
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val prompt = remember {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = viewModel.onBiometricSuccess()
        })
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("BudgetTracker kilidi")
            .setSubtitle("Devam etmek için kimliğini doğrula")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .setNegativeButtonText("PIN kullan")
            .build()
    }
    LaunchedEffect(Unit) {
        if (canUseBiometric) prompt.authenticate(promptInfo)
    }
    LaunchedEffect(pin) {
        if (pin.length >= pinLength) viewModel.submit()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Uygulama kilitli", style = MaterialTheme.typography.headlineSmall)
            Text(
                "PIN'ini gir" + if (canUseBiometric) " veya parmak izini kullan" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(pinLength) { index ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (key.length == 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                                .clickable(enabled = key != "bio" || canUseBiometric) {
                                    when (key) {
                                        "del" -> viewModel.onBackspace()
                                        "bio" -> prompt.authenticate(promptInfo)
                                        else -> viewModel.onDigit(key.first())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (key) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Sil")
                                "bio" -> if (canUseBiometric) Icon(Icons.Default.Fingerprint, contentDescription = "Parmak izi", tint = MaterialTheme.colorScheme.primary)
                                else -> Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
