package ec.cleantok.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val EN_CURSO = setOf("APROBADA", "ACEPTADA", "EN_PREPARACION", "EMPACADA", "LISTA_PARA_RETIRO", "RETIRADA", "DESPACHADA", "EN_TRANSITO")

/** Órdenes de compra del usuario: la empresa ve las suyas, el encargado las de sus sitios, el proveedor y el repartidor las propias. */
@Composable
fun PantallaPedidos(api: Api, nav: Navegacion, rol: String, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    var filtro by remember { mutableStateOf("curso") }
    val subtitulo = when (rol) {
        "PROVEEDOR" -> "Órdenes de tus clientes"
        "REPARTIDOR" -> "Pedidos asignados a ti"
        "ENCARGADO" -> "Lo que llega a tus establecimientos"
        else -> "Órdenes de compra y entregas"
    }
    Marco("Pedidos", subtitulo = subtitulo, acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("ordenes-$rol", { api.arr("/ordenes-compra").objetos() }) { lista, recargar ->
            val visibles = lista.filter {
                when (filtro) {
                    "curso" -> it.getString("estado") in EN_CURSO
                    "cerrados" -> it.getString("estado") !in EN_CURSO
                    else -> true
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item(key = "filtros") {
                    Row(Modifier.entrada(0), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("curso" to "En curso", "cerrados" to "Cerrados", "todos" to "Todos").forEach { (k, t) ->
                            FilterChip(selected = filtro == k, onClick = { filtro = k }, label = { Text(t) })
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = recargar) { Icon(Icons.Filled.Refresh, "Actualizar") }
                    }
                }
                if (visibles.isEmpty()) item(key = "vacio") {
                    EstadoVacio(Iconos.Camion, if (filtro == "curso") "Nada en curso" else "Sin pedidos", "Aquí verás cada pedido y su rastreo.", Tono.NEUTRO)
                }
                itemsIndexed(visibles, key = { _, o -> o.getString("id") }) { i, o ->
                    TarjetaPedido(o, rol, Modifier.animateItem().entrada(1 + i)) { nav.ir(Pantalla.Orden(o.getString("id"), o.getString("codigo"))) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPedido(o: JSONObject, rol: String, modifier: Modifier, alTocar: () -> Unit) {
    val estado = o.getString("estado")
    val destinos = o.getInt("destinos")
    val recibidas = o.getInt("recibidas")
    Tarjeta(modifier, alTocar = alTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(if (o.getString("modalidad") == "ENVIO_DIRECTO") Iconos.Camion else Iconos.Tienda, tonoOrden(estado))
            Column(Modifier.weight(1f)) {
                Text(o.getString("codigo"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (rol == "PROVEEDOR" || rol == "REPARTIDOR") o.nombreDe("empresa") else o.nombreDe("proveedor"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Etiqueta(estadoOrdenLegible(estado), tonoOrden(estado))
        }
        val avance = animadoDesdeCero(if (destinos == 0) 0f else recibidas.toFloat() / destinos)
        LinearProgressIndicator(
            progress = { avance }, modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Tono.OK.colores().contenido, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
        Row {
            Text(
                "$recibidas de $destinos destino${if (destinos == 1) "" else "s"} recibido${if (recibidas == 1) "" else "s"} · ${modalidadLegible(o.getString("modalidad"))}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            if (rol != "REPARTIDOR") Text(dinero(o.get("total")), style = MaterialTheme.typography.titleSmall)
        }
        o.optJSONObject("ultimoEvento")?.let { ev ->
            Text(
                "${ev.getString("titulo")} · ${fechaCorta(ev.getString("ocurridoEn"))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Detalle de la orden: rastreo, destinos, historial y las acciones que le tocan al usuario. */
@Composable
fun PantallaOrden(api: Api, nav: Navegacion, p: Pantalla.Orden) {
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var recibir by remember { mutableStateOf<JSONObject?>(null) }
    var elegirRepartidor by remember { mutableStateOf(false) }
    var rechazar by remember { mutableStateOf(false) }

    Marco("Pedido ${p.codigo}", subtitulo = "Rastreo y entregas") {
        Cargador(p, { api.obj("/ordenes-compra/${p.id}") }) { o, recargar ->
            val acciones = o.getJSONObject("acciones")
            fun ejecutar(exito: String, llamada: () -> Unit) {
                trabajando = true
                scope.launch {
                    enFondo(llamada)
                        .onSuccess { aviso = exito to Tono.OK }
                        .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                    trabajando = false
                    recargar()
                }
            }
            val ocultarPrecios = o.getDouble("total") == 0.0

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item(key = "cabecera") { CabeceraOrden(o, ocultarPrecios, Modifier.entrada(0)) { nav.ir(Pantalla.RutaOrden(p.id, p.codigo)) } }
                aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }

                val siguientes = acciones.getJSONArray("siguientes").objetos()
                if (siguientes.isNotEmpty() || acciones.getBoolean("asignarRepartidor")) item(key = "acciones") {
                    Tarjeta(Modifier.animateItem().entrada(1)) {
                        Text("Siguiente paso", style = MaterialTheme.typography.titleSmall)
                        siguientes.forEach { s ->
                            val estado = s.getString("estado")
                            if (estado == "RECHAZADA") {
                                OutlinedButton(
                                    onClick = { rechazar = true }, enabled = !trabajando, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) { Text("Rechazar pedido") }
                            } else {
                                Button(
                                    onClick = {
                                        ejecutar("Estado actualizado: ${estadoOrdenLegible(estado).lowercase()}") {
                                            api.post("/ordenes-compra/${p.id}/estado", JSONObject().put("estado", estado))
                                        }
                                    },
                                    enabled = !trabajando, modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) { Text(textoAccion(estado)) }
                            }
                        }
                        if (acciones.getBoolean("asignarRepartidor")) {
                            FilledTonalButton(onClick = { elegirRepartidor = true }, enabled = !trabajando, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Person, null, Modifier.size(18.dp))
                                Text(if (o.isNull("repartidor")) "Asignar repartidor" else "Cambiar repartidor", Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }

                item(key = "seguimiento") {
                    Tarjeta(Modifier.animateItem().entrada(2)) {
                        Text("Seguimiento", style = MaterialTheme.typography.titleSmall)
                        LineaDeTiempo(o.getJSONArray("pasos").objetos())
                    }
                }

                item(key = "destinos-titulo") { Seccion("Destinos (${o.getJSONArray("entregas").length()})", Modifier.animateItem()) }
                val marcables = acciones.getJSONArray("marcarEntregada").cadenas()
                val recibibles = acciones.getJSONArray("recibir").cadenas()
                itemsIndexed(o.getJSONArray("entregas").objetos(), key = { _, e -> "entrega-" + e.getString("id") }) { i, e ->
                    val id = e.getString("id")
                    TarjetaEntrega(
                        e, ocultarPrecios, Modifier.animateItem().entrada(3 + i),
                        puedeMarcar = id in marcables && !trabajando,
                        puedeRecibir = id in recibibles && !trabajando,
                        alMarcar = {
                            ejecutar("Entrega registrada en ${e.nombreDe("establecimiento")}") {
                                api.post("/ordenes-compra/${p.id}/entregas/$id/entregar", JSONObject())
                            }
                        },
                        alRecibir = { recibir = e },
                    )
                }

                item(key = "historial-titulo") { Seccion("Historial", Modifier.animateItem()) }
                item(key = "historial") { Historial(o.getJSONArray("eventos").objetos(), Modifier.animateItem()) }
            }

            recibir?.let { e ->
                DialogoRecepcion(api, e, alCerrar = { recibir = null }) { cuerpo ->
                    recibir = null
                    ejecutar("Recepción confirmada: los productos ingresaron a la bodega de ${e.nombreDe("establecimiento")}") {
                        api.post("/ordenes-compra/${p.id}/entregas/${e.getString("id")}/recibir", cuerpo)
                    }
                }
            }
            if (elegirRepartidor) {
                DialogoRepartidor(api, alCerrar = { elegirRepartidor = false }) { r ->
                    elegirRepartidor = false
                    ejecutar("Repartidor asignado: ${r.getString("nombre")}") {
                        api.post("/ordenes-compra/${p.id}/repartidor", JSONObject().put("repartidorId", r.getString("id")))
                    }
                }
            }
            if (rechazar) {
                DialogoRechazo(alCerrar = { rechazar = false }) { motivo ->
                    rechazar = false
                    ejecutar("Pedido rechazado") {
                        api.post("/ordenes-compra/${p.id}/estado", JSONObject().put("estado", "RECHAZADA").put("nota", motivo))
                    }
                }
            }
        }
    }
}

private fun textoAccion(estado: String) = when (estado) {
    "ACEPTADA" -> "Aceptar pedido"
    "EN_PREPARACION" -> "Empezar a preparar"
    "EMPACADA" -> "Marcar como empacado"
    "DESPACHADA" -> "Salió de bodega"
    "EN_TRANSITO" -> "Iniciar ruta (en tránsito)"
    "LISTA_PARA_RETIRO" -> "Listo para retiro"
    "RETIRADA" -> "Marcar como retirado"
    else -> estadoOrdenLegible(estado)
}

private fun JSONArray.cadenas(): List<String> = (0 until length()).map { getString(it) }

@Composable
private fun CabeceraOrden(o: JSONObject, ocultarPrecios: Boolean, modifier: Modifier, alVerRuta: () -> Unit) {
    val estado = o.getString("estado")
    val c = tonoOrden(estado).colores()
    Surface(color = c.fondo, contentColor = c.contenido, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconoRedondo(
                    if (o.getString("modalidad") == "ENVIO_DIRECTO") Iconos.Camion else Iconos.Tienda, tam = 52.dp,
                    fondo = MaterialTheme.colorScheme.primary, tinte = MaterialTheme.colorScheme.onPrimary,
                )
                Column(Modifier.weight(1f)) {
                    Text(estadoOrdenLegible(estado), style = MaterialTheme.typography.headlineSmall)
                    Text(modalidadLegible(o.getString("modalidad")) + " · solicitud " + o.nombreDe("solicitud"),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Proveedor", style = MaterialTheme.typography.labelMedium)
                    Text(o.nombreDe("proveedor"), style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Empresa", style = MaterialTheme.typography.labelMedium)
                    Text(o.nombreDe("empresa"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Repartidor", style = MaterialTheme.typography.labelMedium)
                    Text(o.optJSONObject("repartidor")?.getString("nombre") ?: "Sin asignar", style = MaterialTheme.typography.bodyMedium)
                }
                if (!ocultarPrecios) Column(Modifier.weight(1f)) {
                    Text("Total", style = MaterialTheme.typography.labelMedium)
                    Text(dinero(o.get("total")), style = MaterialTheme.typography.titleMedium)
                }
            }
            o.optJSONObject("puntoRetiro")?.let { pr ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Place, null, Modifier.size(18.dp))
                    Text("Retirar en: ${pr.getString("nombre")} · ${pr.optString("direccion")}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (o.getString("modalidad") == "ENVIO_DIRECTO") {
                FilledTonalButton(onClick = alVerRuta, modifier = Modifier.fillMaxWidth()) {
                    Icon(Iconos.Ruta, null, Modifier.size(18.dp))
                    Text("Ver ruta de reparto", Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/**
 * Línea de tiempo vertical, como el rastreo de un courier: pasos hechos con check y fecha,
 * el actual con un punto que late y los pendientes en gris.
 */
@Composable
private fun LineaDeTiempo(pasos: List<JSONObject>) {
    val latido = rememberInfiniteTransition(label = "latido")
    val escala by latido.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "escala")
    val ok = Tono.OK.colores()
    Column {
        pasos.forEachIndexed { i, paso ->
            val hecho = paso.getBoolean("hecho")
            val actual = paso.getBoolean("actual")
            val ultimo = i == pasos.lastIndex
            val siguienteHecho = !ultimo && pasos[i + 1].getBoolean("hecho")
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        actual -> Box(
                            Modifier.size(22.dp).scale(escala).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape)) }
                        hecho -> Box(Modifier.size(22.dp).background(ok.contenido, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                        }
                        else -> Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                    if (!ultimo) {
                        Box(
                            Modifier.width(2.dp).weight(1f)
                                .background(if (siguienteHecho) ok.contenido else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp, bottom = if (ultimo) 0.dp else 18.dp).weight(1f)) {
                    Text(
                        paso.getString("titulo"),
                        style = if (actual) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (hecho) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    paso.texto("fecha")?.let { Text(fechaCorta(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaEntrega(
    e: JSONObject,
    ocultarPrecios: Boolean,
    modifier: Modifier,
    puedeMarcar: Boolean,
    puedeRecibir: Boolean,
    alMarcar: () -> Unit,
    alRecibir: () -> Unit,
) {
    val estado = e.getString("estado")
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("${e.getInt("parada")}", style = MaterialTheme.typography.titleSmall) }
            }
            Column(Modifier.weight(1f)) {
                Text(e.nombreDe("establecimiento"), style = MaterialTheme.typography.titleMedium)
                e.texto("direccion")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Etiqueta(estadoEntregaLegible(estado), tonoEntrega(estado))
        }
        e.getJSONArray("lineas").objetos().forEach { l ->
            val recibida = l.texto("cantidadRecibida")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.nombreDe("producto"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    buildString {
                        if (recibida != null && recibida.toDouble() != l.getDouble("cantidad")) append("${num(recibida)} de ")
                        append("${num(l.get("cantidad"))} ${l.getString("unidad")}")
                        if (!ocultarPrecios) append(" · ${dinero(l.get("subtotal"))}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (estado == "RECIBIDA") {
            val completa = e.optBoolean("completa", true)
            Aviso(
                listOfNotNull(
                    "${e.nombreDe("recibidaPor")} · ${fechaCorta(e.texto("recibidaEn"))}",
                    e.texto("nota"),
                ).joinToString("\n"),
                if (completa) Tono.OK else Tono.AVISO,
                titulo = if (completa) "Llegó completo" else "Llegó con novedad",
            )
        } else if (estado == "ENTREGADA") {
            Text("Dejado por el repartidor · ${fechaCorta(e.texto("entregadaEn"))}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (puedeMarcar) {
            Button(onClick = alMarcar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Text("Entregado aquí", Modifier.padding(start = 6.dp))
            }
        }
        if (puedeRecibir) {
            Button(onClick = alRecibir, modifier = Modifier.fillMaxWidth()) {
                Icon(Iconos.Inventario, null, Modifier.size(18.dp))
                Text("Confirmar recepción", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun Historial(eventos: List<JSONObject>, modifier: Modifier) {
    Tarjeta(modifier) {
        eventos.forEachIndexed { i, ev ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ev.getString("titulo"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(fechaCorta(ev.getString("ocurridoEn")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ev.texto("detalle")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "${ev.nombreDe("usuario")} · ${rolLegible(ev.getString("rol"))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Confirmar lo que llegó: todo, o cantidades por producto; nota y foto de la guía opcionales. */
@Composable
private fun DialogoRecepcion(api: Api, e: JSONObject, alCerrar: () -> Unit, alConfirmar: (JSONObject) -> Unit) {
    val lineas = e.getJSONArray("lineas").objetos()
    var completo by remember { mutableStateOf(true) }
    val cantidades = remember { mutableStateMapOf<String, String>().apply { lineas.forEach { put(it.getString("id"), num(it.get("cantidad")).replace(',', '.')) } } }
    var nota by remember { mutableStateOf("") }
    var fotoId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(Iconos.Inventario, null) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Confirmar recepción")
                Text(e.nombreDe("establecimiento"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Llegó completo?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = completo, onClick = {
                        completo = true
                        lineas.forEach { cantidades[it.getString("id")] = num(it.get("cantidad")).replace(',', '.') }
                    }, label = { Text("Sí, todo") })
                    FilterChip(selected = !completo, onClick = { completo = false }, label = { Text("No, hubo novedad") })
                }
                lineas.forEach { l ->
                    val id = l.getString("id")
                    if (completo) {
                        Row {
                            Text(l.nombreDe("producto"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${num(l.get("cantidad"))} ${l.getString("unidad")}", style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        OutlinedTextField(
                            cantidades[id] ?: "", { cantidades[id] = it.replace(',', '.'); error = null }, singleLine = true,
                            label = { Text("${l.nombreDe("producto")} (pedido ${num(l.get("cantidad"))})") },
                            suffix = { Text(l.getString("unidad")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OutlinedTextField(nota, { nota = it }, label = { Text(if (completo) "Nota (opcional)" else "¿Qué pasó?") }, modifier = Modifier.fillMaxWidth())
                BotonFoto(api, "Foto de la guía de remisión (opcional)") { fotoId = it }
                error?.let { Aviso(it, Tono.CRITICO) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val arr = JSONArray()
                for (l in lineas) {
                    val id = l.getString("id")
                    val v = cantidades[id]?.toBigDecimalOrNull()
                    if (v == null || v.signum() < 0 || v.toDouble() > l.getDouble("cantidad")) {
                        error = "Revisa la cantidad de ${l.nombreDe("producto")}: entre 0 y ${num(l.get("cantidad"))}"
                        return@Button
                    }
                    arr.put(JSONObject().put("lineaId", id).put("cantidadRecibida", v))
                }
                alConfirmar(
                    JSONObject().put("lineas", arr).put("nota", nota.ifBlank { JSONObject.NULL }).put("fotoId", fotoId ?: JSONObject.NULL),
                )
            }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoRepartidor(api: Api, alCerrar: () -> Unit, alElegir: (JSONObject) -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(Icons.Filled.Person, null) },
        title = { Text("Asignar repartidor") },
        text = {
            Cargador("repartidores", { api.arr("/proveedor/repartidores").objetos() }) { lista, _ ->
                if (lista.isEmpty()) Text("No tienes repartidores activos.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lista.forEach { r ->
                        Tarjeta(alTocar = { alElegir(r) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Iniciales(r.getString("nombre"), tam = 36.dp)
                                Column {
                                    Text(r.getString("nombre"), style = MaterialTheme.typography.titleSmall)
                                    Text(r.getString("email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoRechazo(alCerrar: () -> Unit, alConfirmar: (String) -> Unit) {
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Rechazar pedido") },
        text = {
            OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo (lo verá la empresa)") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(
                onClick = { alConfirmar(motivo.trim()) }, enabled = motivo.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Rechazar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
