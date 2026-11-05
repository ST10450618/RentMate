package com.rentmate.app.data.local

/** Where a locally cached row stands relative to the Supabase server. */
enum class SyncState {
    /** Identical to the server copy. */
    SYNCED,

    /** Created offline; not yet sent to the server. */
    PENDING_CREATE,

    /** Edited offline; the change has not yet been sent. */
    PENDING_UPDATE,

    /** Deleted offline; kept (hidden from the UI) until the delete is sent. */
    PENDING_DELETE
}
