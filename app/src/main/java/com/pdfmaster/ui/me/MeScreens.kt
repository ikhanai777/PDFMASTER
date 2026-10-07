package com.pdfmaster.ui.me

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmaster.BuildConfig
import com.pdfmaster.R
import com.pdfmaster.billing.Plan
import com.pdfmaster.data.Profile
import com.pdfmaster.data.ThemeMode
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.Routes
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.ScreenScaffold
import com.pdfmaster.ui.common.SectionLabel
import com.pdfmaster.ui.tools.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun MeScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val isPro by container.billing.isPro.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.tab_me), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        Card(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { actions.nav.navigate(Routes.PAYWALL) },
            colors = CardDefaults.cardColors(containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkspacePremium, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (isPro) R.string.pro_active else R.string.free_plan), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (isPro) R.string.pro_active_body else R.string.free_plan_body), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        MeRow(Icons.Default.Draw, R.string.signatures, R.string.signatures_desc) { actions.nav.navigate(Routes.SIGNATURES) }
        MeRow(Icons.Default.Badge, R.string.autofill_profile, R.string.autofill_profile_desc) { actions.nav.navigate(Routes.PROFILE) }
        MeRow(Icons.Default.Settings, R.string.settings, R.string.settings_desc) { actions.nav.navigate(Routes.SETTINGS) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
            headlineContent = { Text(stringResource(R.string.privacy_title)) },
            supportingContent = { Text(stringResource(R.string.privacy_body)) },
        )
        Text(
            stringResource(R.string.version_x, BuildConfig.VERSION_NAME),
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeRow(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

// ------------------------------------------------------------------ paywall

private data class PlanUi(val plan: Plan, val title: Int, val listPrice: String, val note: Int)

@Composable
fun PaywallScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPro by container.billing.isPro.collectAsState()
    val products by container.billing.products.collectAsState()
    val debugPro by container.prefs.debugPro.collectAsState()
    val message by container.billing.message.collectAsState()

    LaunchedEffect(Unit) { container.billing.connect() }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); container.billing.clearMessage() }
    }

    val plans = listOf(
        PlanUi(Plan.YEARLY, R.string.plan_yearly, "$34.99", R.string.plan_yearly_note),
        PlanUi(Plan.MONTHLY, R.string.plan_monthly, "$5.99", R.string.plan_monthly_note),
        PlanUi(Plan.LIFETIME, R.string.plan_lifetime, "$69.99", R.string.plan_lifetime_note),
    )
    val features = listOf(
        R.string.feature_all_tools, R.string.feature_unlimited, R.string.feature_watermark_numbers,
        R.string.feature_protect, R.string.feature_flatten, R.string.feature_coming,
    )

    ScreenScaffold(title = stringResource(R.string.pdfmaster_pro), onBack = actions::back) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isPro) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(stringResource(R.string.pro_active_body), Modifier.padding(16.dp))
                }
            }
            Text(stringResource(R.string.paywall_free_promise), style = MaterialTheme.typography.bodyMedium)
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(f))
                }
            }
            plans.forEach { p ->
                val price = products[p.plan]?.let { container.billing.formattedPrice(p.plan) } ?: p.listPrice
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(p.title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(p.note), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(enabled = !isPro, onClick = {
                            val activity = context as? Activity ?: return@Button
                            if (!container.billing.launch(activity, p.plan)) {
                                Toast.makeText(context, R.string.store_unavailable, Toast.LENGTH_LONG).show()
                            }
                        }) { Text(price) }
                    }
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    container.billing.refreshPurchases()
                    Toast.makeText(context, R.string.purchases_restored, Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_purchases)) }
            Text(stringResource(R.string.paywall_legal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (BuildConfig.DEBUG) {
                SwitchRow("Debug: unlock Pro (debug builds only)", debugPro) { container.prefs.setDebugPro(it) }
            }
        }
    }
}

// ------------------------------------------------------------------ profile

@Composable
fun ProfileScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { profile = container.profile.load() }

    ScreenScaffold(
        title = stringResource(R.string.autofill_profile),
        onBack = actions::back,
        actions = {
            TextButton(enabled = profile != null, onClick = {
                scope.launch {
                    profile?.let { container.profile.save(it) }
                    Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                    actions.back()
                }
            }) { Text(stringResource(R.string.save)) }
        },
    ) { modifier ->
        val p = profile ?: return@ScreenScaffold
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.profile_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fields: List<Triple<Int, String, (String) -> Profile>> = listOf(
                Triple(R.string.p_full_name, p.fullName) { v -> p.copy(fullName = v) },
                Triple(R.string.p_first_name, p.firstName) { v -> p.copy(firstName = v) },
                Triple(R.string.p_last_name, p.lastName) { v -> p.copy(lastName = v) },
                Triple(R.string.p_email, p.email) { v -> p.copy(email = v) },
                Triple(R.string.p_phone, p.phone) { v -> p.copy(phone = v) },
                Triple(R.string.p_address, p.address) { v -> p.copy(address = v) },
                Triple(R.string.p_city, p.city) { v -> p.copy(city = v) },
                Triple(R.string.p_postal, p.postalCode) { v -> p.copy(postalCode = v) },
                Triple(R.string.p_country, p.country) { v -> p.copy(country = v) },
                Triple(R.string.p_dob, p.dateOfBirth) { v -> p.copy(dateOfBirth = v) },
                Triple(R.string.p_id, p.idNumber) { v -> p.copy(idNumber = v) },
                Triple(R.string.p_company, p.company) { v -> p.copy(company = v) },
            )
            fields.forEach { (label, value, update) ->
                OutlinedTextField(value, { profile = update(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true)
            }
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val theme by container.prefs.themeMode.collectAsState()
    val dynamic by container.prefs.dynamicColor.collectAsState()
    val lock by container.prefs.appLock.collectAsState()

    ScreenScaffold(title = stringResource(R.string.settings), onBack = actions::back) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            SectionLabel(stringResource(R.string.appearance))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(theme == ThemeMode.SYSTEM, { container.prefs.setThemeMode(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
                FilterChip(theme == ThemeMode.LIGHT, { container.prefs.setThemeMode(ThemeMode.LIGHT) }, label = { Text(stringResource(R.string.theme_light)) })
                FilterChip(theme == ThemeMode.DARK, { container.prefs.setThemeMode(ThemeMode.DARK) }, label = { Text(stringResource(R.string.theme_dark)) })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SwitchRow(stringResource(R.string.dynamic_colour), dynamic) { container.prefs.setDynamicColor(it) }

            SectionLabel(stringResource(R.string.security))
            SwitchRow(stringResource(R.string.app_lock), lock) { on ->
                val can = BiometricManager.from(context).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
                if (on && !can) Toast.makeText(context, R.string.no_screen_lock, Toast.LENGTH_LONG).show()
                else container.prefs.setAppLock(on)
            }
            Text(stringResource(R.string.app_lock_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel(stringResource(R.string.language))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(stringResource(R.string.change_language)) }
            } else {
                Text(stringResource(R.string.language_follows_system), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
