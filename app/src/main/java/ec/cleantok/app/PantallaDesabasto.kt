package ec.cleantok.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Un establecimiento con sus pisos, su inventario y sus alertas abiertas. */
private class SitioDesabasto(val sitio: JSONObject, val pisos: List<JSONObject>, val inventario: List<JSONObject>, val alertas: List<JSONObject>) {
    val id: String = sitio.getString("id")
    val nombre: String = sitio.getString("nombre")
}

private fun cargarDesabasto(api: Api): List<SitioDesabasto> =
    api.arr("/establecimientos/mios").objetos().map { s ->
        val id = s.getString("id")
        SitioDesabasto(
            s,
            api.arr("/establecimientos/$id/ubicaciones").objetos().filter { it.getString("tipo") != "BODEGA" && it.optBoolean("activo", true) },
            // Solo consumibles: las herramientas no se agotan y no generan alerta de nivel.
            api.obj("/establecimientos/$id/inventario").getJSONArray("items").objetos().filter { it.optBoolean("esConsumible", true) },
            api.obj("/establecimientos/$id/alertas?estado=ABIERTA&size=100").getJSONArray("content").objetos(),
        )
    }

/** Niveles que se pueden reportar como desabasto, con su nombre para el encargado. */
private val NIVELES = listOf(0 to "Se acabó", 25 to "Casi vacío", 50 to "A la mitad")

/**
 * Pestaña del encargado para avisar que algo falta en un piso: elige dónde, qué y cuánto queda (con foto
 * opcional). Administración recibe la alerta para reponerlo. Si hay en bodega, se le recuerda traerlo.
 */
@Composable
fun PantallaDesabasto(api: Api, sesion: Sesion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Reportar desabasto", subtitulo = "Avisa a administración lo que falta", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("desabasto", { cargarDesabasto(api) }) { sitios, recargar ->
            // Al enviar se reinicia el formulario (cambia la clave) y se recargan los datos.
            var envios by remember { mutableIntStateOf(0) }
            var resultado by remember { mutableStateOf<Pair<String, Tono>?>(null) }
            var prellenado by remember { mutableStateOf<Pair<String, String>?>(null) }
            val desplazamiento = rememberScrollState()
            val scope = rememberCoroutineScope()

            Column(Modifier.verticalScroll(desplazamiento).padding(FinDeLista), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(resultado != null, enter = fadeIn(resorte()) + expandVertically(resorte(IntSize.VisibilityThreshold)),
                    exit = fadeOut(resorte()) + shrinkVertically(resorte(IntSize.VisibilityThreshold))) {
                    resultado?.let { (t, tono) -> Aviso(t, tono, alCerrar = { resultado = null }) }
                }
                if (sitios.isEmpty()) {
                    EstadoVacio(Iconos.Desabasto, "Sin establecimientos", "Aún no tienes establecimientos asignados.", Tono.NEUTRO)
                } else {
                    key(envios, prellenado) {
                        FormularioDesabasto(api, sesion, sitios, prellenado, Modifier.entrada(0)) { texto, tono ->
                            resultado = texto to tono
                            prellenado = null
                            envios++
                            recargar()
                            scope.launch { desplazamiento.animateScrollTo(0) }
                        }
                    }
                    EnRiesgo(sitios, Modifier.entrada(1)) { sitioId, productoId ->
                        prellenado = sitioId to productoId
                        resultado = null
                        scope.launch { desplazamiento.animateScrollTo(0) }
                    }
                    ReportesAbiertos(sitios, Modifier.entrada(2), recargar)
                }
            }
        }
    }
}

