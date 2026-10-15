package ec.cleantok.app

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Corre una llamada en segundo plano con indicador; muestra el error o deja el resultado a [alExito]. */
class Ejecutor(val scope: kotlinx.coroutines.CoroutineScope) {
    var trabajando by mutableStateOf(false)
    var aviso by mutableStateOf<Pair<String, Tono>?>(null)

    fun <T> correr(exito: String?, llamada: () -> T, alExito: (T) -> Unit = {}) {
        trabajando = true
        scope.launch {
            enFondo(llamada)
                .onSuccess { r -> exito?.let { aviso = it to Tono.OK }; alExito(r) }
                .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
            trabajando = false
        }
    }
}

@Composable
fun rememberEjecutor(): Ejecutor {
    val scope = rememberCoroutineScope()
    return remember { Ejecutor(scope) }
}

// ======================= Empresa =======================

/** Pestaña "Gestión" del admin de la empresa: su personal, sus proveedores y sus establecimientos. */
@Composable
fun PantallaGestionEmpresa(api: Api, sesion: Sesion, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    var vista by remember { mutableStateOf("equipo") }
    Marco("Gestión", subtitulo = "Personal, proveedores y establecimientos", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Row(Modifier.entrada(0).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("equipo" to "Personal", "proveedores" to "Proveedores", "sitios" to "Establecimientos").forEach { (k, t) ->
                FilterChip(selected = vista == k, onClick = { vista = k }, label = { Text(t) })
            }
        }
        when (vista) {
            "equipo" -> EquipoEmpresa(api, sesion)
            "proveedores" -> DirectorioProveedores(api)
            else -> SitiosEmpresa(api, nav)
        }
    }
}

@Composable
private fun ColumnScope.EquipoEmpresa(api: Api, sesion: Sesion) {
    val ej = rememberEjecutor()
    var nueva by remember { mutableStateOf(false) }
    var credencial by remember { mutableStateOf<JSONObject?>(null) }
    var asignar by remember { mutableStateOf<JSONObject?>(null) }

    Cargador("equipo-empresa", {
        api.obj("/usuarios?size=200&sort=nombre").getJSONArray("content").objetos() to api.arr("/establecimientos/mios").objetos()
    }) { (personas, sitios), recargar ->
        Column(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                ej.aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { ej.aviso = null }) } } }
                item(key = "resumen") {
                    val encargados = personas.count { it.getString("rol") == "ENCARGADO" && it.getBoolean("activo") }
                    Seccion("${personas.size} personas · $encargados encargados activos")
                }
                itemsIndexed(personas, key = { _, u -> u.getString("id") }) { i, u ->
                    val id = u.getString("id")
                    val esEncargado = u.getString("rol") == "ENCARGADO"
                    val susSitios = u.optJSONArray("establecimientos")?.objetos().orEmpty()
                    TarjetaPersona(
                        u, Modifier.animateItem().entrada(1 + i),
                        detalle = when {
                            !esEncargado -> "Administra toda la empresa"
                            susSitios.isEmpty() -> "Sin establecimientos asignados"
                            else -> susSitios.joinToString(" · ") { it.getString("nombre") }
                        },
                        trabajando = ej.trabajando,
                        alCambiarActivo = { activo ->
                            ej.correr(if (activo) "${u.getString("nombre")} activado" else "${u.getString("nombre")} desactivado: se cerraron sus sesiones",
                                { api.patch("/usuarios/$id", JSONObject().put("activo", activo)) }) { recargar() }
                        },
                        alRestablecer = { ej.correr(null, { api.post("/usuarios/$id/restablecer-clave") }) { credencial = it; recargar() } },
                        otraAccion = if (esEncargado) "Establecimientos" to { asignar = u } else null,
                    )
                }
            }
            Button(onClick = { nueva = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("Agregar personal", Modifier.padding(start = 6.dp))
            }
        }

        if (nueva) {
            val elegidos = remember { mutableStateListOf<String>() }
            DialogoNuevaPersona(
                "Agregar personal", listOf("ENCARGADO" to "Encargado de limpieza", "ADMIN_EMPRESA" to "Administración"),
                alCerrar = { nueva = false },
                extra = { rol -> if (rol == "ENCARGADO") SelectorSitios(sitios, elegidos) },
                alCrear = { nombre, email, rol ->
                    nueva = false
                    ej.correr(null, {
                        val u = api.post("/usuarios", JSONObject().put("nombre", nombre).put("email", email).put("rol", rol))
                        if (rol == "ENCARGADO" && elegidos.isNotEmpty()) {
                            api.put("/establecimientos/asignaciones/${u.getString("id")}", JSONArray(elegidos.toList()))
                        }
                        u
                    }) { credencial = it; recargar() }
                },
            )
        }
        asignar?.let { u ->
            val elegidos = remember(u) { mutableStateListOf<String>().apply { addAll(u.optJSONArray("establecimientos")?.objetos().orEmpty().map { it.getString("id") }) } }
            AlertDialog(
                onDismissRequest = { asignar = null },
                icon = { Icon(Icons.Filled.Place, null) },
                title = { Text("Establecimientos de ${u.getString("nombre").substringBefore(' ')}") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectorSitios(sitios, elegidos) } },
                confirmButton = {
                    Button(onClick = {
                        asignar = null
                        ej.correr("Establecimientos actualizados", { api.put("/establecimientos/asignaciones/${u.getString("id")}", JSONArray(elegidos.toList())) }) { recargar() }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { asignar = null }) { Text("Cancelar") } },
            )
        }
        credencial?.let { DialogoCredencial(it, sesion.servidor) { credencial = null } }
    }
}

