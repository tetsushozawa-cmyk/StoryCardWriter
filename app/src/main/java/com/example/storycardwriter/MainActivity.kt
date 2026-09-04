package com.example.storycardwriter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
/** Launcher entry point that immediately starts a blank writing project. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, WriterActivity::class.java)
                .putExtra(WriterActivity.CreateNewStoryExtra, true)
        )
        finish()
    }
}
