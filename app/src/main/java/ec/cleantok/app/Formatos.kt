package ec.cleantok.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** 12.5 → "$12,50" */
fun dinero(v: Any?): String = runCatching {
    "$" + BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
}.getOrDefault("—")

fun modalidadLegible(m: String): String = when (m) {
    "ENVIO_DIRECTO" -> "Envío directo"
    "RETIRO_LOCAL" -> "Retiro en local"
    "RETIRO_BODEGA" -> "Retiro en bodega central"
    else -> m.legible()
}

/** Nombre corto del estado del pedido, como en un rastreo de courier. La orden aprobada ya está pagada: no se "acepta". */
fun estadoOrdenLegible(e: String): String = when (e) {
    "APROBADA" -> "Pagado · por despachar"
    "ACEPTADA" -> "Pagado · por despachar"
    "RECHAZADA" -> "Rechazado"
    "EN_PREPARACION" -> "En preparación"
    "EMPACADA" -> "Empacado"
    "LISTA_PARA_RETIRO" -> "Listo para retiro"
    "RETIRADA" -> "Retirado"
    "DESPACHADA" -> "Salió de bodega"
    "EN_TRANSITO" -> "En tránsito"
    "ENTREGADA" -> "Entregado"
    else -> e.legible()
}

fun tonoOrden(e: String): Tono = when (e) {
    "ENTREGADA" -> Tono.OK
    "RECHAZADA" -> Tono.CRITICO
    "DESPACHADA", "EN_TRANSITO", "LISTA_PARA_RETIRO", "RETIRADA" -> Tono.INFO
    "APROBADA", "ACEPTADA" -> Tono.AVISO
    else -> Tono.NEUTRO
}

fun estadoEntregaLegible(e: String): String = when (e) {
    "PENDIENTE" -> "Pendiente"
    "ENTREGADA" -> "Por confirmar"
    "RECIBIDA" -> "Recibido"
    else -> e.legible()
}

fun tonoEntrega(e: String): Tono = when (e) {
    "RECIBIDA" -> Tono.OK
    "ENTREGADA" -> Tono.AVISO
    else -> Tono.NEUTRO
}

fun estadoSolicitudLegible(e: String): String = when (e) {
    "BORRADOR" -> "Borrador"
    "EN_COTIZACION" -> "En cotización"
    "ADJUDICADA" -> "Aprobada"
    "CANCELADA" -> "Cancelada"
    else -> e.legible()
}

fun tonoSolicitud(e: String): Tono = when (e) {
    "EN_COTIZACION" -> Tono.AVISO
    "ADJUDICADA" -> Tono.OK
    "CANCELADA" -> Tono.CRITICO
    else -> Tono.NEUTRO
}

fun rolLegible(r: String): String = when (r) {
    "SUPER_ADMIN" -> "Administración del sistema"
    "ADMIN_EMPRESA" -> "Administración"
    "SUPERVISOR" -> "Supervisor de establecimiento"
    "ADMIN_CONTRATO" -> "Administrador de contrato"
    "ENCARGADO" -> "Encargado"
    "PROVEEDOR" -> "Proveedor"
    "REPARTIDOR" -> "Repartidor"
    else -> r.legible()
}

/** "13 oct 2026 · 10:09" sin el año si es este año. */
fun fechaCorta(iso: String?): String = iso?.let { fecha(it).replace(Regex(" \\d{4} ·"), " ·") } ?: "—"

fun JSONObject.nombreDe(campo: String): String = optJSONObject(campo)?.optString("nombre") ?: "—"
