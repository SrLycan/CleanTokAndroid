package ec.cleantok.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.roundToInt

/** Todo lo que necesita el panel, cargado de una vez. */
internal class DatosPanel(
    val sitios: List<JSONObject>,
    /** Filas de inventario de todos los sitios, cada una con `sitio` = {id, nombre}. */
    val productos: List<JSONObject>,
    /** Alertas abiertas de todos los sitios, cada una con `sitio`. */
    val alertas: List<JSONObject>,
    val sugerencias: List<JSONObject>,
    val porRecibir: List<JSONObject>,
)

internal fun cargarPanel(api: Api): DatosPanel {
    val sitios = api.arr("/establecimientos/mios").objetos()
    val productos = mutableListOf<JSONObject>()
    val alertas = mutableListOf<JSONObject>()
    sitios.forEach { s ->
        val id = s.getString("id")
        val ref = JSONObject().put("id", id).put("nombre", s.getString("nombre"))
        api.obj("/establecimientos/$id/inventario").getJSONArray("items").objetos().forEach { productos += it.put("sitio", ref) }
        api.obj("/establecimientos/$id/alertas?estado=ABIERTA&size=100").getJSONArray("content").objetos().forEach { alertas += it.put("sitio", ref) }
    }
    val porRecibir = api.arr("/ordenes-compra").objetos().filter {
        it.getString("estado") in setOf("DESPACHADA", "EN_TRANSITO", "RETIRADA") && it.getInt("recibidas") < it.getInt("destinos")
    }
    return DatosPanel(sitios, productos, alertas.sortedByDescending { it.getString("creadaEn") }, api.arr("/abastecimiento/sugerencias").objetos(), porRecibir)
}

/** Pestaña de inicio del admin: estado de todos sus sitios, qué comprar y dónde mirar primero. */
@Composable
fun PantallaPanel(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Panel", subtitulo = "Resumen de tus establecimientos", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(FinDeLista), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Saludo(api, Modifier.entrada(0))
            CotizacionesRecibidas(nav)
            AccesosRapidos(nav)
            Cargador("panel", { cargarPanel(api) }) { d, recargar ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Indicadores(d, nav, recargar)
                    SugerenciasCompra(d.sugerencias, nav)
                    AlertasRecientes(d.alertas, nav)
                    PisosConAtencion(d, nav)
                    SaludSitios(d, nav)
                    MasConsumidos(d.productos)
                }
            }
        }
    }
}

/**
 * Alerta del panel: cotizaciones que enviaron los proveedores y aún no se revisan (las mismas de la campana).
 * Al tocar una se abre la solicitud para comparar ofertas.
 */
@Composable
private fun CotizacionesRecibidas(nav: Navegacion) {
    val avisos = LocalAvisos.current ?: return
    val scope = rememberCoroutineScope()
    val nuevas = avisos.recientes.filter { !it.getBoolean("leida") && it.getString("tipo") == "OFERTA_RECIBIDA" }
    AnimatedVisibility(nuevas.isNotEmpty(), enter = fadeIn(resorte()) + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val c = Tono.AVISO.colores()
        Surface(color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Notifications, null)
                    Text(
                        if (nuevas.size == 1) "Te enviaron una cotización" else "Te enviaron ${nuevas.size} cotizaciones",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    )
                }
                nuevas.take(3).forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                            scope.launch { avisos.leer(n) }
                            destinoDe(n)?.let { nav.ir(it) }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n.getString("titulo"), style = MaterialTheme.typography.bodyMedium)
                            Text(n.getString("mensaje"), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Comparar ofertas")
                    }
                }
                if (nuevas.size > 3) Text("Ver todas en la campana", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { nav.ir(Pantalla.Notificaciones) })
            }
        }
    }
}

