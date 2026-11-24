package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TradeSpotlightScreen
import com.example.ui.TradeSpotlightViewModel
import com.example.ui.theme.TradeSpotlightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            TradeSpotlightTheme {
                val vm: TradeSpotlightViewModel = viewModel(
                    factory = TradeSpotlightViewModel.Factory(application),
                )
                TradeSpotlightScreen(viewModel = vm)
            }
        }
    }
}
