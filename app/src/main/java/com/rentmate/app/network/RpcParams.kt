package com.rentmate.app.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** rpc() takes a JsonObject, not an arbitrary @Serializable type - this bridges the two. */
inline fun <reified T> T.toRpcParams(): JsonObject = Json.encodeToJsonElement(this) as JsonObject
