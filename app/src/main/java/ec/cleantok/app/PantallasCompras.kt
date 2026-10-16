package ec.cleantok.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pestaña "Compras" del admin: qué reponer (con cantidad recomendada), pedir cualquier otro producto del
 * catálogo (o uno nuevo) y las solicitudes enviadas a cotizar.
 */
@Composable
fun PantallaCompras(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    var vista by remember { mutableStateOf("reponer") }
    Marco("Compras", subtitulo = "Qué reponer y a quién comprar", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Row(Modifier.entrada(0).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vista == "reponer", onClick = { vista = "reponer" }, label = { Text("Por reponer") })
            FilterChip(selected = vista == "otros", onClick = { vista = "otros" }, label = { Text("Pedir otros") })
            FilterChip(selected = vista == "solicitudes", onClick = { vista = "solicitudes" }, label = { Text("Solicitudes") })
        }
        when (vista) {
            "reponer" -> PorReponer(api, nav)
            "otros" -> PedirOtros(api, nav)
            else -> Solicitudes(api, nav)
        }
    }
}

/** Sugerencias de reposición: el admin elige qué pedir, ajusta cantidades y lo envía a cotizar a sus proveedores. */
@Composable
private fun ColumnScope.PorReponer(api: Api, nav: Navegacion) {
    val scope = rememberCoroutineScope()
    val elegidos = remember { mutableStateMapOf<String, Boolean>() }
    val cantidades = remember { mutableStateMapOf<String, String>() }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** Líneas listas para enviar mientras el admin elige la fecha. */
    var porFechar by remember { mutableStateOf<JSONArray?>(null) }

    Cargador("sugerencias", { api.arr("/abastecimiento/sugerencias").objetos() }) { lista, _ ->
        lista.forEach { s ->
            val id = s.getJSONObject("producto").getString("id")
            if (id !in elegidos) elegidos[id] = s.isNull("yaPedido")
            if (id !in cantidades) cantidades[id] = num(s.get("cantidadRecomendada")).replace(',', '.')
        }
        // Llegó desde "Comprar este producto": solo ese queda elegido y se abre la fecha directamente.
        LaunchedEffect(lista, nav.comprarProducto) {
            val foco = nav.comprarProducto ?: return@LaunchedEffect
            nav.comprarProducto = null
            val s = lista.firstOrNull { it.getJSONObject("producto").getString("id") == foco } ?: return@LaunchedEffect
            lista.forEach { elegidos[it.getJSONObject("producto").getString("id")] = false }
            elegidos[foco] = true
            val c = cantidades[foco]?.toBigDecimalOrNull() ?: s.get("cantidadRecomendada").toString().toBigDecimal()
            porFechar = JSONArray().put(JSONObject().put("productoId", foco).put("cantidad", c))
        }
        val seleccion = lista.filter { elegidos[it.getJSONObject("producto").getString("id")] == true }
        Column(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                item(key = "explica") {
                    Aviso(
                        "Productos bajos, críticos o con la bodega vacía. Cantidad sugerida = consumo diario × (3 días de entrega + 14 de ciclo " +
                            "+ 3 de seguridad) − stock actual. Los que provee la entidad contratante no se compran aquí.",
                        Tono.INFO, titulo = "${lista.size} productos por reponer",
                    )
                }
                if (lista.isEmpty()) item(key = "vacio") { EstadoVacio(Icons.Filled.Check, "Todo abastecido", "Ningún producto que compra la empresa está bajo o crítico.") }
                itemsIndexed(lista, key = { _, s -> s.getJSONObject("producto").getString("id") }) { i, s ->
                    val id = s.getJSONObject("producto").getString("id")
                    Sugerencia(
                        s, elegidos[id] == true, cantidades[id] ?: "", Modifier.animateItem().entrada(i),
                        alElegir = { elegidos[id] = it },
                        alCambiarCantidad = { cantidades[id] = it.replace(',', '.') },
                    )
                }
            }
            error?.let { Aviso(it, Tono.CRITICO, alCerrar = { error = null }) }
            Button(
                onClick = {
                    val lineas = JSONArray()
                    for (s in seleccion) {
                        val id = s.getJSONObject("producto").getString("id")
                        val c = cantidades[id]?.toBigDecimalOrNull()
                        if (c == null || c.signum() <= 0) { error = "Revisa la cantidad de ${s.nombreDe("producto")}"; return@Button }
                        lineas.put(JSONObject().put("productoId", id).put("cantidad", c))
                    }
                    porFechar = lineas
                },
                enabled = seleccion.isNotEmpty() && !enviando,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
            ) {
                if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Pedir cotización · ${seleccion.size} producto${if (seleccion.size == 1) "" else "s"}")
            }
        }
        porFechar?.let { lineas ->
            val coberturas = seleccion.map { s ->
                val dias = s.texto("diasCobertura")?.toDoubleOrNull() ?: if (s.getDouble("stockTotal") <= 0.0) 0.0 else null
                Cobertura(s.nombreDe("producto"), dias)
            }
            DialogoFechaCompra(coberturas, enviando, alCerrar = { porFechar = null }) { fecha ->
                enviando = true
                scope.launch {
                    enFondo { api.post("/solicitudes", JSONObject().put("enviar", true).put("fechaRequerida", fecha.toString()).put("lineas", lineas)) }
                        .onSuccess { r -> porFechar = null; nav.ir(Pantalla.Solicitud(r.getString("id"), r.getString("codigo"))) }
                        .onFailure { porFechar = null; error = mensajeError(it) }
                    enviando = false
                }
            }
        }
    }
}

