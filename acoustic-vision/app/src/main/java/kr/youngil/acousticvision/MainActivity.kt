package kr.youngil.acousticvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kr.youngil.acousticvision.ui.AppRoot
import kr.youngil.acousticvision.ui.AvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvTheme {
                AppRoot()
            }
        }
    }
}
