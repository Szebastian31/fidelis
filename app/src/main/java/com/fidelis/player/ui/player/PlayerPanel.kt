package com.fidelis.player.ui.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core. Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fidelis.player.ui.navigation.PLAYER
import com.fidelis.player.ui.theme.ChromeInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The full player as a panel over the app instead of a separate screen, so it grows out of the mini
 * player under your finger and shrinks back into it.
 */
object PlayerPanel {
    /** 0 = tucked into the mini player, 1 = full screen. */
    val progress = Animatable(0f)
    /** Where it's heading. */
    var open by mutableStateOf(false); private set
    /** How far the player is being pulled down from inside, in pixels. */
    var pull by mutableFloatStateOf(0f)
    /** Pixels between full screen and the mini player; the overlay keeps it up to date. */
    var travel by mutableFloatStateOf(1600f)
    private var scope: CoroutineScope? = null

    private val OPEN = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    private val CLOSE = spring<Float>(dampingRatio = 0.92f, stiffness = 420f)

    fun bind(s: CoroutineScope) { scope = s }

    /** Velocity in panels per second; positive opens. */
    fun expand(velocity: Float = 0f) {
        open = true
        scope?.launch { progress.animateTo(1f, OPEN, velocity) }
    }

    fun collapse(velocity: Float = 0f) {
        open = false
        scope?.launch {
            // Carry on from wherever a pull from inside the player has got to.
            if (pull > 0f) {
                val from = (progress.value - pull / travel).coerceIn(0f, 1f)
                pull = 0f
                progress.snapTo(from)
            }
            progress.animateTo(0f, CLOSE, velocity)
        }
    }

    /** Follows a finger: 0 is the mini player, 1 full screen. */
    fun dragTo(p: Float) { scope?.launch { progress.snapTo(p.coerceIn(0f, 1f)) } }

    /** After a drag: a flick or a pull past a quarter opens it. Returns true if it opens. */
    fun settle(velocity: Float): Boolean {
        val opens = velocity > 1.2f || (velocity > -1.2f && progress.value > 0.25f)
        if (opens) expand(velocity) else collapse(velocity)
        return opens
    }
}

/** Draws the player over everything, growing out of the mini player's spot. */
@Composable
fun PlayerOverlay(nav: NavController) {
    val scope = rememberCoroutineScope()
    SideEffect { PlayerPanel.bind(scope) }
    // Opening a page (an artist from the player, say) tucks the player away.
    DisposableEffect(nav) {
        val l = NavController.OnDestinationChangedListener { _, d, _ ->
            if (d.route != PLAYER && PlayerPanel.open) PlayerPanel.collapse()
        }
        nav.addOnDestinationChangedListener(l)
        onDispose { nav.removeOnDestinationChangedListener(l) }
    }
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().onSizeChanged { height = it.height }) {
        // The mini player's top edge: where the player grows from.
        val startY = height - with(density) { (ChromeInsets.bottom - 18.dp).toPx() }
        SideEffect { if (startY > 0f) PlayerPanel.travel = startY }
        if (PlayerPanel.open || PlayerPanel.progress.value > 0f) {
            PredictiveBackHandler(enabled = PlayerPanel.open) { events ->
                try {
                    events.collect { e -> PlayerPanel.dragTo(1f - 0.3f * e.progress) }
                    PlayerPanel.collapse()
                } catch (e: CancellationException) {
                    PlayerPanel.expand()
                    throw e
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val q = PlayerPanel.progress.value
                    val pulled = (PlayerPanel.pull / size.height).coerceIn(0f, 1f)
                    translationY = (1f - q) * PlayerPanel.travel + PlayerPanel.pull
                    // Starts as wide as the mini player and fills the screen as it rises.
                    val inset = 12.dp.toPx() * (1f - q)
                    val s = (1f - 2f * inset / size.width) * (1f - 0.08f * pulled)
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = (q * 3f).coerceIn(0f, 1f) * (1f - 0.4f * pulled)
                    shape = RoundedCornerShape(20.dp.toPx() * (1f - q) + 28.dp.toPx() * pulled)
                    clip = true
                },
            ) {
                NowPlayingScreen(onBack = { PlayerPanel.collapse() })
            }
        }
    }
}
