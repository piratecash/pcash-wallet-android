package cash.p.terminal.modules.xtransaction.cells

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.contacts.ContactsFragment
import cash.p.terminal.modules.contacts.ContactsModule
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.SelectorDialogCompose
import cash.p.terminal.ui.compose.components.SelectorItem
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import io.horizontalsystems.chartview.cell.CellUniversal
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.ui_compose.components.HudHelper

@Composable
fun AddressCell(
    title: String,
    value: String,
    showAddContactButton: Boolean,
    blockchainType: BlockchainType?,
    navController: NavController? = null,
    borderTop: Boolean = true
) {
    val view = LocalView.current
    var showSaveAddressDialog by remember { mutableStateOf(false) }
    CellUniversal(borderTop = borderTop) {
        subhead2_grey(text = title)

        HSpacer(16.dp)
        subhead1_leah(
            modifier = Modifier.weight(1f),
            text = value,
            textAlign = TextAlign.Right
        )

        if (showAddContactButton) {
            HSpacer(16.dp)
            ButtonSecondaryCircle(
                icon = R.drawable.icon_20_user_plus,
                onClick = { showSaveAddressDialog = true }
            )
        }

        HSpacer(16.dp)
        ButtonSecondaryCircle(
            icon = R.drawable.ic_copy_20,
            onClick = {
                TextHelper.copyText(value)
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            }
        )
    }

    if (showSaveAddressDialog) {
        SelectorDialogCompose(
            title = stringResource(R.string.Contacts_AddAddress),
            items = ContactsModule.AddAddressAction.entries.map {
                SelectorItem(stringResource(it.title), false, it)
            },
            onDismissRequest = {
                showSaveAddressDialog = false
            },
            onSelectItem = { action ->
                blockchainType?.let {
                    val args = when (action) {
                        ContactsModule.AddAddressAction.AddToNewContact -> {
                            ContactsFragment.Input(
                                Mode.AddAddressToNewContact(
                                    blockchainType,
                                    value
                                )
                            )
                        }

                        ContactsModule.AddAddressAction.AddToExistingContact -> {
                            ContactsFragment.Input(
                                Mode.AddAddressToExistingContact(
                                    blockchainType,
                                    value
                                )
                            )
                        }
                    }
                    navController?.slideFromRight(R.id.contactsFragment, args)
                }
            })
    }
}