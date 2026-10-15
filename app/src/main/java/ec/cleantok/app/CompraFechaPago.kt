package ec.cleantok.app

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// ---------------------------------------------------------------------------------------------------------------
// Fecha de la compra: el admin elige para cuándo necesita los productos, con fechas recomendadas según el consumo.
// ---------------------------------------------------------------------------------------------------------------

private val ZONA_ECUADOR: ZoneId = ZoneId.of("America/Guayaquil")

/** Plazo habitual de entrega de los proveedores (cleantok.abastecimiento.dias-entrega del backend). */
private const val DIAS_ENTREGA = 3

private val formatoDia = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es"))

/** 2026-10-18 → "sáb 18 oct" */
fun dia(f: LocalDate): String = f.format(formatoDia).replace(".", "")

/** "2026-10-18" (fecha ISO del backend) → "sáb 18 oct"; si no se puede leer, el texto tal cual. */
fun diaIso(iso: String): String = runCatching { dia(LocalDate.parse(iso)) }.getOrDefault(iso)

/** Producto que se va a pedir y cuántos días le quedan (null si no hay consumo para estimarlo). */
data class Cobertura(val producto: String, val dias: Double?)

private data class OpcionFecha(val titulo: String, val detalle: String, val fecha: LocalDate, val tono: Tono)

/**
 * Fechas sugeridas: la recomendada llega un día antes de que se agote el producto más urgente (como mínimo mañana);
 * además, urgente (mañana), el plazo habitual de entrega y una semana para comparar más ofertas.
 */
private fun opcionesFecha(coberturas: List<Cobertura>, hoy: LocalDate): List<OpcionFecha> {
    val manana = hoy.plusDays(1)
    val habitual = hoy.plusDays(DIAS_ENTREGA.toLong())
    val critico = coberturas.filter { it.dias != null }.minByOrNull { it.dias!! }
    val recomendada = if (critico != null) {
        val agota = hoy.plusDays(floor(critico.dias!!).toLong())
        OpcionFecha(
            "Recomendada",
            if (!agota.isAfter(hoy)) "${critico.producto} ya se agotó: lo antes posible"
            else "${critico.producto} se agota el ${dia(agota)}: llega un día antes",
            maxOf(manana, agota.minusDays(1)), Tono.OK,
        )
    } else {
        OpcionFecha("Recomendada", "Sin consumo registrado para estimar: plazo habitual de entrega", habitual, Tono.OK)
    }
    val resto = listOf(
        OpcionFecha("Urgente", "Mañana: menos proveedores alcanzan y el precio puede subir", manana, Tono.AVISO),
        OpcionFecha("Plazo habitual", "$DIAS_ENTREGA días: la mayoría de proveedores alcanza a ofertar", habitual, Tono.INFO),
        OpcionFecha("Con holgura", "Una semana: más tiempo para comparar ofertas", hoy.plusDays(7), Tono.NEUTRO),
    ).filter { it.fecha != recomendada.fecha }
    return listOf(recomendada) + resto.sortedBy { it.fecha }
}

