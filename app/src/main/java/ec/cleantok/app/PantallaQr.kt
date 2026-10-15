package ec.cleantok.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.UUID

/** Acción elegida sobre un producto en la pantalla del QR. */
private data class Accion(val tipo: String, val etiqueta: String, val icono: ImageVector, val producto: JSONObject)

/**
 * Lo que ve el encargado al escanear un QR: la ubicación, sus alertas y los productos con stock aquí y en bodega.
 * En la bodega: Ingreso (solo el admin). En un piso: Agregar (traslado), Retirar (consumo), Devolver y Nivel.
 */
@Composable
fun PantallaQr(api: Api, sesion: Sesion, nav: Navegacion, token: String) {
    var accion by remember { mutableStateOf<Accion?>(null) }
    var nivelDe by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var resultado by remember { mutableStateOf<Pair<String, Tono>?>(null) }

    Marco("Registrar movimiento") {
        Cargador(token, { api.obj("/qr/$token") }) { qr, recargar ->
            val ubic = qr.getJSONObject("ubicacion")
            val esBodega = ubic.getString("tipo") == "BODEGA"
            val ubicId = ubic.getString("id")
            val productos = qr.getJSONArray("productos").objetos()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item {
                    Card(
                        Modifier.fillMaxWidth().entrada(0), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconoRedondo(if (esBodega) Iconos.Bodega else Iconos.Piso, tam = 52.dp,
                                fondo = MaterialTheme.colorScheme.primary, tinte = MaterialTheme.colorScheme.onPrimary)
                            Column(Modifier.weight(1f)) {
                                Text(ubic.getString("nombre"), style = MaterialTheme.typography.headlineSmall)
                                Text(qr.getJSONObject("establecimiento").getString("nombre"), style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = recargar) { Icon(Icons.Filled.Refresh, "Actualizar") }
                        }
                    }
                }
                resultado?.let { (texto, tono) ->
                    item(key = "resultado") { Box(Modifier.animateItem().entrada()) { Aviso(texto, tono, alCerrar = { resultado = null }) } }
                }
                itemsIndexed(qr.getJSONArray("alertas").objetos(), key = { i, a -> "alerta-" + a.optString("id", "$i") }) { _, a ->
                    val sev = a.getString("severidad")
                    Box(Modifier.animateItem().entrada(1)) { Aviso(a.getString("mensaje"), if (sev == "ALTA") Tono.CRITICO else Tono.AVISO, titulo = "Alerta ${sev.legible().lowercase()}") }
                }
                item(key = "titulo") { Seccion("Productos (${productos.size})", Modifier.animateItem().entrada(2)) }
                if (productos.isEmpty()) item { EstadoVacio(Iconos.Inventario, "Sin productos", "No hay productos registrados para esta ubicación.", Tono.NEUTRO) }
                itemsIndexed(productos, key = { _, p -> p.getString("id") }) { i, p ->
                    TarjetaProducto(p, esBodega, sesion.rol == "ADMIN_EMPRESA", Modifier.animateItem().entrada(3 + i),
                        alAccion = { accion = it },
                        alNivel = { nivelDe = p to ubicId },
                        alKardex = { nav.ir(Pantalla.Kardex(p.getString("id"), p.getString("nombre"))) },
                    )
                }
            }

            accion?.let { a ->
                DialogoMovimiento(api, sesion, a, ubicId, alCerrar = { accion = null }) { texto, tono, sugerirNivel ->
                    accion = null
                    resultado = texto to tono
                    if (sugerirNivel) nivelDe = a.producto to ubicId
                    recargar()
                }
            }
            nivelDe?.let { (p, u) ->
                DialogoNivel(api, p, u, alCerrar = { nivelDe = null }) { texto, tono ->
                    nivelDe = null
                    resultado = texto to tono
                    recargar()
                }
            }
        }
    }
}

