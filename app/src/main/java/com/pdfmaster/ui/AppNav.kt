package com.pdfmaster.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pdfmaster.IncomingRequest
import com.pdfmaster.R
import com.pdfmaster.billing.Access
import com.pdfmaster.billing.ToolId
import com.pdfmaster.ui.common.DocumentPickerSheet
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.files.FilesScreen
import com.pdfmaster.ui.home.HomeScreen
import com.pdfmaster.ui.me.MeScreen
import com.pdfmaster.ui.me.PaywallScreen
import com.pdfmaster.ui.me.ProfileScreen
import com.pdfmaster.ui.me.SettingsScreen
import com.pdfmaster.ui.me.SignaturesScreen
import com.pdfmaster.ui.scan.PagesReviewScreen
import com.pdfmaster.ui.tools.ToolHost
import com.pdfmaster.ui.tools.ToolsScreen
import com.pdfmaster.ui.tools.toolTitle
import com.pdfmaster.ui.viewer.ViewerMode
import com.pdfmaster.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val TOOLS = "tools"
    const val ME = "me"
    const val PAGES_REVIEW = "pages_review"
    const val PAYWALL = "paywall"
    const val SIGNATURES = "signatures"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    fun viewer(path: String, mode: ViewerMode = ViewerMode.READ) = "viewer?path=${Uri.encode(path)}&mode=${mode.name}"
    fun tool(tool: ToolId, path: String? = null) = "tool/${tool.name}" + (path?.let { "?path=${Uri.encode(it)}" } ?: "")
}

