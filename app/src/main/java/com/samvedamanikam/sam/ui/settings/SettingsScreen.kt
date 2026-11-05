package com.samvedamanikam.sam.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samvedamanikam.sam.BuildConfig
import com.samvedamanikam.sam.ui.common.SectionCard
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamNegative
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val credentials = remember { container.deviceAuth.credentials() }
    val scope = rememberCoroutineScope()
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    var unpairing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SectionCard {
            Text("This phone", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Field("Server", credentials?.serverUrl ?: "-")
            Field("Signed in as", credentials?.userName ?: "-")
            Field("Device name", credentials?.deviceName ?: "-")
            Field("Paired", credentials?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.pairedAtEpochMs)) } ?: "-")
            Spacer(Modifier.height(12.dp))
            Text(
                "SAM stays signed in on this phone. Unpairing revokes this phone on the server; you'd need your password to pair again. To use a different server, unpair and pair again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmUnpair = true },
                enabled = !unpairing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SamNegative),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (unpairing) "Unpairing..." else "Unpair this phone") }
        }

        SectionCard {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Field("App", "SAM · Sam All-in-one Management")
            Field("Version", BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(8.dp))
            Text(
                "Coming later: categories editor, fingerprint lock, check inbox / send digest now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this phone?") },
            text = { Text("SAM signs out on this phone and the device is revoked on the server. Pairing again needs your username and password.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    unpairing = true
                    scope.launch { container.unpair() }
                }) { Text("Unpair", color = SamNegative) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
