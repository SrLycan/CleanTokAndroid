package ec.cleantok.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Notificaciones de la app (la campana): por ejemplo, "un proveedor te envió su cotización".
 * Se consultan cada [INTERVALO_MS] mientras la app está abierta; cada aviso nuevo se muestra además como
 * notificación del sistema, y al tocarla se abre lo que corresponde (la solicitud para comparar ofertas).
 */
class Avisos(private val api: Api, private val contexto: Context) {
    var noLeidas by mutableIntStateOf(0)
        private set
    var recientes by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    /** Solo se muestra la campana a quien recibe avisos (o ya tiene alguno). */
    fun visiblePara(rol: String?): Boolean = rol == "ADMIN_EMPRESA" || recientes.isNotEmpty()

    suspend fun actualizar() {
        enFondo { api.obj("/notificaciones") }.onSuccess { r ->
            noLeidas = r.getInt("noLeidas")
            recientes = r.getJSONArray("recientes").objetos()
            notificarNuevas(recientes.filter { !it.getBoolean("leida") })
        }
    }

    suspend fun leer(n: JSONObject) {
        if (n.getBoolean("leida")) return
        enFondo { api.post("/notificaciones/${n.getString("id")}/leer") }
        actualizar()
    }

    suspend fun leerTodas() {
        enFondo { api.post("/notificaciones/leer-todas") }
        actualizar()
    }

    fun limpiar() {
        noLeidas = 0
        recientes = emptyList()
    }

    /** Notificación del sistema por cada aviso sin leer que todavía no se mostró (las 3 más recientes). */
    private fun notificarNuevas(sinLeer: List<JSONObject>) {
        val prefs = contexto.getSharedPreferences("cleantok_avisos", Context.MODE_PRIVATE)
        val mostradas = prefs.getStringSet("mostradas", emptySet()).orEmpty()
        val nuevas = sinLeer.filter { it.getString("id") !in mostradas }.take(3)
        if (nuevas.isEmpty()) return
        prefs.edit().putStringSet("mostradas", (mostradas + nuevas.map { it.getString("id") }).toList().takeLast(200).toSet()).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanal(contexto)
        val gestor = NotificationManagerCompat.from(contexto)
        nuevas.forEach { n ->
            val abrir = Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_AVISO, n.toString())
            val pendiente = PendingIntent.getActivity(
                contexto, n.getString("id").hashCode(), abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val aviso = NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(n.getString("titulo"))
                .setContentText(n.getString("mensaje"))
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.getString("mensaje")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendiente)
                .setAutoCancel(true)
                .build()
            runCatching { gestor.notify(n.getString("id").hashCode(), aviso) }
        }
    }

    companion object {
        const val INTERVALO_MS = 30_000L
        const val EXTRA_AVISO = "ec.cleantok.app.AVISO"
        private const val CANAL = "avisos"

        private fun crearCanal(contexto: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val gestor = contexto.getSystemService(NotificationManager::class.java)
            if (gestor.getNotificationChannel(CANAL) == null) {
                gestor.createNotificationChannel(
                    NotificationChannel(CANAL, "Cotizaciones y pedidos", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Avisos como \"un proveedor te envió su cotización\""
                    },
                )
            }
        }
    }
}

val LocalAvisos = staticCompositionLocalOf<Avisos?> { null }

/** Lo que abre una notificación: hoy, la solicitud cotizada. */
fun destinoDe(n: JSONObject): Pantalla? {
    val id = n.texto("referenciaId") ?: return null
    return when (n.texto("referenciaTipo")) {
        "SOLICITUD" -> Pantalla.Solicitud(id, n.texto("referenciaTexto") ?: "")
        "ORDEN" -> Pantalla.Orden(id, n.texto("referenciaTexto") ?: "")
        else -> null
    }
}

/** Campana de la barra superior, con la cantidad sin leer. */
@Composable
fun Campana(avisos: Avisos, alAbrir: () -> Unit) {
    IconButton(onClick = alAbrir) {
        BadgedBox(badge = { if (avisos.noLeidas > 0) Badge { Text(if (avisos.noLeidas > 9) "9+" else "${avisos.noLeidas}") } }) {
            Icon(Icons.Filled.Notifications, if (avisos.noLeidas > 0) "${avisos.noLeidas} notificaciones sin leer" else "Notificaciones")
        }
    }
}

/** Una notificación en una lista: título, mensaje y cuándo llegó; las sin leer resaltadas. */
@Composable
fun FilaAviso(n: JSONObject, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    val leida = n.getBoolean("leida")
    Tarjeta(modifier, alTocar = alTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoRedondo(Iconos.Tienda, if (leida) Tono.NEUTRO else Tono.AVISO)
            Column(Modifier.weight(1f)) {
                Text(n.getString("titulo"), style = MaterialTheme.typography.titleSmall)
                Text(n.getString("mensaje"), style = MaterialTheme.typography.bodySmall)
                Text(fechaCorta(n.getString("creadaEn")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!leida) Box(Modifier.size(10.dp)) { Badge() }
        }
    }
}

/** Todas las notificaciones; al tocar una se marca leída y se abre (p. ej. la solicitud para comparar ofertas). */
@Composable
fun PantallaNotificaciones(nav: Navegacion) {
    val avisos = LocalAvisos.current ?: return
    val scope = rememberCoroutineScope()
    Marco("Notificaciones", subtitulo = if (avisos.noLeidas > 0) "${avisos.noLeidas} sin leer" else "Todo leído", acciones = {
        if (avisos.noLeidas > 0) TextButton(onClick = { scope.launch { avisos.leerTodas() } }) { Text("Marcar leídas") }
    }) {
        val lista = avisos.recientes
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FinDeLista) {
            if (lista.isEmpty()) item(key = "vacio") {
                EstadoVacio(Icons.Filled.Notifications, "Sin notificaciones", "Aquí te avisamos cuando un proveedor te envíe su cotización.", Tono.NEUTRO)
            }
            itemsIndexed(lista, key = { _, n -> n.getString("id") }) { i, n ->
                FilaAviso(n, Modifier.animateItem().entrada(i)) {
                    scope.launch { avisos.leer(n) }
                    destinoDe(n)?.let { nav.ir(it) }
                }
            }
            item(key = "fin") { Spacer(Modifier.padding(4.dp)) }
        }
    }
}