@Composable
private fun SelectorSitios(sitios: List<JSONObject>, elegidos: MutableList<String>) {
    Column {
        Text("¿Dónde trabaja?", style = MaterialTheme.typography.titleSmall)
        sitios.forEach { s ->
            val id = s.getString("id")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = id in elegidos, onCheckedChange = { if (it) elegidos += id else elegidos -= id })
                Text(s.getString("nombre"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Directorio de proveedores de la plataforma: con cuáles trabajo y cuáles prefiero. */
@Composable
private fun DirectorioProveedores(api: Api) {
    val ej = rememberEjecutor()
    Cargador("directorio", { api.arr("/proveedores/directorio").objetos() }) { lista, recargar ->
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
            item(key = "explica") {
                Aviso(
                    "Los proveedores con los que trabajas reciben tus solicitudes para cotizar. Los preferidos suman 5 % en la recomendación.",
                    Tono.INFO, titulo = "${lista.count { it.getBoolean("vinculado") }} de ${lista.size} proveedores",
                )
            }
            ej.aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { ej.aviso = null }) } } }
            itemsIndexed(lista, key = { _, d -> d.getJSONObject("proveedor").getString("id") }) { i, d ->
                val p = d.getJSONObject("proveedor")
                val id = p.getString("id")
                val vinculado = d.getBoolean("vinculado")
                val preferido = p.optBoolean("preferido", false)
                Tarjeta(Modifier.animateItem().entrada(1 + i)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconoRedondo(Iconos.Tienda, if (vinculado) Tono.OK else Tono.NEUTRO)
                        Column(Modifier.weight(1f)) {
                            Text(p.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                            Text(p.getString("direccion"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (vinculado) IconButton(onClick = {
                            ej.correr(null, { api.put("/proveedores/$id/vinculo", JSONObject().put("preferido", !preferido)) }) { recargar() }
                        }, enabled = !ej.trabajando) {
                            Icon(Icons.Filled.Star, if (preferido) "Preferido" else "Marcar como preferido",
                                tint = if (preferido) Tono.AVISO.colores().contenido else MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Text(
                        "Entrega: " + p.getJSONArray("modalidades").let { m -> (0 until m.length()).joinToString(" · ") { modalidadLegible(m.getString(it)) } },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vinculado) "Trabajo con este proveedor" else "No trabajo con él", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vinculado, enabled = !ej.trabajando, onCheckedChange = { si ->
                            ej.correr(if (si) "Ahora ${p.getString("nombre")} recibirá tus solicitudes" else "Ya no se le invitará a cotizar", {
                                if (si) api.put("/proveedores/$id/vinculo", JSONObject().put("preferido", false)) else api.delete("/proveedores/$id/vinculo")
                            }) { recargar() }
                        })
                    }
                }
            }
        }
    }
}

/** Establecimientos de la empresa: crear, ubicar en el mapa y ver si les falta la ubicación (la ruta la necesita). */
@Composable
private fun ColumnScope.SitiosEmpresa(api: Api, nav: Navegacion) {
    val ej = rememberEjecutor()
    var eliminar by remember { mutableStateOf<JSONObject?>(null) }
    Cargador("sitios", { api.obj("/establecimientos?size=200&sort=nombre").getJSONArray("content").objetos() }) { lista, recargar ->
        Column(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                ej.aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { ej.aviso = null }) } } }
                itemsIndexed(lista, key = { _, e -> e.getString("id") }) { i, e ->
                    val sinUbicacion = e.isNull("lat")
                    Tarjeta(Modifier.animateItem().entrada(i), alTocar = { nav.ir(Pantalla.Sitio(e.getString("id"), e.getString("nombre"))) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconoRedondo(Icons.Filled.Place, if (sinUbicacion) Tono.AVISO else Tono.INFO)
                            Column(Modifier.weight(1f)) {
                                Text(e.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(e.getString("tipo").legible(), e.texto("direccion")).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!e.getBoolean("activo")) Etiqueta("Inactivo", Tono.NEUTRO)
                            // Solo quien lo creó (o el administrador de la plataforma) lo puede eliminar.
                            if (e.optBoolean("puedeEliminar")) IconButton(onClick = { eliminar = e }, enabled = !ej.trabajando) {
                                Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (sinUbicacion) Etiqueta("Sin ubicación en el mapa: no entra en las rutas", Tono.AVISO)
                    }
                }
            }
            Button(onClick = { nav.ir(Pantalla.Sitio(null, "Nuevo establecimiento")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("Nuevo establecimiento", Modifier.padding(start = 6.dp))
            }
        }
        eliminar?.let { e ->
            DialogoEliminarEstablecimiento(e, alCerrar = { eliminar = null }) {
                eliminar = null
                ej.correr("${e.getString("nombre")} eliminado", { api.delete("/establecimientos/${e.getString("id")}") }) { recargar() }
            }
        }
    }
}

/** Confirmación antes de eliminar un establecimiento: no se puede deshacer desde la app. */
@Composable
fun DialogoEliminarEstablecimiento(e: JSONObject, alCerrar: () -> Unit, alConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("¿Eliminar ${e.getString("nombre")}?") },
        text = {
            Text("Desaparece de la app, sus QR dejan de funcionar y sus encargados dejan de verlo. Los pedidos y movimientos " +
                "ya registrados se conservan. No se puede deshacer.")
        },
        confirmButton = {
            Button(onClick = alConfirmar, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

private val TIPOS_ESTABLECIMIENTO = listOf("OFICINA" to "Oficina", "HOSPITAL" to "Hospital", "FABRICA" to "Fábrica", "EDUCATIVO" to "Educativo", "OTRO" to "Otro")

/** Crear o editar un establecimiento: datos, ubicación en el mapa y pisos (la bodega se crea sola). */
@Composable
fun PantallaSitio(api: Api, nav: Navegacion, p: Pantalla.Sitio) {
    val ej = rememberEjecutor()
    Marco(p.nombre, subtitulo = if (p.id == null) "Se crea con su bodega" else "Datos y ubicación") {
        Cargador(p, {
            p.id?.let { api.obj("/establecimientos/$it") to api.arr("/establecimientos/$it/ubicaciones").objetos() }
                ?: (JSONObject() to emptyList())
        }) { (e, ubicaciones), recargar ->
            var nombre by remember(e) { mutableStateOf(e.optString("nombre")) }
            var tipo by remember(e) { mutableStateOf(e.optString("tipo").ifBlank { "OFICINA" }) }
            var entidad by remember(e) { mutableStateOf(e.texto("entidadContratante") ?: "") }
            var direccion by remember(e) { mutableStateOf(e.texto("direccion") ?: "") }
            var lat by remember(e) { mutableStateOf(e.texto("lat")?.toDouble()) }
            var lng by remember(e) { mutableStateOf(e.texto("lng")?.toDouble()) }
            val pisos = remember(e) { mutableStateListOf("Planta baja", "Piso 1") }
            var nuevoPiso by remember { mutableStateOf("") }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ej.aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { ej.aviso = null }) }
                Tarjeta(Modifier.entrada(0)) {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIPOS_ESTABLECIMIENTO.forEach { (k, t) -> FilterChip(selected = tipo == k, onClick = { tipo = k }, label = { Text(t) }) }
                    }
                    OutlinedTextField(entidad, { entidad = it }, label = { Text("Entidad contratante (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(direccion, { direccion = it }, label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Tarjeta(Modifier.entrada(1)) {
                    Text("Ubicación", style = MaterialTheme.typography.titleSmall)
                    Text("Toca el mapa donde está el establecimiento: la ruta de reparto llega a ese punto.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectorUbicacion(lat, lng, Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium)) { a, b -> lat = a; lng = b }
                    Text(if (lat != null) "Lat ${lat} · Lng ${lng}" else "Sin ubicación", style = MaterialTheme.typography.bodySmall)
                }
                Tarjeta(Modifier.entrada(2)) {
                    Text(if (p.id == null) "Pisos o áreas" else "Pisos y bodega", style = MaterialTheme.typography.titleSmall)
                    if (p.id == null) {
                        pisos.forEachIndexed { i, piso ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• $piso", modifier = Modifier.weight(1f))
                                TextButton(onClick = { pisos.removeAt(i) }) { Text("Quitar") }
                            }
                        }
                    } else {
                        ubicaciones.forEach { u -> Text("• ${u.getString("nombre")} (${u.getString("tipo").legible().lowercase()})") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(nuevoPiso, { nuevoPiso = it }, label = { Text("Nuevo piso o área") }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = {
                            val n = nuevoPiso.trim()
                            if (n.isEmpty()) return@FilledTonalButton
                            if (p.id == null) { pisos += n; nuevoPiso = "" } else ej.correr("Piso agregado: tendrá su propio QR", {
                                api.post("/establecimientos/${p.id}/ubicaciones", JSONObject().put("nombre", n).put("tipo", "PISO"))
                            }) { nuevoPiso = ""; recargar() }
                        }, enabled = !ej.trabajando) { Text("Agregar") }
                    }
                }
            }
            Button(
                onClick = {
                    val cuerpo = JSONObject().put("nombre", nombre.trim()).put("tipo", tipo)
                        .put("entidadContratante", entidad.ifBlank { JSONObject.NULL }).put("direccion", direccion.ifBlank { JSONObject.NULL })
                        .put("lat", lat ?: JSONObject.NULL).put("lng", lng ?: JSONObject.NULL)
                    if (p.id == null) {
                        ej.correr(null, {
                            val creado = api.post("/establecimientos", cuerpo)
                            pisos.forEach { api.post("/establecimientos/${creado.getString("id")}/ubicaciones", JSONObject().put("nombre", it).put("tipo", "PISO")) }
                        }) { nav.atras() }
                    } else {
                        ej.correr("Cambios guardados", { api.put("/establecimientos/${p.id}", cuerpo) }) { recargar() }
                    }
                },
                enabled = nombre.isNotBlank() && !ej.trabajando,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
            ) {
                if (ej.trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (p.id == null) "Crear establecimiento" else "Guardar cambios")
            }
        }
    }
}

// ======================= Proveedor =======================

/** Pestaña "Gestión" del proveedor: su equipo (ventas y repartidores) y su perfil de entrega. */
@Composable
fun PantallaGestionProveedor(api: Api, sesion: Sesion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    var vista by remember { mutableStateOf("equipo") }
    Marco("Gestión", subtitulo = "Equipo y forma de entrega", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Row(Modifier.entrada(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vista == "equipo", onClick = { vista = "equipo" }, label = { Text("Equipo") })
            FilterChip(selected = vista == "perfil", onClick = { vista = "perfil" }, label = { Text("Perfil") })
        }
        if (vista == "equipo") EquipoProveedor(api, sesion) else PerfilProveedor(api)
    }
}

@Composable
private fun ColumnScope.EquipoProveedor(api: Api, sesion: Sesion) {
    val ej = rememberEjecutor()
    var nueva by remember { mutableStateOf(false) }
    var credencial by remember { mutableStateOf<JSONObject?>(null) }
    Cargador("equipo-proveedor", { api.arr("/proveedor/equipo").objetos() }) { personas, recargar ->
        Column(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                ej.aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { ej.aviso = null }) } } }
                item(key = "resumen") {
                    Seccion("${personas.count { it.getString("rol") == "REPARTIDOR" && it.getBoolean("activo") }} repartidores activos")
                }
                itemsIndexed(personas, key = { _, u -> u.getString("id") }) { i, u ->
                    val id = u.getString("id")
                    TarjetaPersona(
                        u, Modifier.animateItem().entrada(1 + i),
                        detalle = if (u.getString("rol") == "REPARTIDOR") "Ve sus rutas y marca las entregas" else "Cotiza y gestiona las órdenes",
                        trabajando = ej.trabajando,
                        alCambiarActivo = { activo ->
                            ej.correr(if (activo) "${u.getString("nombre")} activado" else "${u.getString("nombre")} desactivado",
                                { api.patch("/proveedor/equipo/$id", JSONObject().put("activo", activo)) }) { recargar() }
                        },
                        alRestablecer = { ej.correr(null, { api.post("/proveedor/equipo/$id/restablecer-clave") }) { credencial = it; recargar() } },
                    )
                }
            }
            Button(onClick = { nueva = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("Agregar repartidor o vendedor", Modifier.padding(start = 6.dp))
            }
        }
        if (nueva) {
            DialogoNuevaPersona(
                "Nueva persona", listOf("REPARTIDOR" to "Repartidor", "PROVEEDOR" to "Ventas"), alCerrar = { nueva = false },
            ) { nombre, email, rol ->
                nueva = false
                ej.correr(null, { api.post("/proveedor/equipo", JSONObject().put("nombre", nombre).put("email", email).put("rol", rol)) }) {
                    credencial = it
                    recargar()
                }
            }
        }
        credencial?.let { DialogoCredencial(it, sesion.servidor) { credencial = null } }
    }
}

@Composable
private fun ColumnScope.PerfilProveedor(api: Api) {
    val ej = rememberEjecutor()
    Cargador("perfil-proveedor", { api.obj("/proveedor/perfil") }) { p, recargar ->
        FormularioProveedor(p, ej.trabajando, ej.aviso, alCerrarAviso = { ej.aviso = null }, textoBoton = "Guardar perfil", conCuenta = false) { cuerpo ->
            ej.correr("Perfil actualizado", { api.put("/proveedor/perfil", cuerpo) }) { recargar() }
        }
    }
}

/**
 * Datos de un proveedor: teléfono, local y bodega (con su punto en el mapa) y formas de entrega.
 * Lo usan el proveedor (su perfil) y el administrador del sistema (alta, con [conCuenta] = nombre/RUC/usuario).
 */
@Composable
fun ColumnScope.FormularioProveedor(
    p: JSONObject,
    trabajando: Boolean,
    aviso: Pair<String, Tono>?,
    alCerrarAviso: () -> Unit,
    textoBoton: String,
    conCuenta: Boolean,
    alGuardar: (JSONObject) -> Unit,
) {
    var nombre by remember(p) { mutableStateOf(p.optString("nombre")) }
    var ruc by remember(p) { mutableStateOf(p.optString("ruc")) }
    var telefono by remember(p) { mutableStateOf(p.texto("telefono") ?: "") }
    var direccion by remember(p) { mutableStateOf(p.optString("direccion")) }
    var lat by remember(p) { mutableStateOf(p.texto("lat")?.toDouble()) }
    var lng by remember(p) { mutableStateOf(p.texto("lng")?.toDouble()) }
    var tieneBodega by remember(p) { mutableStateOf(!p.isNull("bodegaDireccion") && p.has("bodegaDireccion")) }
    var bodega by remember(p) { mutableStateOf(p.texto("bodegaDireccion") ?: "") }
    var bLat by remember(p) { mutableStateOf(p.texto("bodegaLat")?.toDouble()) }
    var bLng by remember(p) { mutableStateOf(p.texto("bodegaLng")?.toDouble()) }
    val modalidades = remember(p) {
        mutableStateListOf<String>().apply {
            addAll(p.optJSONArray("modalidades")?.let { m -> (0 until m.length()).map { m.getString(it) } } ?: listOf("ENVIO_DIRECTO"))
        }
    }
    var usuarioNombre by remember { mutableStateOf("") }
    var usuarioEmail by remember { mutableStateOf("") }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = alCerrarAviso) }
        Tarjeta {
            if (conCuenta) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Razón social") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ruc, { ruc = it.filter(Char::isDigit).take(13) }, label = { Text("RUC (13 dígitos)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            } else {
                Text(nombre, style = MaterialTheme.typography.titleLarge)
                Text("RUC $ruc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(telefono, { telefono = it }, label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }
        Tarjeta {
            Text("¿Cómo entregas?", style = MaterialTheme.typography.titleSmall)
            listOf("ENVIO_DIRECTO", "RETIRO_LOCAL", "RETIRO_BODEGA").forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = m in modalidades, onCheckedChange = { if (it) modalidades += m else modalidades -= m })
                    Text(modalidadLegible(m))
                }
            }
        }
        Tarjeta {
            Text("Local", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(direccion, { direccion = it }, label = { Text("Dirección del local") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SelectorUbicacion(lat, lng, Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)) { a, b -> lat = a; lng = b }
            Text(if (lat != null) "Ubicado en el mapa" else "Toca el mapa para ubicar el local", style = MaterialTheme.typography.bodySmall)
        }
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bodega centralizada", style = MaterialTheme.typography.titleSmall)
                    Text("De aquí salen los envíos; también se puede retirar.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = tieneBodega, onCheckedChange = { tieneBodega = it; if (!it) modalidades -= "RETIRO_BODEGA" })
            }
            if (tieneBodega) {
                OutlinedTextField(bodega, { bodega = it }, label = { Text("Dirección de la bodega") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectorUbicacion(bLat, bLng, Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)) { a, b -> bLat = a; bLng = b }
            }
        }
        if (conCuenta) Tarjeta {
            Text("Primer usuario (ventas)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(usuarioNombre, { usuarioNombre = it }, label = { Text("Nombre y apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(usuarioEmail, { usuarioEmail = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Text("Recibirá una clave temporal para compartirle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Button(
        onClick = {
            val cuerpo = JSONObject()
                .put("telefono", telefono)
                .put("direccion", direccion)
                .put("lat", lat ?: JSONObject.NULL).put("lng", lng ?: JSONObject.NULL)
                .put("bodegaDireccion", if (tieneBodega) bodega else "")
                .put("bodegaLat", if (tieneBodega) bLat ?: JSONObject.NULL else JSONObject.NULL)
                .put("bodegaLng", if (tieneBodega) bLng ?: JSONObject.NULL else JSONObject.NULL)
                .put("modalidades", JSONArray(modalidades.toList()))
            if (conCuenta) cuerpo.put("nombre", nombre).put("ruc", ruc).put("usuarioNombre", usuarioNombre).put("usuarioEmail", usuarioEmail)
            alGuardar(cuerpo)
        },
        enabled = !trabajando && modalidades.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
    ) {
        if (trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(textoBoton)
    }
}
