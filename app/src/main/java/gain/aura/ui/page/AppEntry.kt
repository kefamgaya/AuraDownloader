package gain.aura.ui.page

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import gain.aura.App
import gain.aura.R
import gain.aura.ui.common.Route
import gain.aura.ui.common.animatedComposable
import gain.aura.ui.common.animatedComposableVariant
import gain.aura.ui.common.arg
import gain.aura.ui.common.id
import gain.aura.ui.common.slideInVerticallyComposable
import gain.aura.ui.page.command.TaskListPage
import gain.aura.ui.page.command.TaskLogPage
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import gain.aura.ui.page.downloadv2.DownloadPageV2
import gain.aura.ui.page.settings.SettingsPage
import gain.aura.ui.page.settings.about.AboutPage
import gain.aura.ui.page.settings.about.CreditsPage
import gain.aura.ui.page.settings.about.SponsorsPage
import gain.aura.ui.page.settings.about.UpdatePage
import gain.aura.ui.page.settings.appearance.AppearancePreferences
import gain.aura.ui.page.settings.appearance.DarkThemePreferences
import gain.aura.ui.page.settings.appearance.LanguagePage
import gain.aura.ui.page.settings.directory.DownloadDirectoryPreferences
import gain.aura.ui.page.settings.format.DownloadFormatPreferences
import gain.aura.ui.page.settings.format.SubtitlePreference
import gain.aura.ui.page.settings.general.GeneralDownloadPreferences
import gain.aura.ui.page.settings.network.CookieProfilePage
import gain.aura.ui.page.settings.network.CookiesViewModel
import gain.aura.ui.page.settings.network.NetworkPreferences
import gain.aura.ui.page.settings.network.WebViewPage
import gain.aura.ui.page.settings.troubleshooting.TroubleShootingPage
import gain.aura.ui.page.TabbedHomePage
import gain.aura.ui.page.DownloadQueuePage
import gain.aura.ui.page.CopyrightDisclaimerDialog
import gain.aura.ui.page.OnboardingPage
import gain.aura.util.ONBOARDING_COMPLETED
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.encodeBoolean
import gain.aura.util.PreferenceUtil.getBoolean
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val TAG = "HomeEntry"

private val TopDestinations =
    listOf(Route.HOME, Route.TASK_LIST, Route.SETTINGS_PAGE, Route.DOWNLOAD_QUEUE)