/** App-wide actions available to every screen. */
class AppActions(
    val nav: NavHostController,
    val launchTool: (ToolId, String?) -> Unit,
    val startScan: () -> Unit,
) {
    fun openViewer(path: String, mode: ViewerMode = ViewerMode.READ) = nav.navigate(Routes.viewer(path, mode))
    fun back() { nav.popBackStack() }
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.tab_home, Icons.Default.Home),
    TopLevel(Routes.FILES, R.string.tab_files, Icons.Default.Folder),
    TopLevel(Routes.TOOLS, R.string.tab_tools, Icons.Default.Apps),
    TopLevel(Routes.ME, R.string.tab_me, Icons.Default.Person),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val container = LocalContainer.current
    val context = LocalContext.current
    // ---- tool gate: decided before the tool opens, never after the work is done
    var gate by remember { mutableStateOf<Pair<ToolId, Access>?>(null) }
    var pendingTool by remember { mutableStateOf<Pair<ToolId, String?>?>(null) }
    var pickForViewer by remember { mutableStateOf<ToolId?>(null) }

    // ---- scanning (ML Kit document scanner: edge detection, auto-capture, crop, filters)
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isNotEmpty()) {
                container.pendingScan.value = pages
                nav.navigate(Routes.PAGES_REVIEW)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) {
            container.pendingScan.value = uris
            nav.navigate(Routes.PAGES_REVIEW)
        }
    }
    val startScan: () -> Unit = {
        val activity = context as Activity
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                // No Google Play services scanner on this device: fall back to picking photos.
                Toast.makeText(context, R.string.scanner_unavailable, Toast.LENGTH_LONG).show()
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
    }

    fun openTool(tool: ToolId, path: String?) {
        when (tool) {
            ToolId.SCAN -> startScan()
            ToolId.IMAGES_TO_PDF -> galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            ToolId.ANNOTATE, ToolId.ADD_TEXT, ToolId.SIGN -> {
                val mode = if (tool == ToolId.SIGN) ViewerMode.SIGN else ViewerMode.ANNOTATE
                if (path != null) nav.navigate(Routes.viewer(path, mode)) else pickForViewer = tool
            }
            else -> nav.navigate(Routes.tool(tool, path))
        }
    }

    val launchTool: (ToolId, String?) -> Unit = { tool, path ->
        when (val access = container.entitlements.access(tool)) {
            is Access.Open -> openTool(tool, path)
            else -> { gate = tool to access; pendingTool = tool to path }
        }
    }

    val actions = remember(nav) { AppActions(nav, launchTool, startScan) }

    // ---- share sheet / "Open with"
    val incoming by container.incoming.collectAsState()
    LaunchedEffect(incoming) {
        val request = incoming ?: return@LaunchedEffect
        container.incoming.value = null
        when (request) {
            is IncomingRequest.OpenPdf -> runCatching { container.documents.importPdf(request.uri) }
                .onSuccess { nav.navigate(Routes.viewer(it.absolutePath)) }
                .onFailure { Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show() }
            is IncomingRequest.Pdfs -> {
                val files = request.uris.mapNotNull { runCatching { container.documents.importPdf(it) }.getOrNull() }
                when {
                    files.size == 1 -> nav.navigate(Routes.viewer(files.first().absolutePath))
                    files.size > 1 -> { com.pdfmaster.ui.tools.MergeInbox.files = files; launchTool(ToolId.MERGE, null) }
                }
            }
            is IncomingRequest.Images -> {
                container.pendingScan.value = request.uris
                nav.navigate(Routes.PAGES_REVIEW)
            }
        }
    }

    CompositionLocalProvider(LocalActions provides actions) {
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        Scaffold(
            bottomBar = {
                if (route in topLevel.map { it.route }) NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = if (route in topLevel.map { it.route }) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f))) {
                composable(Routes.HOME) { HomeScreen() }
                composable(Routes.FILES) { FilesScreen() }
                composable(Routes.TOOLS) { ToolsScreen() }
                composable(Routes.ME) { MeScreen() }
                composable(Routes.PAYWALL) { PaywallScreen() }
                composable(Routes.SIGNATURES) { SignaturesScreen() }
                composable(Routes.PROFILE) { ProfileScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.PAGES_REVIEW) { PagesReviewScreen() }
                composable(
                    "viewer?path={path}&mode={mode}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType },
                        navArgument("mode") { type = NavType.StringType; defaultValue = ViewerMode.READ.name },
                    ),
                ) { entry ->
                    val path = entry.arguments?.getString("path")!!
                    val mode = ViewerMode.valueOf(entry.arguments?.getString("mode") ?: ViewerMode.READ.name)
                    ViewerScreen(path, mode)
                }
                composable(
                    "tool/{tool}?path={path}",
                    arguments = listOf(
                        navArgument("tool") { type = NavType.StringType },
                        navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val tool = ToolId.valueOf(entry.arguments?.getString("tool")!!)
                    ToolHost(tool, entry.arguments?.getString("path"))
                }
            }
        }

        pickForViewer?.let { tool ->
            DocumentPickerSheet(
                onPicked = { file ->
                    pickForViewer = null
                    nav.navigate(Routes.viewer(file.absolutePath, if (tool == ToolId.SIGN) ViewerMode.SIGN else ViewerMode.ANNOTATE))
                },
                onDismiss = { pickForViewer = null },
            )
        }

        gate?.let { (tool, access) ->
            val title = toolTitle(tool)
            val dismiss = { gate = null; pendingTool = null }
            when (access) {
                Access.Trial -> AlertDialog(
                    onDismissRequest = dismiss,
                    icon = { Icon(Icons.Default.WorkspacePremium, null) },
                    title = { Text(stringResource(R.string.pro_tool_title, title)) },
                    text = { Text(stringResource(R.string.pro_tool_trial_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val p = pendingTool; dismiss()
                            p?.let { openTool(it.first, it.second) }
                        }) { Text(stringResource(R.string.try_once_free)) }
                    },
                    dismissButton = { TextButton(onClick = { dismiss(); nav.navigate(Routes.PAYWALL) }) { Text(stringResource(R.string.see_plans)) } },
                )
                Access.Locked -> AlertDialog(
                    onDismissRequest = dismiss,
                    icon = { Icon(Icons.Default.WorkspacePremium, null) },
                    title = { Text(stringResource(R.string.pro_tool_title, title)) },
                    text = { Text(stringResource(R.string.pro_tool_locked_body)) },
                    confirmButton = { TextButton(onClick = { dismiss(); nav.navigate(Routes.PAYWALL) }) { Text(stringResource(R.string.see_plans)) } },
                    dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.not_now)) } },
                )
                is Access.LimitReached -> AlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text(stringResource(R.string.daily_limit_title)) },
                    text = { Text(stringResource(R.string.daily_limit_body, access.limit, title)) },
                    confirmButton = { TextButton(onClick = { dismiss(); nav.navigate(Routes.PAYWALL) }) { Text(stringResource(R.string.see_plans)) } },
                    dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.not_now)) } },
                )
                Access.ComingSoon -> AlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text(title) },
                    text = { Text(stringResource(R.string.coming_soon_body, tool.phase)) },
                    confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.ok)) } },
                )
                is Access.Open -> Unit
            }
        }
    }
}
