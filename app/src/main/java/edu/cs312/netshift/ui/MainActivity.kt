package edu.cs312.netshift.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.cs312.netshift.NetShiftApplication
import edu.cs312.netshift.ui.navigation.NetShiftNavHost
import edu.cs312.netshift.ui.theme.NetShiftTheme
import edu.cs312.netshift.ui.welcome.WelcomeScreen
import kotlin.math.hypot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NetShiftApplication).container
        val preferences = getSharedPreferences("netshift_ui", MODE_PRIVATE)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(preferences.getBoolean("dark", systemDark)) }
            var welcomed by rememberSaveable { mutableStateOf(preferences.getBoolean("welcomed", false)) }
            var previousFrame by remember { mutableStateOf<ImageBitmap?>(null) }
            val reveal = remember { Animatable(1f) }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val uiScope = rememberCoroutineScope()
            LaunchedEffect(previousFrame) {
                if (previousFrame != null) {
                    reveal.snapTo(0f)
                    reveal.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
                    previousFrame = null
                }
            }
            NetShiftTheme(darkTheme = dark) {
                val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                val network by appViewModel.network.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    Crossfade(welcomed, animationSpec = tween(400), label = "Welcome transition") { hasStarted ->
                        if (!hasStarted) WelcomeScreen(onGetStarted = {
                            preferences.edit().putBoolean("welcomed", true).apply()
                            welcomed = true
                        }, transport = network.transport) else NetShiftNavHost(appViewModel, container, dark, previousFrame == null) {
                            val captured = if (android.animation.ValueAnimator.areAnimatorsEnabled())
                                runCatching { view.drawToBitmap(Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull() else null
                            uiScope.launch {
                                reveal.snapTo(0f)
                                previousFrame = captured
                                dark = !dark
                                preferences.edit().putBoolean("dark", dark).apply()
                            }
                        }
                    }
                    previousFrame?.let { old ->
                        Canvas(Modifier.fillMaxSize()) {
                            val hole = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(Offset.Zero, size))
                                val origin = Offset(size.width - 52.dp.toPx(), 60.dp.toPx())
                                val radius = hypot(size.width, size.height) * reveal.value
                                addOval(Rect(origin.x-radius, origin.y-radius, origin.x+radius, origin.y+radius))
                            }
                            clipPath(hole) { drawImage(old) }
                        }
                    }
                }
            }
        }
    }
}
