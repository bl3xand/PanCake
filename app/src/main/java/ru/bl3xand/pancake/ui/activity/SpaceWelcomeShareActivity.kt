package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SpaceWelcomeShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, SpaceShareActivity::class.java)
                .putExtra(SpaceShareActivity.EXTRA_WELCOME_MODE, true)
        )
        finish()
    }
}
