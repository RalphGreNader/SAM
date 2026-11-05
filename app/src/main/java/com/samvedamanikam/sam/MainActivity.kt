package com.samvedamanikam.sam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samvedamanikam.sam.ui.MainScaffold
import com.samvedamanikam.sam.ui.pair.PairScreen
import com.samvedamanikam.sam.ui.theme.SamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamApp).container
        setContent {
            SamTheme {
                // Pair once; afterwards the device key signs in silently. Revocation flips this back to the pair screen.
                val paired by container.deviceAuth.paired.collectAsStateWithLifecycle()
                if (paired) MainScaffold() else PairScreen()
            }
        }
    }
}
