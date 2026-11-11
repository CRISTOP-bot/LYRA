package com.lyra.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyra.assistant.agent.LyraViewModel
import com.lyra.assistant.data.ChatMessage
import com.lyra.assistant.overlay.IslandOverlayService
import java.util.Locale

private val Ink = Color(0xFF090A12)
private val Panel = Color(0xFF151725)
private val Violet = Color(0xFF9C72FF)
private val Cyan = Color(0xFF55D7FF)

class MainActivity : ComponentActivity() {
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }
        setContent { LyraApp(onListen = ::listen, onSpeak = { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "lyra-answer") }) }
    }
    private fun listen(onText: (String) -> Unit, onState: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { onState("Reconocimiento de voz no disponible en este dispositivo"); return }
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onState("Escuchando") }
                override fun onBeginningOfSpeech() { onState("Escuchando") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onState("Procesando") }
                override fun onError(error: Int) { onState("No se reconoció el audio (código $error)") }
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                    onState("En reposo")
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }
    override fun onDestroy() { speech?.destroy(); tts?.shutdown(); super.onDestroy() }
}

@Composable
private fun LyraApp(onListen: ((String) -> Unit, (String) -> Unit) -> Unit, onSpeak: (String) -> Unit, vm: LyraViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val error by vm.error.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    var input by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var micGranted by remember { mutableStateOf(false) }
    var startListeningAfterGrant by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted && startListeningAfterGrant) onListen({ vm.send(it) }, vm::setStatus)
        startListeningAfterGrant = false
    }
    LaunchedEffect(Unit) {
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, background = Ink, surface = Panel)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111226), Ink, Color(0xFF0B1119))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("LYRA", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp); Text("ASISTENTE PERSONAL", color = Color(0xFF9294AB), fontSize = 10.sp, letterSpacing = 2.sp) }
                    Row {
                        IconButton(onClick = { vm.clearConversation() }) { Icon(Icons.Default.DeleteOutline, "Borrar conversación", tint = Color.LightGray) }
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Configuración", tint = Color.LightGray) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Orb(status = if (busy) "Procesando" else status)
                Text(if (busy) "LYRA está pensando…" else status, Modifier.fillMaxWidth(), color = Color(0xFFB8B5D1), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if (!hasKey) Text("Modo local listo · configura tu propio proveedor de IA en ⚙. No hay proveedor activo sin tu clave.", color = Color(0xFFFFD28A), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x332E2418)).padding(12.dp))
                error?.let { Text(it, Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFFFF9A9A), fontSize = 12.sp) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), reverseLayout = false, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages, key = { it.id }) { message -> MessageBubble(message, onSpeak) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onListen({ vm.send(it) }, vm::setStatus)
                        else { startListeningAfterGrant = true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                    }, modifier = Modifier.size(48.dp).background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape)) { Icon(Icons.Default.Mic, "Hablar", tint = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Panel).padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Violet), decorationBox = { inner -> if (input.isEmpty()) Text("Escríbele a LYRA…", color = Color.Gray, fontSize = 14.sp); inner() })
                        IconButton(enabled = !busy, onClick = { vm.send(input); input = "" }) { Icon(Icons.Default.ArrowUpward, "Enviar", tint = Violet) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("◉", color = Color.Gray, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Isla flotante", color = Color(0xFFCAC6DB), fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = {
                        if (Settings.canDrawOverlays(context)) context.startService(Intent(context, IslandOverlayService::class.java))
                        else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29233D))) { Text("Activar", fontSize = 12.sp) }
                    Spacer(Modifier.width(5.dp))
                    IconButton(onClick = { context.stopService(Intent(context, IslandOverlayService::class.java)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Cerrar isla", tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
    if (settingsOpen) SettingsDialog(settings.baseUrl, settings.model, key, hasKey, onKeyChange = { key = it }, onDismiss = { settingsOpen = false }, onSave = { base, model -> vm.saveSettings(base, model, key); key = ""; settingsOpen = false })
}

@Composable
private fun Orb(status: String) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxWidth().height(168.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((112 * pulse).dp).blur(28.dp).background(Brush.radialGradient(listOf(Color(0x889B6AFF), Color(0x4450DFFF), Color.Transparent)), CircleShape))
        Box(Modifier.size(98.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5B4A9C), Color(0xFF15152A), Color(0xFF184B66)))).border(1.dp, Color(0x995CDDFB), CircleShape), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(7) { index ->
                    val h by transition.animateFloat(8f, (12 + ((index * 7 + 12) % 24)).toFloat(), infiniteRepeatable(tween(350 + index * 70), RepeatMode.Reverse), label = "wave$index")
                    Box(Modifier.width(4.dp).height(if (status.contains("Escuchando") || status.contains("Hablando")) h.dp else 8.dp).clip(CircleShape).background(if (index % 2 == 0) Cyan else Color(0xFFD0A6FF)))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onSpeak: (String) -> Unit) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(20.dp, 20.dp, if (user) 5.dp else 20.dp, if (user) 20.dp else 5.dp)).background(if (user) Color(0xFF342B50) else Panel).padding(14.dp)) {
            Text(if (user) "TÚ" else "LYRA", color = if (user) Color(0xFFC7B4FF) else Cyan, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp)); Text(message.text, color = Color(0xFFF1F0F6), fontSize = 14.sp, lineHeight = 20.sp)
            if (!user) { Spacer(Modifier.height(4.dp)); IconButton(onClick = { onSpeak(message.text) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.VolumeUp, "Leer en voz alta", tint = Color(0xFFB6A7E3), modifier = Modifier.size(18.dp)) } }
        }
    }
}

@Composable
private fun SettingsDialog(base: String, model: String, apiKey: String, hasKey: Boolean, onKeyChange: (String) -> Unit, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var url by remember(base) { mutableStateOf(base) }
    var modelId by remember(model) { mutableStateOf(model) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Proveedor de IA", color = Color.White) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Compatible con APIs de chat tipo OpenAI. LYRA no activa un servicio ni incurre en costos sin una clave que tú configures.", color = Color.LightGray, fontSize = 12.sp)
            OutlinedTextField(url, { url = it }, label = { Text("URL base HTTPS") }, singleLine = true)
            OutlinedTextField(modelId, { modelId = it }, label = { Text("Modelo") }, singleLine = true)
            OutlinedTextField(apiKey, onKeyChange, label = { Text(if (hasKey && apiKey.isBlank()) "Clave guardada · vacía para conservar" else "Clave API") }, singleLine = true)
            Text("La clave se cifra con Android Keystore. Revisa los precios/cuotas de tu proveedor antes de usarlo.", color = Color(0xFFB5AFC8), fontSize = 11.sp)
        } },
        confirmButton = { TextButton(onClick = { onSave(url, modelId) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
