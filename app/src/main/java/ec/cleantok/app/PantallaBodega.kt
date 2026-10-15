package ec.cleantok.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val CATEGORIAS = listOf(
    "QUIMICO" to "Químico", "PAPEL_Y_HIGIENE" to "Papel e higiene", "BOLSAS" to "Bolsas", "UTENSILIO" to "Utensilio",
    "EPP" to "EPP", "MAQUINARIA" to "Maquinaria", "OTRO" to "Otro",
)
private val UNIDADES = listOf("UNIDAD", "L", "ML", "GALON", "KG", "G", "ROLLO", "PAQUETE", "PAR")

private fun categoriaLegible(c: String) = CATEGORIAS.firstOrNull { it.first == c }?.second ?: c.legible()

private fun estadoOrdenPdfLegible(e: String) = when (e) {
    "PROCESANDO" -> "Leyendo el PDF…"
    "PENDIENTE_REVISION" -> "Por revisar"
    "CONFIRMADA" -> "Confirmada"
    "ERROR" -> "No se pudo leer"
    else -> e.legible()
}

private fun tonoOrdenPdf(e: String) = when (e) {
    "CONFIRMADA" -> Tono.OK
    "PENDIENTE_REVISION" -> Tono.AVISO
    "ERROR" -> Tono.CRITICO
    else -> Tono.INFO
}

/** INGRESO a la bodega. Con el mismo [clave] no se duplica aunque se reintente (idempotencia por clientUuid). */
private fun ingresoBodega(api: Api, clave: String, productoId: String, bodegaId: String, cantidad: BigDecimal, unidad: String, nota: String) =
    api.post(
        "/movimientos",
        JSONObject()
            .put("clientUuid", UUID.nameUUIDFromBytes(clave.toByteArray()).toString())
            .put("tipo", "INGRESO")
            .put("productoId", productoId)
            .put("ubicacionId", bodegaId)
            .put("cantidad", cantidad)
            .put("unidad", unidad)
            .put("ocurridoEn", OffsetDateTime.now().toString())
            .put("nota", nota),
    )

private class DatosBodega(val sitio: JSONObject, val ubicaciones: List<JSONObject>, val inventario: List<JSONObject>, val ordenes: List<JSONObject>)

/**
 * Materiales en la bodega de un establecimiento. El admin carga los materiales desde el PDF de la orden de
 * compra (o a mano) y es el único que cambia el total de la bodega; los pisos se cuentan desde "Pisos"
 * (lo que sube en un piso sale de la bodega).
 */
