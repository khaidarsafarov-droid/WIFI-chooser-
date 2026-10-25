package com.wifipriority

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

/**
 * Stage-1 placeholder. Priority decisions live in [com.wifipriority.engine.PriorityEngine].
 * [BuildConfig.USE_MOCK_WIFI] selects the mock stack; this activity does not scan or connect.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
