package ec.cleantok.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Estado de una solicitud desde el punto de vista del proveedor. */
private fun estadoParaProveedor(s: JSONObject): Pair<String, Tono> {
    val mia = s.optJSONObject("miOferta")
    return when {
        s.getBoolean("puedeOfertar") && mia == null -> "Por cotizar" to Tono.AVISO
        s.getBoolean("puedeOfertar") -> "Oferta enviada" to Tono.INFO
        mia?.getString("estado") == "ADJUDICADA" -> "Ganaste" to Tono.OK
        mia != null -> "No adjudicada" to Tono.NEUTRO
        else -> "Cerrada" to Tono.NEUTRO
    }
}

/** Pestaña "Cotizar" del proveedor: su perfil de entrega y las solicitudes a las que lo invitaron. */
@Composable
fun PantallaSolicitudesProveedor(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Cotizar", subtitulo = "Solicitudes de tus clientes", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Cargador("proveedor", { api.obj("/proveedor/perfil") to api.arr("/proveedor/solicitudes").objetos() }) { (perfil, lista), _ ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                item(key = "perfil") {
                    Row(Modifier.entrada(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconoRedondo(Iconos.Tienda, tam = 48.dp, fondo = MaterialTheme.colorScheme.primary, tinte = MaterialTheme.colorScheme.onPrimary)
                        Column(Modifier.weight(1f)) {
                            Text(perfil.getString("nombre"), style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Entregas: " + perfil.getJSONArray("modalidades").let { m -> (0 until m.length()).joinToString(" · ") { modalidadLegible(m.getString(it)) } },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val pendientes = lista.count { estadoParaProveedor(it).first == "Por cotizar" }
                if (pendientes > 0) item(key = "aviso") {
                    Aviso("Cotiza solo lo que puedes entregar: la empresa compara precio, disponibilidad y plazo.", Tono.AVISO,
                        titulo = "$pendientes solicitud${if (pendientes == 1) "" else "es"} por cotizar")
                }
                if (lista.isEmpty()) item(key = "vacio") { EstadoVacio(Iconos.Tienda, "Sin solicitudes", "Cuando una empresa pida cotización, aparecerá aquí.", Tono.NEUTRO) }
                itemsIndexed(lista, key = { _, s -> s.getString("id") }) { i, s ->
                    val (estado, tono) = estadoParaProveedor(s)
                    val items = s.getJSONArray("items").objetos()
                    val destinos = items.flatMap { it.getJSONArray("destinos").objetos().map { d -> d.nombreDe("establecimiento") } }.toSet()
                    Tarjeta(Modifier.animateItem().entrada(1 + i), alTocar = { nav.ir(Pantalla.Cotizar(s.getString("id"), s.getString("codigo"))) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${s.getString("codigo")} · ${s.nombreDe("empresa")}", style = MaterialTheme.typography.titleMedium)
                                Text("Entrega hasta el ${diaIso(s.getString("fechaRequerida"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Etiqueta(estado, tono)
                        }
                        Text(items.joinToString(" · ") { "${it.getString("nombre")} ${num(it.get("cantidad"))} ${it.getString("unidad")}" }, style = MaterialTheme.typography.bodySmall)
                        Text("${destinos.size} destino${if (destinos.size == 1) "" else "s"}: ${destinos.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.optJSONObject("miOferta")?.let { Text("Tu oferta: ${dinero(it.get("total"))}", style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
    }
}

/**
 * Formulario de oferta: por producto, si lo tiene, cuánto y a qué precio. La fecha de entrega la fija la empresa
 * (el proveedor no la cambia) y se muestra como información, junto con los datos y la ubicación de la empresa.
 */
@Composable
fun PantallaCotizar(api: Api, p: Pantalla.Cotizar) {
    val scope = rememberCoroutineScope()
    val ofrece = remember { mutableStateMapOf<String, Boolean>() }
    val disponibles = remember { mutableStateMapOf<String, String>() }
    val precios = remember { mutableStateMapOf<String, String>() }
    var nota by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var iniciado by remember { mutableStateOf(false) }

    Marco("Cotizar ${p.codigo}", subtitulo = "Tu oferta") {
        Cargador(p, { api.obj("/proveedor/solicitudes/${p.id}") }) { s, recargar ->
            val items = s.getJSONArray("items").objetos()
            val mia = s.optJSONObject("miOferta")
            if (!iniciado) {
                iniciado = true
                val previos = mia?.getJSONArray("items")?.objetos()?.associateBy { it.getString("itemId") }.orEmpty()
                items.forEach { item ->
                    val id = item.getString("id")
                    val previo = previos[id]
                    ofrece[id] = mia == null || previo != null
                    disponibles[id] = num(previo?.get("cantidadDisponible") ?: item.get("cantidad")).replace(',', '.')
                    precios[id] = previo?.let { num(it.get("precioUnitario")).replace(',', '.') } ?: ""
                }
                nota = mia?.texto("nota") ?: ""
            }
            val editable = s.getBoolean("puedeOfertar")
            val total = items.sumOf { item ->
                val id = item.getString("id")
                if (ofrece[id] != true) 0.0 else {
                    val d = disponibles[id]?.toDoubleOrNull() ?: 0.0
                    minOf(d, item.getDouble("cantidad")) * (precios[id]?.toDoubleOrNull() ?: 0.0)
                }
            }

            Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = FinDeLista) {
                    item(key = "cabecera") {
                        Tarjeta(Modifier.entrada(0)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.nombreDe("empresa"), style = MaterialTheme.typography.titleMedium)
                                    Text("Solicitud ${s.getString("codigo")}", style = MaterialTheme.typography.bodySmall)
                                }
                                val (estado, tono) = estadoParaProveedor(s)
                                Etiqueta(estado, tono)
                            }
                            s.texto("nota")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }
                    // Solo información: la fecha la fija la administración de la empresa.
                    item(key = "entrega") {
                        Tarjeta(Modifier.entrada(1)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Icons.Filled.DateRange, Tono.INFO)
                                Column(Modifier.weight(1f)) {
                                    Text("Entrega hasta el ${diaIso(s.getString("fechaRequerida"))}", style = MaterialTheme.typography.titleSmall)
                                    val dias = s.optInt("plazoDias")
                                    Text(
                                        (if (dias == 0) "Para hoy" else "En $dias ${if (dias == 1) "día" else "días"}") + " · fecha fijada por la empresa",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    s.optJSONObject("empresaContacto")?.let { c -> item(key = "empresa") { FichaEmpresaCliente(c, "Empresa que cotiza", Modifier.entrada(1)) } }
                    item(key = "productos") { Seccion("Productos (${items.size})") }
                    itemsIndexed(items, key = { _, it -> it.getString("id") }) { i, item ->
                        val id = item.getString("id")
                        val unidad = item.getString("unidad")
                        Tarjeta(Modifier.animateItem().entrada(2 + i)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                    Text("Piden ${num(item.get("cantidad"))} $unidad", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (ofrece[id] == true) "Lo tengo" else "No lo ofrezco", style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(end = 8.dp))
                                Switch(checked = ofrece[id] == true, onCheckedChange = { ofrece[id] = it }, enabled = editable)
                            }
                            Text(
                                item.getJSONArray("destinos").objetos().joinToString(" · ") { "${it.nombreDe("establecimiento")}: ${num(it.get("cantidad"))}" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ofrece[id] == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    disponibles[id] ?: "", { disponibles[id] = it.replace(',', '.') }, singleLine = true, enabled = editable,
                                    label = { Text("Disponible") }, suffix = { Text(unidad) }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                OutlinedTextField(
                                    precios[id] ?: "", { precios[id] = it.replace(',', '.') }, singleLine = true, enabled = editable,
                                    label = { Text("Precio") }, prefix = { Text("$") }, suffix = { Text("/$unidad") }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                            }
                        }
                    }
                    if (editable) item(key = "nota") {
                        OutlinedTextField(nota, { nota = it }, label = { Text("Nota para la empresa (opcional)") }, modifier = Modifier.fillMaxWidth())
                    }
                }

                if (editable) Button(
                    onClick = {
                        val arr = JSONArray()
                        for (item in items) {
                            val id = item.getString("id")
                            if (ofrece[id] != true) continue
                            val d = disponibles[id]?.toBigDecimalOrNull()
                            val pr = precios[id]?.toBigDecimalOrNull()
                            if (d == null || d.signum() <= 0 || pr == null || pr.signum() <= 0) {
                                aviso = "Completa disponible y precio de ${item.getString("nombre")}" to Tono.CRITICO
                                return@Button
                            }
                            arr.put(JSONObject().put("itemId", id).put("cantidadDisponible", d).put("precioUnitario", pr))
                        }
                        if (arr.length() == 0) { aviso = "Ofrece al menos un producto" to Tono.CRITICO; return@Button }
                        enviando = true
                        scope.launch {
                            enFondo {
                                api.put(
                                    "/proveedor/solicitudes/${p.id}/oferta",
                                    JSONObject().put("nota", nota.ifBlank { JSONObject.NULL }).put("items", arr),
                                )
                            }.onSuccess { aviso = "Oferta enviada. La empresa ya puede compararla." to Tono.OK; recargar() }
                                .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                            enviando = false
                        }
                    },
                    enabled = !enviando,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                ) {
                    if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text((if (mia == null) "Enviar oferta" else "Actualizar oferta") + " · ${dinero(total)}")
                }
            }
        }
    }
}

/**
 * Datos de la empresa cliente, como información para el proveedor: razón social, RUC, teléfono, correo, dirección
 * y su ubicación en el mapa. Teléfono, correo y mapa abren la app correspondiente.
 */
@Composable
fun FichaEmpresaCliente(c: JSONObject, titulo: String, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    fun abrir(uri: String) = runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    val lat = c.texto("lat")?.toDoubleOrNull()
    val lng = c.texto("lng")?.toDoubleOrNull()
    Tarjeta(modifier) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Limpieza, Tono.INFO)
            Column(Modifier.weight(1f)) {
                Text(c.getString("razonSocial"), style = MaterialTheme.typography.titleMedium)
                Text("RUC ${c.getString("ruc")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DatoContacto(Icons.Filled.Phone, "Teléfono", c.texto("telefono")) { abrir("tel:${it.filter { ch -> ch.isDigit() || ch == '+' }}") }
        DatoContacto(Icons.Filled.Email, "Correo", c.texto("email")) { abrir("mailto:$it") }
        DatoContacto(Icons.Filled.Place, "Dirección", c.texto("direccion"), null)
        if (lat != null && lng != null) {
            MapaPunto(lat, lng, c.getString("razonSocial"), c.texto("direccion"), Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium))
            TextButton(onClick = { abrir("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(c.getString("razonSocial"))})") }) {
                Icon(Iconos.Ruta, null, Modifier.size(18.dp))
                Text("Abrir en el mapa", Modifier.padding(start = 6.dp))
            }
        } else {
            Text("La empresa aún no registró su ubicación en el mapa.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DatoContacto(icono: ImageVector, rotulo: String, valor: String?, alTocar: ((String) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icono, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor ?: "No registrado", style = MaterialTheme.typography.bodyMedium,
                color = if (valor == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
        if (valor != null && alTocar != null) TextButton(onClick = { alTocar(valor) }) { Text(if (rotulo == "Teléfono") "Llamar" else "Escribir") }
    }
}