/** Atajos a las otras pestañas del admin, arriba del todo. */
@Composable
private fun AccesosRapidos(nav: Navegacion) {
    Row(Modifier.entrada(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Acceso(Icons.Filled.Place, "Sedes", Modifier.weight(1f)) { nav.pestana = PESTANA_SEDES }
        Acceso(Icons.Filled.ShoppingCart, "Compras", Modifier.weight(1f)) { nav.pestana = PESTANA_COMPRAS }
        Acceso(Iconos.Camion, "Pedidos", Modifier.weight(1f)) { nav.pestana = PESTANA_PEDIDOS }
    }
}

@Composable
private fun Indicadores(d: DatosPanel, nav: Navegacion, recargar: () -> Unit) {
    val criticos = d.productos.count { it.getString("estado") == "CRITICO" }
    val bajos = d.productos.count { it.getString("estado") == "BAJO" }
    Seccion("Hoy", Modifier.entrada(1)) {
        IconButton(onClick = recargar) { Icon(Icons.Filled.Refresh, "Actualizar") }
    }
    Row(Modifier.entrada(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Indicador("Productos críticos", criticos, if (criticos > 0) Tono.CRITICO else Tono.OK, Iconos.Desabasto, Modifier.weight(1f),
            alTocar = { nav.ir(Pantalla.ListaPanel("CRITICO")) })
        Indicador("Con stock bajo", bajos, if (bajos > 0) Tono.AVISO else Tono.OK, Iconos.Inventario, Modifier.weight(1f),
            alTocar = { nav.ir(Pantalla.ListaPanel("BAJO")) })
    }
    Row(Modifier.entrada(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Indicador("Alertas abiertas", d.alertas.size, if (d.alertas.isEmpty()) Tono.OK else Tono.AVISO, Icons.Filled.Notifications, Modifier.weight(1f),
            alTocar = { nav.ir(Pantalla.ListaPanel("ALERTAS")) })
        Indicador("Pedidos por recibir", d.porRecibir.size, Tono.INFO, Iconos.Camion, Modifier.weight(1f),
            alTocar = { nav.pestana = PESTANA_PEDIDOS })
    }
}

/** Cifra grande con ícono, como los KPI de la web. */
@Composable
private fun Indicador(rotulo: String, cuenta: Int, tono: Tono, icono: ImageVector, modifier: Modifier, alTocar: (() -> Unit)? = null) {
    val c = tono.colores()
    val fuente = remember { MutableInteractionSource() }
    Surface(
        color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.large,
        modifier = modifier.encogerAlTocar(fuente).clip(MaterialTheme.shapes.large)
            .then(if (alTocar != null) Modifier.clickable(interactionSource = fuente, indication = null, onClick = alTocar) else Modifier),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${animadoDesdeCero(cuenta.toFloat()).roundToInt()}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Icon(icono, null, Modifier.size(22.dp))
            }
            Text(rotulo, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lo que conviene comprar ya: productos bajos o críticos con la cantidad recomendada. */
@Composable
private fun SugerenciasCompra(sugerencias: List<JSONObject>, nav: Navegacion) {
    val orden = sugerencias.sortedWith(
        compareBy<JSONObject>({ if (it.getString("estado") == "CRITICO") 0 else 1 }, { it.texto("diasCobertura")?.toDoubleOrNull() ?: Double.MAX_VALUE }),
    )
    val sinPedir = orden.count { it.isNull("yaPedido") }
    Tarjeta(Modifier.entrada(3)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Icons.Filled.ShoppingCart, if (sinPedir > 0) Tono.AVISO else Tono.OK)
            Column(Modifier.weight(1f)) {
                Text("Sugerencias de compra", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        orden.isEmpty() -> "Todo cubierto: nada por reponer"
                        sinPedir == 0 -> "Todo lo bajo ya está pedido"
                        else -> "$sinPedir producto${if (sinPedir == 1) "" else "s"} por pedir · cantidades para ~20 días"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        orden.take(MAX_FILAS).forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val prod = s.getJSONObject("producto")
            val critico = s.getString("estado") == "CRITICO"
            val est = s.getJSONObject("establecimiento")
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                    nav.ir(Pantalla.DetalleProducto(est.getString("id"), est.getString("nombre"), prod.getString("id"), prod.getString("nombre")))
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Punto(if (critico) Tono.CRITICO else Tono.AVISO)
                Column(Modifier.weight(1f)) {
                    Text(prod.getString("nombre"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.nombreDe("establecimiento")} · ${s.getString("motivo")}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${num(s.get("cantidadRecomendada"))} ${prod.getString("unidad")}", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    s.texto("yaPedido")?.let { Etiqueta("En $it", Tono.INFO) }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (orden.size > MAX_FILAS) {
            Text("y ${orden.size - MAX_FILAS} más", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sinPedir > 0) {
            Button(onClick = { nav.pestana = PESTANA_COMPRAS }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ShoppingCart, null, Modifier.size(18.dp))
                Text("Pedir cotizaciones", Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Las últimas alertas abiertas: incluye los desabastos que reportan los encargados. */
@Composable
private fun AlertasRecientes(alertas: List<JSONObject>, nav: Navegacion) {
    if (alertas.isEmpty()) return
    Tarjeta(Modifier.entrada(4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Icons.Filled.Notifications, if (alertas.any { it.getString("severidad") == "ALTA" }) Tono.CRITICO else Tono.AVISO)
            Column(Modifier.weight(1f)) {
                Text("Alertas recientes", style = MaterialTheme.typography.titleMedium)
                Text("${alertas.size} abiertas · toca una para atenderla", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        alertas.take(4).forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val sitio = a.getJSONObject("sitio")
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable { nav.ir(Pantalla.Alertas(sitio.getString("id"), sitio.getString("nombre"))) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Punto(if (a.getString("severidad") == "ALTA") Tono.CRITICO else Tono.AVISO)
                Column(Modifier.weight(1f)) {
                    Text(a.getString("mensaje"), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(sitio.getString("nombre"), a.optJSONObject("ubicacion")?.getString("nombre"), fechaCorta(a.texto("creadaEn")))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Un piso de un sitio con lo que le falta. */
private class PisoAtencion(val sitio: JSONObject, val nombre: String, val agotados: List<String>, val alertas: Int)

/** Pisos donde algo se acabó o hay alertas abiertas, los más urgentes primero. */
@Composable
private fun PisosConAtencion(d: DatosPanel, nav: Navegacion) {
    val pisos = remember(d) {
        val agotados = mutableMapOf<String, Pair<JSONObject, MutableList<String>>>()
        val nombres = mutableMapOf<String, String>()
        d.productos.forEach { f ->
            f.getJSONArray("stockPisos").objetos().forEach { s ->
                val u = s.getJSONObject("ubicacion")
                val id = u.getString("id")
                nombres[id] = u.getString("nombre")
                val lista = agotados.getOrPut(id) { f.getJSONObject("sitio") to mutableListOf() }.second
                // Las herramientas (balde, escoba) suelen quedarse en bodega: solo cuentan los consumibles.
                if (s.getDouble("cantidad") <= 0.0 && f.optBoolean("esConsumible", true)) lista += f.getJSONObject("producto").getString("nombre")
            }
        }
        val alertasPorPiso = d.alertas.mapNotNull { a -> a.optJSONObject("ubicacion")?.let { it.getString("id") to a } }
        alertasPorPiso.forEach { (id, a) ->
            nombres.getOrPut(id) { a.getJSONObject("ubicacion").getString("nombre") }
            agotados.getOrPut(id) { a.getJSONObject("sitio") to mutableListOf() }
        }
        val cuenta = alertasPorPiso.groupingBy { it.first }.eachCount()
        agotados.map { (id, par) -> PisoAtencion(par.first, nombres.getValue(id), par.second, cuenta[id] ?: 0) }
            .filter { it.agotados.isNotEmpty() || it.alertas > 0 }
            .sortedByDescending { it.alertas * 2 + it.agotados.size }
    }
    Tarjeta(Modifier.entrada(5)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Piso, if (pisos.isEmpty()) Tono.OK else Tono.AVISO)
            Column(Modifier.weight(1f)) {
                Text("Pisos que necesitan atención", style = MaterialTheme.typography.titleMedium)
                Text(if (pisos.isEmpty()) "Todos los pisos tienen sus productos" else "Productos agotados en el piso o alertas abiertas",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        pisos.take(MAX_FILAS).forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable { nav.ir(Pantalla.Ubicaciones(p.sitio.getString("id"), p.sitio.getString("nombre"))) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Punto(if (p.alertas > 0) Tono.CRITICO else Tono.AVISO)
                Column(Modifier.weight(1f)) {
                    Text("${p.nombre} · ${p.sitio.getString("nombre")}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            p.agotados.takeIf { it.isNotEmpty() }?.let { "Sin: " + it.joinToString(", ") },
                            p.alertas.takeIf { it > 0 }?.let { "$it alerta${if (it == 1) "" else "s"}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Por sitio: cuántos productos están bien y cuántos días alcanza el más justo. */
@Composable
private fun SaludSitios(d: DatosPanel, nav: Navegacion) {
    if (d.sitios.isEmpty()) return
    Tarjeta(Modifier.entrada(6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Inventario, Tono.INFO)
            Column(Modifier.weight(1f)) {
                Text("Abastecimiento por establecimiento", style = MaterialTheme.typography.titleMedium)
                Text("Productos en buen nivel y el que se acaba primero", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        d.sitios.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val id = s.getString("id")
            val items = d.productos.filter { it.getJSONObject("sitio").getString("id") == id }
            val bien = items.count { it.getString("estado") == "OK" }
            val criticos = items.count { it.getString("estado") == "CRITICO" }
            val masJusto = items.filter { !it.isNull("diasCobertura") }.minByOrNull { it.getDouble("diasCobertura") }
            val tono = when {
                items.isEmpty() -> Tono.NEUTRO
                criticos > 0 -> Tono.CRITICO
                items.any { it.getString("estado") == "BAJO" } -> Tono.AVISO
                else -> Tono.OK
            }
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable { nav.ir(Pantalla.Inventario(id, s.getString("nombre"))) }.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.getString("nombre"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$bien de ${items.size} bien", style = MaterialTheme.typography.labelLarge, color = tono.colores().contenido)
                }
                val avance = animadoDesdeCero(if (items.isEmpty()) 0f else bien.toFloat() / items.size)
                LinearProgressIndicator(
                    progress = { avance },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = tono.colores().contenido,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    masJusto?.let { "Se acaba primero: ${it.getJSONObject("producto").getString("nombre")} (${num(it.get("diasCobertura"))} días)" }
                        ?: if (items.isEmpty()) "Sin productos cargados: súbelos desde Bodega" else "Aún sin consumo suficiente para estimar",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Lo que más se gastó en 7 días y si va por encima o por debajo de su ritmo del mes. */
@Composable
private fun MasConsumidos(productos: List<JSONObject>) {
    val top = productos.filter { it.getDouble("consumo7Dias") > 0 }.sortedByDescending { it.getDouble("consumo7Dias") }.take(5)
    if (top.isEmpty()) return
    Tarjeta(Modifier.entrada(7)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Historial, Tono.INFO)
            Column(Modifier.weight(1f)) {
                Text("Lo más consumido esta semana", style = MaterialTheme.typography.titleMedium)
                Text("Comparado con el ritmo de los últimos 30 días", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        top.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val prod = f.getJSONObject("producto")
            val semana = f.getDouble("consumo7Dias")
            val ritmo = f.getDouble("consumo30Dias") / 30 * 7
            val cambio = if (ritmo > 0) ((semana - ritmo) / ritmo * 100).roundToInt() else null
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(prod.getString("nombre"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.getJSONObject("sitio").getString("nombre"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${num(f.get("consumo7Dias"))} ${prod.getString("unidad")}", style = MaterialTheme.typography.titleSmall)
                    cambio?.takeIf { kotlin.math.abs(it) >= 10 }?.let {
                        Etiqueta(if (it > 0) "↑ $it % más" else "↓ ${-it} % menos", if (it > 0) Tono.AVISO else Tono.OK)
                    }
                }
            }
        }
    }
}

/** Punto de color al inicio de una fila. */
@Composable
private fun Punto(tono: Tono) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(tono.colores().contenido))
}

private const val MAX_FILAS = 5

/** Índices de las pestañas del admin en la raíz (Panel · Sedes · Compras · Pedidos · Gestión). */
internal const val PESTANA_SEDES = 1
internal const val PESTANA_COMPRAS = 2
internal const val PESTANA_PEDIDOS = 3
