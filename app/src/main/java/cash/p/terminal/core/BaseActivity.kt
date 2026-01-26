package cash.p.terminal.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.horizontalsystems.core.CoreApp

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Check if SQLCipher failed to load - redirect to error screen
        if (App.sqlCipherLoadFailed) {
            val intent = Intent(this, NativeLibraryErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
            return
        }

        window.decorView.layoutDirection =
            if (CoreApp.instance.isLocaleRTL()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    override fun attachBaseContext(newBase: Context) {
        // Skip locale-aware context if SQLCipher failed (CoreApp not fully initialized)
        if (App.sqlCipherLoadFailed) {
            super.attachBaseContext(newBase)
            return
        }
        super.attachBaseContext(CoreApp.instance.localeAwareContext(newBase))
    }
}