@Composable
fun AppEntry(dialogViewModel: DownloadDialogViewModel) {

    val navController = rememberNavController()
    val context = LocalContext.current
    val sheetState by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
    val cookiesViewModel: CookiesViewModel = koinViewModel()

    val versionReport = App.packageInfo.versionName.toString()
    val appName = stringResource(R.string.app_name)

    // Check if onboarding is completed
    var showOnboarding by rememberSaveable {
        mutableStateOf(!ONBOARDING_COMPLETED.getBoolean())
    }
    var showCopyrightDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val onNavigateBack: () -> Unit = {
        with(navController) {
            if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                popBackStack()
            }
        }
    }

    if (sheetState is DownloadDialogViewModel.SheetState.Configure) {
        if (navController.currentDestination?.route != Route.HOME) {
            navController.popBackStack(route = Route.HOME, inclusive = false, saveState = true)
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var currentTopDestination by rememberSaveable { mutableStateOf(currentRoute) }

    LaunchedEffect(currentRoute) {
        if (currentRoute in TopDestinations) {
            currentTopDestination = currentRoute
        }
    }

    // Show onboarding if not completed
    if (showOnboarding) {
        OnboardingPage(
            onComplete = {
                showOnboarding = false
                showCopyrightDialog = true
            },
            onSkip = {
                encodeBoolean(ONBOARDING_COMPLETED, true)
                showOnboarding = false
                showCopyrightDialog = true
            },
        )
        return
    }

    // Show copyright disclaimer dialog after onboarding
    if (showCopyrightDialog) {
        CopyrightDisclaimerDialog(
            onDismissRequest = {
                showCopyrightDialog = false
            },
            onUnderstand = {
                showCopyrightDialog = false
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            modifier = Modifier.align(Alignment.Center),
            navController = navController,
            startDestination = Route.HOME,
        ) {
            animatedComposable(Route.HOME) {
                TabbedHomePage(
                    dialogViewModel = dialogViewModel,
                    onNavigateToHome = {
                        navController.navigate(Route.HOME) {
                            launchSingleTop = true
                            popUpTo(route = Route.HOME) { inclusive = false }
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Route.SETTINGS_PAGE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDownloadQueue = {
                        navController.navigate(Route.DOWNLOAD_QUEUE) {
                            launchSingleTop = true
                        }
                    },
                    currentRoute = currentRoute,
                )
            }
            animatedComposable(Route.DOWNLOAD_QUEUE) {
                DownloadQueuePage(
                    dialogViewModel = dialogViewModel,
                    onNavigateToHome = {
                        navController.navigate(Route.HOME) {
                            launchSingleTop = true
                            popUpTo(route = Route.HOME) { inclusive = false }
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Route.SETTINGS_PAGE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDownloadQueue = {
                        navController.navigate(Route.DOWNLOAD_QUEUE) {
                            launchSingleTop = true
                        }
                    },
                    currentRoute = currentRoute,
                )
            }
                animatedComposableVariant(Route.TASK_LIST) {
                    TaskListPage(
                        onNavigateBack = onNavigateBack,
                        onNavigateToDetail = { navController.navigate(Route.TASK_LOG id it) },
                    )
                }
                slideInVerticallyComposable(
                    Route.TASK_LOG arg Route.TASK_HASHCODE,
                    arguments = listOf(navArgument(Route.TASK_HASHCODE) { type = NavType.IntType }),
                ) {
                    TaskLogPage(
                        onNavigateBack = onNavigateBack,
                        taskHashCode = it.arguments?.getInt(Route.TASK_HASHCODE) ?: -1,
                    )
                }

            settingsGraph(
                onNavigateBack = onNavigateBack,
                onNavigateTo = { route ->
                    navController.navigate(route = route) { launchSingleTop = true }
                },
                cookiesViewModel = cookiesViewModel,
                navController = navController,
            )
        }

        AppUpdater()
        YtdlpUpdater()
    }
}

fun NavGraphBuilder.settingsGraph(
    onNavigateBack: () -> Unit,
    onNavigateTo: (route: String) -> Unit,
    cookiesViewModel: CookiesViewModel,
    navController: androidx.navigation.NavController,
) {
    navigation(startDestination = Route.SETTINGS_PAGE, route = Route.SETTINGS) {
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences(onNavigateBack)
        }
        animatedComposable(Route.SETTINGS_PAGE) {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            SettingsPage(
                onNavigateBack = onNavigateBack,
                onNavigateTo = onNavigateTo,
                onNavigateToHome = {
                    navController.navigate(Route.HOME) {
                        launchSingleTop = true
                        popUpTo(route = Route.HOME) { inclusive = false }
                    }
                },
                onNavigateToDownloadQueue = {
                    navController.navigate(Route.DOWNLOAD_QUEUE) {
                        launchSingleTop = true
                    }
                },
                currentRoute = currentRoute,
            )
        }
        animatedComposable(Route.GENERAL_DOWNLOAD_PREFERENCES) {
            GeneralDownloadPreferences(onNavigateBack = { onNavigateBack() })
        }
        animatedComposable(Route.DOWNLOAD_FORMAT) {
            DownloadFormatPreferences(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.SUBTITLE_PREFERENCES)
            }
        }
        animatedComposable(Route.SUBTITLE_PREFERENCES) { SubtitlePreference { onNavigateBack() } }
        animatedComposable(Route.ABOUT) {
            AboutPage(
                onNavigateBack = onNavigateBack,
                onNavigateToCreditsPage = { onNavigateTo(Route.CREDITS) },
                onNavigateToUpdatePage = { onNavigateTo(Route.AUTO_UPDATE) },
                onNavigateToDonatePage = { onNavigateTo(Route.DONATE) },
            )
        }
        animatedComposable(Route.DONATE) { SponsorsPage(onNavigateBack) }
        animatedComposable(Route.CREDITS) { CreditsPage(onNavigateBack) }
        animatedComposable(Route.AUTO_UPDATE) { UpdatePage(onNavigateBack) }
        animatedComposable(Route.APPEARANCE) {
            AppearancePreferences(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.LANGUAGES) { LanguagePage { onNavigateBack() } }
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences { onNavigateBack() }
        }
        animatedComposable(Route.DARK_THEME) { DarkThemePreferences { onNavigateBack() } }
        animatedComposable(Route.NETWORK_PREFERENCES) {
            NetworkPreferences(
                navigateToCookieProfilePage = { onNavigateTo(Route.COOKIE_PROFILE) }
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_PROFILE) {
            CookieProfilePage(
                cookiesViewModel = cookiesViewModel,
                navigateToCookieGeneratorPage = { onNavigateTo(Route.COOKIE_GENERATOR_WEBVIEW) },
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_GENERATOR_WEBVIEW) {
            WebViewPage(cookiesViewModel = cookiesViewModel) {
                onNavigateBack()
                CookieManager.getInstance().flush()
            }
        }
        animatedComposable(Route.TROUBLESHOOTING) {
            TroubleShootingPage(onNavigateTo = onNavigateTo, onBack = onNavigateBack)
        }
    }
}
