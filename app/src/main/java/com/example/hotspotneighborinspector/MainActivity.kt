package com.example.hotspotneighborinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.hotspotneighborinspector.ui.HotspotInspectorScreen
import com.example.hotspotneighborinspector.ui.HotspotInspectorViewModel
import com.example.hotspotneighborinspector.ui.theme.HotspotNeighborInspectorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HotspotInspectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#090D16")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#090D16"))
        )
        setContent {
            HotspotNeighborInspectorTheme {
                HotspotInspectorScreen(viewModel = viewModel)
            }
        }
    }
}