/**
 * Pedido libre: el admin elige el establecimiento, busca en su catálogo cualquier producto (esté bajo o no)
 * o crea uno nuevo, arma el pedido (puede mezclar establecimientos) y lo envía a cotizar a sus proveedores.
 */
@Composable
private fun ColumnScope.PedirOtros(api: Api, nav: Navegacion) {
    val scope = rememberCoroutineScope()
    /** Producto elegido → {nombre, unidad, sitio}; la cantidad va aparte para poder editarla. */
    val pedido = remember { mutableStateMapOf<String, JSONObject>() }
    val cantidades = remember { mutableStateMapOf<String, String>() }
    var sitioId by remember { mutableStateOf<String?>(null) }
    var buscar by remember { mutableStateOf("") }
    var nuevo by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var porFechar by remember { mutableStateOf<JSONArray?>(null) }

    fun agregar(id: String, nombre: String, unidad: String, sitio: String, dias: Double? = null) {
        pedido[id] = JSONObject().put("nombre", nombre).put("unidad", unidad).put("sitio", sitio).put("dias", dias ?: JSONObject.NULL)
        if (id !in cantidades) cantidades[id] = ""
    }
    fun quitar(id: String) { pedido.remove(id); cantidades.remove(id) }

    Cargador("sitios-compra", { api.arr("/establecimientos/mios").objetos() }) { sitios, _ ->
        if (sitios.isEmpty()) {
            EstadoVacio(Icons.Filled.Place, "Sin establecimientos", "Crea uno en Gestión → Establecimientos.", Tono.NEUTRO)
            return@Cargador
        }
        val sitio = sitios.firstOrNull { it.getString("id") == sitioId } ?: sitios.first()
        val sitioNombre = sitio.getString("nombre")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sitios.forEach { e ->
                    FilterChip(
                        selected = e === sitio, onClick = { sitioId = e.getString("id") }, label = { Text(e.getString("nombre")) },
                        leadingIcon = { Icon(Icons.Filled.Place, null, Modifier.size(16.dp)) },
                    )
                }
            }
            OutlinedTextField(
                buscar, { buscar = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar en el catálogo") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (buscar.isNotEmpty()) IconButton(onClick = { buscar = "" }) { Icon(Icons.Filled.Close, "Borrar") } },
            )
            Cargador(sitio.getString("id"), { api.obj("/establecimientos/${sitio.getString("id")}/inventario").getJSONArray("items").objetos() }) { items, recargar ->
                val filtro = buscar.trim()
                val visibles = items.filter { filtro.isEmpty() || it.getJSONObject("producto").getString("nombre").contains(filtro, ignoreCase = true) }
                val lista = rememberLazyListState()
                // Al agregar un producto, sube para que se vea en "Tu pedido" y se escriba la cantidad.
                var antes by remember { mutableIntStateOf(pedido.size) }
                LaunchedEffect(pedido.size) {
                    if (pedido.size > antes) lista.animateScrollToItem(0)
                    antes = pedido.size
                }
                LazyColumn(Modifier.weight(1f), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = FinDeLista) {
                    if (pedido.isNotEmpty()) {
                        item(key = "t-pedido") { Seccion("Tu pedido (${pedido.size})") }
                        pedido.forEach { (id, l) ->
                            item(key = "p-$id") {
                                LineaPedido(
                                    l, cantidades[id] ?: "", Modifier.animateItem(),
                                    alCambiar = { cantidades[id] = it.replace(',', '.') }, alQuitar = { quitar(id) },
                                )
                            }
                        }
                    }
                    item(key = "t-catalogo") { Seccion("Catálogo · $sitioNombre") }
                    item(key = "nuevo") {
                        Tarjeta(alTocar = { nuevo = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Icons.Filled.Add, Tono.INFO)
                                Column(Modifier.weight(1f)) {
                                    Text("Producto nuevo", style = MaterialTheme.typography.titleMedium)
                                    Text("¿No está en la lista? Créalo y pídelo a tus proveedores.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (visibles.isEmpty()) item(key = "vacio") {
                        EstadoVacio(Iconos.Inventario, if (filtro.isEmpty()) "Catálogo vacío" else "Sin resultados",
                            "Usa \"Producto nuevo\" para pedir algo que aún no tienen.", Tono.NEUTRO)
                    }
                    itemsIndexed(visibles, key = { _, f -> "c-" + f.getJSONObject("producto").getString("id") }) { i, f ->
                        val prod = f.getJSONObject("producto")
                        val id = prod.getString("id")
                        val enPedido = id in pedido
                        Tarjeta(Modifier.animateItem().entrada(i), alTocar = {
                            if (enPedido) quitar(id)
                            else agregar(id, prod.getString("nombre"), prod.getString("unidad"), sitioNombre,
                                f.texto("diasCobertura")?.toDoubleOrNull() ?: if (f.getDouble("stockTotal") <= 0.0) 0.0 else null)
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(prod.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                    Text("Hay ${num(f.get("stockTotal"))} ${prod.getString("unidad")}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (enPedido) Etiqueta("En el pedido", Tono.OK)
                                else Icon(Icons.Filled.Add, "Agregar al pedido", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (nuevo) DialogoMaterial("Producto nuevo", null, conCantidad = false, alCerrar = { nuevo = false }) { cuerpo, _ ->
                    nuevo = false
                    trabajando = true
                    scope.launch {
                        enFondo { api.post("/establecimientos/${sitio.getString("id")}/productos", cuerpo) }
                            .onSuccess { p ->
                                agregar(p.getString("id"), p.getString("nombre"), p.getString("unidad"), sitioNombre)
                                recargar()
                            }
                            .onFailure { error = mensajeError(it) }
                        trabajando = false
                    }
                }
            }
        }
        error?.let { Aviso(it, Tono.CRITICO, alCerrar = { error = null }) }
        Button(
            onClick = {
                val lineas = JSONArray()
                for ((id, l) in pedido) {
                    val c = cantidades[id]?.toBigDecimalOrNull()
                    if (c == null || c.signum() <= 0) { error = "Escribe cuánto pedir de ${l.getString("nombre")}"; return@Button }
                    lineas.put(JSONObject().put("productoId", id).put("cantidad", c))
                }
                porFechar = lineas
            },
            enabled = pedido.isNotEmpty() && !trabajando,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
        ) {
            if (trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (pedido.isEmpty()) "Elige productos del catálogo" else "Pedir cotización · ${pedido.size} producto${if (pedido.size == 1) "" else "s"}")
        }
        porFechar?.let { lineas ->
            val coberturas = pedido.values.map { l -> Cobertura(l.getString("nombre"), l.texto("dias")?.toDoubleOrNull()) }
            DialogoFechaCompra(coberturas, trabajando, alCerrar = { porFechar = null }) { fecha ->
                trabajando = true
                scope.launch {
                    enFondo { api.post("/solicitudes", JSONObject().put("enviar", true).put("fechaRequerida", fecha.toString()).put("lineas", lineas)) }
                        .onSuccess { r ->
                            porFechar = null
                            pedido.clear(); cantidades.clear()
                            nav.ir(Pantalla.Solicitud(r.getString("id"), r.getString("codigo")))
                        }
                        .onFailure { porFechar = null; error = mensajeError(it) }
                    trabajando = false
                }
            }
        }
    }
}

/** Producto ya elegido en "Pedir otros": cuánto pedir y para qué establecimiento. */
@Composable
private fun LineaPedido(l: JSONObject, cantidad: String, modifier: Modifier, alCambiar: (String) -> Unit, alQuitar: () -> Unit) {
    val unidad = l.getString("unidad")
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(l.getString("nombre"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(l.getString("sitio"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                cantidad, alCambiar, singleLine = true, modifier = Modifier.width(120.dp),
                label = { Text("Pedir") }, suffix = { Text(unidad) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            IconButton(onClick = alQuitar) { Icon(Icons.Filled.Close, "Quitar del pedido") }
        }
    }
}

@Composable
private fun Sugerencia(
    s: JSONObject,
    elegido: Boolean,
    cantidad: String,
    modifier: Modifier,
    alElegir: (Boolean) -> Unit,
    alCambiarCantidad: (String) -> Unit,
) {
    val estado = s.getString("estado")
    val unidad = s.getJSONObject("producto").getString("unidad")
    Tarjeta(modifier, alTocar = { alElegir(!elegido) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = elegido, onCheckedChange = alElegir)
            Column(Modifier.weight(1f)) {
                Text(s.nombreDe("producto"), style = MaterialTheme.typography.titleMedium)
                Text(s.nombreDe("establecimiento"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val bodegaVacia = estado != "CRITICO" && estado != "BAJO"
            Etiqueta(if (bodegaVacia) "Bodega vacía" else estado.legible(), if (estado == "CRITICO") Tono.CRITICO else Tono.AVISO)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(s.getString("motivo"), style = MaterialTheme.typography.bodySmall)
                Text("Stock ${num(s.get("stockTotal"))} $unidad · " + (s.texto("consumoDiario")?.let { "consumo ${num(s.get("consumoDiario"))}/día" } ?: "sin consumo registrado"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.texto("yaPedido")?.let { Etiqueta("Ya pedido en $it", Tono.INFO) }
            }
            OutlinedTextField(
                cantidad, alCambiarCantidad, singleLine = true, enabled = elegido, modifier = Modifier.width(120.dp),
                label = { Text("Pedir") }, suffix = { Text(unidad) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@Composable
private fun Solicitudes(api: Api, nav: Navegacion) {
    Cargador("solicitudes", { api.arr("/solicitudes").objetos() }) { lista, _ ->
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
            if (lista.isEmpty()) item { EstadoVacio(Icons.Filled.Check, "Sin solicitudes", "Elige productos en \"Por reponer\" o \"Pedir otros\" para pedir cotización.", Tono.NEUTRO) }
            itemsIndexed(lista, key = { _, s -> s.getString("id") }) { i, s ->
                val estado = s.getString("estado")
                Tarjeta(Modifier.animateItem().entrada(i), alTocar = { nav.ir(Pantalla.Solicitud(s.getString("id"), s.getString("codigo"))) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.getString("codigo"), style = MaterialTheme.typography.titleMedium)
                            Text("Creada ${fechaCorta(s.getString("creadaEn"))} · para el ${diaIso(s.getString("fechaRequerida"))}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Etiqueta(estadoSolicitudLegible(estado), tonoSolicitud(estado))
                    }
                    Row {
                        Dato("Productos", "${s.getInt("productos")}", modifier = Modifier.weight(1f))
                        Dato("Destinos", "${s.getInt("destinos")}", modifier = Modifier.weight(1f))
                        Dato("Ofertas", "${s.getInt("ofertas")} de ${s.getInt("invitados")}", modifier = Modifier.weight(1f),
                            color = if (estado == "EN_COTIZACION" && s.getInt("ofertas") > 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                    }
                }
            }
        }
    }
}

/** Detalle de la solicitud: destinos por producto, comparación de ofertas con puntaje y aprobación. */
@Composable
fun PantallaSolicitud(api: Api, nav: Navegacion, p: Pantalla.Solicitud) {
    val scope = rememberCoroutineScope()
    val elegidas = remember { mutableStateMapOf<String, String>() }
    var trabajando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var pagando by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    // Al revisar la solicitud, sus avisos de "te cotizaron" quedan leídos.
    val avisos = LocalAvisos.current
    LaunchedEffect(p.id, avisos?.recientes) {
        avisos?.recientes?.filter { !it.getBoolean("leida") && it.texto("referenciaId") == p.id }?.forEach { avisos.leer(it) }
    }

    Marco("Solicitud ${p.codigo}", subtitulo = "Comparar ofertas y aprobar") {
        Cargador(p, { api.obj("/solicitudes/${p.id}") }) { s, recargar ->
            val estado = s.getString("estado")
            val items = s.getJSONArray("items").objetos()
            items.forEach { item ->
                val id = item.getString("id")
                if (id !in elegidas) item.getJSONArray("cotizaciones").objetos().firstOrNull { it.getBoolean("recomendada") }
                    ?.let { elegidas[id] = it.getString("ofertaId") }
            }
            fun ejecutar(exito: String, llamada: () -> Unit) {
                trabajando = true
                scope.launch {
                    enFondo(llamada).onSuccess { aviso = exito to Tono.OK }.onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                    trabajando = false
                    recargar()
                }
            }
            val adjudicadas = items.mapNotNull { item ->
                item.getJSONArray("cotizaciones").objetos().firstOrNull { it.getString("ofertaId") == elegidas[item.getString("id")] }
            }
            val total = adjudicadas.sumOf { it.getDouble("total") }
            /** Lo que se paga a cada proveedor elegido. */
            val pagos = adjudicadas.groupBy { it.nombreDe("proveedor") }.map { (prov, cs) -> PagoProveedor(prov, cs.sumOf { it.getDouble("total") }) }

            Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                    item(key = "cabecera") {
                        Tarjeta(Modifier.entrada(0)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Para el ${diaIso(s.getString("fechaRequerida"))}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Etiqueta(estadoSolicitudLegible(estado), tonoSolicitud(estado))
                            }
                            val invitados = s.getJSONArray("invitados").objetos()
                            val ofertas = s.getJSONArray("ofertas").objetos()
                            Text(
                                "${ofertas.size} de ${invitados.size} proveedores ofertaron: " + invitados.joinToString(", ") { inv ->
                                    inv.getString("nombre") + if (ofertas.any { it.getJSONObject("proveedor").getString("id") == inv.getString("id") }) " ✓" else ""
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }
                    if (estado == "ADJUDICADA") pagoGuardado(contexto, p.id)?.let { pago ->
                        item(key = "pago") {
                            Tarjeta(Modifier.animateItem().entrada(1)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IconoRedondo(if (pago.getString("descripcion").startsWith("Transferencia")) Iconos.Banco else Iconos.Tarjeta, Tono.OK)
                                    Column(Modifier.weight(1f)) {
                                        Text("Pagado · ${dinero(pago.get("total"))}", style = MaterialTheme.typography.titleSmall)
                                        Text("${pago.getString("descripcion")} · ${fechaCorta(pago.getString("en"))}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Etiqueta("Demo", Tono.AVISO)
                                }
                            }
                        }
                    }

                    val ordenes = s.getJSONArray("ordenes").objetos()
                    if (ordenes.isNotEmpty()) {
                        item(key = "ordenes-titulo") { Seccion("Órdenes de compra emitidas") }
                        itemsIndexed(ordenes, key = { _, o -> "orden-" + o.getString("id") }) { i, o ->
                            Tarjeta(Modifier.animateItem().entrada(1 + i), alTocar = { nav.ir(Pantalla.Orden(o.getString("id"), o.getString("codigo"))) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IconoRedondo(Iconos.Camion, tonoOrden(o.getString("estado")))
                                    Column(Modifier.weight(1f)) {
                                        Text("${o.getString("codigo")} · ${o.nombreDe("proveedor")}", style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${modalidadLegible(o.getString("modalidad"))} · ${dinero(o.get("total"))}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Etiqueta(estadoOrdenLegible(o.getString("estado")), tonoOrden(o.getString("estado")))
                                }
                            }
                        }
                    }

                    if (estado == "EN_COTIZACION") item(key = "criterios") {
                        Aviso("Entrega 40 % · Disponibilidad 30 % · Costo 25 % · Proveedor preferido 5 %", Tono.INFO, titulo = "Cómo se recomienda")
                    }
                    item(key = "productos-titulo") { Seccion("Productos (${items.size})") }
                    itemsIndexed(items, key = { _, it -> "item-" + it.getString("id") }) { i, item ->
                        ItemSolicitud(
                            item, estado == "EN_COTIZACION", elegidas[item.getString("id")], Modifier.animateItem().entrada(2 + i),
                        ) { elegidas[item.getString("id")] = it }
                    }
                }

                when (estado) {
                    "EN_COTIZACION" -> Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pagando = true },
                            enabled = !trabajando && elegidas.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Aprobar y pagar · ${dinero(total)}") }
                        OutlinedButton(
                            onClick = { ejecutar("Solicitud cancelada") { api.post("/solicitudes/${p.id}/cancelar") } },
                            enabled = !trabajando, modifier = Modifier.fillMaxWidth(),
                        ) { Text("Cancelar solicitud") }
                    }
                    "BORRADOR" -> Button(
                        onClick = { ejecutar("Solicitud enviada a cotizar") { api.post("/solicitudes/${p.id}/enviar") } },
                        enabled = !trabajando, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                    ) { Text("Enviar a cotizar") }
                }
            }
            if (pagando) DialogoPago(p.codigo, pagos, alCerrar = { pagando = false }) { descripcion ->
                pagando = false
                val asignaciones = JSONArray()
                elegidas.forEach { (item, oferta) -> asignaciones.put(JSONObject().put("itemId", item).put("ofertaId", oferta)) }
                trabajando = true
                scope.launch {
                    enFondo { api.post("/solicitudes/${p.id}/adjudicar", JSONObject().put("asignaciones", asignaciones)) }
                        .onSuccess {
                            guardarPago(contexto, p.id, descripcion, total)
                            aviso = "Pago aprobado ($descripcion): se emitió una orden por proveedor" to Tono.OK
                        }
                        .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                    trabajando = false
                    recargar()
                }
            }
        }
    }
}

@Composable
private fun ItemSolicitud(item: JSONObject, puedeElegir: Boolean, elegida: String?, modifier: Modifier, alElegir: (String) -> Unit) {
    val unidad = item.getString("unidad")
    val cotizaciones = item.getJSONArray("cotizaciones").objetos()
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.getString("nombre"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${num(item.get("cantidad"))} $unidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            item.getJSONArray("destinos").objetos().joinToString(" · ") { "${it.nombreDe("establecimiento")}: ${num(it.get("cantidad"))}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cotizaciones.isEmpty()) {
            Text("Todavía sin ofertas para este producto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        cotizaciones.forEach { c ->
            Cotizacion(c, unidad, elegida == c.getString("ofertaId"), puedeElegir) { alElegir(c.getString("ofertaId")) }
        }
    }
}

/** Una oferta para el producto, con su puntaje y los datos que lo explican. */
@Composable
private fun Cotizacion(c: JSONObject, unidad: String, elegida: Boolean, puedeElegir: Boolean, alElegir: () -> Unit) {
    val borde = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = alElegir, enabled = puedeElegir, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (elegida) 2.dp else 1.dp, borde),
        color = if (elegida) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (puedeElegir) RadioButton(selected = elegida, onClick = alElegir, modifier = Modifier.size(32.dp))
                Text(c.nombreDe("proveedor"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 4.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.getBoolean("preferido")) Icon(Icons.Filled.Star, "Preferido", Modifier.size(16.dp), tint = Tono.AVISO.colores().contenido)
                if (c.getBoolean("recomendada")) Etiqueta("Recomendada", Tono.OK)
            }
            Row {
                Dato("Precio", dinero(c.get("precioUnitario")), "/$unidad", Modifier.weight(1f))
                Dato("Entrega", "${c.getInt("plazoDias")}", if (c.getInt("plazoDias") == 1) "día" else "días", Modifier.weight(1f))
                Dato("Total", dinero(c.get("total")), modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta(if (c.getBoolean("completo")) "Completo" else "Parcial · ${num(c.get("cantidadDisponible"))} $unidad", if (c.getBoolean("completo")) Tono.OK else Tono.AVISO)
                Text(modalidadLegible(c.getString("modalidad")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val puntaje = c.getInt("puntaje")
                val barra = animadoDesdeCero(puntaje / 100f)
                LinearProgressIndicator(
                    progress = { barra }, modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                )
                Text("$puntaje pts", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
