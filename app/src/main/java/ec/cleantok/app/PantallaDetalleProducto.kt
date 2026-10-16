package ec.cleantok.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Lo que muestra el detalle: el producto en ese sitio, su sugerencia de compra (si hay) y quiénes lo atienden. */
private class DatosDetalle(
    val sitio: JSONObject,
    val item: JSONObject?,
    val sugerencia: JSONObject?,
    val encargados: List<JSONObject>,
)

private fun cargarDetalle(api: Api, p: Pantalla.DetalleProducto): DatosDetalle {
    val sitio = api.obj("/establecimientos/${p.estId}")
    val item = api.obj("/establecimientos/${p.estId}/inventario").getJSONArray("items").objetos()
        .firstOrNull { it.getJSONObject("producto").getString("id") == p.productoId }
    val sugerencia = api.arr("/abastecimiento/sugerencias").objetos().firstOrNull {
        it.getJSONObject("producto").getString("id") == p.productoId && it.getJSONObject("establecimiento").getString("id") == p.estId
    }
    val encargados = api.obj("/usuarios?size=200&sort=nombre").getJSONArray("content").objetos().filter { u ->
        u.getString("rol") == "ENCARGADO" && u.optBoolean("activo", true) &&
            u.optJSONArray("establecimientos")?.objetos().orEmpty().any { it.getString("id") == p.estId }
    }
    return DatosDetalle(sitio, item, sugerencia, encargados)
}

