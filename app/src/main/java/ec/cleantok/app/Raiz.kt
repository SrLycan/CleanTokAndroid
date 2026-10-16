package ec.cleantok.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

/** Una pestaña de la raíz. [contenido] recibe la barra inferior para ponerla en su propio Marco. */
private class Pestana(val clave: String, val titulo: String, val icono: ImageVector, val contenido: @Composable (barra: @Composable () -> Unit) -> Unit)

/**
 * Raíz de la app según el rol:
 * - Administración del sistema: Resumen · Empresas · Proveedores
 * - Administración de la empresa: Panel · Compras · Pedidos · Gestión
 * - Supervisor de establecimiento: Mi sede · [Escanear QR] · Pedidos (solo la sede que tiene asignada)
 * - Administrador de contrato: Consumo atípico · Insumos por piso (solo lectura de sus sedes)
 * - Encargado: Inicio · [Escanear QR] · Desabasto
 * - Proveedor: Cotizar · Pedidos · Gestión
 * - Repartidor: Mi ruta · Pedidos
 * Con una clave temporal, primero se pide crear la contraseña propia.
 */
@Composable
fun PantallaRaiz(sesion: Sesion, api: Api, nav: Navegacion, alSalir: () -> Unit) {
    var rol by remember { mutableStateOf(sesion.rol) }
    val r = rol
    if (r == null) {
        // Sesión iniciada antes de que la app conociera los roles: se pregunta una vez.
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Cargador("rol", { api.obj("/auth/me") }) { me, _ ->
                LaunchedEffect(me) {
                    val u = me.getJSONObject("usuario")
                    sesion.recordarUsuario(u.getString("nombre"), u.getString("rol"))
                    rol = u.getString("rol")
                }
            }
        }
        return
    }
    var cambiarClave by remember { mutableStateOf(sesion.debeCambiarClave) }
    if (cambiarClave) {
        PantallaCambiarClave(api, sesion, alSalir) { cambiarClave = false }
        return
    }

    // Escáner del botón central del encargado.
    val contexto = LocalContext.current
    val escaner = rememberLauncherForActivityResult(ScanContract()) { res ->
        val token = res.contents?.let { MainActivity.tokenDe(it) }
        if (token != null) nav.ir(Pantalla.Qr(token))
        else if (res.contents != null) Toast.makeText(contexto, "Ese QR no es de CleanTok", Toast.LENGTH_LONG).show()
    }

    val pedidos = Pestana("pedidos", "Pedidos", Iconos.Camion) { b -> PantallaPedidos(api, nav, r, alSalir, b) }
    val pestanas = when (r) {
        "ADMIN_EMPRESA" -> listOf(
            Pestana("panel", "Panel", Iconos.Panel) { b -> PantallaPanel(api, nav, alSalir, b) },
            Pestana("sedes", "Sedes", Icons.Filled.Place) { b -> PantallaMisEstablecimientos(api, nav, alSalir, b) },
            Pestana("compras", "Compras", Icons.Filled.ShoppingCart) { b -> PantallaCompras(api, nav, alSalir, b) },
            pedidos,
            Pestana("gestion", "Gestión", Icons.Filled.Settings) { b -> PantallaGestionEmpresa(api, sesion, nav, alSalir, b) },
        )
        "SUPERVISOR" -> listOf(
            Pestana("sede", "Mi sede", Icons.Filled.Place) { b -> PantallaSupervisor(api, nav, alSalir, b) },
            pedidos,
        )
        "ADMIN_CONTRATO" -> listOf(
            Pestana("consumo", "Consumo atípico", Iconos.Panel) { b -> PantallaConsumoAtipico(api, nav, alSalir, b) },
            Pestana("insumos", "Insumos por piso", Iconos.Piso) { b -> PantallaInsumosPisos(api, nav, alSalir, b) },
        )
        "ENCARGADO" -> listOf(
            Pestana("inicio", "Inicio", Icons.Filled.Home) { b -> PantallaInicio(sesion, api, nav, alSalir, b) },
            Pestana("desabasto", "Desabasto", Iconos.Desabasto) { b -> PantallaDesabasto(api, sesion, alSalir, b) },
        )
        "PROVEEDOR" -> listOf(
            Pestana("cotizar", "Cotizar", Iconos.Tienda) { b -> PantallaSolicitudesProveedor(api, nav, alSalir, b) },
            pedidos,
            Pestana("gestion", "Gestión", Icons.Filled.Settings) { b -> PantallaGestionProveedor(api, sesion, alSalir, b) },
        )
        "SUPER_ADMIN" -> listOf(
            Pestana("resumen", "Resumen", Icons.Filled.Home) { b -> PantallaResumenPlataforma(api, alSalir, b) },
            Pestana("empresas", "Empresas", Iconos.Limpieza) { b -> PantallaEmpresasPlataforma(api, sesion, alSalir, b) },
            Pestana("proveedores", "Proveedores", Iconos.Tienda) { b -> PantallaProveedoresPlataforma(api, nav, alSalir, b) },
        )
        "REPARTIDOR" -> listOf(Pestana("ruta", "Mi ruta", Iconos.Ruta) { b -> PantallaMiRuta(api, nav, alSalir, b) }, pedidos)
        else -> listOf(Pestana("inicio", "Inicio", Icons.Filled.Home) { b -> PantallaInicio(sesion, api, nav, alSalir, b) })
    }

    // Insignias: lo que espera una acción de este rol.
    val insignias = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(r, nav.pila.size) {
        enFondo { contarPendientes(api, r) }.onSuccess { insignias.clear(); insignias.putAll(it) }
    }

    val i = nav.pestana.coerceIn(pestanas.indices)
    val barra: @Composable () -> Unit = {
        if (r == "ENCARGADO" || r == "SUPERVISOR") {
            BarraConEscaner(pestanas, i, insignias, alElegir = { nav.pestana = it }) {
                escaner.launch(ScanOptions().setPrompt("Escanea el QR del piso o la bodega").setBeepEnabled(false).setOrientationLocked(false))
            }
        } else if (pestanas.size > 1) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                pestanas.forEachIndexed { j, p ->
                    NavigationBarItem(
                        selected = j == i,
                        onClick = { nav.pestana = j },
                        icon = {
                            val n = insignias[p.clave] ?: 0
                            BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) { Icon(p.icono, null) }
                        },
                        label = { Text(p.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        }
    }
    AnimatedContent(i, transitionSpec = { fadeIn(resorte()) togetherWith fadeOut(tween(120)) }, label = "pestanas") { k ->
        pestanas[k].contenido(barra)
    }
}

/**
 * Barra del encargado: dos pestañas y, al centro, el botón de escanear QR, más grande y sobresaliendo
 * de la barra (su tarea principal está a un toque desde cualquier pestaña).
 */
@Composable
private fun BarraConEscaner(pestanas: List<Pestana>, elegida: Int, insignias: Map<String, Int>, alElegir: (Int) -> Unit, alEscanear: () -> Unit) {
    @Composable
    fun RowScope.Item(j: Int) {
        val p = pestanas[j]
        NavigationBarItem(
            selected = j == elegida,
            onClick = { alElegir(j) },
            icon = {
                val n = insignias[p.clave] ?: 0
                BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) { Icon(p.icono, null) }
            },
            label = { Text(p.titulo) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
        )
    }
    Box(Modifier.fillMaxWidth()) {
        NavigationBar(Modifier.padding(top = SOBRESALE), containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Item(0)
            // Hueco del botón central, con su rótulo a la altura de los demás.
            Text("Escanear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).align(Alignment.Bottom).padding(bottom = 16.dp))
            Item(1)
        }
        val fuente = remember { MutableInteractionSource() }
        Surface(
            onClick = alEscanear,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 8.dp,
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.background),
            interactionSource = fuente,
            modifier = Modifier.align(Alignment.TopCenter).size(72.dp).encogerAlTocar(fuente, 0.92f),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Iconos.Qr, "Escanear QR", Modifier.size(34.dp)) }
        }
    }
}

