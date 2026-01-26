package cash.p.terminal.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class NativeLibraryErrorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NativeLibraryErrorScreen(
                onClose = {
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            )
        }
    }
}

@Composable
private fun NativeLibraryErrorScreen(onClose: () -> Unit) {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.tyler)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            title3_leah(
                text = stringResource(R.string.native_library_error_title),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            body_grey(
                text = stringResource(R.string.native_library_error_message),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            ButtonPrimaryRed(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.native_library_error_close),
                onClick = onClose
            )
        }
    }
}
