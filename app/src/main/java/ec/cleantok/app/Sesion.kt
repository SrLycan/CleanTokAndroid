package ec.cleantok.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Datos persistentes: tokens, rol y cola de movimientos sin enviar. */
class Sesion(context: Context) {
    private val prefs = context.getSharedPreferences("cleantok", Context.MODE_PRIVATE)

    /** Siempre el servidor de AWS con que se compiló la app (ya no se elige en el login). */
    val servidor: String get() = BuildConfig.SERVIDOR.trimEnd('/')

    var accessToken: String?
        get() = prefs.getString("access", null)
        set(v) = prefs.edit().putString("access", v).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh", null)
        set(v) = prefs.edit().putString("refresh", v).apply()

    var email: String
        get() = prefs.getString("email", "maria@brillototal.ec")!!
        set(v) = prefs.edit().putString("email", v).apply()

    /** ADMIN_EMPRESA, ENCARGADO, PROVEEDOR o REPARTIDOR; null en sesiones anteriores a los roles (se pide a /auth/me). */
    val rol: String? get() = prefs.getString("rol", null)

    val nombre: String get() = prefs.getString("nombre", "")!!

    fun recordarUsuario(nombre: String, rol: String, debeCambiarClave: Boolean = false) =
        prefs.edit().putString("nombre", nombre).putString("rol", rol).putBoolean("cambiarClave", debeCambiarClave).apply()

    /** Entró con una clave temporal: debe cambiarla antes de usar la app. */
    var debeCambiarClave: Boolean
        get() = prefs.getBoolean("cambiarClave", false)
        set(v) = prefs.edit().putBoolean("cambiarClave", v).apply()

    val conSesion: Boolean get() = accessToken != null

    fun cerrar() = prefs.edit().remove("access").remove("refresh").remove("rol").remove("nombre").remove("cambiarClave").apply()

    // ---- Cola offline: ítems listos para POST /sincronizacion/lote ----

    fun pendientes(): JSONArray = JSONArray(prefs.getString("cola", "[]"))

    fun encolar(item: JSONObject) {
        val cola = pendientes().put(item)
        prefs.edit().putString("cola", cola.toString()).apply()
    }

    fun vaciarCola() = prefs.edit().putString("cola", "[]").apply()
}
