package com.samvedamanikam.sam.ui.pair

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samvedamanikam.sam.AppContainer
import com.samvedamanikam.sam.BuildConfig
import com.samvedamanikam.sam.auth.DeviceAuthException
import com.samvedamanikam.sam.ui.common.appContainer
import com.samvedamanikam.sam.ui.theme.SamAccent
import com.samvedamanikam.sam.ui.theme.SamNegative
import kotlinx.coroutines.launch

class PairViewModel(private val container: AppContainer) : ViewModel() {
    var server by mutableStateOf(BuildConfig.DEFAULT_BASE_URL)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var deviceName by mutableStateOf(listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" "))
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val canSubmit get() = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && deviceName.isNotBlank()

    fun pair() {
        if (!canSubmit) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                container.pair(server, username, password, deviceName)
                password = "" // never kept after pairing
            } catch (e: DeviceAuthException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}

/** First run only: the password is used once to pair, then discarded. */
@Composable
fun PairScreen() {
    val container = appContainer()
    val vm: PairViewModel = viewModel { PairViewModel(container) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Text("SAM", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = SamAccent)
            Text("Sam All-in-one Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Pair this phone", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sign in once with your Helios account. After that SAM stays signed in on this phone; the password isn't stored.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = vm.server, onValueChange = { vm.server = it }, label = { Text("Server") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.username, onValueChange = { vm.username = it }, label = { Text("Username") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.password, onValueChange = { vm.password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.deviceName, onValueChange = { vm.deviceName = it }, label = { Text("Device name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.pair() }),
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(it, color = SamNegative, style = MaterialTheme.typography.bodyMedium) }

            Button(onClick = vm::pair, enabled = vm.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text("Pair this phone")
            }
            Text(
                "The first request can take a few seconds while the server wakes up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
