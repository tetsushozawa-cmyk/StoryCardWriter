package com.tetsushozawa.storycardwriter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class OpenScwActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(incomingIntent: Intent) {
        val importResult = ExternalScwIntentHandler.importFromViewIntent(this, incomingIntent)
        if (importResult == null) {
            finish()
            return
        }

        importResult
            .onSuccess {
                startActivity(Intent(this, WriterActivity::class.java))
                finish()
            }
            .onFailure {
                Toast.makeText(this, "ファイルを開けませんでした", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}
