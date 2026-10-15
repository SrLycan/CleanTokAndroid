package ec.cleantok.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Colores de la comparación (los mismos de las diapositivas): base naranja, optimizada verde. */
private val NaranjaBase = Color(0xFFF28C38)
private val VerdeOptima = Color(0xFF28A66A)

/** Pestaña "Mi ruta" del repartidor: todas sus órdenes en camino, en una sola ruta optimizada. */
@Composable
fun PantallaMiRuta(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Mi ruta", subtitulo = "Reparto de hoy", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("mi-ruta", { api.obj("/rutas/mia") }) { ruta, recargar ->
            if (ruta.getJSONArray("paradas").length() == 0) {
                EstadoVacio(Iconos.Ruta, "Sin ruta por ahora", "Cuando el proveedor te asigne pedidos empacados, aquí verás el orden de entrega.", Tono.NEUTRO)
            } else {
                VistaRuta(ruta, nav, alActualizar = recargar)
            }
        }
    }
}

/** Ruta de una orden de envío directo (empresa, encargado, proveedor o repartidor). */
@Composable
fun PantallaRutaOrden(api: Api, p: Pantalla.RutaOrden) {
    val nav = LocalNav.current!!
    Marco("Ruta ${p.codigo}", subtitulo = "Base vs. optimizada") {
        Cargador(p, { api.obj("/ordenes-compra/${p.id}/ruta") }) { ruta, recargar -> VistaRuta(ruta, nav, alActualizar = recargar) }
    }
}

@Composable
private fun VistaRuta(ruta: JSONObject, nav: Navegacion, alActualizar: () -> Unit) {
    val contexto = LocalContext.current
    val paradas = ruta.getJSONArray("paradas").objetos()
    val base = ruta.getJSONObject("base")
    val opt = ruta.getJSONObject("optimizada")
    val trazados = rememberTrazados(ruta)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
        item(key = "mapa") {
            Tarjeta(Modifier.entrada(0)) {
                MapaOsm(ruta, trazados, Modifier.fillMaxWidth().height(360.dp).clip(MaterialTheme.shapes.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Leyenda(NaranjaBase, "Ruta base", punteada = true)
                    Leyenda(VerdeOptima, "Optimizada")
                }
                Text(
                    buildString {
                        append("Mapa y calles © colaboradores de OpenStreetMap · rutas con OSRM")
                        if (ruta.optString("fuente") == "ESTIMADA") append(".\nSin enrutador: km estimados en línea recta.")
                        if (trazados != null && trazados.optimizada == null) append(".\nSin conexión con el enrutador: se unen las paradas en línea recta.")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "comparacion") {
            Row(Modifier.entrada(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResumenRuta("Ruta base", base, NaranjaBase, Modifier.weight(1f))
                ResumenRuta("Optimizada", opt, VerdeOptima, Modifier.weight(1f))
            }
        }
        item(key = "ahorro") {
            Aviso(
                "−${ruta.getInt("ahorroPct")} % de distancia (${num(ruta.get("ahorroKm"))} km) · −${num(ruta.get("ahorroCo2Kg"))} kg CO₂e\n${ruta.getString("metodo")}",
                Tono.OK, titulo = "Ahorro de la ruta optimizada",
            )
        }
        item(key = "navegar") {
            Button(onClick = {
                val pendientes = paradas.filter { it.getString("estado") == "PENDIENTE" }.ifEmpty { paradas }
                contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlMaps(ruta.getJSONObject("origen"), pendientes))))
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Iconos.Ruta, null, Modifier.size(18.dp))
                Text("Navegar en Google Maps", Modifier.padding(start = 6.dp))
            }
        }
        item(key = "paradas-titulo") {
            Seccion("Paradas en orden (${paradas.size})") { androidx.compose.material3.TextButton(onClick = alActualizar) { Text("Actualizar") } }
        }
        item(key = "origen") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoRedondo(Iconos.Bodega, Tono.NEUTRO, tam = 36.dp)
                Column {
                    Text("Salida: ${ruta.getJSONObject("origen").getString("nombre")}", style = MaterialTheme.typography.titleSmall)
                    ruta.getJSONObject("origen").texto("direccion")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        itemsIndexed(paradas, key = { _, p -> p.getJSONObject("establecimiento").getString("id") }) { i, p ->
            val estado = p.getString("estado")
            Tarjeta(Modifier.animateItem().entrada(2 + i)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = VerdeOptima, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${p.getInt("orden")}", style = MaterialTheme.typography.titleSmall) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.nombreDe("establecimiento"), style = MaterialTheme.typography.titleMedium)
                        Text("${num(p.get("kmTramo"))} km desde la parada anterior", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Etiqueta(estadoEntregaLegible(estado), tonoEntrega(estado))
                }
                p.texto("direccion")?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.getJSONArray("ordenes").objetos().forEach { o ->
                        AssistChip(
                            onClick = { nav.ir(Pantalla.Orden(o.getString("id"), o.getString("nombre"))) },
                            label = { Text("Pedido ${o.getString("nombre")}") },
                            leadingIcon = { Icon(Iconos.Camion, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Leyenda(color: Color, texto: String, punteada: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = 24.dp, height = 4.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                pathEffect = if (punteada) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null, cap = StrokeCap.Round,
            )
        }
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ResumenRuta(titulo: String, r: JSONObject, color: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.12f), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = color)
            Text("${num(r.get("km"))} km", style = MaterialTheme.typography.headlineSmall)
            Text("${r.getInt("minutos")} min · ${num(r.get("co2Kg"))} kg CO₂e", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Google Maps con la salida, las paradas intermedias y el último destino. */
private fun urlMaps(origen: JSONObject, paradas: List<JSONObject>): String {
    fun coord(o: JSONObject) = "${o.getDouble("lat")},${o.getDouble("lng")}"
    val destino = paradas.last()
    val intermedias = paradas.dropLast(1).joinToString("|") { coord(it) }
    return "https://www.google.com/maps/dir/?api=1&origin=${coord(origen)}&destination=${coord(destino)}" +
        (if (intermedias.isNotEmpty()) "&waypoints=" + Uri.encode(intermedias) else "") + "&travelmode=driving"
}
