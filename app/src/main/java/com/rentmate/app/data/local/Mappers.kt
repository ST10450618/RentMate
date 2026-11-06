package com.rentmate.app.data.local

import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.ProfileNameRow
import com.rentmate.app.data.ShoppingItemRow

// RM-0016: conversions between the Supabase row types the UI already uses and
// the Room entities. Server rows always map to SYNCED entities. Money becomes
// cents on the way in and Rand only on the way out to the UI.

/** A server bill as a SYNCED [BillEntity] (shares are mapped by [toShareEntities]). */
fun BillWithSharesRow.toEntity(): BillEntity = BillEntity(
    id = id,
    household_id = household_id,
    title = title,
    amount_cents = amount.toCents(),
    due_date = due_date,
    split_method = split_method,
    issued_by_user_id = issued_by_user_id,
    created_at = created_at,
    server_updated_at = updated_at,
    syncState = SyncState.SYNCED,
    updatedAt = System.currentTimeMillis(),
    repeat = repeat,
    repeat_stopped = repeat_stopped
)

/** The bill's shares as SYNCED [BillShareEntity] rows, scoped to its household. */
fun BillWithSharesRow.toShareEntities(): List<BillShareEntity> = bill_shares.map { share ->
    BillShareEntity(
        id = share.id,
        bill_id = id,
        household_id = household_id,
        user_id = share.user_id,
        amount_owed_cents = share.amount_owed.toCents(),
        is_paid = share.is_paid,
        paid_at = share.paid_at,
        display_name = share.profiles?.display_name,
        syncState = SyncState.SYNCED,
        updatedAt = System.currentTimeMillis()
    )
}

/** A cached bill and its shares as the row type the Bills and Dashboard screens use. */
fun BillWithShares.toRow(): BillWithSharesRow = BillWithSharesRow(
    id = bill.id,
    household_id = bill.household_id,
    title = bill.title,
    amount = bill.amount_cents.centsToRand(),
    due_date = bill.due_date,
    split_method = bill.split_method,
    issued_by_user_id = bill.issued_by_user_id,
    created_at = bill.created_at,
    updated_at = bill.server_updated_at,
    repeat = bill.repeat,
    repeat_stopped = bill.repeat_stopped,
    bill_shares = shares
        .filter { it.syncState != SyncState.PENDING_DELETE }
        .map { share ->
            BillShareWithProfileRow(
                id = share.id,
                user_id = share.user_id,
                amount_owed = share.amount_owed_cents.centsToRand(),
                is_paid = share.is_paid,
                paid_at = share.paid_at,
                profiles = share.display_name?.let { ProfileNameRow(it) }
            )
        }
)

/** A server chore as a SYNCED [ChoreEntity]. */
fun ChoreRow.toEntity(): ChoreEntity = ChoreEntity(
    id = id,
    household_id = household_id,
    title = title,
    recurrence = recurrence,
    point_value = point_value,
    rotation_order = rotation_order,
    current_rotation_index = current_rotation_index,
    created_at = created_at,
    syncState = SyncState.SYNCED,
    updatedAt = System.currentTimeMillis()
)

/** A cached chore as the row type the Chores and Dashboard screens use. */
fun ChoreEntity.toRow(): ChoreRow = ChoreRow(
    id = id,
    household_id = household_id,
    title = title,
    recurrence = recurrence,
    point_value = point_value,
    rotation_order = rotation_order,
    current_rotation_index = current_rotation_index,
    created_at = created_at
)

/** A server shopping item as a SYNCED [ShoppingItemEntity], with names flattened. */
fun ShoppingItemRow.toEntity(): ShoppingItemEntity = ShoppingItemEntity(
    id = id,
    household_id = household_id,
    name = name,
    added_by_name = added_by_user_id?.display_name,
    is_purchased = is_purchased,
    purchased_by_name = purchased_by_user_id?.display_name,
    purchased_at = purchased_at,
    converted_to_bill_id = converted_to_bill_id,
    created_at = created_at,
    syncState = SyncState.SYNCED,
    updatedAt = System.currentTimeMillis()
)

/** A cached shopping item as the row type the Shopping List screen uses. */
fun ShoppingItemEntity.toRow(): ShoppingItemRow = ShoppingItemRow(
    id = id,
    household_id = household_id,
    name = name,
    added_by_user_id = added_by_name?.let { ProfileNameRow(it) },
    is_purchased = is_purchased,
    purchased_by_user_id = purchased_by_name?.let { ProfileNameRow(it) },
    purchased_at = purchased_at,
    converted_to_bill_id = converted_to_bill_id,
    created_at = created_at
)
