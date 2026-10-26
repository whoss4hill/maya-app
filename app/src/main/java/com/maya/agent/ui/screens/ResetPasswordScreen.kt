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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.maya.agent.data.auth.AuthRepository
import com.maya.agent.data.auth.RecoveryLink
import com.maya.agent.ui.components.MayaPrimaryButton
import com.maya.agent.ui.theme.Emerald
import kotlinx.coroutines.launch

/** Set a new password after tapping the emailed recovery link (opens only via the maya://auth recovery redirect). */
@Composable
fun ResetPasswordScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val auth = MayaApp.from(LocalContext.current).auth
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<String?>(null) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        auth.verifyRecoverySession().fold(
            onSuccess = { account = it },
            onFailure = { failed = "That reset link is no longer valid. Ask for a new one." },
        )
        checking = false
    }
    DisposableEffect(Unit) { onDispose { auth.cancelRecovery() } }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { auth.cancelRecovery(); onCancel() }) { Text("< Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        Text("New password", style = MaterialTheme.typography.displaySmall)
        when {
            checking -> {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Emerald) }
            }
            failed != null -> {
                Spacer(Modifier.height(16.dp))
                Text(failed!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                MayaPrimaryButton(text = "Back to sign in", onClick = onCancel)
            }
            else -> {
                Text("Set a new password for ${account ?: "your account"}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Text("New password", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Spacer(Modifier.height(12.dp))
                Text("Repeat new password", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pw2, onValueChange = { pw2 = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(20.dp))
                if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Emerald) }
                else MayaPrimaryButton(text = "Update password", onClick = {
                    when {
                        pw.length < RecoveryLink.MIN_PASSWORD -> error = "Use at least ${RecoveryLink.MIN_PASSWORD} characters."
                        pw != pw2 -> error = "The two passwords do not match."
                        else -> { busy = true; error = null
                            scope.launch {
                                when (val r = auth.setNewPassword(pw)) {
                                    is AuthRepository.Result.Success -> { pw = ""; pw2 = ""; onDone() }
                                    is AuthRepository.Result.Failure -> { error = r.message; busy = false }
                                }
                            } }
                    }
                })
            }
        }
    }
}
