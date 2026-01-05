package cash.p.terminal.feature.logging.history

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cash.p.terminal.feature.logging.R
import cash.p.terminal.feature.logging.components.DeleteLogsConfirmationDialog
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.flowOf
import java.io.File

@Composable
fun LoggingListScreen(
    loginRecords: LazyPagingItems<LoginRecordViewItem>,
    onDeleteAllClick: () -> Unit,
    onDeleteClick: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
    onClose: () -> Unit
) {
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var deleteRecordId by remember { mutableStateOf<Long?>(null) }
    var revealedCardId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.authorization_information),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (loginRecords.itemCount == 0) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        bottom = 116.dp
                    )
                ) {
                    items(
                        count = loginRecords.itemCount,
                        key = { loginRecords.peek(it)?.id ?: it }
                    ) { index ->
                        loginRecords[index]?.let { item ->
                            SwipableLoginRecordItem(
                                modifier = Modifier.animateItem(),
                                item = item,
                                revealed = revealedCardId == item.id,
                                onReveal = { id -> revealedCardId = id },
                                onConceal = { revealedCardId = null },
                                onDelete = { deleteRecordId = item.id },
                                onClick = { onItemClick(item.id) }
                            )
                        }
                    }
                }

                ButtonPrimaryRed(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    title = stringResource(id = R.string.delete_all),
                    onClick = {
                        showDeleteAllConfirmation = true
                    }
                )
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirmation) {
        DeleteLogsConfirmationDialog(
            onConfirm = {
                onDeleteAllClick()
                showDeleteAllConfirmation = false
            },
            onDismiss = { showDeleteAllConfirmation = false }
        )
    }

    // Delete Single Record Confirmation Dialog
    deleteRecordId?.let { recordId ->
        DeleteLogsConfirmationDialog(
            text = stringResource(R.string.login_logging_delete_record_confirmation),
            onConfirm = {
                onDeleteClick(recordId)
                deleteRecordId = null
                revealedCardId = null
            },
            onDismiss = { deleteRecordId = null }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = ComposeAppTheme.colors.raina,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(R.drawable.ic_user_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey
            )
        }
        VSpacer(32.dp)
        subhead2_grey(
            text = stringResource(R.string.auth_info_no_records),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwipableLoginRecordItem(
    item: LoginRecordViewItem,
    revealed: Boolean,
    onReveal: (Long) -> Unit,
    onConceal: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HsIconButton(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .width(88.dp),
            onClick = onDelete,
            content = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(id = R.drawable.ic_delete_20),
                    tint = ComposeAppTheme.colors.lucian,
                    contentDescription = "delete",
                )
            }
        )
        DraggableCardSimple(
            key = item.id,
            isRevealed = revealed,
            cardOffset = 88f,
            onReveal = { onReveal(item.id) },
            onConceal = onConceal,
            content = {
                LoginRecordItemContent(
                    item = item,
                    onClick = onClick
                )
            }
        )
    }
}

@Composable
private fun LoginRecordItemContent(
    item: LoginRecordViewItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photo/placeholder
        LoginRecordPhoto(
            photoPath = item.photoPath,
            modifier = Modifier.size(60.dp)
        )

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            // Status with different colors for status and duress mode
            val statusColor = if (item.isSuccessful) {
                ComposeAppTheme.colors.remus
            } else {
                ComposeAppTheme.colors.lucian
            }
            val statusText = if (item.isSuccessful) {
                stringResource(R.string.auth_info_successful)
            } else {
                stringResource(R.string.auth_info_unsuccessful)
            }
            val duressText = stringResource(R.string.auth_info_duress_mode)

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = statusColor)) {
                        append(statusText)
                    }
                    if (item.isDuressMode) {
                        withStyle(SpanStyle(color = ComposeAppTheme.colors.grey)) {
                            append(" ")
                            append(duressText)
                        }
                    }
                },
                style = ComposeAppTheme.typography.body
            )

            VSpacer(4.dp)

            // Wallet name
            body_leah(
                text = stringResource(R.string.auth_info_entrance_to, item.walletName)
            )

            VSpacer(4.dp)

            // Timestamp
            subhead2_grey(
                text = "${item.formattedTime} ${item.relativeTime}"
            )
        }
    }
}

@Composable
private fun LoginRecordPhoto(
    photoPath: String?,
    modifier: Modifier = Modifier
) {
    if (photoPath != null) {
        Image(
            painter = rememberAsyncImagePainter(
                model = File(photoPath),
                error = painterResource(R.drawable.ic_no_image)
            ),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_no_image),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoggingListScreenPreview() {
    val previewItems = listOf(
        LoginRecordViewItem(
            id = 1,
            photoPath = null,
            isSuccessful = true,
            isDuressMode = false,
            walletName = "Wallet 1",
            formattedTime = "11:15 13.08.2025",
            relativeTime = "(25 minutes ago)"
        ),
        LoginRecordViewItem(
            id = 2,
            photoPath = null,
            isSuccessful = false,
            isDuressMode = true,
            walletName = "Wallet 1",
            formattedTime = "09:15 13.08.2025",
            relativeTime = "(2 hours ago)"
        )
    )

    ComposeAppTheme {
        LoggingListScreen(
            loginRecords = flowOf(PagingData.from(previewItems)).collectAsLazyPagingItems(),
            onDeleteAllClick = {},
            onDeleteClick = {},
            onItemClick = {},
            onClose = {}
        )
    }
}
