package ec.cleantok.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONObject

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
    /** Detalle de un producto en un establecimiento: pisos, lugar, encargados y botón para comprarlo. */
    data class DetalleProducto(val estId: String, val estNombre: String, val productoId: String, val nombre: String) : Pantalla
    /** Lo que abre cada indicador del panel: CRITICO, BAJO o ALERTAS. */
    data class ListaPanel(val tipo: String) : Pantalla
    /** La campana: notificaciones como "un proveedor te envió su cotización". */
    data object Notificaciones : Pantalla
    /** Administración del sistema: nueva sede de una empresa con su supervisor, o cambiar el supervisor de una. */
    data class SedeEmpresa(val empresaId: String, val empresaNombre: String, val sede: String? = null) : Pantalla
}

class Navegacion(inicial: Pantalla) {
    val pila = mutableStateListOf(inicial)
    val actual: Pantalla get() = pila.last()

    /** Pestañas visitadas antes de la actual: "atrás" en la raíz vuelve a la anterior en vez de cerrar la app. */
    private val pestanasPrevias = mutableStateListOf<Int>()
    private var pestanaActual by mutableIntStateOf(0)

    /** Pestaña elegida en la raíz; se conserva al volver de una pantalla de detalle. */
    var pestana: Int
        get() = pestanaActual
        set(v) {
            if (v == pestanaActual) return
            pestanasPrevias.remove(v)
            pestanasPrevias.add(pestanaActual)
            pestanaActual = v
        }

    /** Hay a dónde volver: una pantalla abierta encima o una pestaña visitada antes. */
    val puedeVolver: Boolean get() = pila.size > 1 || pestanasPrevias.isNotEmpty()

    fun ir(p: Pantalla) { pila.add(p) }
    fun atras(): Boolean = when {
        pila.size > 1 -> { pila.removeAt(pila.lastIndex); true }
        pestanasPrevias.isNotEmpty() -> { pestanaActual = pestanasPrevias.removeAt(pestanasPrevias.lastIndex); true }
        else -> false
    }
    fun reiniciar(p: Pantalla) { pila.clear(); pila.add(p); pestanasPrevias.clear(); pestanaActual = 0 }
    /** Vuelve a la raíz (pestañas) sin cambiar la pestaña elegida. */
    fun volverARaiz() { while (pila.size > 1) pila.removeAt(pila.lastIndex) }

    /** Producto que se eligió "Comprar" desde el detalle: Compras lo deja como único seleccionado y abre la fecha. */
    var comprarProducto by mutableStateOf<String?>(null)
}

class MainActivity : ComponentActivity() {
    private lateinit var sesion: Sesion
    private lateinit var api: Api
    private lateinit var nav: Navegacion
    private lateinit var avisos: Avisos

    /** Token de un QR que llegó por deep link antes de iniciar sesión. */
    private var tokenPendiente by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = Sesion(this)
        api = Api(sesion)
        nav = Navegacion(if (sesion.conSesion) Pantalla.Inicio else Pantalla.Login)
        avisos = Avisos(api, applicationContext)
        manejarDeepLink(intent)
        manejarAviso(intent)
        enableEdgeToEdge()
        setContent {
            CleanTokTema {
                // Con sesión: consulta las notificaciones cada 30 s y pide permiso para mostrarlas (Android 13+).
                val conSesion = nav.pila.firstOrNull() == Pantalla.Inicio
                CompositionLocalProvider(LocalNav provides nav, LocalAvisos provides avisos, LocalRol provides (if (conSesion) sesion.rol else null)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                        LaunchedEffect(conSesion) {
                            if (!conSesion) { avisos.limpiar(); return@LaunchedEffect }
                            if (Build.VERSION.SDK_INT >= 33 && sesion.rol == "ADMIN_EMPRESA" &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                            while (true) {
                                if (!sesion.debeCambiarClave) avisos.actualizar()
                                delay(Avisos.INTERVALO_MS)
                            }
                        }
                        BackHandler(enabled = nav.puedeVolver) { nav.atras() }
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
            is Pantalla.DetalleProducto -> PantallaDetalleProducto(api, nav, p)
            is Pantalla.ListaPanel -> PantallaListaPanel(api, nav, p)
            Pantalla.Notificaciones -> PantallaNotificaciones(nav)
            is Pantalla.SedeEmpresa -> PantallaSedeEmpresa(api, sesion, nav, p)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarDeepLink(intent)
        manejarAviso(intent)
    }

    /** Se tocó una notificación del sistema: abre lo que avisa (la solicitud cotizada) y la marca leída. */
    private fun manejarAviso(intent: Intent?) {
        val texto = intent?.getStringExtra(Avisos.EXTRA_AVISO) ?: return
        intent.removeExtra(Avisos.EXTRA_AVISO)
        if (!sesion.conSesion) return
        val n = runCatching { JSONObject(texto) }.getOrNull() ?: return
        destinoDe(n)?.let { nav.ir(it) }
        runCatching { Thread { runCatching { api.post("/notificaciones/${n.getString("id")}/leer") } }.start() }
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
