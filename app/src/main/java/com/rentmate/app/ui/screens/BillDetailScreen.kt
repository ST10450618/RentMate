package com.rentmate.app.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rentmate.app.R
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.isRepeating
import com.rentmate.app.ui.components.AttachmentThumbnails
import com.rentmate.app.ui.components.ListDivider
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRandShort
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "BillDetailScreen"

/**
 * RM-0057: one bill, opened by tapping its row on Money (mockup "Bill detail").
 *
 * The big figure is the signed-in user's share. Below it are the due pill, who
 * added the bill and how it is split, then one row per share with a Paid or
 * Owes pill, and the photo if there is one.
 *
 * Only the person who added the bill marks shares paid (RM-0010): they see a
 * Mark paid button on each unpaid share, and marking one shows a 5 s Undo
 * snackbar. Everyone else sees whom to ask. The issuer can also stop a
 * repeating bill (RM-0049). A bill saved offline (RM-0017) shows "Waiting to
 * sync" and offers no server actions until it uploads.
 *
 * BillsScreen shows this as a full-screen state, because the navigation graph
 * has no BillDetail route yet.
 *
 * @param bill the bill to show.
 * @param currentUserId the signed-in user, or null before the session loads.
 * @param photos thumbnails for this bill (RM-0029).
 * @param isPaid true when a share is paid on the server or inside its Undo window.
 * @param onPay marks one share paid, with Undo.
 * @param onStopRepeat stops a repeating bill.
 * @param onBack returns to Money.
 */
@Composable
fun BillDetailScreen(
    bill: BillWithSharesRow,
    currentUserId: String?,
    photos: List<PhotoLink>,
    isPaid: (shareId: String, isPaid: Boolean) -> Boolean,
    onPay: (String) -> Unit,
    onStopRepeat: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(bill.id) { Log.d(TAG, "open bill=${bill.id}") }
    val housemate = stringResource(R.string.housemate_fallback)
    val issuerName = bill.bill_shares.firstOrNull { it.user_id == bill.issued_by_user_id }?.profiles?.display_name ?: housemate
    val isIssuer = bill.issued_by_user_id == currentUserId
    val canMarkPaid = isIssuer && !bill.pending
    val myShare = bill.bill_shares.firstOrNull { it.user_id == currentUserId }
    val myShareOpen = myShare != null && !isPaid(myShare.id, myShare.is_paid)

    Scaffold(topBar = { ScreenHeader(title = bill.title, onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.money_detail_your_share),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatRandShort(myShare?.amount_owed ?: 0.0),
                    style = MaterialTheme.typography.displaySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        stringResource(R.string.bills_due, longDate(bill.due_date)),
                        if (myShareOpen) PillTone.Attention else PillTone.Neutral
                    )
                    if (bill.pending) StatusPill(stringResource(R.string.sync_pending), PillTone.Neutral)
                }
                Text(
                    stringResource(
                        R.string.money_detail_added_split,
                        issuerName,
                        stringResource(splitLabel(bill.split_method)),
                        formatRandShort(bill.amount)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ListHeading(stringResource(R.string.money_detail_shares))
            ListGroup {
                bill.bill_shares.forEachIndexed { index, share ->
                    if (index > 0) ListDivider()
                    val name = share.profiles?.display_name ?: housemate
                    val paid = isPaid(share.id, share.is_paid)
                    ListRow(
                        title = name,
                        supporting = formatRandShort(share.amount_owed),
                        leading = { MemberAvatar(name, userId = share.user_id) },
                        trailing = {
                            when {
                                paid -> StatusPill(stringResource(R.string.bills_paid), PillTone.Positive)
                                canMarkPaid -> SecondaryButton(
                                    stringResource(R.string.bills_mark_paid),
                                    onClick = { onPay(share.id) }
                                )
                                else -> StatusPill(stringResource(R.string.bills_owes), PillTone.Attention)
                            }
                        }
                    )
                }
            }

            if (photos.isNotEmpty()) AttachmentThumbnails(photos)

            if (canMarkPaid && isRepeating(bill.repeat, bill.repeat_stopped)) {
                // RM-0049: only the issuer can stop a bill repeating.
                SecondaryButton(
                    stringResource(R.string.bills_stop_repeat),
                    onClick = { onStopRepeat(bill.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!isIssuer && myShareOpen) {
                // RM-0010: only the person owed can mark a share paid, so say whom to ask.
                Text(
                    stringResource(R.string.bills_ask_to_confirm, issuerName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The split label for a bill's split method: "split equally", "split by percentage" or "exact amounts". */
private fun splitLabel(splitMethod: String): Int = when (splitMethod) {
    SplitMode.Percentage.wire -> R.string.money_split_percent
    SplitMode.ExactAmount.wire -> R.string.money_split_exact
    else -> R.string.money_split_equally
}

/** "1 Dec 2026" in the app language, or the raw date if it cannot be read. */
@Composable
private fun longDate(isoDate: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(isoDate, locale) {
        runCatching { LocalDate.parse(isoDate.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)) }
            .getOrDefault(isoDate.take(10))
    }
}
