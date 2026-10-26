package com.maya.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.maya.agent.MayaApp
import com.maya.agent.ui.components.GlassCard
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.theme.Emerald
import kotlinx.coroutines.launch

/** Screen 04 - Sign in ("Wapas aa gaye?") with the owner's existing account, or skip. */
@Composable
fun SignInScreen(onSignedIn: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = MayaApp.from(context).auth
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resetSentTo by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { auth.consumeLinkError()?.let { error = it } }

    fun sendReset() {
        if (busy) return
        val target = email.trim()
        if (target.isEmpty()) { error = "Enter your email first, then tap Forgot password"; return }
        busy = true; error = null; resetSentTo = null
        scope.launch {
            when (val result = auth.sendPasswordReset(target)) {
                is com.maya.agent.data.auth.AuthRepository.Result.Success -> resetSentTo = target
                is com.maya.agent.data.auth.AuthRepository.Result.Failure -> error = result.message
            }
            busy = false
        }
    }

    fun submit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            // Existing accounts only: the owner signs in with the account
            // they already have. No Google OAuth, no in-app sign-up.
            val result = auth.signInWithEmail(email, password)
            busy = false
            when (result) {
                is com.maya.agent.data.auth.AuthRepository.Result.Success -> onSignedIn()
                is com.maya.agent.data.auth.AuthRepository.Result.Failure -> error = result.message
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("< Wapas", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        Text("Wapas aa gaye?", style = MaterialTheme.typography.displaySmall)
        Text(
            "Sign in to keep your memories.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Text("Email", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Password", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { sendReset() }) {
                Text("Forgot password?", style = MaterialTheme.typography.bodyMedium, color = Emerald)
            }
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        resetSentTo?.let {
            Spacer(Modifier.height(8.dp))
            Text("Reset link sent to $it - check your inbox.", color = Emerald, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        if (busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Emerald)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The server may be waking up. The first request takes a little longer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MayaPrimaryButton(text = "Sign in", onClick = { submit() })
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
    }
}