@Composable
private fun FormularioDesabasto(
    api: Api,
    sesion: Sesion,
    sitios: List<SitioDesabasto>,
    prellenado: Pair<String, String>?,
    modifier: Modifier,
    alEnviar: (String, Tono) -> Unit,
) {
    var sitioId by remember { mutableStateOf(prellenado?.first ?: sitios.first().id) }
    val sitio = sitios.first { it.id == sitioId }
    var pisoId by remember(sitioId) { mutableStateOf(sitio.pisos.singleOrNull()?.getString("id")) }
    var productoId by remember(sitioId) { mutableStateOf(prellenado?.second?.takeIf { prellenado.first == sitioId }) }
    var nivel by remember { mutableStateOf<Int?>(null) }
    var fotoId by remember { mutableStateOf<String?>(null) }
    var nota by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun cantidadEn(f: JSONObject, ubicacionId: String?): BigDecimal =
        f.getJSONArray("stockPisos").objetos().firstOrNull { it.getJSONObject("ubicacion").getString("id") == ubicacionId }
            ?.let { BigDecimal(it.get("cantidad").toString()) } ?: BigDecimal.ZERO

    fun enviar() {
        val piso = sitio.pisos.first { it.getString("id") == pisoId }
        val fila = sitio.inventario.first { it.getJSONObject("producto").getString("id") == productoId }
        val prod = fila.getJSONObject("producto")
        val cuerpo = JSONObject()
            .put("clientUuid", UUID.randomUUID().toString())
            .put("productoId", productoId)
            .put("ubicacionId", pisoId)
            .put("nivelPct", nivel)
            .put("fotoId", fotoId ?: JSONObject.NULL)
            .put("ocurridoEn", OffsetDateTime.now().toString())
            .put("nota", nota.trim().ifBlank { JSONObject.NULL })
        enviando = true
        error = null
        scope.launch {
            enFondo { api.post("/reportes-nivel", cuerpo) }
                .onSuccess { r ->
                    val alertas = r.getJSONArray("alertas").objetos()
                    val bodega = BigDecimal(fila.get("stockBodega").toString())
                    val texto = buildString {
                        append("${prod.getString("nombre")} en ${piso.getString("nombre")}: ")
                        append(if (alertas.isEmpty()) "reporte registrado." else "se avisó a administración.")
                        alertas.forEach { append("\n⚠ ${it.getString("mensaje")}") }
                        if (bodega.signum() > 0) {
                            append("\nHay ${num(bodega)} ${prod.getString("unidad")} en bodega: escanea el QR del piso y usa \"Agregar\" para traerlo.")
                        }
                    }
                    alEnviar(texto, if (alertas.isEmpty()) Tono.OK else Tono.AVISO)
                }
                .onFailure { e ->
                    if (Api.sinConexion(e)) {
                        // Sin red: se guarda y sale con "Sincronizar" del inicio (mismo clientUuid = sin duplicados).
                        sesion.encolar(JSONObject().put("tipo", "REPORTE_NIVEL").put("reporteNivel", cuerpo))
                        alEnviar("Sin conexión: el reporte quedó guardado en el teléfono. Envíalo con \"Sincronizar\" en el inicio.", Tono.AVISO)
                    } else {
                        enviando = false
                        error = mensajeError(e)
                    }
                }
        }
    }

    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Desabasto, Tono.CRITICO)
            Column(Modifier.weight(1f)) {
                Text("Nuevo reporte", style = MaterialTheme.typography.titleMedium)
                Text("Administración recibe la alerta para reponerlo.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (sitios.size > 1) {
            Paso("¿Dónde?")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sitios.forEach { s -> FilterChip(selected = s.id == sitioId, onClick = { sitioId = s.id }, label = { Text(s.nombre) }) }
            }
        }
        Paso("¿En qué piso?")
        if (sitio.pisos.isEmpty()) Text("Este establecimiento no tiene pisos.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sitio.pisos.forEach { u -> FilterChip(selected = u.getString("id") == pisoId, onClick = { pisoId = u.getString("id") }, label = { Text(u.getString("nombre")) }) }
        }
        Paso("¿Qué falta?")
        if (sitio.inventario.isEmpty()) Text("Aún no hay productos cargados en este establecimiento.", style = MaterialTheme.typography.bodySmall)
        Column {
            // Primero lo que está más bajo en ese piso.
            sitio.inventario.sortedBy { cantidadEn(it, pisoId) }.forEach { f ->
                val prod = f.getJSONObject("producto")
                val id = prod.getString("id")
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { productoId = id }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = id == productoId, onClick = { productoId = id })
                    Column(Modifier.weight(1f)) {
                        Text(prod.getString("nombre"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (pisoId != null) "Aquí: ${num(cantidadEn(f, pisoId))} · " else "") + "Bodega: ${num(f.get("stockBodega"))} ${prod.getString("unidad")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (f.getString("estado") in setOf("CRITICO", "BAJO")) Etiqueta(f.getString("estado").legible(), if (f.getString("estado") == "CRITICO") Tono.CRITICO else Tono.AVISO)
                }
            }
        }
        Paso("¿Cuánto queda?")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NIVELES.forEach { (n, texto) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Envase(n, enviando = false, enabled = !enviando, modifier = Modifier.fillMaxWidth()) { nivel = n }
                    Text(texto, style = MaterialTheme.typography.labelMedium,
                        color = if (nivel == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (nivel == n) Icon(Icons.Filled.CheckCircle, "Elegido", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        BotonFoto(api, "Foto del dispensador o envase (opcional)") { fotoId = it }
        OutlinedTextField(nota, { nota = it }, label = { Text("Comentario (opcional)") }, placeholder = { Text("Ej.: se acabó en el baño de hombres") },
            modifier = Modifier.fillMaxWidth())
        error?.let { Aviso(it, Tono.CRITICO, alCerrar = { error = null }) }
        Button(
            onClick = ::enviar,
            enabled = !enviando && pisoId != null && productoId != null && nivel != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(
                when {
                    pisoId == null -> "Elige el piso"
                    productoId == null -> "Elige el producto"
                    nivel == null -> "Elige cuánto queda"
                    else -> "Enviar reporte"
                },
            )
        }
    }
}

@Composable
private fun Paso(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

/** Productos críticos o bajos de mis sitios: un toque los pone en el formulario. */
@Composable
private fun EnRiesgo(sitios: List<SitioDesabasto>, modifier: Modifier, alElegir: (String, String) -> Unit) {
    val riesgo = sitios.flatMap { s -> s.inventario.filter { it.getString("estado") in setOf("CRITICO", "BAJO") }.map { s to it } }
        .sortedBy { (_, f) -> if (f.getString("estado") == "CRITICO") 0 else 1 }
    if (riesgo.isEmpty()) return
    Tarjeta(modifier) {
        Text("Se están acabando", style = MaterialTheme.typography.titleMedium)
        Text("Según el consumo registrado. Toca uno para reportarlo.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        riesgo.take(6).forEachIndexed { i, (s, f) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val prod = f.getJSONObject("producto")
            val critico = f.getString("estado") == "CRITICO"
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { alElegir(s.id, prod.getString("id")) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(prod.getString("nombre"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            s.nombre.takeIf { sitios.size > 1 },
                            f.texto("diasCobertura")?.let { "alcanza ${num(it)} días" },
                            "bodega ${num(f.get("stockBodega"))} ${prod.getString("unidad")}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Etiqueta(if (critico) "Crítico" else "Bajo", if (critico) Tono.CRITICO else Tono.AVISO)
            }
        }
    }
}

/** Alertas abiertas de mis sitios (incluye mis reportes): se cierran solas cuando llega el producto. */
@Composable
private fun ReportesAbiertos(sitios: List<SitioDesabasto>, modifier: Modifier, recargar: () -> Unit) {
    val abiertas = sitios.flatMap { s -> s.alertas.map { s to it } }.sortedByDescending { it.second.getString("creadaEn") }
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Esperando reposición", style = MaterialTheme.typography.titleMedium)
                Text(if (abiertas.isEmpty()) "No hay faltantes pendientes." else "Se cierran solas cuando llega el producto al piso.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = recargar) { Icon(Icons.Filled.Refresh, "Actualizar") }
        }
        abiertas.take(8).forEachIndexed { i, (s, a) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val alta = a.getString("severidad") == "ALTA"
            Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etiqueta(if (alta) "Urgente" else "Pendiente", if (alta) Tono.CRITICO else Tono.AVISO)
                    Text(fechaCorta(a.texto("creadaEn")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(a.getString("mensaje"), style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(s.nombre, a.optJSONObject("ubicacion")?.getString("nombre")).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
