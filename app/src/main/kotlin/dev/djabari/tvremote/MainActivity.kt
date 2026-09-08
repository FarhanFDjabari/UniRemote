package dev.djabari.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dev.djabari.tvremote.ui.RemoteLayout
import dev.djabari.tvremote.ui.rememberRemoteLayout

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    // TODO(phase1): WindowInfoTracker -> FoldingFeature to detect TABLETOP,
                    //   then hand `layout` to the nav host so each screen picks its pane split.
                    val layout: RemoteLayout = rememberRemoteLayout()
                    TvRemoteApp(layout = layout)
                }
            }
        }
    }
}
