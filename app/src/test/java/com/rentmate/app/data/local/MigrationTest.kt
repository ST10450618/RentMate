package com.rentmate.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val TEST_DB = "migration-test.db"

/**
 * RM-0016: the v1 -> v2 migration keeps every cached bill and share (including
 * offline edits) and turns Rand into exact cents. RM-0017: v2 -> v3 keeps queued
 * outbox entries and adds the replay fields and the member roster. RM-0018:
 * v3 -> v4 adds outbox photo paths and the rejection log.
 *
 * Room's MigrationTestHelper reads schemas from APK assets, which Robolectric
 * unit tests do not get from the test source set. So this test builds the v1
 * file itself from the committed schemas/.../1.json, then opens it with Room.
 * Room runs [MIGRATION_1_2] and checks the result against the v2 entities; a
 * mismatch throws IllegalStateException on open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private lateinit var context: Context
    private var room: RentMateDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(TEST_DB)
    }

    /** Creates a version 1 database exactly as the exported v1 schema describes it. */
    private fun createVersion1(seed: SQLiteDatabase.() -> Unit) = createVersion(1, seed)

    /** Creates a database at [version] exactly as the exported schema file describes it. */
    private fun createVersion(version: Int, seed: SQLiteDatabase.() -> Unit) {
        val schemaFile = File("schemas/com.rentmate.app.data.local.RentMateDatabase/$version.json")
        assertTrue("v$version schema not found at ${schemaFile.absolutePath}", schemaFile.isFile)
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject

        val path = context.getDatabasePath(TEST_DB).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            for (entity in database.getValue("entities").jsonArray) {
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            for (query in database.getValue("setupQueries").jsonArray) db.execSQL(query.jsonPrimitive.content)
            db.version = version
            db.seed()
        }
    }

    @Test
    fun migrate1To2_convertsMoneyToCentsAndKeepsRows() {
        createVersion1 {
            execSQL(
                "INSERT INTO bills (id, household_id, title, amount, due_date, split_method, issued_by_user_id, " +
                    "created_at, server_updated_at, syncState, updatedAt) VALUES " +
                    "('b1', 'h1', 'Rent', 123.45, '2026-01-01', 'EQUAL', 'u1', 'c', 'u', 'SYNCED', 1), " +
                    "('b2', 'h1', 'Wifi', 0.1, '2026-02-01', 'EQUAL', 'u1', 'c', 'u', 'PENDING_CREATE', 2)"
            )
            execSQL(
                "INSERT INTO bill_shares (id, bill_id, household_id, user_id, amount_owed, is_paid, paid_at, " +
                    "display_name, syncState, updatedAt) VALUES " +
                    "('s1', 'b1', 'h1', 'u2', 61.73, 0, NULL, 'Thandi', 'PENDING_UPDATE', 3), " +
                    "('s2', 'b1', 'h1', 'u3', 61.72, 1, 'p', NULL, 'SYNCED', 4)"
            )
            execSQL(
                "INSERT INTO chores (id, household_id, title, recurrence, point_value, rotation_order, " +
                    "current_rotation_index, created_at, syncState, updatedAt) VALUES " +
                    "('c1', 'h1', 'Dishes', 'WEEKLY', 5, '[\"u1\"]', 0, 'c', 'SYNCED', 1)"
            )
        }

        val db = Room.databaseBuilder(context, RentMateDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = db

        val bills = runBlocking { db.billDao().observeBills("h1").first() }.associateBy { it.bill.id }
        assertEquals(12345L, bills.getValue("b1").bill.amount_cents)
        assertEquals(10L, bills.getValue("b2").bill.amount_cents)
        assertEquals(SyncState.PENDING_CREATE, bills.getValue("b2").bill.syncState)
        assertEquals("Never", bills.getValue("b1").bill.repeat)

        val shares = bills.getValue("b1").shares.associateBy { it.id }
        assertEquals(6173L, shares.getValue("s1").amount_owed_cents)
        assertEquals(6172L, shares.getValue("s2").amount_owed_cents)
        assertEquals(SyncState.PENDING_UPDATE, shares.getValue("s1").syncState)
        assertEquals("Thandi", shares.getValue("s1").display_name)

        // Untouched tables come through as they were.
        assertEquals(listOf("c1"), runBlocking { db.choreDao().observeChores("h1").first() }.map { it.id })
    }

    @Test
    fun migratedDatabase_keepsShareCascadeOnBillDelete() {
        createVersion1 {
            execSQL(
                "INSERT INTO bills (id, household_id, title, amount, due_date, split_method, issued_by_user_id, " +
                    "created_at, server_updated_at, syncState, updatedAt) VALUES " +
                    "('b1', 'h1', 'Rent', 10.0, '2026-01-01', 'EQUAL', 'u1', 'c', 'u', 'SYNCED', 1)"
            )
            execSQL(
                "INSERT INTO bill_shares (id, bill_id, household_id, user_id, amount_owed, is_paid, paid_at, " +
                    "display_name, syncState, updatedAt) VALUES ('s1', 'b1', 'h1', 'u2', 5.0, 0, NULL, NULL, 'SYNCED', 1)"
            )
        }
        val db = Room.databaseBuilder(context, RentMateDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = db

        runBlocking {
            db.billDao().deleteBill("b1")
            db.billDao().upsertBills(listOf(BillEntity("b1", "h1", "Rent", 1_000L, "d", "EQUAL", "u1", "c", "u")))
            assertTrue(db.billDao().observeBills("h1").first().single().shares.isEmpty())
        }
    }

    @Test
    fun migrate2To3_keepsOutboxEntriesAndRows() {
        createVersion(2) {
            execSQL(
                "INSERT INTO outbox (op, tableName, payloadJson, createdAt) VALUES " +
                    "('INSERT', 'shopping_items', '{\"name\":\"Milk\"}', 5)"
            )
            execSQL(
                "INSERT INTO shopping_items (id, household_id, name, added_by_name, is_purchased, purchased_by_name, " +
                    "purchased_at, converted_to_bill_id, created_at, syncState, updatedAt) VALUES " +
                    "('i1', 'h1', 'Milk', NULL, 0, NULL, NULL, NULL, 'c', 'PENDING_CREATE', 1)"
            )
        }

        val db = Room.databaseBuilder(context, RentMateDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = db

        runBlocking {
            val entry = db.outboxDao().getAllOldestFirst().single()
            assertEquals("INSERT", entry.op)
            assertEquals("{\"name\":\"Milk\"}", entry.payloadJson)
            assertEquals("", entry.entityId)
            assertEquals(0, entry.attemptCount)
            assertEquals(null, entry.lastError)
            assertEquals(SyncState.PENDING_CREATE, db.shoppingItemDao().getById("i1")?.syncState)

            // The new roster table works.
            db.memberDao().upsert(listOf(HouseholdMemberEntity("h1", "u1", "Sipho")))
            assertEquals("Sipho", db.memberDao().getMember("h1", "u1")?.display_name)
        }
    }

    @Test
    fun migrate3To4_keepsOutboxEntriesAndAddsPhotoPathsAndRejections() {
        createVersion(3) {
            execSQL(
                "INSERT INTO outbox (op, tableName, payloadJson, createdAt, entityId, attemptCount, lastError) VALUES " +
                    "('RPC', 'create_bill', '{}', 7, 'b1', 2, 'HTTP 500')"
            )
            execSQL(
                "INSERT INTO bills (id, household_id, title, amount_cents, due_date, split_method, issued_by_user_id, " +
                    "created_at, server_updated_at, syncState, updatedAt, repeat, repeat_stopped) VALUES " +
                    "('b1', 'h1', 'Rent', 1000, 'd', 'Equal', 'u1', 'c', '', 'PENDING_CREATE', 1, 'Never', 0)"
            )
        }

        val db = Room.databaseBuilder(context, RentMateDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = db

        runBlocking {
            val entry = db.outboxDao().getAllOldestFirst().single()
            assertEquals("b1", entry.entityId)
            assertEquals(2, entry.attemptCount)
            assertEquals("HTTP 500", entry.lastError)
            assertEquals(emptyList<String>(), entry.attachmentPaths)
            assertEquals(SyncState.PENDING_CREATE, db.billDao().getBill("b1")?.syncState)

            // The new column and table work.
            db.outboxDao().enqueue(entry.copy(id = 0, attachmentPaths = listOf("h1/bill/p.jpg")))
            assertEquals(listOf("h1/bill/p.jpg"), db.outboxDao().getAllOldestFirst().last().attachmentPaths)
            db.syncRejectionDao().insert(
                SyncRejectionEntity(op = "RPC", tableName = "create_bill", entityId = "b1", reason = "INVALID", rejectedAt = 1)
            )
            assertEquals("INVALID", db.syncRejectionDao().observeAll().first().single().reason)
        }
    }

    @Test
    fun migrate1To3_runsBothSteps() {
        createVersion1 {
            execSQL(
                "INSERT INTO bills (id, household_id, title, amount, due_date, split_method, issued_by_user_id, " +
                    "created_at, server_updated_at, syncState, updatedAt) VALUES " +
                    "('b1', 'h1', 'Rent', 10.5, '2026-01-01', 'EQUAL', 'u1', 'c', 'u', 'SYNCED', 1)"
            )
        }
        val db = Room.databaseBuilder(context, RentMateDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = db
        assertEquals(1_050L, runBlocking { db.billDao().observeBills("h1").first() }.single().bill.amount_cents)
    }
}
