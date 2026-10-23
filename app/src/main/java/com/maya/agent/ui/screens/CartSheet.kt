package com.maya.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maya.agent.ui.theme.Emerald

data class CartLine(val label: String, val amount: String)

/**
 * Cart-first, confirm-before-pay (spec section 7): Maya ALWAYS stops here and
 * waits for an explicit "haan, kar do" before any payment or booking. One yes
 * covers one action. If the total moves after confirm, show again.
 */
@Composable
fun CartConfirmDialog(
    title: String,
    lines: List<CartLine>,
    total: String,
    warning: String? = null,   // fees, surge, small-cart charges to point out
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.label, style = MaterialTheme.typography.bodyLarge)
                        Text(line.amount, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", style = MaterialTheme.typography.titleLarge)
                    Text(total, style = MaterialTheme.typography.titleLarge, color = Emerald)
                }
                warning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Payment sirf tumhare 'haan' ke baad. Ek haan = ek payment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.padding(4.dp)) {
                Text("Haan, kar do", color = Emerald)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Ruko") } },
    )
}
