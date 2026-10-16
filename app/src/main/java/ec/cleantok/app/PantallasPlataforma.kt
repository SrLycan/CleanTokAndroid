package ec.cleantok.app

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/** Pestaña "Resumen" del administrador del sistema: cifras de toda la plataforma. */
@Composable
fun PantallaResumenPlataforma(api: Api, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Plataforma", subtitulo = "Administración del sistema", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("resumen", { api.obj("/plataforma/resumen") }) { r, _ ->
            val porRol = r.getJSONObject("usuariosPorRol")
            val ordenes = r.getJSONObject("ordenesPorEstado")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item {
                    Row(Modifier.entrada(0), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cifra("Empresas", r.getLong("empresas"), Iconos.Limpieza, Modifier.weight(1f))
                        Cifra("Proveedores", r.getLong("proveedores"), Iconos.Tienda, Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.entrada(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cifra("Establecimientos", r.getLong("establecimientos"), Iconos.Piso, Modifier.weight(1f))
                        Cifra("Entregas recibidas", r.getLong("entregasRecibidas"), Iconos.Camion, Modifier.weight(1f))
                    }
                }
                item {
                    Tarjeta(Modifier.entrada(2)) {
                        Text("Usuarios activos por rol", style = MaterialTheme.typography.titleSmall)
                        val total = porRol.keys().asSequence().sumOf { porRol.getLong(it) }.coerceAtLeast(1)
                        porRol.keys().asSequence().sorted().forEach { rol -> Barra(rolLegible(rol), porRol.getLong(rol), total) }
                    }
                }
                item {
                    Tarjeta(Modifier.entrada(3)) {
                        Text("Órdenes de compra por estado", style = MaterialTheme.typography.titleSmall)
                        val total = ordenes.keys().asSequence().sumOf { ordenes.getLong(it) }.coerceAtLeast(1)
                        if (ordenes.length() == 0) Text("Todavía no hay órdenes", style = MaterialTheme.typography.bodySmall)
                        ordenes.keys().asSequence().sorted().forEach { e -> Barra(estadoOrdenLegible(e), ordenes.getLong(e), total) }
                        Text("${r.getLong("solicitudesEnCotizacion")} solicitudes en cotización", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cifra(rotulo: String, valor: Long, icono: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Tarjeta(modifier) {
        IconoRedondo(icono, Tono.INFO, tam = 36.dp)
        Text("${animadoDesdeCero(valor.toFloat()).roundToInt()}", style = MaterialTheme.typography.headlineMedium)
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Barra(rotulo: String, valor: Long, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(rotulo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("$valor", style = MaterialTheme.typography.labelLarge)
        }
        val avance = animadoDesdeCero(valor.toFloat() / total)
        LinearProgressIndicator(
            progress = { avance }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
    }
}

/** Pestaña "Empresas": alta de una empresa con su primer administrador; restablecer la clave de un admin. */
@Composable
fun PantallaEmpresasPlataforma(api: Api, sesion: Sesion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var nueva by remember { mutableStateOf(false) }
    var nuevoAdminDe by remember { mutableStateOf<JSONObject?>(null) }
    var credencial by remember { mutableStateOf<JSONObject?>(null) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var sitiosDe by remember { mutableStateOf<JSONObject?>(null) }
    fun correr(llamada: () -> JSONObject, alExito: (JSONObject) -> Unit) {
        scope.launch { enFondo(llamada).onSuccess(alExito).onFailure { aviso = mensajeError(it) to Tono.CRITICO } }
    }

    Marco("Empresas", subtitulo = "Empresas de limpieza en la plataforma", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("empresas", { api.arr("/plataforma/empresas").objetos() }) { lista, recargar ->
            Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                    aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }
                    itemsIndexed(lista, key = { _, e -> e.getString("id") }) { i, e ->
                        Tarjeta(Modifier.animateItem().entrada(i)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val activa = e.getBoolean("activo")
                                IconoRedondo(Iconos.Limpieza, if (activa) Tono.INFO else Tono.NEUTRO)
                                Column(Modifier.weight(1f)) {
                                    Text(e.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        listOfNotNull("RUC ${e.getString("ruc")}", e.texto("telefono"), e.texto("email")).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = activa, onCheckedChange = { si ->
                                    correr({ api.patch("/plataforma/empresas/${e.getString("id")}", JSONObject().put("activo", si)) }) {
                                        aviso = (if (si) "Empresa activada" else "Empresa desactivada: su gente ya no puede entrar") to Tono.OK
                                        recargar()
                                    }
                                })
                            }
                            Row {
                                Dato("Establecimientos", "${e.getLong("establecimientos")}", modifier = Modifier.weight(1f))
                                Dato("Usuarios", "${e.getLong("usuarios")}", modifier = Modifier.weight(1f))
                                Dato("Proveedores", "${e.getLong("proveedores")}", modifier = Modifier.weight(1f))
                            }
                            Text("Administradores", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            e.getJSONArray("admins").objetos().forEach { a ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.getString("nombre"), style = MaterialTheme.typography.bodyMedium)
                                        Text(a.getString("email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        correr({ api.post("/plataforma/usuarios/${a.getString("id")}/restablecer-clave") }) { credencial = it }
                                    }) { Text("Restablecer") }
                                }
                            }
                            val supervisores = e.optJSONArray("supervisores")?.objetos().orEmpty()
                            if (supervisores.isNotEmpty()) {
                                Text("Supervisores de establecimiento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                supervisores.forEach { s ->
                                    val sedes = s.optJSONArray("establecimientos")?.objetos().orEmpty()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.getString("nombre"), style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                if (sedes.isEmpty()) "Sin sede asignada" else sedes.joinToString(" · ") { it.getString("nombre") },
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = {
                                            correr({ api.post("/plataforma/usuarios/${s.getString("id")}/restablecer-clave") }) { credencial = it }
                                        }) { Text("Restablecer") }
                                    }
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { nuevoAdminDe = e }) { Text("Agregar administrador") }
                                TextButton(onClick = { sitiosDe = e }) { Text("Sedes y supervisores") }
                            }
                        }
                    }
                }
                Button(onClick = { nueva = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("Nueva empresa", Modifier.padding(start = 6.dp))
                }
            }
            if (nueva) DialogoNuevaEmpresa(alCerrar = { nueva = false }) { empresa, adminNombre, adminEmail ->
                nueva = false
                correr({
                    val emp = api.post("/empresas", empresa)
                    api.post("/empresas/${emp.getString("id")}/admins", JSONObject().put("nombre", adminNombre).put("email", adminEmail))
                }) { credencial = it; recargar() }
            }
            nuevoAdminDe?.let { e ->
                DialogoNuevaPersona("Administrador de ${e.getString("nombre")}", listOf("ADMIN_EMPRESA" to "Administración"), alCerrar = { nuevoAdminDe = null }) { n, m, _ ->
                    nuevoAdminDe = null
                    correr({ api.post("/empresas/${e.getString("id")}/admins", JSONObject().put("nombre", n).put("email", m)) }) { credencial = it; recargar() }
                }
            }
            credencial?.let { DialogoCredencial(it, sesion.servidor) { credencial = null } }
            sitiosDe?.let { e ->
                DialogoEstablecimientosEmpresa(api, e, alCerrar = { sitiosDe = null }) { texto -> aviso = texto to Tono.OK; recargar() }
            }
        }
    }
}

@Composable
private fun DialogoNuevaEmpresa(alCerrar: () -> Unit, alCrear: (empresa: JSONObject, adminNombre: String, adminEmail: String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var ruc by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var adminNombre by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nueva empresa de limpieza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Razón social") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ruc, { ruc = it.filter(Char::isDigit).take(13) }, label = { Text("RUC (13 dígitos)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Contacto (lo ve el proveedor)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(telefono, { telefono = it }, label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(correo, { correo = it.trim() }, label = { Text("Correo de la empresa") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(direccion, { direccion = it }, label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Primer administrador", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(adminNombre, { adminNombre = it }, label = { Text("Nombre y apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(adminEmail, { adminEmail = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Text("Después, la empresa ubica su dirección en el mapa (Gestión → Empresa), crea a sus encargados y elige sus proveedores.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val empresa = JSONObject().put("nombre", nombre.trim()).put("ruc", ruc)
                        .put("telefono", telefono.ifBlank { JSONObject.NULL }).put("email", correo.ifBlank { JSONObject.NULL })
                        .put("direccion", direccion.ifBlank { JSONObject.NULL })
                    alCrear(empresa, adminNombre.trim(), adminEmail)
                },
                enabled = nombre.isNotBlank() && ruc.length == 13 && adminNombre.isNotBlank() && "@" in adminEmail && (correo.isBlank() || "@" in correo),
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Pestaña "Proveedores": alta con su primer usuario de ventas, y activar o desactivar. */
@Composable
fun PantallaProveedoresPlataforma(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    Marco("Proveedores", subtitulo = "Distribuidores en la plataforma", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("proveedores-plataforma", { api.arr("/plataforma/proveedores").objetos() }) { lista, recargar ->
            Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                    aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }
                    itemsIndexed(lista, key = { _, x -> x.getJSONObject("proveedor").getString("id") }) { i, x ->
                        val p = x.getJSONObject("proveedor")
                        val activo = x.getBoolean("activo")
                        Tarjeta(Modifier.animateItem().entrada(i)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Iconos.Tienda, if (activo) Tono.INFO else Tono.NEUTRO)
                                Column(Modifier.weight(1f)) {
                                    Text(p.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                    Text("RUC ${p.getString("ruc")} · ${p.getString("direccion")}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = activo, onCheckedChange = { si ->
                                    scope.launch {
                                        enFondo { api.patch("/plataforma/proveedores/${p.getString("id")}", JSONObject().put("activo", si)) }
                                            .onSuccess { aviso = (if (si) "Proveedor activado" else "Proveedor desactivado: sale del directorio") to Tono.OK; recargar() }
                                            .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                                    }
                                })
                            }
                            Text(
                                "Entrega: " + p.getJSONArray("modalidades").let { m -> (0 until m.length()).joinToString(" · ") { modalidadLegible(m.getString(it)) } },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                Dato("Empresas", "${x.getInt("empresas")}", modifier = Modifier.weight(1f))
                                Dato("Usuarios", "${x.getLong("usuarios")}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Button(onClick = { nav.ir(Pantalla.NuevoProveedor) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("Nuevo proveedor", Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Alta de un proveedor: datos, local y bodega en el mapa, formas de entrega y su primer usuario de ventas. */
@Composable
fun PantallaNuevoProveedor(api: Api, sesion: Sesion, nav: Navegacion) {
    val scope = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var credencial by remember { mutableStateOf<JSONObject?>(null) }
    // Una sola instancia: el formulario recuerda lo escrito con esta clave (si cambiara, se vaciaría al redibujar).
    val vacio = remember { JSONObject() }
    Marco("Nuevo proveedor", subtitulo = "Se crea con su primer usuario de ventas") {
        FormularioProveedor(vacio, trabajando, aviso, alCerrarAviso = { aviso = null }, textoBoton = "Crear proveedor", conCuenta = true) { cuerpo ->
            trabajando = true
            scope.launch {
                enFondo { api.post("/plataforma/proveedores", cuerpo) }
                    .onSuccess { credencial = it.getJSONObject("usuario") }
                    .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                trabajando = false
            }
        }
        credencial?.let { DialogoCredencial(it, sesion.servidor) { credencial = null; nav.atras() } }
    }
}

/**
 * Sedes de una empresa con su supervisor de establecimiento: el administrador de la plataforma crea sedes
 * (asignando su supervisor), cambia el supervisor de cada una o la elimina.
 */
@Composable
private fun DialogoEstablecimientosEmpresa(api: Api, empresa: JSONObject, alCerrar: () -> Unit, alEliminar: (String) -> Unit) {
    val nav = LocalNav.current
    val ej = rememberEjecutor()
    var eliminar by remember { mutableStateOf<JSONObject?>(null) }
    var version by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(Icons.Filled.Place, null) },
        title = { Text("Sedes de ${empresa.getString("nombre")}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                ej.aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { ej.aviso = null }) }
                Cargador(empresa.getString("id") to version, { api.arr("/plataforma/empresas/${empresa.getString("id")}/establecimientos").objetos() }) { lista, _ ->
                    if (lista.isEmpty()) Text("No tiene establecimientos.", style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        lista.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.getString("nombre"), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(
                                            "Supervisor: " + (s.optJSONObject("supervisor")?.getString("nombre") ?: "sin asignar"),
                                            if (!s.getBoolean("activo")) "Inactivo" else null,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    alCerrar()
                                    nav?.ir(Pantalla.SedeEmpresa(empresa.getString("id"), empresa.getString("nombre"), s.getString("id")))
                                }) { Icon(Icons.Filled.Person, "Cambiar supervisor", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { eliminar = s }, enabled = !ej.trabajando) {
                                    Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = {
            TextButton(onClick = {
                alCerrar()
                nav?.ir(Pantalla.SedeEmpresa(empresa.getString("id"), empresa.getString("nombre")))
            }) { Text("Nueva sede") }
        },
    )
    eliminar?.let { s ->
        DialogoEliminarEstablecimiento(s, alCerrar = { eliminar = null }) {
            eliminar = null
            ej.correr(null, { api.delete("/establecimientos/${s.getString("id")}") }) {
                version++
                alEliminar("${s.getString("nombre")} eliminado")
            }
        }
    }
}
