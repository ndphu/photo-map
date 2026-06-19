package com.photomap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.photomap.app.ui.PhotoMapApp
import com.photomap.app.ui.theme.PhotoMapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PhotoMapApplication).container
        setContent {
            PhotoMapTheme {
                PhotoMapApp(container)
            }
        }
    }
}
