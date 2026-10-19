# Hyper Ultra Launcher (v0.3.2)

Launcher Android ligero (Kotlin + Jetpack Compose) con estética inspirada en HyperOS. Proyecto independiente, no es software oficial de Xiaomi.

- Paquete: `io.github.hyperultra.launcher` · minSdk 26 · targetSdk 35 · sin código nativo (no depende de la arquitectura de la CPU).
- Permisos (todos opcionales, los concedes tú en Ajustes): `WRITE_SETTINGS` (brillo), `SYSTEM_ALERT_WINDOW` (burbuja y pantalla de bloqueo), `FOREGROUND_SERVICE(_SPECIAL_USE)` (mantener viva la burbuja), acceso a notificaciones y servicio de accesibilidad.
- **Estado de verificación:** revisa el resultado de compilación en GitHub Actions o Android Studio antes de instalar; los servicios del sistema pueden requerir pruebas en el dispositivo.

## Compilar el APK

### Opción A: GitHub Actions (sin instalar nada)
1. Crea un repositorio privado en GitHub y sube el contenido de esta carpeta.
2. Pestaña **Actions → Build APK → Run workflow**.
3. Al terminar, descarga el artefacto `HyperUltraLauncher-apk`:
   `HyperUltraLauncher-debug.apk` y `HyperUltraLauncher-release.apk` (la variante release está firmada con la clave de depuración para instalación manual; no es una firma apropiada para publicar en Play Store).

### Opción B: Android Studio
Abre la carpeta, deja que sincronice Gradle y usa **Build → Build APK(s)**.
Salida: `app/build/outputs/apk/debug/app-debug.apk`.

### Opción C: línea de comandos
Requiere JDK 17, Android SDK (plataforma 35) y Gradle 8.9: `gradle assembleDebug`.

## Instalar en el REDMI A5
1. Copia el APK al móvil y ábrelo; permite "instalar apps de esta fuente" si lo pide.
2. Ajustes → Apps → Apps predeterminadas → App de inicio → Hyper Ultra Launcher
   (o pulsa "Launcher predeterminado" dentro de los ajustes del launcher).

## Uso
- Deslizar arriba: cajón de apps (búsqueda arriba). Deslizar abajo: Centro de control o búsqueda (configurable).
- Mantener pulsado en inicio: ajustes. Mantener pulsado un icono del cajón: info, dock, ocultar.

## Implementado
Inicio con reloj, fecha, batería, calendario y nota; dock (por defecto con teléfono/mensajes/navegador/cámara predeterminados); cajón con búsqueda; columnas y tamaño de icono; ocultar apps (protegido con el bloqueo del sistema si existe); temas y colores; claro/oscuro; Modo Lite (por defecto); desenfoque real del fondo en Android 12+; Centro de control (linterna, volumen, brillo con permiso, multimedia, atajos a ajustes); exportar/importar configuración por portapapeles; fondo de pantalla del sistema.

## Novedades v0.3: desbloqueo propio
En Ajustes → Funciones avanzadas → "Método de desbloqueo":
- **Sistema** (por defecto): solo el PIN/patrón/huella del sistema.
- **Huella / PIN del móvil**: `BiometricPrompt` (huella, rostro si es seguro, o el PIN del móvil) y después el desbloqueo del sistema. Requiere Android 11+ para el modo combinado; si no está disponible pasa directo al sistema.
- **PIN propio** (4-8 dígitos): teclado en la pantalla de bloqueo, hash PBKDF2 con sal, bloqueo creciente tras 5 fallos (30 s, 60 s, ...). Crear/cambiar/quitar pide el PIN actual.
Es una barrera EXTRA: tras ella el sistema sigue pidiendo su desbloqueo si tienes uno, y no cambia ni sustituye tu PIN real. Si olvidas el PIN propio, desliza Inicio/atrás para salir de la pantalla y quita el PIN en Ajustes (pide el PIN actual: si lo olvidaste, desinstala/borra datos de la app).

## Novedades v0.2 (Ajustes → FUNCIONES AVANZADAS)
- **Pantalla de bloqueo propia**: reloj grande, fecha, batería, música en curso, hasta 4 notificaciones, atajos a teléfono y cámara, color del reloj y oscurecimiento. Se muestra al encender la pantalla, *encima* de la del sistema. Deslizar arriba llama a `requestDismissKeyguard`: el sistema siempre pide tu PIN/patrón/huella. Se cierra al apagar la pantalla o al salir de ella. Incluye botón de vista previa.
- **Superposición / burbuja flotante**: botón arrastrable sobre cualquier app con Inicio, apagar pantalla, notificaciones, recientes y ocultar burbuja.
- **Doble toque en inicio para apagar la pantalla** (acción global de accesibilidad, Android 9+).
- **Notificaciones y música reales** en el Centro de control (título y estado desde la sesión multimedia).
- Cada interruptor solo se activa si el sistema confirma el permiso; el estado se relee al volver de Ajustes.

Servicios: `OverlayService` (solo corre si activas burbuja o bloqueo), `NotifListener`, `LauncherAccessibility` (sin leer contenido de otras apps).
**Estado: igual que v0.1, NO compilado ni probado en dispositivo.**

## Limitaciones
- La pantalla de bloqueo propia NO reemplaza ni elude la del sistema (Android no lo permite). Con un bloqueo seguro, algunas ROM (MIUI/HyperOS) exigen permisos extra para esta app: "Mostrar en pantalla de bloqueo", "Inicio automático" y sin restricción de batería; si no, el sistema cierra el servicio o bloquea el arranque desde segundo plano.
- Las notificaciones del bloqueo propio se ven sin desbloquear si activas esa opción (desactívala en Ajustes si te preocupa la privacidad).
- Android 13+ puede ocultar la notificación del servicio en primer plano si no concedes notificaciones; el servicio funciona igual.
- Sin fondo propio en el bloqueo: usa el fondo del sistema (windowShowWallpaper).
## Limitaciones previas (v0.1)
- Wi-Fi, Bluetooth, modo avión y ahorro de batería: Android no permite activarlos desde una app normal; abren el panel oficial y no muestran estado.
- No incluido todavía: carpetas, widgets de terceros redimensionables, paquetes de iconos, relojes/widgets de música y fotos, cuadrícula libre en inicio.
