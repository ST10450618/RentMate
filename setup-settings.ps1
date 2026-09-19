# setup-settings.ps1
# Builds the RentMate Settings screen (S10): DataStore-backed preferences,
# a ViewModel, the Compose UI, string resources in en/af/xh, and a unit test.
#
# Run from the project root:
#   powershell -ExecutionPolicy Bypass -File .\setup-settings.ps1
#
# Overwrites ui/screens/SettingsScreen.kt and res/values/strings.xml.
# Commit before running if you want an easy undo.

$ErrorActionPreference = 'Stop'
$base = "app\src\main\java\com\rentmate\app"
$res  = "app\src\main\res"
$test = "app\src\test\java\com\rentmate\app"

if (-not (Test-Path "$base\MainActivity.kt")) {
    Write-Host "Can't find $base\MainActivity.kt - are you in the project root?" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path "$base\data" | Out-Null
New-Item -ItemType Directory -Force -Path "$res\values-af" | Out-Null
New-Item -ItemType Directory -Force -Path "$res\values-xh" | Out-Null
New-Item -ItemType Directory -Force -Path "$test" | Out-Null

function Write-Kt($path, $text) {
    Set-Content -Path $path -Value $text -Encoding UTF8
    Write-Host "  wrote $path"
}

# -------------------- data layer

Write-Kt "$base\data\AppLanguage.kt" @'
package com.rentmate.app.data

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R

private const val TAG = "AppLanguage"

/**
 * The languages RentMate ships (US-4). Two of the three are official South
 * African languages, which is the module requirement.
 *
 * The tag is a BCP-47 language tag and must match the resource folder suffix:
 * "af" -> values-af, "xh" -> values-xh, English is the default in values/.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    ENGLISH("en", R.string.lang_english),
    AFRIKAANS("af", R.string.lang_afrikaans),
    XHOSA("xh", R.string.lang_xhosa);

    companion object {
        /** Falls back to English for a null or unrecognised tag. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/**
 * Applies the chosen language at runtime.
 *
 * Android 13 (API 33) and above exposes per-app languages through LocaleManager,
 * so the choice survives restarts and appears in system settings. Below 33 the
 * preference is still stored, but applying it needs AppCompat's
 * setApplicationLocales plus a manifest service entry - deferred to the final
 * POE, where multi-language support is actually assessed.
 */
fun applyLanguage(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
        Log.d(TAG, "applied locale ${language.tag} via LocaleManager")
    } else {
        Log.d(TAG, "stored locale ${language.tag}; runtime switch needs AppCompat on API < 33")
    }
}
'@

Write-Kt "$base\data\SettingToggle.kt" @'
package com.rentmate.app.data

import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.rentmate.app.R

/** Sections of the Settings screen, in display order. */
enum class SettingGroup(@StringRes val titleRes: Int) {
    NOTIFICATIONS(R.string.section_notifications),
    SECURITY_DISPLAY(R.string.section_security)
}

/**
 * Every boolean preference in one place: its storage key, its default, the
 * strings that label it, and the section it belongs to. Adding a setting means
 * adding one entry here - the screen renders whatever this enum contains.
 *
 * Notification preferences are per category, as US-3 requires.
 */
