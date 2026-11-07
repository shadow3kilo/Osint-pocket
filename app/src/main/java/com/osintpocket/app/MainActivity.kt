package com.osintpocket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.osintpocket.app.core.AppPrefs
import com.osintpocket.app.ui.disclaimer.DisclaimerScreen
import com.osintpocket.app.ui.navigation.MainScaffold
import com.osintpocket.app.ui.theme.OsintTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OsintTheme {
                var accepted by rememberSaveable { mutableStateOf(prefs.disclaimerAccepted) }
                if (accepted) {
                    MainScaffold(torEnabled = prefs.torEnabledFlow)
                } else {
                    DisclaimerScreen(onAccept = {
                        prefs.disclaimerAccepted = true
                        accepted = true
                    })
                }
            }
        }
    }
}
