package com.mamoro.investments

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.mamoro.investments.ui.InvestmentsScreen
import com.mamoro.investments.ui.InvestmentsViewModel
import com.mamoro.investments.ui.theme.InvestmentsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: InvestmentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            InvestmentsTheme { InvestmentsScreen(viewModel) }
        }
    }
}
