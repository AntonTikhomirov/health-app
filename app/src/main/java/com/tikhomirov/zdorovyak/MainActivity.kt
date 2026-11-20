package com.tikhomirov.zdorovyak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tikhomirov.zdorovyak.ui.AppRoot
import com.tikhomirov.zdorovyak.ui.theme.ZTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent { ZTheme { AppRoot(app) } }
    }
}
