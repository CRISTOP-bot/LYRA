# LYRA

Asistente Android nativo en Kotlin y Jetpack Compose. Esta primera base incluye una app ejecutable por la persona usuaria, no una demostración de interfaz aislada.

## Implementado en esta entrega

- Aplicación Android separada en `agent`, `data`, `security`, `overlay` y UI.
- Conversación de texto compartida con la transcripción de voz del sistema.
- Historial local persistente (DataStore, máximo 300 mensajes).
- Cliente de chat para API HTTPS compatible con el formato OpenAI Chat Completions. Proveedor/modelo configurables.
- Clave opcional cifrada con AES-GCM y clave no exportable guardada por Android Keystore. No hay credencial incluida ni proveedor activo por defecto.
- SpeechRecognizer en español (es-MX), solicitud de permiso en contexto; síntesis TTS del sistema con locale es-MX.
- Visualizador animado de estado y burbujas de conversación.
- Isla superpuesta optativa mediante `TYPE_APPLICATION_OVERLAY`; la app abre el ajuste especial de Android y el usuario inicia/detiene explícitamente el servicio. No altera SystemUI ni solicita root.
- Respuestas y errores del proveedor se muestran en la conversación; no se simulan acciones externas.

## Aún no implementado

No se deben anunciar como terminados: enrutamiento/fallback de múltiples proveedores, health/quota monitor, agente de planificación y herramientas con permisos, Room/contextual/persistent memory controls, Accessibility Service, automatizaciones, App Hunter, verificación/descargas e instalación, WorkManager/foreground task runner, streaming, cancelación real de voz mediante UI, tests Android/instrumentados, gestión completa de claves/proveedores, exportación de datos y auditoría de operaciones. El texto compartido con el modelo se limita a las últimas 24 entradas; el historial completo se queda local.

La compatibilidad OpenAI-like no garantiza que todos los servicios acepten esta misma ruta/esquema. Confirma precio, límites y términos actuales de cada proveedor antes de configurarlo. LYRA no selecciona servicios de pago por su cuenta. La voz y disponibilidad de SpeechRecognizer/TTS dependen del dispositivo y sus servicios instalados.

## Construcción

Requisitos: JDK 17, Android SDK Platform 36 y Android Build Tools instalados.

```sh
./gradlew :app:assembleDebug
```

No se incluyó ningún secreto. Para probar una API, abre LYRA → ⚙ → escribe URL HTTPS, modelo y clave proporcionada por ti. Puedes activar la isla desde la pantalla principal; Android te pedirá habilitar el permiso especial de superposición. Desactívala con el botón ×.

## Estado de verificación

En el entorno donde se generó esta carpeta no hay `java`, `ANDROID_HOME`/`ANDROID_SDK_ROOT` ni un Android SDK, así que no fue posible ejecutar Gradle, pruebas JVM, instalar el APK, ni validar la ventana flotante en un dispositivo. La primera verificación pendiente es compilar en un entorno Android real y corregir cualquier incompatibilidad encontrada.
