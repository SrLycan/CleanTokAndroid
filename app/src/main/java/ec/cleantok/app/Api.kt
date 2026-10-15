package ec.cleantok.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Error de la API en formato ProblemDetail: `codigo` estable + mensaje en español. */
class ApiError(val status: Int, val codigo: String?, mensaje: String, val cuerpo: JSONObject?) : Exception(mensaje) {
    override fun toString(): String {
        val disponible = cuerpo?.optString("disponible")?.takeIf { it.isNotEmpty() }
        return listOfNotNull(codigo, message, disponible?.let { "disponible: $it ${cuerpo.optString("unidad")}" }).joinToString(" · ")
    }
}

/** Cliente HTTP mínimo (sin librerías). Renueva el access token con el refresh token si recibe 401. */
class Api(private val sesion: Sesion) {

    private val base get() = sesion.servidor + "/api/v1"

    fun login(email: String, clave: String): JSONObject {
        val r = JSONObject(texto("POST", "/auth/login", JSONObject().put("email", email).put("password", clave), conToken = false))
        sesion.accessToken = r.getString("accessToken")
        sesion.refreshToken = r.getString("refreshToken")
        sesion.email = email
        r.getJSONObject("usuario").let { sesion.recordarUsuario(it.getString("nombre"), it.getString("rol"), it.optBoolean("debeCambiarClave")) }
        // Solo en memoria: evita volver a pedir la clave temporal en el cambio obligatorio.
        claveRecienIngresada = clave
        return r
    }

    fun logout() {
        sesion.refreshToken?.let { runCatching { texto("POST", "/auth/logout", JSONObject().put("refreshToken", it), conToken = false) } }
        sesion.cerrar()
    }

    fun obj(path: String): JSONObject = JSONObject(texto("GET", path))
    fun arr(path: String): JSONArray = JSONArray(texto("GET", path))
    fun post(path: String, body: JSONObject? = null): JSONObject = JSONObject(texto("POST", path, body).ifBlank { "{}" })
    fun put(path: String, body: JSONObject? = null): JSONObject = JSONObject(texto("PUT", path, body).ifBlank { "{}" })
    fun patch(path: String, body: JSONObject? = null): JSONObject = JSONObject(texto("PATCH", path, body).ifBlank { "{}" })
    fun put(path: String, body: org.json.JSONArray): org.json.JSONArray =
        org.json.JSONArray(String(llamar("PUT", path, Contenido(body.toString().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")), Charsets.UTF_8))
    fun delete(path: String) { llamar("DELETE", path, null) }

    /** Contenido binario (PNG del QR, hoja PDF, foto). */
    fun bytes(path: String): ByteArray = llamar("GET", path, null)

    /** Sube una foto JPEG (multipart) y devuelve su id para adjuntarla a un reporte o recepción. */
    fun subirFoto(jpeg: ByteArray): String = subirArchivo("/fotos", jpeg, "foto.jpg", "image/jpeg").getString("id")

    /** Sube un archivo en el campo `archivo` (multipart): fotos y el PDF de la orden de compra. */
    fun subirArchivo(path: String, datos: ByteArray, nombre: String, tipo: String): JSONObject {
        val limite = "cleantok-" + UUID.randomUUID()
        val seguro = nombre.replace("\"", "").replace("\r", "").replace("\n", "")
        val cuerpo = buildList<ByteArray> {
            add("--$limite\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"$seguro\"\r\nContent-Type: $tipo\r\n\r\n".toByteArray())
            add(datos)
            add("\r\n--$limite--\r\n".toByteArray())
        }.reduce { a, b -> a + b }
        return JSONObject(String(llamar("POST", path, Contenido(cuerpo, "multipart/form-data; boundary=$limite")), Charsets.UTF_8))
    }

    private fun refrescar(): Boolean {
        val rt = sesion.refreshToken ?: return false
        return try {
            val r = JSONObject(texto("POST", "/auth/refresh", JSONObject().put("refreshToken", rt), conToken = false))
            sesion.accessToken = r.getString("accessToken")
            sesion.refreshToken = r.getString("refreshToken")
            true
        } catch (e: ApiError) {
            sesion.cerrar()
            false
        }
    }

    private class Contenido(val bytes: ByteArray, val tipo: String)

    private fun texto(metodo: String, path: String, body: JSONObject? = null, conToken: Boolean = true): String =
        String(llamar(metodo, path, body?.let { Contenido(it.toString().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8") }, conToken), Charsets.UTF_8)

    /** Lanza IOException si no hay conexión (se usa para la cola offline) y ApiError si el servidor responde error. */
    private fun llamar(metodo: String, path: String, body: Contenido?, conToken: Boolean = true, reintentar: Boolean = true): ByteArray {
        val con = (URL(base + path).openConnection() as HttpURLConnection).apply {
            if (metodo == "PATCH") fijarPatch(this) else requestMethod = metodo
            connectTimeout = 8000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json, image/png, application/pdf, image/jpeg")
            if (conToken) sesion.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", body.tipo)
                outputStream.use { it.write(body.bytes) }
            }
        }
        val codigo = con.responseCode
        val datos = (if (codigo < 400) con.inputStream else con.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        if (codigo == 401 && conToken && reintentar && refrescar()) return llamar(metodo, path, body, conToken, reintentar = false)
        if (codigo >= 400) {
            val json = runCatching { JSONObject(String(datos, Charsets.UTF_8)) }.getOrNull()
            throw ApiError(codigo, json?.optString("codigo"), json?.optString("detail")?.ifBlank { null } ?: "Error HTTP $codigo", json)
        }
        return datos
    }

    /** HttpURLConnection no admite PATCH: se fija el campo por reflexión antes de conectar (suficiente para la demo). */
    private fun fijarPatch(con: HttpURLConnection) {
        val campo = HttpURLConnection::class.java.getDeclaredField("method")
        campo.isAccessible = true
        campo.set(con, "PATCH")
    }

    companion object {
        fun sinConexion(e: Throwable) = e is IOException

        /** Clave usada en el último login de esta ejecución (nunca se guarda en disco). */
        var claveRecienIngresada: String? = null
    }
}
