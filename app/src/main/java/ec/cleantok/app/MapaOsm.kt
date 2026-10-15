package ec.cleantok.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Trazado por calles de una ruta, con su distancia y duración reales (OSRM). */
class TrazadoVial(val puntos: List<GeoPoint>, val km: Double, val minutos: Int)

/**
 * Pide a OSRM (enrutador libre sobre OpenStreetMap) el camino por calles que pasa por [puntos] en ese orden.
 * Devuelve null si no hay conexión o el servicio no responde: el mapa dibuja líneas rectas.
 */
fun trazadoVial(puntos: List<GeoPoint>): TrazadoVial? = runCatching {
    if (puntos.size < 2) return null
    val coords = puntos.joinToString(";") { "${it.longitude},${it.latitude}" }
    val con = (URL("$SERVIDOR_OSRM/route/v1/driving/$coords?overview=full&geometries=geojson").openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 10000
        setRequestProperty("User-Agent", AGENTE)
    }
    val r = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
    val ruta = r.getJSONArray("routes").getJSONObject(0)
    val linea = ruta.getJSONObject("geometry").getJSONArray("coordinates")
    TrazadoVial(
        (0 until linea.length()).map { i -> linea.getJSONArray(i).let { GeoPoint(it.getDouble(1), it.getDouble(0)) } },
        ruta.getDouble("distance") / 1000,
        (ruta.getDouble("duration") / 60).toInt(),
    )
}.getOrNull()

private const val SERVIDOR_OSRM = "https://router.project-osrm.org"
private const val AGENTE = "CleanTok/0.1 (demo hackathon Expo Clean Ecuador)"

/** Una vez por proceso: identificación ante los servidores de OSM (su política lo exige) y caché de tiles. */
private fun configurarOsm(contexto: Context) {
    Configuration.getInstance().apply {
        userAgentValue = AGENTE
        osmdroidBasePath = File(contexto.cacheDir, "osmdroid")
        osmdroidTileCache = File(contexto.cacheDir, "osmdroid/tiles")
    }
}

/** Trazados de la ruta: el optimizado y el base (por calles si OSRM responde). */
class TrazadosRuta(val optimizada: TrazadoVial?, val base: TrazadoVial?)

/** Calcula en segundo plano los trazados por calles de la ruta optimizada y de la base. */
fun trazadosDe(ruta: JSONObject): TrazadosRuta {
    val (origen, paradas) = puntosDe(ruta)
    val ordenBase = ruta.getJSONArray("ordenBase").enteros()
    return TrazadosRuta(
        optimizada = trazadoVial(listOf(origen) + paradas),
        base = trazadoVial(listOf(origen) + ordenBase.map { paradas[it] }),
    )
}

private fun puntosDe(ruta: JSONObject): Pair<GeoPoint, List<GeoPoint>> {
    val o = ruta.getJSONObject("origen")
    return GeoPoint(o.getDouble("lat"), o.getDouble("lng")) to
        ruta.getJSONArray("paradas").objetos().map { GeoPoint(it.getDouble("lat"), it.getDouble("lng")) }
}

private fun JSONArray.enteros(): List<Int> = (0 until length()).map { getInt(it) }

/**
 * Mapa real (OpenStreetMap) con la salida, las paradas numeradas y las dos rutas: base (naranja, punteada)
 * y optimizada (verde). Si [trazados] no trae el camino por calles, une los puntos en línea recta.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapaOsm(ruta: JSONObject, trazados: TrazadosRuta?, modifier: Modifier) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mapa = remember {
        configurarOsm(contexto)
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 5.0
            // Dentro de una lista: el mapa se queda con los gestos mientras se arrastra sobre él.
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> {}
            }
        }
        ciclo.addObserver(observador)
        mapa.onResume()
        onDispose {
            ciclo.removeObserver(observador)
            mapa.onPause()
            mapa.onDetach()
        }
    }

    val densidad = contexto.resources.displayMetrics.density
    AndroidView({ mapa }, modifier) { m ->
        val (origen, paradas) = puntosDe(ruta)
        val estados = ruta.getJSONArray("paradas").objetos().map { it.getString("estado") }
        val nombres = ruta.getJSONArray("paradas").objetos().map { it.nombreDe("establecimiento") }
        val ordenBase = ruta.getJSONArray("ordenBase").enteros()
        m.overlays.clear()

        // Ruta base: naranja punteada, debajo.
        m.overlays += Polyline(m).apply {
            setPoints(trazados?.base?.puntos ?: (listOf(origen) + ordenBase.map { paradas[it] }))
            outlinePaint.apply {
                color = NARANJA
                strokeWidth = 4 * densidad
                pathEffect = DashPathEffect(floatArrayOf(10 * densidad, 7 * densidad), 0f)
                strokeCap = Paint.Cap.ROUND
            }
            isGeodesic = trazados?.base == null
        }
        // Ruta optimizada: verde, encima.
        m.overlays += Polyline(m).apply {
            setPoints(trazados?.optimizada?.puntos ?: (listOf(origen) + paradas))
            outlinePaint.apply {
                color = VERDE
                strokeWidth = 6 * densidad
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            isGeodesic = trazados?.optimizada == null
        }
        m.overlays += Marker(m).apply {
            position = origen
            title = ruta.getJSONObject("origen").getString("nombre")
            snippet = ruta.getJSONObject("origen").optString("direccion")
            icon = BitmapDrawable(m.resources, insignia(densidad, "S", MARINO, cuadrada = true))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        paradas.forEachIndexed { i, p ->
            m.overlays += Marker(m).apply {
                position = p
                title = "${i + 1}. ${nombres[i]}"
                snippet = estadoEntregaLegible(estados[i])
                val fondo = when (estados[i]) {
                    "RECIBIDA" -> GRIS
                    "ENTREGADA" -> NARANJA
                    else -> VERDE
                }
                icon = BitmapDrawable(m.resources, insignia(densidad, "${i + 1}", fondo))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
        }
        // Encuadre: toda la ruta con margen.
        val todos = listOf(origen) + paradas + (trazados?.optimizada?.puntos ?: emptyList())
        m.post {
            if (m.width > 0 && todos.size > 1) m.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(todos), false, (36 * densidad).toInt())
        }
        m.invalidate()
    }
}

/** Círculo (o cuadrado) de color con un número o letra, para los marcadores. */
private fun insignia(densidad: Float, texto: String, fondo: Int, cuadrada: Boolean = false): Bitmap {
    val lado = (30 * densidad).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = lado / 2f
    val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    pintura.color = android.graphics.Color.WHITE
    if (cuadrada) c.drawRoundRect(0f, 0f, lado.toFloat(), lado.toFloat(), 6 * densidad, 6 * densidad, pintura) else c.drawCircle(r, r, r, pintura)
    pintura.color = fondo
    val m = 2.5f * densidad
    if (cuadrada) c.drawRoundRect(m, m, lado - m, lado - m, 5 * densidad, 5 * densidad, pintura) else c.drawCircle(r, r, r - m, pintura)
    pintura.color = android.graphics.Color.WHITE
    pintura.textSize = 14 * densidad
    pintura.typeface = Typeface.DEFAULT_BOLD
    pintura.textAlign = Paint.Align.CENTER
    c.drawText(texto, r, r - (pintura.descent() + pintura.ascent()) / 2, pintura)
    return bmp
}

