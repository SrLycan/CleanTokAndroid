package ec.cleantok.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Movimiento al estilo de la web (React + Motion): todo se anima con resortes, los elementos entran
 * escalonados con fade + desplazamiento hacia arriba, y lo tocable se encoge un poco al presionarlo.
 */

/** Equivale a `transition={{ type: "spring", stiffness: 300, damping: 30 }}` de Motion. */
fun <T> resorte(visibilidad: T? = null): SpringSpec<T> = spring(dampingRatio = 0.87f, stiffness = 300f, visibilityThreshold = visibilidad)

/** Resorte más suave para barras y rellenos. */
fun <T> resorteSuave(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow)

/** Retraso entre elementos al entrar (como `staggerChildren: 0.05`); los de más abajo entran a la vez. */
private const val ESCALON_MS = 50L
private const val MAX_ESCALONES = 8

/**
 * `initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}` con escalonado según `indice`.
 * Se anima una sola vez por composición del elemento.
 */
@Composable
fun Modifier.entrada(indice: Int = 0): Modifier {
    val alfa = remember { Animatable(0f) }
    val y = remember { Animatable(1f) }
    val desplazamiento = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(ESCALON_MS * indice.coerceAtMost(MAX_ESCALONES))
        launch { alfa.animateTo(1f, resorte()) }
        y.animateTo(0f, resorte())
    }
    return graphicsLayer {
        this.alpha = alfa.value
        translationY = y.value * desplazamiento
    }
}

/** `whileTap={{ scale: 0.97 }}`: se encoge mientras se presiona y vuelve con resorte. */
@Composable
fun Modifier.encogerAlTocar(fuente: InteractionSource, escala: Float = 0.97f): Modifier {
    val presionado by fuente.collectIsPressedAsState()
    val s by animateFloatAsState(if (presionado) escala else 1f, resorte(), label = "tap")
    return graphicsLayer { scaleX = s; scaleY = s }
}

/** Valor que arranca en 0 y llega a `objetivo` con resorte suave (barras, rellenos). */
@Composable
fun animadoDesdeCero(objetivo: Float): Float {
    val valor = remember { Animatable(0f) }
    LaunchedEffect(objetivo) { valor.animateTo(objetivo, resorteSuave()) }
    return valor.value
}
