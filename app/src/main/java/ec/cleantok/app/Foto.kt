package ec.cleantok.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

/** Lado mayor con el que se sube la foto (el servidor la vuelve a reducir y le quita los metadatos). */
private const val LADO_MAXIMO = 1600

/**
 * Toma una foto con la cámara del teléfono y la sube a POST /fotos. Muestra la miniatura y permite cambiarla o quitarla.
 * [alCambiar] recibe el id de la foto subida, o null si se quitó.
 */
@Composable
fun BotonFoto(api: Api, texto: String, alCambiar: (String?) -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var miniatura by remember { mutableStateOf<Bitmap?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val destino = remember { archivoFoto(contexto) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        subiendo = true
        error = null
        scope.launch {
            enFondo {
                val bmp = reducir(destino.second)
                val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                bmp to api.subirFoto(jpeg)
            }.onSuccess { (bmp, id) ->
                miniatura = bmp
                alCambiar(id)
            }.onFailure { error = mensajeError(it) }
            subiendo = false
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) camara.launch(destino.first) else error = "Sin permiso de cámara no se puede tomar la foto"
    }
    fun tomar() {
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camara.launch(destino.first)
        else permiso.launch(Manifest.permission.CAMERA)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val m = miniatura
        if (m == null) {
            OutlinedButton(onClick = ::tomar, enabled = !subiendo, modifier = Modifier.fillMaxWidth()) {
                if (subiendo) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Iconos.Camara, null, Modifier.size(18.dp))
                Text(if (subiendo) "Subiendo…" else texto, Modifier.padding(start = 8.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    m.asImageBitmap(), "Foto", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text("Foto adjunta", style = MaterialTheme.typography.titleSmall)
                    Row {
                        TextButton(onClick = ::tomar, enabled = !subiendo) { Text("Cambiar") }
                        TextButton(onClick = { miniatura = null; alCambiar(null) }, enabled = !subiendo) { Text("Quitar") }
                    }
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** Archivo temporal en la caché, compartido con la app de cámara mediante FileProvider. */
private fun archivoFoto(contexto: Context): Pair<Uri, File> {
    val dir = File(contexto.cacheDir, "fotos").apply { mkdirs() }
    val archivo = File(dir, "foto-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo) to archivo
}

/** Decodifica la foto submuestreada para no cargar 12 MP en memoria. */
private fun reducir(archivo: File): Bitmap {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(archivo.path, limites)
    var muestra = 1
    while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
    return BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra })
        ?: error("No se pudo leer la foto")
}
