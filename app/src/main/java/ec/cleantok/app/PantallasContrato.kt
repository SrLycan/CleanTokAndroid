package ec.cleantok.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate

// Administrador de contrato: monitorea el consumo por piso de sus sedes (solo lectura).

/** Análisis de consumo por piso de cada sede asignada (GET /establecimientos/{id}/consumo-pisos). */
private fun cargarConsumo(api: Api): List<JSONObject> =
    api.arr("/establecimientos/mios").objetos().map { api.obj("/establecimientos/${it.getString("id")}/consumo-pisos?dias=30") }

/** Una fila producto–piso con su sede y piso, para listas y para abrir el detalle. */
private class FilaConsumo(val sede: String, val piso: String, val p: JSONObject)

private fun filas(sedes: List<JSONObject>): List<FilaConsumo> = sedes.flatMap { s ->
    s.getJSONArray("pisos").objetos().flatMap { piso ->
        piso.getJSONArray("productos").objetos().map { FilaConsumo(s.nombreDe("establecimiento"), piso.nombreDe("ubicacion"), it) }
    }
}

fun suficienciaLegible(s: String): String = when (s) {
    "SUFICIENTE" -> "Suficiente"
    "BAJO" -> "Insuficiente"
    "AGOTADO" -> "Agotado en el piso"
    "SIN_CONSUMO" -> "Sin consumo"
    else -> s.legible()
}

fun tonoSuficiencia(s: String): Tono = when (s) {
    "SUFICIENTE" -> Tono.OK
    "BAJO" -> Tono.AVISO
    "AGOTADO" -> Tono.CRITICO
    else -> Tono.NEUTRO
}

/** Filtro por sede (si tiene más de una) y el contenido con las sedes elegidas. */
@Composable
private fun ConSedes(sedes: List<JSONObject>, contenido: @Composable (List<JSONObject>) -> Unit) {
    var elegida by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sedes.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = elegida == null, onClick = { elegida = null }, label = { Text("Todas") })
            sedes.forEach { s ->
                val id = s.getJSONObject("establecimiento").getString("id")
                FilterChip(selected = elegida == id, onClick = { elegida = id }, label = { Text(s.nombreDe("establecimiento")) })
            }
        }
        contenido(if (elegida == null) sedes else sedes.filter { it.getJSONObject("establecimiento").getString("id") == elegida })
    }
}

/** Pestaña "Consumo atípico": pisos donde se consume fuera de lo normal, con sus estadísticas. */
@Composable
fun PantallaConsumoAtipico(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Consumo atípico", subtitulo = "Administrador de contrato · últimos 30 días", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("consumo-atipico", { cargarConsumo(api) }) { sedes, _ ->
            ConSedes(sedes) { elegidas ->
                val todas = filas(elegidas)
                val anomalas = todas.filter { it.p.getBoolean("anomalo") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                    item(key = "resumen") {
                        Row(Modifier.entrada(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Cifra("Pisos", "${elegidas.sumOf { it.getJSONObject("resumen").getInt("pisos") }}", Tono.INFO, Modifier.weight(1f))
                            Cifra("Atípicos", "${anomalas.size}", if (anomalas.isEmpty()) Tono.OK else Tono.CRITICO, Modifier.weight(1f))
                            Cifra("Pisos afectados", "${anomalas.map { it.sede to it.piso }.toSet().size}", Tono.AVISO, Modifier.weight(1f))
                        }
                    }
                    item(key = "explica") {
                        Text("Atípico: un día con consumo mayor que el promedio + 2 desviaciones (y al menos 1,5 veces el promedio), " +
                            "o una semana muy por encima o por debajo de lo habitual.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (anomalas.isEmpty()) item(key = "vacio") {
                        EstadoVacio(Iconos.Piso, "Sin consumos atípicos", "Todos los pisos consumen dentro de lo normal.")
                    }
                    itemsIndexed(anomalas, key = { _, f -> f.sede + f.piso + f.p.getJSONObject("producto").getString("id") }) { i, f ->
                        TarjetaConsumo(f, Modifier.animateItem().entrada(1 + i)) { nav.ir(Pantalla.ConsumoProducto(f.sede, f.piso, f.p.toString())) }
                    }
                }
            }
        }
    }
}

