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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/** Screen 04 - Sign in ("Wapas aa gaye?") with Google, email, or skip. */
@Composable
fun SignInScreen(onSignedIn: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = MayaApp.from(context).auth
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(signUp: Boolean) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            val result = if (signUp) auth.signUpWithEmail(email, password)
                         else auth.signInWithEmail(email, password)
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
            "Yaadein saath rakhne ke liye sign in karo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = { context.startActivity(auth.googleSignInIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with Google", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  ya email se  ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
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
            Text("Forgot password?", style = MaterialTheme.typography.bodyMedium, color = Emerald)
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
        Spacer(Modifier.height(20.dp))
        if (busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Emerald)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Server jaag raha hoga - pehli request me thoda time lagta hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MayaPrimaryButton(text = "Sign in", onClick = { submit(signUp = false) })
        }
        TextButton(onClick = { submit(signUp = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Naya account banayein", color = Emerald)
        }
        Spacer(Modifier.weight(1f))
        GlassCard {
            Column {
                Text("Abhi bina account chalao", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Yaadein sirf iss session tak rahengi. App band hone par memory clear ho jayegi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                    Text("Skip for now ->", color = Emerald)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
