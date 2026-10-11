package com.oneping.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.oneping.ui.theme.OnePingTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    SETUP("Setup", Icons.Filled.Home),
    SETTINGS("Settings", Icons.Filled.Settings),
    PEOPLE("Sources", Icons.Filled.Person),
    LOG("Log", Icons.AutoMirrored.Filled.List),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = if (Permissions.hasListenerAccess(this)) Tab.SETTINGS else Tab.SETUP
        setContent {
            OnePingTheme {
                var tab by rememberSaveable { mutableStateOf(startTab) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        Tab.SETUP -> SetupScreen(modifier)
                        Tab.SETTINGS -> SettingsScreen(modifier)
                        Tab.PEOPLE -> PeopleScreen(modifier)
                        Tab.LOG -> LogScreen(modifier)
                    }
                }
            }
        }
    }
}