enum class SettingToggle(
    val key: Preferences.Key<Boolean>,
    val default: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int?,
    val group: SettingGroup
) {
    NOTIFY_BILLS(
        booleanPreferencesKey("notify_bills"), true,
        R.string.notif_bills, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_CHORES(
        booleanPreferencesKey("notify_chores"), true,
        R.string.notif_chores, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_SHOPPING(
        booleanPreferencesKey("notify_shopping"), false,
        R.string.notif_shopping, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_MAINTENANCE(
        booleanPreferencesKey("notify_maintenance"), true,
        R.string.notif_maintenance, null, SettingGroup.NOTIFICATIONS
    ),

    // Off by default: US-2 requires one successful SSO sign-in before biometrics
    // can be enabled, so the user opts in deliberately.
    BIOMETRIC_UNLOCK(
        booleanPreferencesKey("biometric_unlock"), false,
        R.string.biometric_title, R.string.biometric_desc, SettingGroup.SECURITY_DISPLAY
    ),

    // US-12: hiding the leaderboard does not stop points accruing.
    SHOW_LEADERBOARD(
        booleanPreferencesKey("show_leaderboard"), true,
        R.string.leaderboard_title, R.string.leaderboard_desc, SettingGroup.SECURITY_DISPLAY
    );

    companion object {
        fun inGroup(group: SettingGroup): List<SettingToggle> = entries.filter { it.group == group }
    }
}
'@

Write-Kt "$base\data\SettingsRepository.kt" @'
package com.rentmate.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "rentmate_settings")

private val LANGUAGE_KEY = stringPreferencesKey("language")

/** Immutable snapshot of everything on the Settings screen. */
data class UserSettings(
    val toggles: Map<SettingToggle, Boolean> = SettingToggle.entries.associateWith { it.default },
    val language: AppLanguage = AppLanguage.ENGLISH
) {
    fun isOn(toggle: SettingToggle): Boolean = toggles[toggle] ?: toggle.default
}

/**
 * Preferences are stored locally with DataStore so they survive restart.
 *
 * US-3 also requires them to sync to the account so they follow the user to a
 * new device; that write goes through Seth's API and is wired up once the
 * endpoint exists. Storing locally first is the offline-first pattern the whole
 * app follows - the UI never waits on the network.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            toggles = SettingToggle.entries.associateWith { prefs[it.key] ?: it.default },
            language = AppLanguage.fromTag(prefs[LANGUAGE_KEY])
        )
    }

    suspend fun setToggle(toggle: SettingToggle, value: Boolean) {
        context.settingsDataStore.edit { it[toggle.key] = value }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[LANGUAGE_KEY] = language.tag }
    }
}
'@

# -------------------- view model

Write-Kt "$base\ui\screens\SettingsViewModel.kt" @'
package com.rentmate.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.UserSettings
import com.rentmate.app.data.applyLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "SettingsViewModel"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val state: StateFlow<UserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    fun setToggle(toggle: SettingToggle, value: Boolean) {
        Log.d(TAG, "setToggle ${toggle.name} = $value")
        viewModelScope.launch { repository.setToggle(toggle, value) }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        viewModelScope.launch {
            repository.setLanguage(language)
            applyLanguage(getApplication(), language)
        }
    }
}
'@

# -------------------- screen

Write-Kt "$base\ui\screens\SettingsScreen.kt" @'
package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rentmate.app.R
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingGroup
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.UserSettings

/**
 * S10 - Settings. Delivers US-3 (preferences) and US-4 (language), and holds the
 * biometric opt-in for US-2.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    SettingsContent(
        settings = settings,
        onToggle = viewModel::setToggle,
        onLanguage = viewModel::setLanguage
    )
}

/** Stateless body, so it can be previewed and tested without a ViewModel. */
@Composable
fun SettingsContent(
    settings: UserSettings,
    onToggle: (SettingToggle, Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )

        SettingGroup.entries.forEach { group ->
            SettingsSection(title = stringResource(group.titleRes)) {
                SettingToggle.inGroup(group).forEachIndexed { index, toggle ->
                    if (index > 0) HorizontalDivider()
                    ToggleRow(
                        title = stringResource(toggle.titleRes),
                        description = toggle.descRes?.let { stringResource(it) },
                        checked = settings.isOn(toggle),
                        onCheckedChange = { onToggle(toggle, it) }
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.section_language)) {
            Row3 {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = settings.language == language,
                        onClick = { onLanguage(language) },
                        label = { Text(stringResource(language.labelRes)) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { /* Michael wires this to sign-out in Stage 2 */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.sign_out))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Row3(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
'@

# -------------------- strings

Write-Kt "$res\values\strings.xml" @'
<resources>
    <string name="app_name">RentMate</string>

    <string name="settings_title">Settings</string>
    <string name="section_notifications">Notify me about</string>
    <string name="notif_bills">Bills due</string>
    <string name="notif_chores">Chores assigned to me</string>
    <string name="notif_shopping">Shopping list changes</string>
    <string name="notif_maintenance">Maintenance updates</string>

    <string name="section_language">Language</string>
    <string name="lang_english">English</string>
    <string name="lang_afrikaans">Afrikaans</string>
    <string name="lang_xhosa">isiXhosa</string>

    <string name="section_security">Security and display</string>
    <string name="biometric_title">Unlock with biometrics</string>
    <string name="biometric_desc">Fingerprint or face</string>
    <string name="leaderboard_title">Show points leaderboard</string>
    <string name="leaderboard_desc">Hides it for the whole household</string>

    <string name="sign_out">Sign out</string>
</resources>
'@

Write-Kt "$res\values-af\strings.xml" @'
<resources>
    <string name="settings_title">Instellings</string>
    <string name="section_notifications">Stel my in kennis oor</string>
    <string name="notif_bills">Rekeninge wat betaalbaar is</string>
    <string name="notif_chores">Take wat aan my toegewys is</string>
    <string name="notif_shopping">Veranderinge aan die inkopielys</string>
    <string name="notif_maintenance">Opdaterings oor onderhoud</string>

    <string name="section_language">Taal</string>
    <string name="lang_english">Engels</string>
    <string name="lang_afrikaans">Afrikaans</string>
    <string name="lang_xhosa">isiXhosa</string>

    <string name="section_security">Sekuriteit en vertoning</string>
    <string name="biometric_title">Ontsluit met biometrie</string>
    <string name="biometric_desc">Vingerafdruk of gesig</string>
    <string name="leaderboard_title">Wys die puntelys</string>
    <string name="leaderboard_desc">Versteek dit vir die hele huishouding</string>

    <string name="sign_out">Teken uit</string>
</resources>
'@

Write-Kt "$res\values-xh\strings.xml" @'
<resources>
    <string name="settings_title">Useto</string>
    <string name="section_notifications">Ndazise ngazo</string>
    <string name="notif_bills">Iibhili ekufuneka zihlawulwe</string>
    <string name="notif_chores">Imisebenzi endabelwe yona</string>
    <string name="notif_shopping">Utshintsho kuluhlu lokuthenga</string>
    <string name="notif_maintenance">Uhlaziyo ngolungiso</string>

    <string name="section_language">Ulwimi</string>
    <string name="lang_english">IsiNgesi</string>
    <string name="lang_afrikaans">IsiBhulu</string>
    <string name="lang_xhosa">IsiXhosa</string>

    <string name="section_security">Ukhuseleko nomboniso</string>
    <string name="biometric_title">Vula ngebhayometriki</string>
    <string name="biometric_desc">Umnwe okanye ubuso</string>
    <string name="leaderboard_title">Bonisa ibhodi yamanqaku</string>
    <string name="leaderboard_desc">Iyifihla kuyo yonke indlu</string>

    <string name="sign_out">Phuma</string>
</resources>
'@

# -------------------- unit test

Write-Kt "$test\AppLanguageTest.kt" @'
package com.rentmate.app

import com.rentmate.app.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US-4 requires the app to default to a supported language and fall back to
 * English for anything unsupported. These run on the JVM, so they execute in
 * GitHub Actions on every push.
 */
class AppLanguageTest {

    @Test
    fun `known tags map to their language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromTag("af"))
        assertEquals(AppLanguage.XHOSA, AppLanguage.fromTag("xh"))
    }

    @Test
    fun `unsupported tag falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("zu"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(""))
    }

    @Test
    fun `absent preference falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
    }

    @Test
    fun `tags are unique and non-blank`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertTrue(tags.none { it.isBlank() })
    }

    @Test
    fun `at least two South African languages are supported`() {
        val saLanguages = AppLanguage.entries.filter { it.tag in setOf("af", "xh", "zu", "st", "tn") }
        assertTrue(saLanguages.size >= 2)
    }
}
'@

Write-Host ""
Write-Host "Done. Add the DataStore and lifecycle dependencies (see the chat), then sync and build." -ForegroundColor Green
