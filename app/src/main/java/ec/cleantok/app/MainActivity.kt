package ec.cleantok.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/** Pantallas de la app (navegación con una pila simple, sin librerías). */
sealed interface Pantalla {
    data object Login : Pantalla
    /** Raíz con pestañas según el rol. */
    data object Inicio : Pantalla
    data class Ubicaciones(val estId: String, val nombre: String) : Pantalla
    data class Qr(val token: String) : Pantalla
    data class Inventario(val estId: String, val nombre: String) : Pantalla
    data class Kardex(val productoId: String, val nombre: String) : Pantalla
    data class Alertas(val estId: String, val nombre: String) : Pantalla
    data class HojaQr(val estId: String, val nombre: String) : Pantalla
    data class Solicitud(val id: String, val codigo: String) : Pantalla
    data class Orden(val id: String, val codigo: String) : Pantalla
    data class Cotizar(val id: String, val codigo: String) : Pantalla
    data class RutaOrden(val id: String, val codigo: String) : Pantalla
    /** Establecimiento: [id] null para crear uno nuevo. */
    data class Sitio(val id: String?, val nombre: String) : Pantalla
    data object NuevoProveedor : Pantalla
    /** Bodega del establecimiento: existencias, carga desde la orden de compra (PDF) y conteos. */
    data class Bodega(val estId: String, val nombre: String) : Pantalla
    /** Revisión de los materiales leídos de una orden de compra en PDF. */
    data class OrdenPdf(val id: String, val estId: String, val titulo: String) : Pantalla
    /** Conteo físico de una ubicación (piso o bodega). */
    data class Conteo(val estId: String, val ubicacionId: String, val nombre: String, val esBodega: Boolean) : Pantalla
}

class Navegacion(inicial: Pantalla) {
    val pila = mutableStateListOf(inicial)
    val actual: Pantalla get() = pila.last()

    /** Pestaña elegida en la raíz; se conserva al volver de una pantalla de detalle. */
    var pestana by mutableIntStateOf(0)

    fun ir(p: Pantalla) { pila.add(p) }
    fun atras(): Boolean = if (pila.size > 1) { pila.removeAt(pila.lastIndex); true } else false
    fun reiniciar(p: Pantalla) { pila.clear(); pila.add(p); pestana = 0 }
}

class MainActivity : ComponentActivity() {
    private lateinit var sesion: Sesion
    private lateinit var api: Api
    private lateinit var nav: Navegacion

    /** Token de un QR que llegó por deep link antes de iniciar sesión. */
    private var tokenPendiente by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = Sesion(this)
        api = Api(sesion)
        nav = Navegacion(if (sesion.conSesion) Pantalla.Inicio else Pantalla.Login)
        manejarDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            CleanTokTema {
                CompositionLocalProvider(LocalNav provides nav) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        BackHandler(enabled = nav.pila.size > 1) { nav.atras() }
                        // Como en la web (Motion): desliza con resorte, hacia la izquierda al avanzar y a la derecha al volver.
                        AnimatedContent(
                            targetState = nav.pila.size to nav.actual,
                            transitionSpec = {
                                val avanza = targetState.first >= initialState.first
                                val signo = if (avanza) 1 else -1
                                (slideInHorizontally(resorte(IntOffset.VisibilityThreshold)) { signo * it / 4 } + fadeIn(resorte()))
                                    .togetherWith(slideOutHorizontally(resorte(IntOffset.VisibilityThreshold)) { -signo * it / 4 } + fadeOut(tween(150)))
                            },
                            label = "pantallas",
                        ) { (_, p) -> Mostrar(p) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Mostrar(p: Pantalla) {
        when (p) {
            Pantalla.Login -> PantallaLogin(sesion, api) {
                nav.reiniciar(Pantalla.Inicio)
                tokenPendiente?.let { nav.ir(Pantalla.Qr(it)); tokenPendiente = null }
            }
            Pantalla.Inicio -> PantallaRaiz(sesion, api, nav) { nav.reiniciar(Pantalla.Login) }
            is Pantalla.Ubicaciones -> PantallaUbicaciones(api, nav, p)
            is Pantalla.Qr -> PantallaQr(api, sesion, nav, p.token)
            is Pantalla.Inventario -> PantallaInventario(api, nav, p)
            is Pantalla.Kardex -> PantallaKardex(api, p)
            is Pantalla.Alertas -> PantallaAlertas(api, p)
            is Pantalla.HojaQr -> PantallaHojaQr(api, p)
            is Pantalla.Solicitud -> PantallaSolicitud(api, nav, p)
            is Pantalla.Orden -> PantallaOrden(api, nav, p)
            is Pantalla.Cotizar -> PantallaCotizar(api, p)
            is Pantalla.RutaOrden -> PantallaRutaOrden(api, p)
            is Pantalla.Sitio -> PantallaSitio(api, nav, p)
            Pantalla.NuevoProveedor -> PantallaNuevoProveedor(api, sesion, nav)
            is Pantalla.Bodega -> PantallaBodega(api, sesion, nav, p)
            is Pantalla.OrdenPdf -> PantallaOrdenPdf(api, nav, p)
            is Pantalla.Conteo -> PantallaConteo(api, p)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarDeepLink(intent)
    }

    /** http(s)://{servidor}/q/{token} → pantalla del QR. */
    private fun manejarDeepLink(intent: Intent?) {
        val token = tokenDe(intent?.dataString ?: return) ?: return
        if (sesion.conSesion) nav.ir(Pantalla.Qr(token)) else tokenPendiente = token
    }

    companion object {
        /** Acepta la URL completa del QR o solo el token. */
        fun tokenDe(texto: String): String? {
            val t = texto.trim()
            if ("/q/" in t) return t.substringAfter("/q/").substringBefore('?').substringBefore('/').ifBlank { null }
            return t.takeIf { it.length >= 20 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        }
    }
}
