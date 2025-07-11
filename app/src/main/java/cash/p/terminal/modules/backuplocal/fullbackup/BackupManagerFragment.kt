package cash.p.terminal.modules.backuplocal.fullbackup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.core.Caution
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.navigateWithTermsAccepted
import io.horizontalsystems.core.slideFromBottom
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.importwallet.getFileName
import cash.p.terminal.modules.restorelocal.RestoreLocalFragment
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BackupManagerFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        BackupManagerScreen(
            onBackClick = {
                navController.popBackStack()
            },
            onRestoreBackup = { jsonString, fileName ->
                navController.navigateWithTermsAccepted {
                    navController.slideFromBottom(
                        R.id.restoreLocalFragment,
                        RestoreLocalFragment.Input(
                            R.id.backupManagerFragment,
                            false,
                            jsonString,
                            fileName,
                        )
                    )
                }
            },
            onCreateBackup = {
                navController.authorizedAction {
                    navController.slideFromRight(R.id.backupLocalFragment)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BackupManagerScreen(
    onBackClick: () -> Unit,
    onRestoreBackup: (jsonString: String, fileName: String?) -> Unit,
    onCreateBackup: () -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { uriNonNull ->
            context.contentResolver.openInputStream(uriNonNull)?.use { inputStream ->
                try {
                    inputStream.bufferedReader().use { br ->
                        val jsonString = br.readText()
                        //validate json format
                        BackupFileValidator().validate(jsonString)

                        val fileName = context.getFileName(uriNonNull)
                        onRestoreBackup(jsonString, fileName)
                    }
                } catch (e: Throwable) {
                    //show json parsing error
                    coroutineScope.launch {
                        delay(300)
                        bottomSheetState.show()
                    }
                }
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetBackgroundColor = cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.transparent,
        sheetContent = {
            ConfirmationBottomSheet(
                title = stringResource(R.string.ImportWallet_WarningInvalidJson),
                text = stringResource(R.string.ImportWallet_WarningInvalidJsonDescription),
                iconPainter = painterResource(R.drawable.icon_warning_2_20),
                iconTint = ColorFilter.tint(cash.p.terminal.ui_compose.theme.ComposeAppTheme.colors.lucian),
                confirmText = stringResource(R.string.ImportWallet_SelectAnotherFile),
                cautionType = Caution.Type.Warning,
                cancelText = stringResource(R.string.Button_Cancel),
                onConfirm = {
                    restoreLauncher.launch(arrayOf("application/json"))
                    coroutineScope.launch { bottomSheetState.hide() }
                },
                onClose = {
                    coroutineScope.launch { bottomSheetState.hide() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.tyler,
            topBar = {
                AppBar(
                    title = stringResource(R.string.BackupManager_Title),
                    navigationIcon = {
                        HsBackButton(onClick = onBackClick)
                    },
                )
            }
        ) {
            Column(modifier = Modifier.padding(it)) {
                Spacer(modifier = Modifier.height(12.dp))
                CellUniversalLawrenceSection(
                    buildList {
                        add {
                            RowUniversal(onClick = { restoreLauncher.launch(arrayOf("application/json")) }) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    painter = painterResource(R.drawable.ic_download_20),
                                    contentDescription = null,
                                    tint = ComposeAppTheme.colors.jacob
                                )
                                body_jacob(text = stringResource(R.string.BackupManager_RestoreBackup))
                            }
                        }

                        add {
                            RowUniversal(onClick = onCreateBackup) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    painter = painterResource(R.drawable.ic_plus),
                                    contentDescription = null,
                                    tint = ComposeAppTheme.colors.jacob
                                )
                                body_jacob(text = stringResource(R.string.BackupManager_CreateBackup))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
