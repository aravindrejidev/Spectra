package com.aravind.spectra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aravind.spectra.ui.AnalyzerScreen
import com.aravind.spectra.ui.theme.SpectraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpectraTheme {
                AnalyzerScreen()
            }
        }
    }
}