@Composable
fun PantallaBodega(api: Api, sesion: Sesion, nav: Navegacion, p: Pantalla.Bodega) {
    val esAdmin = sesion.rol == "ADMIN_EMPRESA"
    val contexto = LocalContext.current
    val ej = rememberEjecutor()
    var nuevo by remember { mutableStateOf(false) }

    Marco("Materiales en bodega", subtitulo = p.nombre) {
        Cargador(p, {
            DatosBodega(
                api.obj("/establecimientos/${p.estId}"),
                api.arr("/establecimientos/${p.estId}/ubicaciones").objetos(),
                api.obj("/establecimientos/${p.estId}/inventario").getJSONArray("items").objetos(),
                if (esAdmin) api.obj("/establecimientos/${p.estId}/ordenes?size=20").getJSONArray("content").objetos() else emptyList(),
            )
        }) { d, recargar ->
            val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                ej.correr(null, { subirOrden(contexto, api, p.estId, uri) }) { o -> nav.ir(Pantalla.OrdenPdf(o.getString("id"), p.estId, o.optString("nombreArchivo"))) }
            }

            // Fuera de la lista: un aviso insertado arriba quedaría oculto si la lista está desplazada.
            ej.aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { ej.aviso = null }) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                if (esAdmin) item(key = "cargar") {
                    Tarjeta(Modifier.entrada(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconoRedondo(Iconos.SubirArchivo, Tono.INFO)
                            Column(Modifier.weight(1f)) {
                                Text("Cargar materiales", style = MaterialTheme.typography.titleMedium)
                                Text("Sube el PDF de la orden de compra: CleanTok lee los materiales y tú los revisas. La cantidad es opcional.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = { selector.launch(arrayOf("application/pdf")) }, enabled = !ej.trabajando, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            if (ej.trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else {
                                Icon(Iconos.SubirArchivo, null, Modifier.size(18.dp))
                                Text("Subir orden de compra (PDF)", Modifier.padding(start = 8.dp))
                            }
                        }
                        OutlinedButton(onClick = { nuevo = true }, enabled = !ej.trabajando, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Text("Agregar material", Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (d.ordenes.isNotEmpty()) {
                    item(key = "t-ordenes") { Seccion("Órdenes subidas", Modifier.entrada(1)) }
                    itemsIndexed(d.ordenes, key = { _, o -> "orden-" + o.getString("id") }) { i, o ->
                        val estado = o.getString("estado")
                        val r = o.getJSONObject("resumen")
                        Tarjeta(Modifier.animateItem().entrada(2 + i), alTocar = {
                            nav.ir(Pantalla.OrdenPdf(o.getString("id"), p.estId, o.texto("numeroOrden") ?: o.optString("nombreArchivo")))
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.texto("numeroOrden") ?: o.texto("nombreArchivo") ?: "Orden", style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${r.getInt("total")} materiales · ${fechaCorta(o.texto("creadaEn"))}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Etiqueta(estadoOrdenPdfLegible(estado), tonoOrdenPdf(estado))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Conteo del total en bodega: solo administración. Los pisos se cuentan desde "Pisos".
                val bodega = d.ubicaciones.firstOrNull { it.getString("tipo") == "BODEGA" }
                if (esAdmin && bodega != null) item(key = "contar") {
                    Tarjeta(Modifier.entrada(3)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconoRedondo(Iconos.Conteo, Tono.OK)
                            Column(Modifier.weight(1f)) {
                                Text("Contar existencias", style = MaterialTheme.typography.titleMedium)
                                Text("Escribe el total de cada producto que hay en la bodega. El sistema ajusta la diferencia.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedButton(
                            onClick = { nav.ir(Pantalla.Conteo(p.estId, bodega.getString("id"), bodega.getString("nombre"), esBodega = true)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Iconos.Bodega, null, Modifier.size(18.dp))
                            Text("Contar stock total en bodega", Modifier.weight(1f).padding(start = 8.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                        }
                    }
                }
                item(key = "t-stock") { Seccion("En bodega · ${d.inventario.size} productos", Modifier.entrada(4)) }
                if (d.inventario.isEmpty()) item(key = "vacio") {
                    EstadoVacio(Iconos.Bodega, "Bodega vacía",
                        if (esAdmin) "Sube la orden de compra o agrega los materiales a mano." else "Administración aún no carga los materiales.", Tono.NEUTRO)
                }
                itemsIndexed(d.inventario, key = { _, f -> f.getJSONObject("producto").getString("id") }) { i, f ->
                    val prod = f.getJSONObject("producto")
                    val u = prod.getString("unidad")
                    val pisos = f.getJSONArray("stockPisos").objetos()
                    Tarjeta(Modifier.animateItem().entrada(5 + i), alTocar = { nav.ir(Pantalla.Kardex(prod.getString("id"), prod.getString("nombre"))) }) {
                        Text(prod.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                        Row {
                            Dato("En bodega", num(f.get("stockBodega")), u, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            Dato("En pisos", num(pisos.sumOf { BigDecimal(it.get("cantidad").toString()) }), u, Modifier.weight(1f))
                            Dato("Total", num(f.get("stockTotal")), u, Modifier.weight(1f))
                        }
                        if (pisos.isNotEmpty()) Text(
                            pisos.joinToString("   ") { s -> "${s.getJSONObject("ubicacion").getString("nombre")}: ${num(s.get("cantidad"))}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (nuevo) DialogoMaterial("Nuevo material", null, conCantidad = true, alCerrar = { nuevo = false }) { cuerpo, cantidad ->
                nuevo = false
                ej.correr("Material agregado a la bodega", {
                    val prod = api.post("/establecimientos/${p.estId}/productos", cuerpo)
                    if (cantidad != null) {
                        ingresoBodega(api, "ingreso-manual:${prod.getString("id")}", prod.getString("id"), d.sitio.getString("bodegaId"),
                            cantidad, prod.getString("unidad"), "Carga inicial")
                    }
                }) { recargar() }
            }
        }
    }
}

/** Lee el PDF elegido y lo sube; el servidor responde enseguida y extrae los materiales en segundo plano. */
private fun subirOrden(contexto: Context, api: Api, estId: String, uri: Uri): JSONObject {
    val cr = contexto.contentResolver
    val nombre = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "orden.pdf"
    val datos = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
    return try {
        api.subirArchivo("/establecimientos/$estId/ordenes", datos, nombre, "application/pdf")
    } catch (e: ApiError) {
        // Ese PDF ya se había subido: el servidor responde 409 con el id de esa orden y se abre esa.
        val existente = e.cuerpo?.texto("ordenId")
        if (e.codigo == "PDF_DUPLICADO" && existente != null) api.obj("/ordenes/$existente") else throw e
    }
}

/**
 * Revisión de una orden de compra leída del PDF: elegir qué materiales entran, corregir nombre, categoría
 * o unidad, agregar los que falten y, opcionalmente, la cantidad que llegó a la bodega.
 */
@Composable
fun PantallaOrdenPdf(api: Api, nav: Navegacion, p: Pantalla.OrdenPdf) {
    val ej = rememberEjecutor()
    val cantidades = remember { mutableStateMapOf<String, String>() }
    var editar by remember { mutableStateOf<JSONObject?>(null) }
    var agregar by remember { mutableStateOf(false) }

    Marco("Orden de compra", subtitulo = p.titulo.ifBlank { null }) {
        Cargador(p, { api.obj("/ordenes/${p.id}") to api.arr("/ordenes/${p.id}/materiales").objetos() }) { (orden, grupos), recargar ->
            val estado = orden.getString("estado")
            val editable = estado == "PENDIENTE_REVISION" || estado == "ERROR"
            // Mientras el servidor lee el PDF, se consulta cada segundo y medio.
            LaunchedEffect(orden.toString()) {
                if (estado == "PROCESANDO") { delay(1500); recargar() }
            }
            val materiales = grupos.flatMap { g -> g.getJSONArray("items").objetos() }
            val seleccionados = materiales.filter { it.getBoolean("seleccionado") }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ej.aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { ej.aviso = null }) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                        item(key = "cabecera") { CabeceraOrden(orden, materiales.size, seleccionados.size) }
                    if (estado == "PROCESANDO") item(key = "leyendo") {
                        Tarjeta(Modifier.entrada(1)) {
                            Text("Leyendo los materiales del PDF…", style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    if (estado == "CONFIRMADA") item(key = "listo") {
                        Aviso("Los materiales ya están en la bodega. Ahora registra cuánto hay en cada piso desde Pisos → Contar.",
                            Tono.OK, titulo = "Orden confirmada")
                    }
                    if (estado != "PROCESANDO" && materiales.isEmpty()) item(key = "sin") {
                        EstadoVacio(Iconos.Inventario, "Sin materiales leídos", "Agrégalos a mano con el botón de abajo.", Tono.NEUTRO)
                    }
                    grupos.forEach { g ->
                        val items = g.getJSONArray("items").objetos()
                        if (items.isEmpty()) return@forEach
                        item(key = "s-" + g.getString("seccion")) { Seccion(g.getString("seccion").legible() + " (${items.size})") }
                        itemsIndexed(items, key = { _, m -> m.getString("id") }) { i, m ->
                            val id = m.getString("id")
                            TarjetaMaterial(
                                m, editable, cantidades[id] ?: "", Modifier.animateItem().entrada(2 + i),
                                alElegir = { si ->
                                    ej.correr(null, { api.patch("/materiales/$id", JSONObject().put("estado", if (si) "ACEPTADO" else "DESCARTADO")) }) { recargar() }
                                },
                                alEditar = { editar = m },
                                alCantidad = { cantidades[id] = it },
                            )
                        }
                    }
                    if (editable) item(key = "agregar") {
                        OutlinedButton(onClick = { agregar = true }, enabled = !ej.trabajando, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Text("Agregar material", Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (estado == "PENDIENTE_REVISION") {
                    Button(
                        onClick = {
                            val malas = seleccionados.filter { m -> cantidades[m.getString("id")]?.let { it.isNotBlank() && (it.toBigDecimalOrNull()?.signum() ?: -1) < 0 } == true }
                            if (malas.isNotEmpty()) { ej.aviso = "Revisa la cantidad de ${malas.first().getString("nombre")}" to Tono.CRITICO; return@Button }
                            ej.correr(null, { confirmarOrden(api, p, orden, seleccionados, cantidades) }) { texto ->
                                ej.aviso = texto
                                recargar()
                            }
                        },
                        enabled = seleccionados.isNotEmpty() && !ej.trabajando,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                    ) {
                        if (ej.trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Pasar ${seleccionados.size} material${if (seleccionados.size == 1) "" else "es"} a la bodega")
                    }
                } else if (estado == "CONFIRMADA") {
                    OutlinedButton(onClick = { nav.atras() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) { Text("Volver a la bodega") }
                }
            }

            editar?.let { m ->
                DialogoMaterial("Corregir material", m, conCantidad = false, alCerrar = { editar = null }) { cuerpo, _ ->
                    editar = null
                    ej.correr("Material corregido", { api.patch("/materiales/${m.getString("id")}", cuerpo) }) { recargar() }
                }
            }
            if (agregar) DialogoMaterial("Agregar material", null, conCantidad = true, alCerrar = { agregar = false }) { cuerpo, cantidad ->
                agregar = false
                ej.correr("Material agregado", { api.post("/ordenes/${p.id}/materiales", cuerpo) }) { m ->
                    cantidad?.let { cantidades[m.getString("id")] = it.toPlainString() }
                    recargar()
                }
            }
        }
    }
}

@Composable
private fun CabeceraOrden(orden: JSONObject, total: Int, elegidos: Int) {
    val estado = orden.getString("estado")
    Tarjeta(Modifier.entrada(0)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(orden.texto("numeroOrden") ?: orden.texto("nombreArchivo") ?: "Orden de compra", style = MaterialTheme.typography.titleMedium)
                listOfNotNull(orden.texto("entidadContratante"), orden.texto("fechaEmision")).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Etiqueta(estadoOrdenPdfLegible(estado), tonoOrdenPdf(estado))
        }
        if (estado != "PROCESANDO") {
            Text("$total materiales leídos · $elegidos marcados para la bodega", style = MaterialTheme.typography.bodyMedium)
        }
        if (estado == "PENDIENTE_REVISION") {
            Text("Desmarca lo que no aplica y corrige con ✎ lo que se haya leído mal. La cantidad en bodega es opcional: también se puede registrar después con un conteo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        orden.optJSONArray("avisos")?.let { a -> (0 until a.length()).forEach { Aviso(a.getString(it), Tono.INFO) } }
        orden.texto("errorDetalle")?.let { Aviso(it, Tono.CRITICO, titulo = "No se pudieron leer los materiales") }
    }
}

@Composable
private fun TarjetaMaterial(
    m: JSONObject,
    editable: Boolean,
    cantidad: String,
    modifier: Modifier,
    alElegir: (Boolean) -> Unit,
    alEditar: () -> Unit,
    alCantidad: (String) -> Unit,
) {
    val elegido = m.getBoolean("seleccionado")
    val unidad = m.getString("unidad")
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = elegido, onCheckedChange = alElegir, enabled = editable)
            Column(Modifier.weight(1f)) {
                Text(m.getString("nombre"), style = MaterialTheme.typography.titleSmall,
                    color = if (elegido) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOfNotNull(categoriaLegible(m.getString("categoria")), unidad.legible(), m.texto("presentacion")).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editable) IconButton(onClick = alEditar) { Icon(Icons.Filled.Edit, "Corregir", tint = MaterialTheme.colorScheme.primary) }
        }
        m.texto("textoOriginal")?.takeIf { !it.equals(m.getString("nombre"), ignoreCase = true) }?.let { t ->
            Text("En el PDF: “$t”" + (m.texto("pagina")?.let { " · pág. $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val confianza = m.getString("confianza")
        val notas = listOfNotNull(
            m.texto("motivoRevision"),
            "Posible duplicado de otro material".takeIf { !m.isNull("fusionarConId") },
            "Lo provee la entidad contratante".takeIf { m.getString("abastece") == "ENTIDAD_CONTRATANTE" },
        )
        if (m.getString("origen") != "MANUAL" && (confianza != "ALTA" || notas.isNotEmpty())) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (confianza != "ALTA") Etiqueta("Lectura ${confianza.lowercase()}", if (confianza == "BAJA") Tono.CRITICO else Tono.AVISO)
                notas.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tono.AVISO.colores().contenido, maxLines = 2) }
            }
        }
        if (editable && elegido) {
            OutlinedTextField(
                cantidad, { alCantidad(it.replace(',', '.')) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Cantidad en bodega (opcional)") }, suffix = { Text(unidad) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        } else if (!m.isNull("productoId")) {
            Etiqueta("En la bodega", Tono.OK)
        }
    }
}

/** Confirma la orden (crea los productos con stock 0) y registra el INGRESO de las cantidades que se escribieron. */
private fun confirmarOrden(
    api: Api,
    p: Pantalla.OrdenPdf,
    orden: JSONObject,
    seleccionados: List<JSONObject>,
    cantidades: Map<String, String>,
): Pair<String, Tono> {
    val r = api.post("/ordenes/${p.id}/confirmar")
    val productos = r.getJSONArray("productos").objetos()
    val bodegaId = api.obj("/establecimientos/${p.estId}").getString("bodegaId")
    val nota = "Orden de compra " + (orden.texto("numeroOrden") ?: orden.optString("nombreArchivo"))
    var ingresos = 0
    val errores = mutableListOf<String>()
    seleccionados.forEach { m ->
        val cantidad = cantidades[m.getString("id")]?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return@forEach
        val producto = productos.firstOrNull { it.optString("materialExtraidoId") == m.getString("id") }
            ?: productos.firstOrNull { it.getString("nombre").equals(m.getString("nombre"), ignoreCase = true) }
            ?: return@forEach
        runCatching {
            ingresoBodega(api, "ingreso-orden:${m.getString("id")}", producto.getString("id"), bodegaId, cantidad, m.getString("unidad"), nota)
        }.onSuccess { ingresos++ }.onFailure { errores += "${m.getString("nombre")}: ${mensajeError(it)}" }
    }
    val texto = buildString {
        append("${r.getInt("creados")} productos nuevos")
        if (r.getInt("vinculados") > 0) append(", ${r.getInt("vinculados")} ya existían")
        append(". ")
        append(if (ingresos > 0) "$ingresos con cantidad en bodega." else "Sin cantidades: regístralas con un conteo cuando las tengas.")
        errores.forEach { append("\n• $it") }
    }
    return texto to if (errores.isEmpty()) Tono.OK else Tono.AVISO
}

/**
 * Datos de un material o producto: nombre, categoría, unidad y presentación. [actual] trae los valores al corregir.
 * Con [conCantidad], también la cantidad que hay en bodega (opcional). [alGuardar] recibe el cuerpo para la API.
 */
@Composable
internal fun DialogoMaterial(
    titulo: String,
    actual: JSONObject?,
    conCantidad: Boolean,
    alCerrar: () -> Unit,
    alGuardar: (JSONObject, BigDecimal?) -> Unit,
) {
    var nombre by remember { mutableStateOf(actual?.optString("nombre") ?: "") }
    var categoria by remember { mutableStateOf(actual?.optString("categoria") ?: "QUIMICO") }
    var unidad by remember { mutableStateOf(actual?.optString("unidad") ?: "UNIDAD") }
    var presentacion by remember { mutableStateOf(actual?.texto("presentacion") ?: "") }
    var cantidad by remember { mutableStateOf("") }
    val cantidadValida = cantidad.isBlank() || (cantidad.toBigDecimalOrNull()?.signum() ?: -1) >= 0

    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(if (actual == null) Icons.Filled.Add else Icons.Filled.Edit, null) },
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Categoría", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIAS.forEach { (k, t) -> FilterChip(selected = categoria == k, onClick = { categoria = k }, label = { Text(t) }) }
                }
                Text("Unidad", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UNIDADES.forEach { u -> FilterChip(selected = unidad == u, onClick = { unidad = u }, label = { Text(u.legible()) }) }
                }
                OutlinedTextField(presentacion, { presentacion = it }, label = { Text("Presentación (opcional)") }, placeholder = { Text("Ej.: Galón 3.785 L") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (conCantidad) {
                    OutlinedTextField(
                        cantidad, { cantidad = it.replace(',', '.') }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cantidad en bodega (opcional)") }, suffix = { Text(unidad.legible()) }, isError = !cantidadValida,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cuerpo = JSONObject().put("nombre", nombre.trim()).put("categoria", categoria).put("unidad", unidad)
                        .put("presentacion", presentacion.trim().ifBlank { JSONObject.NULL })
                    alGuardar(cuerpo, cantidad.toBigDecimalOrNull()?.takeIf { it.signum() > 0 })
                },
                enabled = nombre.isNotBlank() && cantidadValida,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/**
 * Conteo físico de un piso o de la bodega: se escribe lo que hay y el servidor registra la diferencia.
 * En un piso, lo que sube sale de la bodega (no puede superar lo que hay) y lo que baja es consumo; la
 * bodega solo la cuenta el admin. Lo que se deja vacío no cambia.
 */
@Composable
fun PantallaConteo(api: Api, p: Pantalla.Conteo) {
    val ej = rememberEjecutor()
    Marco(if (p.esBodega) "Conteo de bodega" else "Conteo · ${p.nombre}", subtitulo = "Escribe cuánto hay ahora") {
        Cargador(p, { api.obj("/establecimientos/${p.estId}/inventario").getJSONArray("items").objetos() }) { items, recargar ->
            val valores = remember(items) { mutableStateMapOf<String, String>() }
            fun actual(f: JSONObject): BigDecimal = BigDecimal(
                if (p.esBodega) f.get("stockBodega").toString()
                else f.getJSONArray("stockPisos").objetos().firstOrNull { it.getJSONObject("ubicacion").getString("id") == p.ubicacionId }
                    ?.get("cantidad")?.toString() ?: "0",
            )
            val cambios = items.mapNotNull { f ->
                val id = f.getJSONObject("producto").getString("id")
                valores[id]?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it.compareTo(actual(f)) != 0 }?.let { id to it }
            }
            fun enBodega(f: JSONObject) = BigDecimal(f.get("stockBodega").toString())
            /** En un piso: subir más de lo que hay en la bodega. El servidor también lo rechaza. */
            fun excede(f: JSONObject): Boolean {
                if (p.esBodega) return false
                val ahora = valores[f.getJSONObject("producto").getString("id")]?.toBigDecimalOrNull() ?: return false
                return ahora.subtract(actual(f)) > enBodega(f)
            }
            val invalidos = valores.values.any { it.isNotBlank() && (it.toBigDecimalOrNull()?.signum() ?: -1) < 0 } || items.any(::excede)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ej.aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { ej.aviso = null }) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                        item(key = "ayuda") {
                        Aviso(
                            if (p.esBodega) "Escribe el total de cada producto que hay en la bodega. Lo que dejes vacío no cambia."
                            else "Escribe la cantidad exacta de cada producto que hay en ${p.nombre}. Si es más de lo registrado, la diferencia " +
                                "se descuenta de la bodega (no puede superar lo que hay allí); si es menos, se registra como consumo. Lo que dejes vacío no cambia.",
                            Tono.INFO,
                        )
                    }
                    if (items.isEmpty()) item(key = "vacio") {
                        EstadoVacio(Iconos.Inventario, "Sin productos", "Primero se cargan los materiales en la bodega.", Tono.NEUTRO)
                    }
                    itemsIndexed(items, key = { _, f -> f.getJSONObject("producto").getString("id") }) { i, f ->
                        val prod = f.getJSONObject("producto")
                        val id = prod.getString("id")
                        val u = prod.getString("unidad")
                        val antes = actual(f)
                        val ahora = valores[id]?.toBigDecimalOrNull()
                        Tarjeta(Modifier.animateItem().entrada(1 + i)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(prod.getString("nombre"), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Registrado: ${num(antes)} $u" + if (p.esBodega) "" else " · En bodega: ${num(enBodega(f))} $u",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (excede(f)) Text("No alcanza lo que hay en bodega", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                    if (ahora != null && ahora.signum() >= 0 && ahora.compareTo(antes) != 0) {
                                        val dif = ahora.subtract(antes)
                                        Etiqueta((if (dif.signum() > 0) "+" else "−") + "${num(dif.abs())} $u", if (dif.signum() > 0) Tono.OK else Tono.AVISO)
                                    }
                                }
                                OutlinedTextField(
                                    valores[id] ?: "", { valores[id] = it.replace(',', '.') }, singleLine = true,
                                    modifier = Modifier.width(130.dp), placeholder = { Text(num(antes)) }, suffix = { Text(u) },
                                    isError = valores[id]?.let { it.isNotBlank() && (it.toBigDecimalOrNull()?.signum() ?: -1) < 0 } == true || excede(f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val cuerpo = JSONObject().put("items", JSONArray(cambios.map { (id, c) -> JSONObject().put("productoId", id).put("cantidad", c) }))
                        ej.correr(null, { api.post("/ubicaciones/${p.ubicacionId}/conteo", cuerpo) }) { r ->
                            val alertas = r.getJSONArray("alertas").objetos().joinToString("") { "\n⚠ ${it.getString("mensaje")}" }
                            ej.aviso = "Conteo guardado: ${r.getInt("ajustados")} producto${if (r.getInt("ajustados") == 1) "" else "s"} actualizado${if (r.getInt("ajustados") == 1) "" else "s"}$alertas" to Tono.OK
                            recargar()
                        }
                    },
                    enabled = cambios.isNotEmpty() && !invalidos && !ej.trabajando,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                ) {
                    if (ej.trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(if (cambios.isEmpty()) "Escribe al menos una cantidad" else "Guardar conteo · ${cambios.size} cambio${if (cambios.size == 1) "" else "s"}")
                }
            }
        }
    }
}
