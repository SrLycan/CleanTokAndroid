package ec.cleantok.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Distribución de los QR (solo admin): el QR de cada ubicación, la hoja A4 imprimible para compartir
 * (correo, WhatsApp, impresora) y la regeneración de un QR perdido o dañado.
 */
@Composable
fun PantallaHojaQr(api: Api, p: Pantalla.HojaQr) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<String, Tono>?>(null) }
    var regenerar by remember { mutableStateOf<JSONObject?>(null) }

    Marco("Códigos QR", subtitulo = p.nombre) {
        Cargador(p, { api.arr("/establecimientos/${p.estId}/ubicaciones").objetos() }) { lista, recargar ->
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = FinDeLista) {
                item(span = { GridItemSpan(2) }) {
                    Column(Modifier.entrada(0), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Aviso(
                            "Imprime la hoja y pega cada QR en su piso o en la bodega. Al escanearlo, el encargado registra movimientos y niveles.",
                            Tono.INFO, titulo = "${lista.size} ubicaciones",
                        )
                        Button(
                            onClick = {
                                preparando = true
                                scope.launch {
                                    enFondo { guardarHoja(contexto, api.bytes("/establecimientos/${p.estId}/qr-hoja.pdf"), p.nombre) }
                                        .onSuccess { archivo -> compartirPdf(contexto, archivo, p.nombre) }
                                        .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                                    preparando = false
                                }
                            },
                            enabled = !preparando, modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (preparando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else {
                                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                                Text("Compartir o imprimir hoja PDF", Modifier.padding(start = 8.dp))
                            }
                        }
                        aviso?.let { (t, tono) -> Aviso(t, tono, alCerrar = { aviso = null }) }
                    }
                }
                itemsIndexed(lista, key = { _, u -> u.getString("id") + u.optString("urlQr") }) { i, u ->
                    TarjetaQr(api, u, Modifier.entrada(1 + i)) { regenerar = u }
                }
            }

            regenerar?.let { u ->
                AlertDialog(
                    onDismissRequest = { regenerar = null },
                    icon = { Icon(Icons.Filled.Refresh, null) },
                    title = { Text("¿Regenerar el QR de ${u.getString("nombre")}?") },
                    text = { Text("El QR impreso actual dejará de funcionar. Úsalo si se perdió o se dañó, y reemplázalo por el nuevo.") },
                    confirmButton = {
                        Button(onClick = {
                            regenerar = null
                            scope.launch {
                                enFondo { api.post("/ubicaciones/${u.getString("id")}/regenerar-qr") }
                                    .onSuccess { aviso = "QR nuevo para ${u.getString("nombre")}: imprímelo y reemplaza el anterior" to Tono.OK; recargar() }
                                    .onFailure { aviso = mensajeError(it) to Tono.CRITICO }
                            }
                        }) { Text("Regenerar") }
                    },
                    dismissButton = { TextButton(onClick = { regenerar = null }) { Text("Cancelar") } },
                )
            }
        }
    }
}

@Composable
private fun TarjetaQr(api: Api, u: JSONObject, modifier: Modifier, alRegenerar: () -> Unit) {
    var imagen by remember(u.optString("urlQr")) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(u.optString("urlQr")) {
        enFondo { api.bytes("/ubicaciones/${u.getString("id")}/qr.png?tamano=400").let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            .onSuccess { imagen = it }
    }
    val esBodega = u.getString("tipo") == "BODEGA"
    Tarjeta(modifier) {
        Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(contentAlignment = Alignment.Center) {
                val img = imagen
                if (img == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Image(img.asImageBitmap(), "QR de ${u.getString("nombre")}", Modifier.fillMaxWidth().padding(6.dp), filterQuality = FilterQuality.None)
            }
        }
        Text(u.getString("nombre"), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(u.getString("tipo").legible(), if (esBodega) Tono.INFO else Tono.NEUTRO)
            TextButton(onClick = alRegenerar) { Text("Regenerar") }
        }
    }
}

private fun guardarHoja(contexto: Context, pdf: ByteArray, nombre: String): File {
    val dir = File(contexto.cacheDir, "hojas").apply { mkdirs() }
    val seguro = nombre.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40)
    return File(dir, "qr-$seguro.pdf").apply { writeBytes(pdf) }
}

private fun compartirPdf(contexto: Context, archivo: File, nombre: String) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Códigos QR – $nombre")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartir hoja de QR"))
}
