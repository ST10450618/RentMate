package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.theme.Sizes
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "AccountAvatar"

/** The signed-in user as the avatar shows them. */
data class AccountBadge(val userId: String?, val name: String)

/**
 * RM-0056: reads who is signed in, for the avatar that opens Settings.
 *
 * It reads the session each time it is asked, because the app scaffold
 * creates this ViewModel once, possibly before the user signs in.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(private val supabase: SupabaseClient) : ViewModel() {
    /** The current user's id and display name. The name is empty when signed out. */
    fun badge(): AccountBadge {
        val user = supabase.auth.currentUserOrNull()
        val name = listOf("full_name", "name")
            .firstNotNullOfOrNull { key -> runCatching { user?.userMetadata?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull() }
            ?: user?.email
            ?: ""
        return AccountBadge(user?.id, name)
    }
}

/**
 * The user's avatar at the end of a tab screen's header. Tapping it opens
 * Settings, which replaced the More tab (RM-0056). TalkBack reads it as the
 * "Settings" button; the touch target is 48 dp.
 */
@Composable
fun SettingsAvatarButton(onClick: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val badge = viewModel.badge()
    val label = stringResource(R.string.settings_title)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = Sizes.minTouch, minHeight = Sizes.minTouch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = {
                Log.d(TAG, "open Settings from avatar")
                onClick()
            })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        MemberAvatar(badge.name, size = Sizes.avatarHeader, userId = badge.userId)
    }
}
