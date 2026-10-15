package ec.cleantok.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Muestra la clave temporal de una cuenta recién creada o restablecida (no se vuelve a ver) y permite
 * compartirla por WhatsApp, correo, etc. Al entrar, la persona deberá cambiarla.
 */
@Composable
fun DialogoCredencial(usuario: JSONObject, servidor: String, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val clave = usuario.optString("claveTemporal")
    val mensaje = "Hola ${usuario.getString("nombre").substringBefore(' ')}, esta es tu cuenta de CleanTok " +
        "(${rolLegible(usuario.getString("rol")).lowercase()}):\n" +
        "Usuario: ${usuario.getString("email")}\nClave temporal: $clave\nServidor: $servidor\n" +
        "Al entrar te pedirá crear tu propia contraseña."
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text("Cuenta lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Entrégale estos datos a ${usuario.getString("nombre")}. La clave temporal no se vuelve a mostrar.")
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Usuario", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(usuario.getString("email"), style = MaterialTheme.typography.bodyLarge)
                        Text("Clave temporal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(clave, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp))
                    }
                }
                TextButton(onClick = {
                    (contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Clave", clave))
                }) { Text("Copiar clave") }
            }
        },
        confirmButton = {
            Button(onClick = {
                contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, mensaje)
                }, "Enviar acceso"))
            }) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Text("Compartir", Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Listo") } },
    )
}

/** Primer ingreso con clave temporal: crear la contraseña propia antes de usar la app. */
@Composable
fun PantallaCambiarClave(api: Api, sesion: Sesion, alSalir: () -> Unit, alTerminar: () -> Unit) {
    val scope = rememberCoroutineScope()
    val previa = Api.claveRecienIngresada
    var actual by remember { mutableStateOf(previa ?: "") }
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            IconoRedondo(Icons.Filled.Lock, tam = 72.dp, fondo = MaterialTheme.colorScheme.primary, tinte = MaterialTheme.colorScheme.onPrimary)
            Text("Crea tu contraseña", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Hola ${sesion.nombre.substringBefore(' ')}. Entraste con una clave temporal; elige una contraseña propia para continuar.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Tarjeta(Modifier.entrada(0)) {
                if (previa == null) {
                    OutlinedTextField(actual, { actual = it }, label = { Text("Clave temporal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    nueva, { nueva = it; error = null }, label = { Text("Contraseña nueva (mínimo 8)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar" else "Ver") } },
                )
                OutlinedTextField(
                    repetir, { repetir = it; error = null }, label = { Text("Repítela") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                error?.let { Aviso(it, Tono.CRITICO) }
                Button(
                    onClick = {
                        when {
                            nueva.length < 8 -> error = "Usa al menos 8 caracteres"
                            nueva != repetir -> error = "Las contraseñas no coinciden"
                            else -> {
                                enviando = true
                                scope.launch {
                                    enFondo { api.post("/auth/cambiar-clave", JSONObject().put("actual", actual).put("nueva", nueva)) }
                                        .onSuccess {
                                            sesion.debeCambiarClave = false
                                            Api.claveRecienIngresada = null
                                            alTerminar()
                                        }
                                        .onFailure { error = mensajeError(it) }
                                    enviando = false
                                }
                            }
                        }
                    },
                    enabled = !enviando, modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Guardar y continuar")
                }
            }
            TextButton(onClick = { scope.launch { enFondo { api.logout() }; alSalir() } }) { Text("Cerrar sesión") }
        }
    }
}

/** Una persona del equipo: rol, estado, sitios y acciones. */
@Composable
fun TarjetaPersona(
    u: JSONObject,
    modifier: Modifier,
    detalle: String?,
    trabajando: Boolean,
    alCambiarActivo: (Boolean) -> Unit,
    alRestablecer: () -> Unit,
    otraAccion: Pair<String, () -> Unit>? = null,
) {
    val activo = u.getBoolean("activo")
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Iniciales(u.getString("nombre"), tam = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(u.getString("nombre"), style = MaterialTheme.typography.titleMedium,
                    color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(u.getString("email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = activo, onCheckedChange = alCambiarActivo, enabled = !trabajando)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(rolLegible(u.getString("rol")), Tono.INFO)
            if (!activo) Etiqueta("Inactivo", Tono.NEUTRO)
            else if (u.optBoolean("debeCambiarClave")) Etiqueta("Clave temporal pendiente", Tono.AVISO)
        }
        detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            otraAccion?.let { (t, f) -> TextButton(onClick = f, enabled = !trabajando && activo) { Text(t) } }
            TextButton(onClick = alRestablecer, enabled = !trabajando && activo) { Text("Restablecer clave") }
        }
    }
}

/** Nombre, email y rol de una persona nueva; [extra] agrega campos (por ejemplo, sus sitios). */
@Composable
fun DialogoNuevaPersona(
    titulo: String,
    roles: List<Pair<String, String>>,
    alCerrar: () -> Unit,
    extra: @Composable (rol: String) -> Unit = {},
    alCrear: (nombre: String, email: String, rol: String) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(roles.first().first) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (roles.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { (k, t) -> FilterChip(selected = rol == k, onClick = { rol = k }, label = { Text(t) }) }
                }
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre y apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    email, { email = it.trim() }, label = { Text("Email (será su usuario)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                extra(rol)
                Text("Se generará una clave temporal para compartirle.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { alCrear(nombre.trim(), email, rol) }, enabled = nombre.isNotBlank() && "@" in email) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
