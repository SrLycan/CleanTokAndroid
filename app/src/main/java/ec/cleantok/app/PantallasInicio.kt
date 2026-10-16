package ec.cleantok.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Usuarios de la demo: email y quién es (un ejemplo de cada rol de la cadena). */
private val usuariosDemo = listOf(
    "admin@brillototal.ec" to "Administración · Brillo Total",
    "supervisor@brillototal.ec" to "Supervisora de establecimiento · solo Hospital San Andrés",
    "contrato@brillototal.ec" to "Administradora de contrato · consumo por piso",
    "maria@brillototal.ec" to "Encargada · Brillo Total",
    "luis@brillototal.ec" to "Encargado · Brillo Total",
    "ventas@lahigiene.ec" to "Proveedor · Distribuidora La Higiene",
    "reparto@lahigiene.ec" to "Repartidor · Distribuidora La Higiene",
    "ventas@proclean.ec" to "Proveedor · ProClean Ecuador",
    "admin@andina.ec" to "Administración · Limpieza Andina",
    "supervisor@andina.ec" to "Supervisor de establecimiento · solo Planta Textil",
    "superadmin.demo@cleantok.ec" to "Administración del sistema · clave SuperAdmin2026!",
)

@Composable
fun PantallaLogin(sesion: Sesion, api: Api, alEntrar: () -> Unit) {
    var email by remember { mutableStateOf(sesion.email) }
    var clave by remember { mutableStateOf("Demo2026!") }
    var verClave by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun entrar() {
        if (cargando) return
        cargando = true
        error = null
        scope.launch {
            enFondo { api.login(email.trim(), clave) }
                .onSuccess { alEntrar() }
                .onFailure { error = mensajeError(it) }
            cargando = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.entrada(0).size(84.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Iconos.Limpieza, null, Modifier.size(46.dp)) }
            }
            Column(Modifier.entrada(1), horizontalAlignment = Alignment.CenterHorizontally) {
                // Mismo logotipo que la cabecera de la web: CLEANTOK en azul marino, espaciado.
                Text("CLEANTOK", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 3.sp), color = MaterialTheme.colorScheme.secondary)
                Text("Control de insumos de limpieza", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Tarjeta(Modifier.entrada(2)) {
                Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    clave, { clave = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { verClave = !verClave }) {
                            Icon(if (verClave) Iconos.Ocultar else Iconos.Ver, if (verClave) "Ocultar contraseña" else "Mostrar contraseña")
                        }
                    },
                    visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { entrar() }),
                )
                error?.let { Aviso(it, Tono.CRITICO, alCerrar = { error = null }) }
                Button(onClick = ::entrar, enabled = !cargando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (cargando) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                    else Text("Entrar", style = MaterialTheme.typography.titleSmall)
                }
            }

            Tarjeta(Modifier.entrada(3)) {
                Text("Usuarios de la demo", style = MaterialTheme.typography.titleSmall)
                Text("Toca uno para usarlo · clave Demo2026!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                usuariosDemo.forEach { (u, empresa) ->
                    val elegido = u == email.trim()
                    Surface(
                        color = if (elegido) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.clickable { email = u; clave = if (u.startsWith("superadmin")) "SuperAdmin2026!" else "Demo2026!"; error = null }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Iniciales(u.substringBefore('@'), tam = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(u, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(empresa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (elegido) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inicio del encargado: lo que quedó guardado sin conexión y sus establecimientos.
 * Escanear un QR está en el botón central de la barra inferior.
 */
@Composable
fun PantallaInicio(sesion: Sesion, api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit = {}) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendientes by remember { mutableIntStateOf(sesion.pendientes().length()) }
    var sincronizando by remember { mutableStateOf(false) }
    var codigoManual by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }

    fun abrirCodigo() {
        MainActivity.tokenDe(codigoManual)?.let { nav.ir(Pantalla.Qr(it)) }
            ?: Toast.makeText(contexto, "Código inválido", Toast.LENGTH_SHORT).show()
    }

    fun sincronizar() {
        val cola = sesion.pendientes()
        if (cola.length() == 0) { aviso = "No hay movimientos pendientes" to Tono.INFO; return }
        sincronizando = true
        scope.launch {
            enFondo { api.post("/sincronizacion/lote", JSONObject().put("items", cola)) }
                .onSuccess { r ->
                    sesion.vaciarCola()
                    pendientes = 0
                    val errores = r.getJSONArray("items").objetos().filter { it.optString("resultado") == "ERROR" }
                    aviso = ("${r.optInt("ok")} enviados, ${r.optInt("duplicados")} duplicados, ${r.optInt("errores")} errores" +
                        errores.joinToString("") { "\n• ${it.optString("codigo")}: ${it.optString("mensaje")}" }) to
                        (if (errores.isEmpty()) Tono.OK else Tono.AVISO)
                }
                .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
            sincronizando = false
        }
    }

    Marco("CleanTok", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(FinDeLista), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Saludo(api, Modifier.entrada(0))

            Tarjeta(Modifier.entrada(1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconoRedondo(Iconos.Sincronizar, if (pendientes > 0) Tono.AVISO else Tono.OK)
                    Column(Modifier.weight(1f)) {
                        Text("Movimientos sin enviar", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (pendientes > 0) "$pendientes guardado${if (pendientes == 1) "" else "s"} en el teléfono" else "Todo está al día",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = ::sincronizar, enabled = !sincronizando) {
                        if (sincronizando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sincronizar")
                    }
                }
                OutlinedTextField(
                    codigoManual, { codigoManual = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("¿Sin cámara? Pega el código QR") },
                    trailingIcon = { IconButton(onClick = ::abrirCodigo, enabled = codigoManual.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Abrir") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { abrirCodigo() }),
                )
            }

            // AnimatePresence: el aviso entra y sale con resorte; se recuerda el último para animar la salida.
            var ultimoAviso by remember { mutableStateOf(aviso) }
            if (aviso != null) ultimoAviso = aviso
            AnimatedVisibility(aviso != null, enter = fadeIn(resorte()) + expandVertically(resorte(IntSize.VisibilityThreshold)),
                exit = fadeOut(resorte()) + shrinkVertically(resorte(IntSize.VisibilityThreshold))) {
                ultimoAviso?.let { (texto, tono) -> Aviso(texto, tono, alCerrar = { aviso = null }) }
            }

            Seccion("Mis establecimientos", Modifier.entrada(2))
            ListaEstablecimientos(api, nav, esAdmin = false, primerIndice = 3)
        }
    }
}

/** "Hola, María" con el rol y la empresa. */
@Composable
fun Saludo(api: Api, modifier: Modifier = Modifier) {
    Cargador("me", { api.obj("/auth/me") }) { me, _ ->
        val u = me.getJSONObject("usuario")
        val nombre = u.getString("nombre")
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Iniciales(nombre)
            Column {
                Text("Hola, ${nombre.substringBefore(' ')}", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(rolLegible(u.getString("rol")), me.optJSONObject("empresa")?.optString("nombre")).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Pestaña "Mi sede" del supervisor de establecimiento: solo la sede (o sedes) que le asignaron, con sus pisos,
 * materiales, inventario, alertas y la hoja de QR. Escanear está en el botón central.
 */
@Composable
fun PantallaSupervisor(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Mi sede", subtitulo = "Supervisor de establecimiento", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(FinDeLista), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Saludo(api, Modifier.entrada(0))
            Aviso("Ves y controlas solo la sede que te asignó la administración.", Tono.INFO)
            ListaEstablecimientos(api, nav, esAdmin = true, primerIndice = 1)
        }
    }
}

/** Los establecimientos asignados (todos, para el admin), cada uno con su mapa y accesos. */
/** Pestaña "Sedes" del admin: sus establecimientos con mapa, pisos, materiales, inventario y alertas. */
@Composable
fun PantallaMisEstablecimientos(api: Api, nav: Navegacion, alSalir: () -> Unit, barra: @Composable () -> Unit) {
    Marco("Mis establecimientos", subtitulo = "Pisos, materiales, inventario y alertas", acciones = { BotonSalir(api, alSalir) }, barraInferior = barra) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(FinDeLista)) {
            ListaEstablecimientos(api, nav, esAdmin = true, primerIndice = 0)
        }
    }
}

@Composable
fun ListaEstablecimientos(api: Api, nav: Navegacion, esAdmin: Boolean, primerIndice: Int) {
    Cargador("mios", { api.arr("/establecimientos/mios").objetos() }) { lista, _ ->
        if (lista.isEmpty()) EstadoVacio(Icons.Filled.Place, "Sin establecimientos", "Aún no tienes establecimientos asignados.", Tono.NEUTRO)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lista.forEachIndexed { i, e -> TarjetaEstablecimiento(e, esAdmin, nav, Modifier.entrada(primerIndice + i)) }
        }
    }
}

/** Establecimiento: al tocar la cabecera se abre el mapa con su ubicación. */
@Composable
fun TarjetaEstablecimiento(e: JSONObject, esAdmin: Boolean, nav: Navegacion, modifier: Modifier = Modifier) {
    val id = e.getString("id")
    val nombre = e.getString("nombre")
    val direccion = e.texto("direccion")
    val lat = e.texto("lat")?.toDoubleOrNull()
    val lng = e.texto("lng")?.toDoubleOrNull()
    var verMapa by rememberSaveable(id) { mutableStateOf(false) }
    val giro by animateFloatAsState(if (verMapa) 180f else 0f, resorte(), label = "flecha")

    Tarjeta(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { verMapa = !verMapa },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconoRedondo(Icons.Filled.Place, if (lat == null) Tono.AVISO else Tono.INFO)
            Column(Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(e.getString("tipo").legible(), direccion).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.optJSONObject("supervisor")?.let {
                    Text("Supervisor: ${it.getString("nombre")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (esAdmin) IconButton(onClick = { nav.ir(Pantalla.HojaQr(id, nombre)) }) {
                Icon(Iconos.Qr, "Códigos QR", tint = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, if (verMapa) "Ocultar mapa" else "Ver en el mapa", Modifier.rotate(giro),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(verMapa, enter = fadeIn(resorte()) + expandVertically(resorte(IntSize.VisibilityThreshold)),
            exit = fadeOut(resorte()) + shrinkVertically(resorte(IntSize.VisibilityThreshold))) {
            if (lat != null && lng != null) {
                MapaPunto(lat, lng, nombre, direccion, Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium))
            } else {
                Aviso(
                    "Aún no tiene ubicación en el mapa." + if (LocalRol.current == "ADMIN_EMPRESA") " Agrégala en Gestión → Establecimientos." else " Pídesela a administración.",
                    Tono.AVISO,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Acceso(Iconos.Piso, "Pisos", Modifier.weight(1f)) { nav.ir(Pantalla.Ubicaciones(id, nombre)) }
            Acceso(Iconos.Bodega, "Materiales", Modifier.weight(1f)) { nav.ir(Pantalla.Bodega(id, nombre)) }
            Acceso(Iconos.Inventario, "Inventario", Modifier.weight(1f)) { nav.ir(Pantalla.Inventario(id, nombre)) }
            Acceso(Icons.Filled.Notifications, "Alertas", Modifier.weight(1f)) { nav.ir(Pantalla.Alertas(id, nombre)) }
        }
    }
}

/** Lista de bodega y pisos. El admin abre cada uno sin escanear y ve su código QR. */
@Composable
fun PantallaUbicaciones(api: Api, nav: Navegacion, p: Pantalla.Ubicaciones) {
    var verQr by remember { mutableStateOf<JSONObject?>(null) }
    Marco("Pisos y bodega", subtitulo = p.nombre) {
        Cargador(p, { api.arr("/establecimientos/${p.estId}/ubicaciones").objetos() }) { lista, _ ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
                if (lista.isNotEmpty() && lista.all { it.isNull("urlQr") }) item {
                    Aviso("Para registrar movimientos en un piso, escanea su QR con el botón central de abajo.", Tono.INFO)
                }
                if (lista.isEmpty()) item { EstadoVacio(Iconos.Piso, "Sin ubicaciones", tono = Tono.NEUTRO) }
                itemsIndexed(lista) { i, u ->
                    val url = u.texto("urlQr")
                    val esBodega = u.getString("tipo") == "BODEGA"
                    Tarjeta(Modifier.animateItem().entrada(i), alTocar = url?.let { { MainActivity.tokenDe(it)?.let { t -> nav.ir(Pantalla.Qr(t)) } } }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconoRedondo(if (esBodega) Iconos.Bodega else Iconos.Piso, if (esBodega) Tono.INFO else Tono.OK)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(u.getString("nombre"), style = MaterialTheme.typography.titleMedium)
                                Etiqueta(u.getString("tipo").legible(), if (esBodega) Tono.INFO else Tono.NEUTRO)
                            }
                            // Conteo físico del piso: lo que sube sale de la bodega, lo que baja es consumo.
                            if (!esBodega) TextButton(onClick = { nav.ir(Pantalla.Conteo(p.estId, u.getString("id"), u.getString("nombre"), esBodega = false)) }) {
                                Icon(Iconos.Conteo, null, Modifier.size(18.dp))
                                Text("Contar", Modifier.padding(start = 6.dp))
                            }
                            if (url != null) IconButton(onClick = { verQr = u }) { Icon(Iconos.Qr, "Ver código QR", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            verQr?.let { u -> DialogoQr(api, u, alHoja = { verQr = null; nav.ir(Pantalla.HojaQr(p.estId, p.nombre)) }) { verQr = null } }
        }
    }
}

/** El código QR de una ubicación, grande, para mostrarlo o escanearlo desde otro teléfono. */
@Composable
private fun DialogoQr(api: Api, u: JSONObject, alHoja: () -> Unit, alCerrar: () -> Unit) {
    val id = u.getString("id")
    var imagen by remember(id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        enFondo { api.bytes("/ubicaciones/$id/qr.png?tamano=600").let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            .onSuccess { imagen = it }
            .onFailure { error = mensajeError(it) }
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(u.getString("nombre")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    Box(contentAlignment = Alignment.Center) {
                        val img = imagen
                        val err = error
                        when {
                            img != null -> Image(img.asImageBitmap(), "Código QR de ${u.getString("nombre")}", Modifier.fillMaxWidth().padding(8.dp),
                                filterQuality = FilterQuality.None)
                            err != null -> Text(err, color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Text("Al escanearlo, el encargado registra movimientos y niveles de este lugar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = alHoja) { Text("Hoja para imprimir") } },
    )
}
