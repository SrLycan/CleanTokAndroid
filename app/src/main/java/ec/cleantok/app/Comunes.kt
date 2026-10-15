package ec.cleantok.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ejecuta en segundo plano y devuelve un mensaje legible si falla. */
suspend fun <T> enFondo(bloque: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(bloque) }

fun mensajeError(e: Throwable): String = when {
    e is ApiError -> e.toString()
    Api.sinConexion(e) -> "Sin conexión con el servidor (${e.message})"
    else -> e.message ?: e.javaClass.simpleName
}

/** La navegación, para que la barra superior sepa si muestra "atrás". */
val LocalNav = staticCompositionLocalOf<Navegacion?> { null }

/**
 * Carga datos con indicador y botón de reintento; `recargar` vuelve a pedirlos.
 * Al recargar se mantiene lo que ya se veía (con una barra fina arriba), así las listas animan el cambio en vez de parpadear.
 */
@Composable
fun <T> Cargador(clave: Any, cargar: () -> T, contenido: @Composable (T, recargar: () -> Unit) -> Unit) {
    var version by remember(clave) { mutableIntStateOf(0) }
    var resultado by remember(clave) { mutableStateOf<Result<T>?>(null) }
    var recargando by remember(clave) { mutableStateOf(false) }
    LaunchedEffect(clave, version) {
        recargando = resultado?.isSuccess == true
        if (!recargando) resultado = null
        resultado = enFondo(cargar)
        recargando = false
    }
    val r = resultado
    AnimatedVisibility(recargando, enter = fadeIn(), exit = fadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    when {
        r == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        r.isFailure -> Card(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).entrada(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("No se pudo cargar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Text(mensajeError(r.exceptionOrNull()!!), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                FilledTonalButton(onClick = { version++ }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Text("Reintentar", Modifier.padding(start = 6.dp))
                }
            }
        }
        else -> contenido(r.getOrThrow()) { version++ }
    }
}

/** Estructura de cada pantalla: barra superior (con "atrás" si hay a dónde volver) y contenido con márgenes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Marco(
    titulo: String,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    barraInferior: @Composable () -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = barraInferior,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitulo?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    if (nav != null && nav.pila.size > 1) {
                        IconButton(onClick = { nav.atras() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                    }
                },
                actions = acciones,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).consumeWindowInsets(relleno).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { contenido() }
    }
}

/** Relleno inferior para listas, para que el último elemento no quede pegado al borde. */
val FinDeLista = PaddingValues(bottom = 24.dp)

@Composable
fun Fila(contenido: @Composable RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { contenido() }

/** Tarjeta base de la app: superficie clara, bordes redondeados y, opcionalmente, tocable. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, alTocar: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val colores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val borde = CardDefaults.outlinedCardBorder()
    if (alTocar != null) {
        val fuente = remember { MutableInteractionSource() }
        Card(onClick = alTocar, modifier.fillMaxWidth().encogerAlTocar(fuente), shape = MaterialTheme.shapes.large, colors = colores,
            border = borde, interactionSource = fuente) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
        }
    } else {
        Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colores, border = borde) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
        }
    }
}

/** Píldora de color: estado, severidad, tipo. */
@Composable
fun Etiqueta(texto: String, tono: Tono = Tono.NEUTRO) {
    val c = tono.colores()
    Surface(color = c.fondo, contentColor = c.contenido, shape = CircleShape) {
        Text(texto, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
    }
}

/** Mensaje destacado (resultado de una acción, alerta, ayuda). */
@Composable
fun Aviso(texto: String, tono: Tono, titulo: String? = null, alCerrar: (() -> Unit)? = null) {
    val c = tono.colores()
    val icono = when (tono) {
        Tono.OK -> Icons.Filled.CheckCircle
        Tono.AVISO, Tono.CRITICO -> Icons.Filled.Warning
        else -> Icons.Filled.Info
    }
    Surface(color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (alCerrar == null) 14.dp else 4.dp)) {
            Icon(icono, null, Modifier.size(20.dp).padding(top = 1.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                titulo?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(texto, style = MaterialTheme.typography.bodyMedium)
            }
            alCerrar?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Cerrar", Modifier.size(18.dp)) }
            }
        }
    }
}

/** Ícono dentro de un círculo de color. */
@Composable
fun IconoRedondo(icono: ImageVector, tono: Tono = Tono.INFO, tam: Dp = 40.dp, fondo: Color? = null, tinte: Color? = null) {
    val c = tono.colores()
    Surface(color = fondo ?: c.fondo, contentColor = tinte ?: c.contenido, shape = CircleShape, modifier = Modifier.size(tam)) {
        Box(contentAlignment = Alignment.Center) { Icon(icono, null, Modifier.size(tam * 0.55f)) }
    }
}

/** Círculo con las iniciales de una persona. */
@Composable
fun Iniciales(nombre: String, tam: Dp = 48.dp) {
    val ini = nombre.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape, modifier = Modifier.size(tam)) {
        Box(contentAlignment = Alignment.Center) { Text(ini, style = MaterialTheme.typography.titleMedium) }
    }
}

/** Cifra grande con su rótulo (stock, cobertura…). */
@Composable
fun Dato(rotulo: String, valor: String, unidad: String? = null, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = MaterialTheme.typography.titleLarge, color = color)
            unidad?.let {
                Text(" $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, extra: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        extra()
    }
}

/** Pantalla vacía con ícono y explicación. */
@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, texto: String? = null, tono: Tono = Tono.OK) {
    Column(Modifier.fillMaxWidth().entrada().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconoRedondo(icono, tono, tam = 72.dp)
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        texto?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

/** Botón cuadrado con ícono arriba y texto abajo (accesos de un establecimiento). */
@Composable
fun Acceso(icono: ImageVector, texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    val fuente = remember { MutableInteractionSource() }
    Surface(onClick = alTocar, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = modifier.encogerAlTocar(fuente, 0.94f), interactionSource = fuente) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icono, null, Modifier.size(22.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** 7.570 → "7,57" */
fun num(v: Any?): String = when (v) {
    null, JSONObject.NULL -> "—"
    else -> runCatching { BigDecimal(v.toString()).stripTrailingZeros().toPlainString().replace('.', ',') }.getOrDefault(v.toString())
}

fun JSONObject.texto(campo: String): String? = if (isNull(campo)) null else optString(campo)

/** "SIN_DATOS" → "Sin datos" */
fun String.legible(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val formatoFecha = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es"))

/** ISO-8601 → "13 oct 2026 · 10:09" en la hora del teléfono. */
fun fecha(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(formatoFecha)
}.getOrDefault(iso.take(16).replace('T', ' '))