/** Pestaña "Insumos por piso": que cada piso tenga lo suficiente al ritmo de consumo actual. */
@Composable
fun PantallaInsumosPisos(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Insumos por piso", subtitulo = "¿Alcanza lo que hay en cada piso?", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("insumos-pisos", { cargarConsumo(api) }) { sedes, _ ->
            ConSedes(sedes) { elegidas ->
                val pisos = elegidas.flatMap { s -> s.getJSONArray("pisos").objetos().map { s to it } }
                    .sortedByDescending { (_, piso) -> piso.getInt("insuficientes") }
                val minimo = elegidas.firstOrNull()?.optInt("coberturaMinimaDias", 2) ?: 2
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                    item(key = "resumen") {
                        val faltan = pisos.count { it.second.getInt("insuficientes") > 0 }
                        Aviso(
                            "Cada piso debe tener insumos para al menos $minimo días al ritmo de su consumo.",
                            if (faltan == 0) Tono.OK else Tono.AVISO,
                            titulo = if (faltan == 0) "Todos los pisos están abastecidos" else "$faltan piso${if (faltan == 1) "" else "s"} con insumos insuficientes",
                        )
                    }
                    if (pisos.isEmpty()) item(key = "vacio") { EstadoVacio(Iconos.Piso, "Sin pisos", "Tus sedes aún no tienen pisos con consumo.", Tono.NEUTRO) }
                    itemsIndexed(pisos, key = { _, (s, piso) -> s.nombreDe("establecimiento") + piso.nombreDe("ubicacion") }) { i, (s, piso) ->
                        val sede = s.nombreDe("establecimiento")
                        val nombrePiso = piso.nombreDe("ubicacion")
                        val faltan = piso.getInt("insuficientes")
                        Tarjeta(Modifier.animateItem().entrada(1 + i)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Iconos.Piso, if (faltan > 0) Tono.AVISO else Tono.OK)
                                Column(Modifier.weight(1f)) {
                                    Text(nombrePiso, style = MaterialTheme.typography.titleMedium)
                                    Text(sede, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Etiqueta(if (faltan > 0) "$faltan por reponer" else "Abastecido", if (faltan > 0) Tono.AVISO else Tono.OK)
                            }
                            val productos = piso.getJSONArray("productos").objetos()
                            if (productos.isEmpty()) Text("Sin insumos ni consumos registrados.", style = MaterialTheme.typography.bodySmall)
                            productos.forEachIndexed { j, p ->
                                if (j > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val unidad = p.getJSONObject("producto").getString("unidad")
                                Row(
                                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                        .clickable { nav.ir(Pantalla.ConsumoProducto(sede, nombrePiso, p.toString())) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.nombreDe("producto"), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Hay ${num(p.get("stockPiso"))} $unidad · usa ${num(p.get("promedioDiario"))}/día" +
                                                (p.texto("coberturaDias")?.let { " · alcanza ${num(it)} días" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Etiqueta(suficienciaLegible(p.getString("suficiencia")), tonoSuficiencia(p.getString("suficiencia")))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cifra(rotulo: String, valor: String, tono: Tono, modifier: Modifier) {
    val c = tono.colores()
    androidx.compose.material3.Surface(color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(valor, style = MaterialTheme.typography.headlineSmall)
            Text(rotulo, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TarjetaConsumo(f: FilaConsumo, modifier: Modifier, alTocar: () -> Unit) {
    val p = f.p
    Tarjeta(modifier, alTocar = alTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Piso, Tono.CRITICO)
            Column(Modifier.weight(1f)) {
                Text(p.nombreDe("producto"), style = MaterialTheme.typography.titleMedium)
                Text("${f.piso} · ${f.sede}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            p.texto("variacionPct")?.toIntOrNull()?.let { v -> Etiqueta((if (v > 0) "+" else "") + "$v %", if (v > 0) Tono.CRITICO else Tono.AVISO) }
        }
        p.texto("motivo")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        BarrasConsumo(p, Modifier.fillMaxWidth().height(56.dp))
    }
}

/** Barras del consumo diario: en rojo los días atípicos; la línea punteada es el promedio. */
@Composable
fun BarrasConsumo(p: JSONObject, modifier: Modifier) {
    val serie = (0 until p.getJSONArray("serie").length()).map { p.getJSONArray("serie").getDouble(it) }
    val atipicas = p.getJSONArray("diasAtipicos").objetos().map { it.getString("fecha") }.toSet()
    val hasta = LocalDate.now()
    val promedio = p.getDouble("promedioDiario")
    val normal = MaterialTheme.colorScheme.primary
    val alerta = Tono.CRITICO.colores().contenido
    val linea = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val maximo = (serie.maxOrNull() ?: 0.0).coerceAtLeast(promedio).coerceAtLeast(0.001)
        val ancho = size.width / serie.size.coerceAtLeast(1)
        serie.forEachIndexed { i, v ->
            val alto = (v / maximo * size.height).toFloat()
            val fecha = hasta.minusDays((serie.size - 1 - i).toLong()).toString()
            drawRoundRect(
                color = if (fecha in atipicas) alerta else normal.copy(alpha = if (v > 0) 0.75f else 0.15f),
                topLeft = Offset(i * ancho + ancho * 0.15f, size.height - alto.coerceAtLeast(2f)),
                size = Size(ancho * 0.7f, alto.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
        val y = (size.height - promedio / maximo * size.height).toFloat()
        drawLine(linea, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    }
}

/** Detalle de un insumo en un piso: gráfico diario, estadísticas, días atípicos y si alcanza. */
@Composable
fun PantallaConsumoProducto(p: Pantalla.ConsumoProducto) {
    val d = remember(p.datos) { JSONObject(p.datos) }
    val unidad = d.getJSONObject("producto").getString("unidad")
    Marco(d.nombreDe("producto"), subtitulo = "${p.piso} · ${p.sede}") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
            item(key = "estado") {
                val suf = d.getString("suficiencia")
                Aviso(
                    d.texto("motivo") ?: "Consumo dentro de lo normal en los últimos 30 días.",
                    if (d.getBoolean("anomalo")) Tono.CRITICO else Tono.OK,
                    titulo = if (d.getBoolean("anomalo")) "Consumo atípico" else "Consumo normal",
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Aviso(
                    "Hay ${num(d.get("stockPiso"))} $unidad en el piso" + (d.texto("coberturaDias")?.let { ": alcanza ${num(it)} días." } ?: "."),
                    tonoSuficiencia(suf), titulo = suficienciaLegible(suf),
                )
            }
            item(key = "grafico") {
                Tarjeta(Modifier.entrada(1)) {
                    Text("Consumo diario · últimos 30 días", style = MaterialTheme.typography.titleSmall)
                    BarrasConsumo(d, Modifier.fillMaxWidth().height(140.dp))
                    Text("Rojo: días atípicos · línea: promedio diario", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "estadisticas") {
                Tarjeta(Modifier.entrada(2)) {
                    Text("Estadísticas", style = MaterialTheme.typography.titleSmall)
                    Row {
                        Dato("Promedio diario", num(d.get("promedioDiario")), unidad, Modifier.weight(1f))
                        Dato("Desviación", num(d.get("desviacion")), unidad, Modifier.weight(1f))
                    }
                    Row {
                        Dato("Máximo en un día", num(d.get("maximoDiario")), unidad, Modifier.weight(1f))
                        Dato("Total 30 días", num(d.get("totalPeriodo")), unidad, Modifier.weight(1f))
                    }
                    Row {
                        Dato("Últimos 7 días", num(d.get("ultimos7Dias")), unidad, Modifier.weight(1f))
                        Dato("Esperado 7 días", num(d.get("esperado7Dias")), unidad, Modifier.weight(1f))
                    }
                    d.texto("variacionPct")?.toIntOrNull()?.let { v ->
                        Text("Variación semanal: ${if (v > 0) "+" else ""}$v % frente a lo habitual", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val dias = d.getJSONArray("diasAtipicos").objetos()
            item(key = "dias-titulo") { Seccion("Días atípicos (${dias.size})") }
            if (dias.isEmpty()) item(key = "sin-dias") {
                Text("Ningún día fuera de lo normal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(dias.reversed(), key = { _, x -> x.getString("fecha") }) { i, x ->
                Tarjeta(Modifier.entrada(3 + i)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(diaIso(x.getString("fecha")), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${num(x.get("cantidad"))} $unidad", style = MaterialTheme.typography.titleSmall)
                        Etiqueta("${num(x.get("vecesPromedio"))} × promedio", Tono.CRITICO)
                    }
                }
            }
        }
    }
}
