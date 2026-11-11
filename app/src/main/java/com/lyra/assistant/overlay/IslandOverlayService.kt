package com.lyra.assistant.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import com.lyra.assistant.MainActivity

/** User-started overlay; Android shows the standard special-access permission flow. */
class IslandOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var island: LinearLayout? = null
    private var expanded = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Autoriza la ventana flotante en Ajustes para activar la isla.", Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY }
        if (island == null) showIsland()
        return START_NOT_STICKY
    }

    private fun showIsland() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22)
            background = GradientDrawable().apply { setColor(Color.rgb(8, 9, 16)); cornerRadius = 64f; setStroke(2, Color.rgb(88, 76, 136)) }
        }
        val title = TextView(this).apply { text = "LYRA  ·  • • •"; setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        val content = TextView(this).apply { text = "Toca para abrir LYRA"; setTextColor(Color.LTGRAY); textSize = 11f; gravity = Gravity.CENTER; visibility = android.view.View.GONE; setPadding(0, 12, 0, 3) }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.setOnClickListener {
            expanded = !expanded
            content.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
            content.text = if (expanded) "Conversación y controles en la app  ·  Toca para abrir" else "Toca para abrir LYRA"
            val params = root.layoutParams as WindowManager.LayoutParams
            params.width = if (expanded) dp(290) else dp(180)
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            runCatching { windowManager?.updateViewLayout(root, params) }
            if (expanded) {
                val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(open)
            }
        }
        val params = WindowManager.LayoutParams(
            dp(180), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(32) }
        runCatching { windowManager?.addView(root, params); island = root }
            .onFailure { Toast.makeText(this, "No se pudo mostrar la isla: ${it.message}", Toast.LENGTH_LONG).show(); stopSelf() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { island?.let { runCatching { windowManager?.removeView(it) } }; island = null; windowManager = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