@Composable
private fun TarjetaProducto(p: JSONObject, esBodega: Boolean, esAdmin: Boolean, modifier: Modifier, alAccion: (Accion) -> Unit, alNivel: () -> Unit, alKardex: () -> Unit) {
    val u = p.getString("unidad")
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.getString("nombre"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (p.getString("abastece") == "ENTIDAD_CONTRATANTE") Etiqueta("Lo provee la entidad", Tono.INFO)
        }
        Row {
            if (!esBodega) Dato("Aquí", num(p.get("stockUbicacion")), u, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
            Dato("En bodega", num(p.get("stockBodega")), u, Modifier.weight(1f))
        }
        if (esBodega) {
            // El stock de la bodega solo lo cambia administración; el encargado lo lleva a los pisos con "Agregar".
            if (esAdmin) Button(onClick = { alAccion(Accion("INGRESO", "Ingreso a bodega", Icons.Filled.Add, p)) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("Ingreso", Modifier.padding(start = 6.dp))
            } else Text("Para llevarlo a un piso, escanea el QR del piso y usa Agregar.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { alAccion(Accion("TRASLADO", "Agregar desde bodega", Icons.Filled.Add, p)) }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("Agregar", Modifier.padding(start = 6.dp))
                }
                Button(onClick = { alAccion(Accion("CONSUMO", "Retirar (consumo)", Iconos.Quitar, p)) }, Modifier.weight(1f)) {
                    Icon(Iconos.Quitar, null, Modifier.size(18.dp))
                    Text("Retirar", Modifier.padding(start = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AccionChica(Iconos.Devolver, "Devolver") { alAccion(Accion("DEVOLUCION", "Devolver a bodega", Iconos.Devolver, p)) }
                AccionChica(Iconos.Nivel, "Nivel", alNivel)
                AccionChica(Iconos.Historial, "Kardex", alKardex)
            }
        }
    }
}

@Composable
private fun AccionChica(icono: ImageVector, texto: String, alTocar: () -> Unit) {
    TextButton(onClick = alTocar, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Icon(icono, null, Modifier.size(18.dp))
        Text(texto, Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DialogoMovimiento(
    api: Api,
    sesion: Sesion,
    a: Accion,
    ubicacionId: String,
    alCerrar: () -> Unit,
    alTerminar: (texto: String, tono: Tono, sugerirNivel: Boolean) -> Unit,
) {
    val unidades = a.producto.getJSONArray("unidadesCompatibles").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
    var cantidad by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf(a.producto.getString("unidad")) }
    var nota by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun registrar() {
        val valor = cantidad.toBigDecimalOrNull()
        if (valor == null || valor.signum() <= 0) { error = "Ingresa una cantidad mayor que 0"; return }
        enviando = true
        error = null
        val cuerpo = JSONObject()
            .put("clientUuid", UUID.randomUUID().toString())
            .put("tipo", a.tipo)
            .put("productoId", a.producto.getString("id"))
            .put("ubicacionId", ubicacionId)
            .put("cantidad", valor)
            .put("unidad", unidad)
            .put("ocurridoEn", OffsetDateTime.now().toString())
            .put("nota", nota.ifBlank { JSONObject.NULL })
        scope.launch {
            enFondo { api.post("/movimientos", cuerpo) }
                .onSuccess { r ->
                    val saldos = r.getJSONArray("saldos").objetos().joinToString(" · ") {
                        "${it.getJSONObject("ubicacion").getString("nombre")}: ${num(it.get("cantidad"))}"
                    }
                    val alertas = r.getJSONArray("alertas").objetos().joinToString("") { "\n⚠ ${it.getString("mensaje")}" }
                    val dup = if (r.optBoolean("duplicado")) " (ya estaba registrado)" else ""
                    alTerminar("${a.etiqueta}$dup. Saldos → $saldos$alertas", Tono.OK, r.optBoolean("sugerirReporteNivel"))
                }
                .onFailure { e ->
                    if (Api.sinConexion(e)) {
                        // Sin red: queda en la cola y se envía con "Sincronizar" (mismo clientUuid = sin duplicados).
                        sesion.encolar(JSONObject().put("tipo", "MOVIMIENTO").put("movimiento", cuerpo))
                        alTerminar("Sin conexión: guardado en el teléfono. Usa \"Sincronizar\" en el inicio.", Tono.AVISO, false)
                    } else {
                        enviando = false
                        error = mensajeError(e)
                    }
                }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!enviando) alCerrar() },
        icon = { Icon(a.icono, null) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(a.etiqueta)
                Text(a.producto.getString("nombre"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    cantidad, { cantidad = it.replace(',', '.'); error = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cantidad") }, placeholder = { Text("Ej.: 1, 0.5, 3.5") },
                    suffix = { Text(unidad) }, isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
                if (unidades.size > 1) {
                    Text("Unidad", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        unidades.forEach { u ->
                            FilterChip(
                                selected = u == unidad, onClick = { unidad = u }, label = { Text(u) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
                OutlinedTextField(nota, { nota = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Aviso(it, Tono.CRITICO) }
            }
        },
        confirmButton = {
            Button(onClick = ::registrar, enabled = !enviando) {
                if (enviando) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Registrar")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar, enabled = !enviando) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoNivel(api: Api, p: JSONObject, ubicacionId: String, alCerrar: () -> Unit, alTerminar: (String, Tono) -> Unit) {
    val scope = rememberCoroutineScope()
    var enviando by remember { mutableStateOf<Int?>(null) }
    var fotoId by remember { mutableStateOf<String?>(null) }

    fun reportar(nivel: Int) {
        enviando = nivel
        val cuerpo = JSONObject()
            .put("clientUuid", UUID.randomUUID().toString())
            .put("productoId", p.getString("id"))
            .put("ubicacionId", ubicacionId)
            .put("nivelPct", nivel)
            .put("ocurridoEn", OffsetDateTime.now().toString())
            .put("fotoId", fotoId ?: JSONObject.NULL)
        scope.launch {
            enFondo { api.post("/reportes-nivel", cuerpo) }
                .onSuccess { r ->
                    val alertas = r.getJSONArray("alertas").objetos().joinToString("") { "\n⚠ ${it.getString("mensaje")}" }
                    alTerminar("Nivel $nivel % reportado${if (fotoId != null) " con foto" else ""}$alertas", Tono.OK)
                }
                .onFailure { alTerminar(mensajeError(it), Tono.CRITICO) }
        }
    }

    AlertDialog(
        onDismissRequest = { if (enviando == null) alCerrar() },
        icon = { Icon(Iconos.Nivel, null) },
        title = { Text("¿Cuánto queda?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Toma una foto del envase o dispensador de ${p.getString("nombre")} (opcional) y toca el nivel aproximado.")
                BotonFoto(api, "Tomar foto (evidencia)") { fotoId = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 25, 50, 75, 100).forEach { nivel ->
                        Envase(nivel, enviando == nivel, enabled = enviando == null, modifier = Modifier.weight(1f)) { reportar(nivel) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar, enabled = enviando == null) { Text("Cancelar") } },
    )
}

/** Envase dibujado con el nivel de llenado; se toca para reportar ese nivel. */
@Composable
fun Envase(nivel: Int, enviando: Boolean, enabled: Boolean, modifier: Modifier, alTocar: () -> Unit) {
    val tono = when {
        nivel <= 25 -> Tono.CRITICO
        nivel <= 50 -> Tono.AVISO
        else -> Tono.OK
    }
    val c = tono.colores()
    val fuente = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = alTocar, enabled = enabled, shape = MaterialTheme.shapes.small, interactionSource = fuente,
            border = BorderStroke(2.dp, c.contenido.copy(alpha = 0.6f)),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().height(72.dp).encogerAlTocar(fuente, 0.92f),
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                // El líquido "sube" con resorte al abrir el diálogo.
                val relleno = animadoDesdeCero(nivel / 100f)
                if (nivel > 0) Surface(color = c.contenido.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth().fillMaxHeight(relleno)) {}
                if (enviando) Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        Text("$nivel %", style = MaterialTheme.typography.labelMedium)
    }
}
