package ec.cleantok.app

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                                Text("Para el ${s.getString("fechaRequerida")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Etiqueta(estado, tono)
                        }
                        Text(items.joinToString(" · ") { "${it.getString("nombre")} ${num(it.get("cantidad"))} ${it.getString("unidad")}" }, style = MaterialTheme.typography.bodySmall)
                        Text("${destinos.size} destino${if (destinos.size == 1) "" else "s"}: ${destinos.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.optJSONObject("miOferta")?.let { Text("Tu oferta: ${dinero(it.get("total"))} · ${it.getInt("plazoDias")} día(s)", style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
    }
}

/** Formulario de oferta: por producto, si lo tiene, cuánto y a qué precio; más plazo y modalidad de entrega. */
@Composable
fun PantallaCotizar(api: Api, p: Pantalla.Cotizar) {
    val scope = rememberCoroutineScope()
    val ofrece = remember { mutableStateMapOf<String, Boolean>() }
    val disponibles = remember { mutableStateMapOf<String, String>() }
    val precios = remember { mutableStateMapOf<String, String>() }
    var modalidad by remember { mutableStateOf<String?>(null) }
    var plazo by remember { mutableIntStateOf(1) }
    var nota by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var iniciado by remember { mutableStateOf(false) }

    Marco("Cotizar ${p.codigo}", subtitulo = "Tu oferta") {
        Cargador(p, { api.obj("/proveedor/solicitudes/${p.id}") }) { s, recargar ->
            val items = s.getJSONArray("items").objetos()
            val mia = s.optJSONObject("miOferta")
            val modalidades = s.getJSONArray("modalidadesDisponibles").let { m -> (0 until m.length()).map { m.getString(it) } }
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
                modalidad = mia?.getString("modalidad") ?: modalidades.firstOrNull()
                plazo = mia?.getInt("plazoDias") ?: 1
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
                                    Text("Entregar hasta el ${s.getString("fechaRequerida")}", style = MaterialTheme.typography.bodySmall)
                                }
                                val (estado, tono) = estadoParaProveedor(s)
                                Etiqueta(estado, tono)
                            }
                            s.texto("nota")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    aviso?.let { (t, tono) -> item(key = "aviso") { Box(Modifier.animateItem()) { Aviso(t, tono, alCerrar = { aviso = null }) } } }
                    item(key = "entrega") {
                        Tarjeta(Modifier.entrada(1)) {
                            Text("¿Cómo entregas?", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                modalidades.forEach { m ->
                                    FilterChip(selected = modalidad == m, onClick = { modalidad = m }, enabled = editable, label = { Text(modalidadLegible(m)) })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Plazo de entrega", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                FilledTonalIconButton(onClick = { plazo = (plazo - 1).coerceAtLeast(0) }, enabled = editable && plazo > 0) { Icon(Iconos.Quitar, "Menos") }
                                Text("$plazo ${if (plazo == 1) "día" else "días"}", style = MaterialTheme.typography.titleMedium)
                                FilledTonalIconButton(onClick = { plazo = (plazo + 1).coerceAtMost(60) }, enabled = editable) { Icon(Icons.Filled.Add, "Más") }
                            }
                        }
                    }
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
                                    JSONObject().put("modalidad", modalidad).put("plazoDias", plazo).put("nota", nota.ifBlank { JSONObject.NULL }).put("items", arr),
                                )
                            }.onSuccess { aviso = "Oferta enviada. La empresa ya puede compararla." to Tono.OK; recargar() }
                                .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                            enviando = false
                        }
                    },
                    enabled = !enviando && modalidad != null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                ) {
                    if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text((if (mia == null) "Enviar oferta" else "Actualizar oferta") + " · ${dinero(total)}")
                }
            }
        }
    }
}