private fun LocalDate.millisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Antes de enviar a cotizar: ¿para cuándo se necesita la compra? Muestra cuándo se agota cada producto,
 * fechas recomendadas y un calendario para elegir otra. Los proveedores ven esa fecha al ofertar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFechaCompra(coberturas: List<Cobertura>, enviando: Boolean, alCerrar: () -> Unit, alConfirmar: (LocalDate) -> Unit) {
    val hoy = remember { LocalDate.now(ZONA_ECUADOR) }
    val opciones = remember(coberturas) { opcionesFecha(coberturas, hoy) }
    var elegida by remember { mutableStateOf(opciones.first().fecha) }
    var otra by remember { mutableStateOf<LocalDate?>(null) }
    var calendario by remember { mutableStateOf(false) }
    val urgentes = remember(coberturas) { coberturas.filter { it.dias != null }.sortedBy { it.dias }.take(3) }
    val primeraEnAgotarse = urgentes.firstOrNull()?.let { it.producto to hoy.plusDays(floor(it.dias!!).toLong()) }

    AlertDialog(
        onDismissRequest = { if (!enviando) alCerrar() },
        icon = { Icon(Icons.Filled.DateRange, null) },
        title = { Text("¿Para cuándo lo necesitas?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fecha en que los productos deben llegar. Los proveedores la ven al cotizar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (urgentes.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Cuándo se agota", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            urgentes.forEach { c ->
                                val agota = hoy.plusDays(floor(c.dias!!).toLong())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.producto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(if (!agota.isAfter(hoy)) "Agotado" else dia(agota), style = MaterialTheme.typography.labelMedium,
                                        color = if (agota.isAfter(hoy.plusDays(DIAS_ENTREGA.toLong()))) Color.Unspecified else Tono.CRITICO.colores().contenido)
                                }
                            }
                        }
                    }
                }
                opciones.forEach { o ->
                    OpcionDeFecha(o.titulo, o.detalle, o.fecha, o.tono, elegida == o.fecha && otra == null) { elegida = o.fecha; otra = null }
                }
                OpcionDeFecha("Otra fecha", otra?.let { "Elegida en el calendario" } ?: "Elegir en el calendario", otra, Tono.NEUTRO, otra != null) {
                    calendario = true
                }
                // Avisos sobre la fecha elegida.
                if (primeraEnAgotarse != null && !primeraEnAgotarse.second.isAfter(hoy)) {
                    if (elegida.isAfter(hoy.plusDays(1))) Aviso("${primeraEnAgotarse.first} ya está agotado: cada día de espera es un día sin producto.", Tono.AVISO)
                } else if (primeraEnAgotarse != null && elegida.isAfter(primeraEnAgotarse.second)) {
                    Aviso("Llegaría después de que se agote ${primeraEnAgotarse.first} (${dia(primeraEnAgotarse.second)}).", Tono.CRITICO)
                } else if (elegida.isBefore(hoy.plusDays(DIAS_ENTREGA.toLong()))) {
                    Aviso("Menos que el plazo habitual de $DIAS_ENTREGA días: algunos proveedores podrían no alcanzar.", Tono.AVISO)
                }
            }
        },
        confirmButton = {
            Button(onClick = { alConfirmar(elegida) }, enabled = !enviando) {
                if (enviando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Pedir para el ${dia(elegida)}")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar, enabled = !enviando) { Text("Cancelar") } },
    )

    if (calendario) {
        val minimo = hoy.plusDays(1).millisUtc()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = elegida.millisUtc(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minimo
                override fun isSelectableYear(year: Int) = year >= hoy.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { ms ->
                        val f = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        otra = f; elegida = f
                    }
                    calendario = false
                }) { Text("Elegir") }
            },
            dismissButton = { TextButton(onClick = { calendario = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }
}

@Composable
private fun OpcionDeFecha(titulo: String, detalle: String, fecha: LocalDate?, tono: Tono, elegida: Boolean, alElegir: () -> Unit) {
    Surface(
        onClick = alElegir, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = if (elegida) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = elegida, onClick = alElegir, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall)
                    fecha?.let { Etiqueta(dia(it), tono) }
                }
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pago de demostración: tarjeta o transferencia. No hay pasarela: nada se cobra ni sale del teléfono.
// ---------------------------------------------------------------------------------------------------------------

/** Lo que se le paga a cada proveedor de la compra. */
data class PagoProveedor(val proveedor: String, val monto: Double)

/** Tarjeta que la demo siempre rechaza, para mostrar el caso de pago fallido. */
private const val TARJETA_RECHAZADA = "4000000000000002"

private enum class PasoPago { DATOS, PROCESANDO, APROBADO, RECHAZADO }

/**
 * Pago de la compra (demo, sin pasarela). Con tarjeta valida número (Luhn), vencimiento y CVV; con transferencia
 * muestra datos bancarios de ejemplo de cada proveedor y pide el número de comprobante.
 * [alPagar] recibe la descripción del pago, por ejemplo "Visa •••• 4242".
 */
@Composable
fun DialogoPago(referencia: String, pagos: List<PagoProveedor>, alCerrar: () -> Unit, alPagar: (String) -> Unit) {
    val total = pagos.sumOf { it.monto }
    var metodo by remember { mutableStateOf("tarjeta") }
    var paso by remember { mutableStateOf(PasoPago.DATOS) }
    var descripcion by remember { mutableStateOf("") }
    var rechazar by remember { mutableStateOf(false) }
    val autorizacion = remember { (100000..999999).random().toString(16).uppercase() }

    LaunchedEffect(paso) {
        if (paso == PasoPago.PROCESANDO) {
            delay(1800)
            paso = if (rechazar) PasoPago.RECHAZADO else PasoPago.APROBADO
        }
    }

    Dialog(
        onDismissRequest = { if (paso != PasoPago.PROCESANDO) alCerrar() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pagar compra", style = MaterialTheme.typography.titleLarge)
                        Text("Solicitud $referencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Etiqueta("DEMO · sin cobro real", Tono.AVISO)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(dinero(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }

                when (paso) {
                    PasoPago.DATOS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = metodo == "tarjeta", onClick = { metodo = "tarjeta" }, label = { Text("Tarjeta") },
                                leadingIcon = { Icon(Iconos.Tarjeta, null, Modifier.size(18.dp)) })
                            FilterChip(selected = metodo == "transferencia", onClick = { metodo = "transferencia" }, label = { Text("Transferencia") },
                                leadingIcon = { Icon(Iconos.Banco, null, Modifier.size(18.dp)) })
                        }
                        if (metodo == "tarjeta") {
                            PagoConTarjeta(total, alCancelar = alCerrar) { desc, rechazada ->
                                descripcion = desc; rechazar = rechazada; paso = PasoPago.PROCESANDO
                            }
                        } else {
                            PagoPorTransferencia(referencia, pagos, alCancelar = alCerrar) { desc ->
                                descripcion = desc; rechazar = false; paso = PasoPago.PROCESANDO
                            }
                        }
                    }
                    PasoPago.PROCESANDO -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(if (metodo == "tarjeta") "Procesando el pago…" else "Verificando la transferencia…", style = MaterialTheme.typography.bodyMedium)
                    }
                    PasoPago.APROBADO -> Resultado(true, "Pago aprobado", "$descripcion · autorización $autorizacion") {
                        Button(onClick = { alPagar(descripcion) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Emitir órdenes de compra") }
                    }
                    PasoPago.RECHAZADO -> Resultado(false, "Pago rechazado", "El banco no autorizó $descripcion (simulado). Prueba con otra tarjeta o por transferencia.") {
                        Button(onClick = { paso = PasoPago.DATOS }, modifier = Modifier.fillMaxWidth()) { Text("Intentar de nuevo") }
                        TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Resultado(ok: Boolean, titulo: String, detalle: String, botones: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val escala by animateFloatAsState(if (visible) 1f else 0.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "escala")
    val tono = if (ok) Tono.OK else Tono.CRITICO
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.scale(escala)) { IconoRedondo(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, tono, tam = 72.dp) }
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = tono.colores().contenido)
        Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        botones()
    }
}

@Composable
private fun PagoConTarjeta(total: Double, alCancelar: () -> Unit, alPagar: (descripcion: String, rechazada: Boolean) -> Unit) {
    var numero by remember { mutableStateOf("") }
    var titular by remember { mutableStateOf("") }
    var vence by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var intentado by remember { mutableStateOf(false) }

    val marca = marcaTarjeta(numero)
    val errNumero = when {
        numero.length < 15 -> "Escribe los ${if (marca == "AMEX") 15 else 16} dígitos"
        !luhn(numero) -> "Número de tarjeta no válido"
        else -> null
    }
    val errVence = venceError(vence)
    val errCvv = if (cvv.length < 3) "3 o 4 dígitos" else null
    val errTitular = if (titular.isBlank()) "Escribe el nombre como aparece en la tarjeta" else null
    val valido = listOf(errNumero, errVence, errCvv, errTitular).all { it == null }

    VistaTarjeta(numero, titular, vence, marca)
    OutlinedTextField(
        numero, { numero = it.filter(Char::isDigit).take(16) }, label = { Text("Número de tarjeta") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), visualTransformation = Agrupar(4, ' '),
        leadingIcon = { Icon(Iconos.Tarjeta, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = intentado && errNumero != null, supportingText = if (intentado && errNumero != null) ({ Text(errNumero) }) else null,
    )
    OutlinedTextField(
        titular, { titular = it.take(26) }, label = { Text("Titular") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = intentado && errTitular != null, supportingText = if (intentado && errTitular != null) ({ Text(errTitular) }) else null,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            vence, { vence = it.filter(Char::isDigit).take(4) }, label = { Text("Vence (MM/AA)") }, singleLine = true,
            modifier = Modifier.weight(1f), visualTransformation = FormatoVence,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = intentado && errVence != null, supportingText = if (intentado && errVence != null) ({ Text(errVence) }) else null,
        )
        OutlinedTextField(
            cvv, { cvv = it.filter(Char::isDigit).take(4) }, label = { Text("CVV") }, singleLine = true,
            modifier = Modifier.weight(1f), visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = intentado && errCvv != null, supportingText = if (intentado && errCvv != null) ({ Text(errCvv) }) else null,
        )
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tarjetas de prueba", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { numero = "4242424242424242"; titular = "EQUIPO CLEANTOK"; vence = "1230"; cvv = "123" }) { Text("Aprobada") }
                TextButton(onClick = { numero = TARJETA_RECHAZADA; titular = "EQUIPO CLEANTOK"; vence = "1230"; cvv = "123" }) { Text("Rechazada") }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Demo: los datos no se guardan ni se envían a ningún lado.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(
        onClick = {
            intentado = true
            if (valido) alPagar("${marcaLegible(marca)} •••• ${numero.takeLast(4)}", numero == TARJETA_RECHAZADA)
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Pagar ${dinero(total)}") }
    OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
}

/** Vista previa de la tarjeta con los colores de CleanTok (marino → azul). */
@Composable
private fun VistaTarjeta(numero: String, titular: String, vence: String, marca: String?) {
    val visible = (numero + "•".repeat((16 - numero.length).coerceAtLeast(0))).chunked(4).joinToString("  ")
    Box(
        Modifier.fillMaxWidth().height(180.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF15365C), Color(0xFF2476C8))), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 42.dp, height = 30.dp).background(Color(0xFFE8C46A), RoundedCornerShape(6.dp)))
                Spacer(Modifier.weight(1f))
                Text(marca?.let(::marcaLegible)?.uppercase() ?: "CLEANTOK PAY", color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.5.sp))
            }
            Text(visible, color = Color.White, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
            Row {
                Column(Modifier.weight(1f)) {
                    Text("TITULAR", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(titular.ifBlank { "NOMBRE APELLIDO" }.uppercase(), color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Column(Modifier.width(80.dp)) {
                    Text("VENCE", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    val mm = vence.take(2).padEnd(2, '•')
                    val aa = vence.drop(2).padEnd(2, '•')
                    Text("$mm/$aa", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PagoPorTransferencia(referencia: String, pagos: List<PagoProveedor>, alCancelar: () -> Unit, alPagar: (String) -> Unit) {
    var comprobante by remember { mutableStateOf("") }
    var intentado by remember { mutableStateOf(false) }
    val error = if (comprobante.trim().length < 4) "Escribe el número de comprobante (mínimo 4 caracteres)" else null

    Text("Transfiere a cada proveedor y escribe la referencia $referencia en el concepto. Datos bancarios de ejemplo.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    pagos.forEach { p ->
        val cuenta = cuentaDemo(p.proveedor)
        Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoRedondo(Iconos.Banco, Tono.INFO, tam = 32.dp)
                    Text(p.proveedor, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                    Text(dinero(p.monto), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                FilaDato("Banco", cuenta.getString("banco"))
                FilaDato("Cuenta corriente", cuenta.getString("cuenta"))
                FilaDato("RUC", cuenta.getString("ruc"))
                FilaDato("Concepto", referencia)
            }
        }
    }
    OutlinedTextField(
        comprobante, { comprobante = it.take(30) }, label = { Text("N.º de comprobante") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = intentado && error != null, supportingText = if (intentado && error != null) ({ Text(error) }) else null,
    )
    Button(
        onClick = { intentado = true; if (error == null) alPagar("Transferencia · comprobante ${comprobante.trim()}") },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Ya transferí") }
    OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
}

@Composable
private fun FilaDato(rotulo: String, valor: String) {
    Row {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

/** Banco y número de cuenta de ejemplo, siempre los mismos para el mismo proveedor. */
private fun cuentaDemo(proveedor: String): JSONObject {
    val h = abs(proveedor.hashCode().toLong())
    val bancos = listOf("Banco Pichincha", "Banco Guayaquil", "Produbanco", "Banco del Pacífico")
    return JSONObject()
        .put("banco", bancos[(h % bancos.size).toInt()])
        .put("cuenta", "22" + (h % 100_000_000).toString().padStart(8, '0'))
        .put("ruc", "17" + (h % 100_000_000).toString().padStart(8, '0') + "001")
}

private fun marcaTarjeta(n: String): String? = when {
    n.startsWith("4") -> "VISA"
    n.length >= 2 && n.take(2).toInt() in 51..55 -> "MASTERCARD"
    n.startsWith("34") || n.startsWith("37") -> "AMEX"
    n.startsWith("36") || n.startsWith("30") -> "DINERS"
    else -> null
}

private fun marcaLegible(m: String?): String = when (m) {
    "VISA" -> "Visa"
    "MASTERCARD" -> "Mastercard"
    "AMEX" -> "American Express"
    "DINERS" -> "Diners Club"
    else -> "Tarjeta"
}

/** Dígito verificador de Luhn: descarta números mal escritos. */
private fun luhn(n: String): Boolean = n.reversed().mapIndexed { i, c ->
    val d = c.digitToInt()
    if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
}.sum() % 10 == 0

private fun venceError(v: String): String? {
    if (v.length < 4) return "MM/AA"
    val mes = v.take(2).toInt()
    val anio = 2000 + v.drop(2).toInt()
    if (mes !in 1..12) return "Mes no válido"
    val hoy = LocalDate.now(ZONA_ECUADOR)
    return if (anio < hoy.year || (anio == hoy.year && mes < hoy.monthValue)) "Tarjeta vencida" else null
}

/** "4242424242424242" → "4242 4242 4242 4242" sin cambiar el texto guardado. */
private class Agrupar(private val cada: Int, private val separador: Char) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val n = text.length
        val salida = text.text.chunked(cada).joinToString(separador.toString())
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 0) 0 else offset + (offset - 1) / cada
            override fun transformedToOriginal(offset: Int) = (offset - offset / (cada + 1)).coerceIn(0, n)
        }
        return TransformedText(AnnotatedString(salida), mapa)
    }
}

/** "1230" → "12/30" */
private object FormatoVence : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val n = text.length
        val salida = if (n > 2) text.text.take(2) + "/" + text.text.drop(2) else text.text
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 2 || n <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = (if (offset <= 2) offset else offset - 1).coerceIn(0, n)
        }
        return TransformedText(AnnotatedString(salida), mapa)
    }
}

// ---- Registro local del pago (demo): se muestra en el detalle de la solicitud ya aprobada. ----

fun guardarPago(context: Context, solicitudId: String, descripcion: String, total: Double) {
    context.getSharedPreferences("cleantok_pagos", Context.MODE_PRIVATE).edit()
        .putString(solicitudId, JSONObject().put("descripcion", descripcion).put("total", total).put("en", Instant.now().toString()).toString())
        .apply()
}

fun pagoGuardado(context: Context, solicitudId: String): JSONObject? =
    context.getSharedPreferences("cleantok_pagos", Context.MODE_PRIVATE).getString(solicitudId, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
