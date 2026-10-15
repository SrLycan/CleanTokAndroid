package ec.cleantok.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.json.JSONObject

private fun tonoEstado(e: String) = when (e) {
    "CRITICO" -> Tono.CRITICO
    "BAJO" -> Tono.AVISO
    "SIN_DATOS" -> Tono.NEUTRO
    else -> Tono.OK
}

/** Días de cobertura que se consideran "llenos" en la barra. */
private const val COBERTURA_PLENA = 30f

@Composable
fun PantallaInventario(api: Api, nav: Navegacion, p: Pantalla.Inventario) {
    Marco("Inventario", subtitulo = p.nombre) {
        Cargador(p, { api.obj("/establecimientos/${p.estId}/inventario") }) { inv, recargar ->
            val items = inv.getJSONArray("items").objetos()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item {
                    val porEstado = items.groupingBy { it.getString("estado") }.eachCount()
                    val criticos = porEstado["CRITICO"] ?: 0
                    val bajos = porEstado["BAJO"] ?: 0
                    Row(Modifier.entrada(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Resumen("Críticos", criticos, Tono.CRITICO, Modifier.weight(1f))
                        Resumen("Bajos", bajos, Tono.AVISO, Modifier.weight(1f))
                        Resumen("Bien", items.size - criticos - bajos - (porEstado["SIN_DATOS"] ?: 0), Tono.OK, Modifier.weight(1f))
                    }
                }
                item {
                    Seccion("${items.size} productos · toca uno para ver su kardex", Modifier.entrada(1)) {
                        IconButton(onClick = recargar) { Icon(Icons.Filled.Refresh, "Actualizar") }
                    }
                }
                if (items.isEmpty()) item { EstadoVacio(Iconos.Inventario, "Sin productos", tono = Tono.NEUTRO) }
                itemsIndexed(items, key = { _, f -> f.getJSONObject("producto").getString("id") }) { i, f ->
                    ProductoInventario(f, nav, Modifier.animateItem().entrada(2 + i))
                }
            }
        }
    }
}