private val VERDE = Color(0xFF28A66A).toArgb()
private val NARANJA = Color(0xFFF28C38).toArgb()
private val MARINO = Color(0xFF15365C).toArgb()
private val GRIS = Color(0xFF8A9AAD).toArgb()

/** Carga los trazados por calles una vez por ruta (null mientras carga o si no hay servicio). */
@Composable
fun rememberTrazados(ruta: JSONObject): TrazadosRuta? {
    var trazados by remember(ruta.toString()) { mutableStateOf<TrazadosRuta?>(null) }
    LaunchedEffect(ruta.toString()) { enFondo { trazadosDe(ruta) }.onSuccess { trazados = it } }
    return trazados
}

/**
 * Elegir un punto en el mapa con un toque (establecimiento, local o bodega). Centra en el punto actual
 * o, si no hay, en Quito. [alElegir] recibe latitud y longitud con 6 decimales.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun SelectorUbicacion(lat: Double?, lng: Double?, modifier: Modifier, alElegir: (Double, Double) -> Unit) {
    val contexto = LocalContext.current
    val densidad = contexto.resources.displayMetrics.density
    val mapa = remember {
        configurarOsm(contexto)
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 5.0
            controller.setZoom(if (lat != null) 16.0 else 12.0)
            controller.setCenter(GeoPoint(lat ?: -0.1807, lng ?: -78.4678))
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marcador = remember {
        Marker(mapa).apply {
            icon = BitmapDrawable(mapa.resources, insignia(densidad, "•", VERDE))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }
    DisposableEffect(Unit) {
        mapa.onResume()
        onDispose { mapa.onPause(); mapa.onDetach() }
    }
    AndroidView({ mapa }, modifier) { m ->
        m.overlays.clear()
        m.overlays += org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                alElegir(Math.round(p.latitude * 1e6) / 1e6, Math.round(p.longitude * 1e6) / 1e6)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = singleTapConfirmedHelper(p)
        })
        if (lat != null && lng != null) {
            marcador.position = GeoPoint(lat, lng)
            m.overlays += marcador
        }
        m.invalidate()
    }
}

/**
 * Mapa fijo con el punto de un establecimiento: no se arrastra ni hace zoom (una capa encima se queda
 * con los toques sin consumirlos, así la lista que lo contiene sigue desplazándose).
 */
@Composable
fun MapaPunto(lat: Double, lng: Double, nombre: String, direccion: String?, modifier: Modifier) {
    val contexto = LocalContext.current
    val densidad = contexto.resources.displayMetrics.density
    val mapa = remember {
        configurarOsm(contexto)
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isFlingEnabled = false
            controller.setZoom(16.5)
        }
    }
    DisposableEffect(Unit) {
        mapa.onResume()
        onDispose { mapa.onPause(); mapa.onDetach() }
    }
    Box(modifier) {
        AndroidView({ mapa }, Modifier.matchParentSize()) { m ->
            val punto = GeoPoint(lat, lng)
            m.overlays.clear()
            m.overlays += Marker(m).apply {
                position = punto
                title = nombre
                snippet = direccion
                icon = BitmapDrawable(m.resources, insignia(densidad, "•", AZUL))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }
            m.controller.setCenter(punto)
            m.invalidate()
        }
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    }
}

private val AZUL = Color(0xFF2476C8).toArgb()
