package com.rentmate.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room type converters for [SyncState] and string lists. */
class Converters {

    /** Converts a [SyncState] to its stored name. */
    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    /** Parses a stored name back into a [SyncState]. */
    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    /** Serialises a list of strings to JSON. */
    @TypeConverter
    fun listToJson(value: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), value)

    /** Parses a JSON array of strings. */
    @TypeConverter
    fun jsonToList(value: String): List<String> =
        Json.decodeFromString(ListSerializer(String.serializer()), value)
}