/** Cuánto sobresale el botón central por encima de la barra. */
private val SOBRESALE = 28.dp

/** Cuántas cosas esperan a este rol, por pestaña. */
private fun contarPendientes(api: Api, rol: String): Map<String, Int> {
    if (rol == "SUPER_ADMIN" || rol == "ENCARGADO") return emptyMap()
    val ordenes = api.arr("/ordenes-compra").objetos()
    fun enCamino(o: org.json.JSONObject) = o.getString("estado") in setOf("DESPACHADA", "EN_TRANSITO", "RETIRADA") && o.getInt("recibidas") < o.getInt("destinos")
    return when (rol) {
        "ADMIN_EMPRESA" -> mapOf(
            "pedidos" to ordenes.count(::enCamino),
            "compras" to api.arr("/solicitudes").objetos().count { it.getString("estado") == "EN_COTIZACION" && it.getInt("ofertas") > 0 },
        )
        "PROVEEDOR" -> mapOf(
            "pedidos" to ordenes.count { it.getString("estado") in setOf("APROBADA", "ACEPTADA", "EN_PREPARACION", "EMPACADA") },
            "cotizar" to api.arr("/proveedor/solicitudes").objetos().count { it.getBoolean("puedeOfertar") && it.isNull("miOferta") },
        )
        "SUPERVISOR" -> mapOf("pedidos" to ordenes.count(::enCamino))
        "REPARTIDOR" -> mapOf("pedidos" to ordenes.count { it.getString("estado") in setOf("EMPACADA", "DESPACHADA", "EN_TRANSITO") })
        else -> emptyMap()
    }
}

/** Cerrar sesión (revoca el refresh token en el servidor, en segundo plano). */
@Composable
fun BotonSalir(api: Api, alSalir: () -> Unit) {
    val scope = rememberCoroutineScope()
    IconButton(onClick = { scope.launch { enFondo { api.logout() }; alSalir() } }) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión")
    }
}
