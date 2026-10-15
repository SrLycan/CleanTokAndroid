# CleanTok Android (app de demo)

App de demo para probar el backend en `../CLEANTOK`, con la misma paleta y estilo de movimiento que la web (React + Motion).

## Qué hace

Una sola app con pantallas según el rol (la web queda para después):

| Rol | Pestañas | Qué puede hacer |
|---|---|---|
| **Administración** (`admin@brillototal.ec`) | Inicio · Compras · Pedidos | Inventario, alertas, **hoja de QR** de cada establecimiento (compartir/imprimir, regenerar). **Compras**: qué reponer y cuánto, pedir cotización, **comparar ofertas** con puntaje y aprobar. Rastreo de cada pedido. |
| **Encargado** (`maria@`, `luis@brillototal.ec`) | Inicio · Pedidos | Escanear el QR del piso o bodega: agregar, retirar, devolver, **nivel con foto** y kardex. **Confirmar la recepción** de lo que llega (completo o con novedad, foto de la guía): entra solo a la bodega. |
| **Proveedor** (`ventas@lahigiene.ec`, `ventas@proclean.ec`) | Cotizar · Pedidos | Ver las solicitudes, **ofertar** por producto (precio, disponible, plazo, envío o retiro). Aceptar o rechazar órdenes, prepararlas, empacarlas, **asignar repartidor**. |
| **Repartidor** (`reparto@lahigiene.ec`) | Mi ruta · Pedidos | **Ruta optimizada** sobre un mapa real de OpenStreetMap, por calles (base vs. optimizada: km, minutos, CO₂e), navegar en Google Maps, marcar "salió de bodega", "en tránsito" y cada destino entregado. |

**Gestión de cuentas** (cada organización administra a su gente; las cuentas nuevas reciben una clave temporal que se comparte por WhatsApp o correo y se cambia al entrar):

| Rol | Pestañas | Qué gestiona |
|---|---|---|
| **Administración del sistema** (`superadmin@cleantok.ec` / `SuperAdmin2026!`) | Resumen · Empresas · Proveedores | Cifras de la plataforma; alta de empresas con su primer admin y de proveedores (local y bodega en el mapa, formas de entrega) con su primer vendedor; activar o desactivar proveedores. |
| **Administración de la empresa** | … · **Gestión** | **Equipo** (encargados y admins, sus sitios, restablecer clave, activar/desactivar), **Proveedores** (directorio: con quién trabajo y preferidos) y **Sitios** (crear establecimientos y pisos, ubicarlos en el mapa). |
| **Proveedor** | … · **Gestión** | **Equipo** (repartidores y vendedores) y **Perfil** (teléfono, local, bodega y formas de entrega). |

Cada pedido tiene un **rastreo tipo courier**: aprobado → aceptado → en preparación → empacado → salió de bodega →
en tránsito → entregado, con fecha, hora y responsable de cada paso.

Además:
- **Sin conexión**: si no hay red al registrar un movimiento, queda en el teléfono; **Sincronizar** lo envía a `POST /sincronizacion/lote` (el mismo `clientUuid`, así que no se duplica).
- **Deep link** `http(s)://…/q/{token}`: el QR impreso puede abrir la app.
- Muestra los errores del backend (`STOCK_INSUFICIENTE` con lo disponible, `UNIDAD_INCOMPATIBLE`, etc.).

**Compras** (admin): al pedir cotización se elige **para cuándo** se necesita, con fechas recomendadas según cuándo se
agota cada producto; al aprobar se abre un **pago demo** (tarjeta o transferencia, sin cobro real; tarjeta de prueba
`4242 4242 4242 4242` aprobada y `4000 0000 0000 0002` rechazada).

## Compartir el APK (servidor en AWS)

Con el backend desplegado (ver `../CLEANTOK/DESPLIEGUE-AWS.md`):

```powershell
.\generar-apk.ps1 -Servidor https://3-85-10-20.sslip.io
```

Deja `dist\CleanTok-<versión>.apk` con ese servidor por defecto, firmado con `cleantok-equipo.jks` (respáldalo).

## Cómo probarla

1. Levanta el backend: en `../CLEANTOK`, `docker compose up --build`.
   - Emulador: `CLEANTOK_URL_PUBLICA=http://10.0.2.2:8080` (así las URL de los QR funcionan dentro del emulador).
   - Celular real (misma Wi-Fi): `CLEANTOK_URL_PUBLICA=http://IP-de-tu-PC:8080` y permite el puerto 8080 en el firewall de Windows.
2. Abre esta carpeta en Android Studio y dale **Run**, o compila e instala:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. En el login, servidor `http://10.0.2.2:8080` (emulador) o `http://IP-de-tu-PC:8080` (celular).

### Abrir un piso sin cámara (emulador)

- Entra como **admin** (`admin@brillototal.ec`): en **Pisos** cada ubicación muestra su URL y se abre con un toque.
- O pega la URL del QR en el campo del inicio.
- O simula el escaneo con un deep link:
  ```bash
  adb shell am start -a android.intent.action.VIEW -d "http://10.0.2.2:8080/q/<token>" -p ec.cleantok.app
  ```

Con el encargado (`maria@brillototal.ec`) los pisos se abren escaneando el QR: imprime o muestra en pantalla la hoja
`GET /api/v1/establecimientos/{id}/qr-hoja.pdf` (descárgala como admin desde Swagger).

> Al escanear el QR con la **cámara del sistema**, Android 12+ abre el navegador, porque el enlace no está verificado.
> Usa el botón **Escanear QR** de la app, o activa "Abrir enlaces compatibles" para CleanTok en los ajustes de Android.
> Para App Links verificados, configura `CLEANTOK_ANDROID_PAQUETE=ec.cleantok.app` y la huella SHA-256 en el backend.

## Técnico

- Kotlin + Jetpack Compose, `minSdk 26`, `compileSdk 36`, AGP 9.4.1, Gradle 9.8.1.
- Sin Retrofit ni librerías de red: `HttpURLConnection` + `org.json`. El token se renueva solo con el refresh token.
- Mapa: `org.osmdroid:osmdroid-android` (Apache 2.0) con tiles de OpenStreetMap (© colaboradores de OSM, ODbL) y trazado por calles con OSRM. Sin API key.
- Escáner: `com.journeyapps:zxing-android-embedded` (Apache 2.0). Fotos con la cámara del sistema (`TakePicture` + `FileProvider`).
- HTTP sin TLS permitido solo para la demo (`network_security_config.xml`).
- Diseño: `Tema.kt` (paleta de la web: azul #2476C8, marino #15365C, verde, naranja; modo oscuro), `Comunes.kt` (barra superior, tarjetas, etiquetas, avisos) e `Iconos.kt`.
- Movimiento (`Movimiento.kt`): Motion es solo para React, así que se replica con Compose: resortes, entradas escalonadas,
  "whileTap" (las tarjetas se encogen al tocarlas) y animación de layout en las listas.
