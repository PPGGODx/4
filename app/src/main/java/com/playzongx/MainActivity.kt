package com.playzongx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.playzongx.ui.App
import com.playzongx.ui.theme.PlayZongXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayZongXTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(container = (application as PlayZongXApp).container)
                }
            }
        }
    }
}