/** Detalle de un producto en un establecimiento: dónde está, cuánto hay por piso, quiénes son los encargados y botón para comprarlo. */
@Composable
fun PantallaDetalleProducto(api: Api, nav: Navegacion, p: Pantalla.DetalleProducto) {
    Marco(p.nombre, subtitulo = p.estNombre) {
        Cargador(p, { cargarDetalle(api, p) }) { d, _ ->
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(FinDeLista), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val item = d.item
                val sug = d.sugerencia
                val unidad = (item ?: sug)?.getJSONObject("producto")?.getString("unidad")?.lowercase() ?: ""
                val estado = (sug ?: item)?.getString("estado") ?: "SIN_DATOS"
                val tono = when (estado) { "CRITICO" -> Tono.CRITICO; "BAJO" -> Tono.AVISO; "OK" -> Tono.OK; else -> Tono.NEUTRO }

                // Resumen
                Tarjeta(Modifier.entrada(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconoRedondo(Iconos.Inventario, tono)
                        Column(Modifier.weight(1f)) {
                            Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(sug?.getString("motivo") ?: item?.texto("diasCobertura")?.let { "Cobertura de ${num(it)} días" }
                                ?: "Aún sin consumo suficiente para estimar",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Etiqueta(estado.legible(), tono)
                    }
                    if (item != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dato("Total", "${num(item.get("stockTotal"))} $unidad", Modifier.weight(1f))
                        Dato("Bodega", "${num(item.get("stockBodega"))} $unidad", Modifier.weight(1f))
                        Dato("Consumo 7 días", "${num(item.get("consumo7Dias"))} $unidad", Modifier.weight(1f))
                    }
                }

                // Lugar
                Seccion("Lugar", Modifier.entrada(1))
                Tarjeta(Modifier.entrada(1)) {
                    val direccion = d.sitio.texto("direccion")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconoRedondo(Icons.Filled.Place, Tono.INFO)
                        Column(Modifier.weight(1f)) {
                            Text(d.sitio.getString("nombre"), style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(d.sitio.texto("tipo")?.legible(), direccion).joinToString(" · ").ifEmpty { "Sin dirección" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val lat = d.sitio.texto("lat")?.toDoubleOrNull()
                    val lng = d.sitio.texto("lng")?.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        MapaPunto(lat, lng, d.sitio.getString("nombre"), direccion, Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium))
                    }
                }

                // Por piso
                Seccion("Por piso", Modifier.entrada(2))
                Tarjeta(Modifier.entrada(2)) {
                    val pisos = item?.getJSONArray("stockPisos")?.objetos().orEmpty()
                    if (pisos.isEmpty()) {
                        Text("Este producto no está en ningún piso.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    pisos.sortedBy { it.getDouble("cantidad") }.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val cant = s.getDouble("cantidad")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Iconos.Piso, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.getJSONObject("ubicacion").getString("nombre"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (cant <= 0.0) Etiqueta("Agotado", Tono.CRITICO)
                            else Text("${num(s.get("cantidad"))} $unidad", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                // Encargados
                Seccion("Encargados", Modifier.entrada(3))
                Tarjeta(Modifier.entrada(3)) {
                    if (d.encargados.isEmpty()) {
                        Text("No hay encargados asignados a este establecimiento. Asígnalos en Gestión → Equipo.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.encargados.forEachIndexed { i, u ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Iniciales(u.getString("nombre"), tam = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(u.getString("nombre"), style = MaterialTheme.typography.bodyLarge)
                                Text(u.getString("email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Compra
                when {
                    sug == null -> Aviso("No está en las sugerencias de compra (no está bajo, o lo provee la entidad contratante).", Tono.INFO)
                    !sug.isNull("yaPedido") -> Aviso("Ya está pedido en ${sug.getString("yaPedido")}.", Tono.INFO)
                    else -> {
                        Aviso("Se recomienda pedir +${num(sug.get("cantidadRecomendada"))} $unidad.", Tono.AVISO)
                        Button(
                            onClick = { nav.comprarProducto = p.productoId; nav.pestana = PESTANA_COMPRAS; nav.volverARaiz() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.ShoppingCart, null, Modifier.size(18.dp))
                            Text("Comprar este producto", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dato(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier) {
        Text(valor, style = MaterialTheme.typography.titleSmall)
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lista que abre cada indicador del panel: productos críticos, con stock bajo o alertas abiertas. */
@Composable
fun PantallaListaPanel(api: Api, nav: Navegacion, p: Pantalla.ListaPanel) {
    val titulo = when (p.tipo) { "CRITICO" -> "Productos críticos"; "BAJO" -> "Con stock bajo"; else -> "Alertas abiertas" }
    Marco(titulo, subtitulo = "Todos tus establecimientos") {
        Cargador(p, { cargarPanel(api) }) { d, _ ->
            if (p.tipo == "ALERTAS") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                    if (d.alertas.isEmpty()) item { EstadoVacio(Icons.Filled.Check, "Sin alertas abiertas") }
                    itemsIndexed(d.alertas) { i, a ->
                        val sitio = a.getJSONObject("sitio")
                        val alta = a.getString("severidad") == "ALTA"
                        Tarjeta(Modifier.entrada(i), alTocar = { nav.ir(Pantalla.Alertas(sitio.getString("id"), sitio.getString("nombre"))) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Icons.Filled.Notifications, if (alta) Tono.CRITICO else Tono.AVISO)
                                Column(Modifier.weight(1f)) {
                                    Text(a.getString("mensaje"), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(sitio.getString("nombre"), a.optJSONObject("ubicacion")?.getString("nombre"), fechaCorta(a.texto("creadaEn")))
                                        .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                val lista = d.productos.filter { it.getString("estado") == p.tipo }
                    .sortedBy { it.texto("diasCobertura")?.toDoubleOrNull() ?: Double.MAX_VALUE }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                    if (lista.isEmpty()) item { EstadoVacio(Icons.Filled.Check, "Nada por aquí", "Ningún producto está en este estado.") }
                    itemsIndexed(lista) { i, f ->
                        val prod = f.getJSONObject("producto")
                        val sitio = f.getJSONObject("sitio")
                        val unidad = prod.getString("unidad").lowercase()
                        val agotados = f.getJSONArray("stockPisos").objetos().filter { it.getDouble("cantidad") <= 0.0 }
                            .map { it.getJSONObject("ubicacion").getString("nombre") }
                        Tarjeta(Modifier.entrada(i), alTocar = {
                            nav.ir(Pantalla.DetalleProducto(sitio.getString("id"), sitio.getString("nombre"), prod.getString("id"), prod.getString("nombre")))
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoRedondo(Iconos.Inventario, if (p.tipo == "CRITICO") Tono.CRITICO else Tono.AVISO)
                                Column(Modifier.weight(1f)) {
                                    Text(prod.getString("nombre"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            sitio.getString("nombre"),
                                            "${num(f.get("stockTotal"))} $unidad",
                                            f.texto("diasCobertura")?.let { "${num(f.get("diasCobertura"))} días" },
                                            agotados.takeIf { it.isNotEmpty() }?.let { "Sin stock en: " + it.joinToString(", ") },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
