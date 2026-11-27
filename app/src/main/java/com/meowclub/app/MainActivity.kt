package com.meowclub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.meowclub.app.data.CatViewModel
import com.meowclub.app.ui.screens.MainAppRoot

class MainActivity : ComponentActivity() {
    private val viewModel: CatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainAppRoot(viewModel = viewModel)
        }
    }
}