@Composable
private fun Resumen(rotulo: String, cuenta: Int, tono: Tono, modifier: Modifier) {
    val c = tono.colores()
    Surface(color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Contador animado, como los KPI del panel web.
            Text("${animadoDesdeCero(cuenta.toFloat()).roundToInt()}", style = MaterialTheme.typography.headlineSmall)
            Text(rotulo, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProductoInventario(fila: JSONObject, nav: Navegacion, modifier: Modifier) {
    val prod = fila.getJSONObject("producto")
    val u = prod.getString("unidad")
    val estado = fila.getString("estado")
    val tono = tonoEstado(estado)
    Tarjeta(modifier, alTocar = { nav.ir(Pantalla.Kardex(prod.getString("id"), prod.getString("nombre"))) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(prod.getString("nombre"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Etiqueta(estado.legible(), tono)
        }
        Row {
            Dato("Total", num(fila.get("stockTotal")), u, Modifier.weight(1f))
            Dato("Bodega", num(fila.get("stockBodega")), u, Modifier.weight(1f))
            Dato("Cobertura", if (fila.isNull("diasCobertura")) "—" else num(fila.get("diasCobertura")),
                if (fila.isNull("diasCobertura")) null else "días", Modifier.weight(1f))
        }
        if (!fila.isNull("diasCobertura")) {
            val dias = fila.getDouble("diasCobertura").toFloat()
            val avance = animadoDesdeCero((dias / COBERTURA_PLENA).coerceIn(0f, 1f))
            LinearProgressIndicator(
                progress = { avance },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = tono.colores().contenido,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        val pisos = fila.getJSONArray("stockPisos").objetos()
        if (pisos.isNotEmpty()) {
            Text(
                pisos.joinToString("   ") { s -> "${s.getJSONObject("ubicacion").getString("nombre")}: ${num(s.get("cantidad"))}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Consumo 7 d: ${num(fila.get("consumo7Dias"))} · 30 d: ${num(fila.get("consumo30Dias"))} $u",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (fila.getString("abastece") == "ENTIDAD_CONTRATANTE") Etiqueta("Lo provee la entidad", Tono.INFO)
        }
    }
}

/** Ícono y color de cada tipo de movimiento. */
private fun estiloMovimiento(tipo: String): Pair<ImageVector, Tono> = when (tipo) {
    "INGRESO" -> Icons.Filled.Add to Tono.OK
    "CONSUMO" -> Iconos.Quitar to Tono.CRITICO
    "TRASLADO" -> Icons.AutoMirrored.Filled.ArrowForward to Tono.INFO
    "DEVOLUCION" -> Iconos.Devolver to Tono.AVISO
    else -> Iconos.Historial to Tono.NEUTRO
}

@Composable
fun PantallaKardex(api: Api, p: Pantalla.Kardex) {
    Marco("Kardex", subtitulo = p.nombre) {
        Cargador(p, { api.obj("/productos/${p.productoId}/kardex?size=100") }) { pagina, _ ->
            val filas = pagina.getJSONArray("content").objetos()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = FinDeLista) {
                if (filas.isEmpty()) item { EstadoVacio(Iconos.Historial, "Sin movimientos", "Este producto aún no tiene movimientos.", Tono.NEUTRO) }
                itemsIndexed(filas) { i, fila ->
                    val m = fila.getJSONObject("movimiento")
                    val tipo = m.getString("tipo")
                    val (icono, tono) = estiloMovimiento(tipo)
                    val origen = m.optJSONObject("origen")?.getString("nombre")
                    val destino = m.optJSONObject("destino")?.getString("nombre")
                    Tarjeta(Modifier.entrada(i)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconoRedondo(icono, tono, tam = 36.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tipo.legible(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${num(m.get("cantidad"))} ${m.getString("unidad")}", style = MaterialTheme.typography.titleMedium,
                                        color = tono.colores().contenido)
                                }
                                Text(
                                    when {
                                        origen != null && destino != null -> "$origen → $destino"
                                        origen != null -> "Sale de $origen"
                                        destino != null -> "Entra a $destino"
                                        else -> "—"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${fecha(m.getString("ocurridoEn"))} · ${m.getJSONObject("usuario").getString("nombre")}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val saldos = listOfNotNull(
                                    origen?.let { "Saldo $it: ${num(m.get("saldoResultanteOrigen"))}" },
                                    destino?.let { "Saldo $it: ${num(m.get("saldoResultanteDestino"))}" },
                                    m.texto("motivo")?.let { "Motivo: $it" },
                                )
                                if (saldos.isNotEmpty()) Text(saldos.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaAlertas(api: Api, p: Pantalla.Alertas) {
    val scope = rememberCoroutineScope()
    var atendiendo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Marco("Alertas", subtitulo = p.nombre) {
        Cargador(p, { api.obj("/establecimientos/${p.estId}/alertas?estado=ABIERTA&size=100") }) { pagina, recargar ->
            val lista = pagina.getJSONArray("content").objetos()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                error?.let { item(key = "error") { Box(Modifier.animateItem()) { Aviso(it, Tono.CRITICO, alCerrar = { error = null }) } } }
                if (lista.isEmpty()) item { EstadoVacio(Icons.Filled.CheckCircle, "Todo en orden", "No hay alertas abiertas.") }
                itemsIndexed(lista, key = { _, a -> a.getString("id") }) { i, a ->
                    val id = a.getString("id")
                    val sev = a.getString("severidad")
                    val tono = if (sev == "ALTA") Tono.CRITICO else Tono.AVISO
                    // Al marcarla atendida, la tarjeta sale y las demás suben (animación de layout).
                    Card(
                        Modifier.animateItem().entrada(i).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(Modifier.width(5.dp).fillMaxHeight().background(tono.colores().contenido))
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Etiqueta(sev.legible(), tono)
                                    Text(a.getString("tipo").legible(), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(a.getString("mensaje"), style = MaterialTheme.typography.bodyLarge)
                                a.optJSONObject("ubicacion")?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it.getString("nombre"), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FilledTonalButton(enabled = atendiendo == null, onClick = {
                                    atendiendo = id
                                    scope.launch {
                                        enFondo { api.patch("/alertas/$id/atender", JSONObject().put("nota", "Atendida desde la app")) }
                                            .onFailure { error = mensajeError(it) }
                                        atendiendo = null
                                        recargar()
                                    }
                                }) {
                                    if (atendiendo == id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                                    Text("Marcar atendida", Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
