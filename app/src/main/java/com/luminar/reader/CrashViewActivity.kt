// app/src/main/java/com/luminar/reader/CrashViewActivity.kt
package com.luminar.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Ultra-simple activity (NO Compose, NO Hilt, NO Room) that shows
 * the crash log file. Accessible from launcher as a second entry point.
 */
class CrashViewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = File(filesDir, "crash_log.txt")
        val crashText = if (file.exists()) file.readText() else "No crash log found. App hasn't crashed yet!"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Luminar Crash Log"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        val copyBtn = Button(this).apply {
            text = "Copy to clipboard"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", crashText))
                Toast.makeText(this@CrashViewActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(copyBtn)

        val clearBtn = Button(this).apply {
            text = "Clear crash log"
            setOnClickListener {
                file.delete()
                Toast.makeText(this@CrashViewActivity, "Cleared!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        layout.addView(clearBtn)

        val textView = TextView(this).apply {
            text = crashText
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, 16, 0, 0)
        }

        val scroll = ScrollView(this)
        scroll.addView(textView)
        layout.addView(scroll)

        setContentView(layout)
    }
}
