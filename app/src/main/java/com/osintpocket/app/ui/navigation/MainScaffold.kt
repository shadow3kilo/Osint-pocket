package com.osintpocket.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Shield
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.osintpocket.app.ui.alias.AliasScreen
import com.osintpocket.app.ui.domain.DomainScreen
import com.osintpocket.app.ui.exif.ExifScreen
import com.osintpocket.app.ui.more.BreachScreen
import com.osintpocket.app.ui.more.CatalogScreen
import com.osintpocket.app.ui.more.DorksScreen
import com.osintpocket.app.ui.more.EmailReconScreen
import com.osintpocket.app.ui.more.PhoneScreen
import com.osintpocket.app.ui.more.HashScreen
import com.osintpocket.app.ui.more.HistoryScreen
import com.osintpocket.app.ui.more.MoreDest
import com.osintpocket.app.ui.more.MoreHomeScreen
import com.osintpocket.app.ui.more.SettingsScreen
import com.osintpocket.app.ui.web.WebScreen

enum class Dest(val route: String, val label: String, val title: String, val icon: ImageVector) {
    DOMAIN("domain", "Dominio", "Dominio / IP", Icons.Outlined.Public),
    WEB("web", "Web", "Cabeceras web", Icons.Outlined.Http),
    ALIAS("alias", "Alias", "Username check", Icons.Outlined.PersonSearch),
    EXIF("exif", "EXIF", "Metadatos de imagen", Icons.Outlined.Image),
    MORE("more", "Más", "Más herramientas", Icons.Outlined.MoreHoriz),
}

private const val MORE_HOME = "more/home"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(torEnabled: StateFlow<Boolean>) {
    val tor by torEnabled.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = Dest.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }
        ?: Dest.DOMAIN
    val sub = MoreDest.entries.firstOrNull { it.route == destination?.route }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(sub?.title ?: currentTab.title) },
                actions = {
                    if (tor) {
                        Icon(Icons.Outlined.Shield, "Tor activado", tint = Color(0xFF7E4798), modifier = Modifier.padding(end = 4.dp))
                        Text("Tor", color = Color(0xFF7E4798), modifier = Modifier.padding(end = 12.dp))
                    }
                },
                navigationIcon = {
                    if (sub != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Dest.DOMAIN.route, modifier = Modifier.padding(padding)) {
            composable(Dest.DOMAIN.route) { DomainScreen() }
            composable(Dest.WEB.route) { WebScreen() }
            composable(Dest.ALIAS.route) { AliasScreen() }
            composable(Dest.EXIF.route) { ExifScreen() }
            navigation(startDestination = MORE_HOME, route = Dest.MORE.route) {
                composable(MORE_HOME) { MoreHomeScreen(onOpen = { nav.navigate(it.route) }) }
                composable(MoreDest.BREACH.route) {
                    BreachScreen(onOpenSettings = { nav.navigate(MoreDest.SETTINGS.route) })
                }
                composable(MoreDest.CATALOG.route) { CatalogScreen() }
                composable(MoreDest.EMAIL.route) { EmailReconScreen() }
                composable(MoreDest.PHONE.route) { PhoneScreen() }
                composable(MoreDest.DORKS.route) { DorksScreen() }
                composable(MoreDest.HASH.route) { HashScreen() }
                composable(MoreDest.HISTORY.route) { HistoryScreen() }
                composable(MoreDest.SETTINGS.route) { SettingsScreen() }
            }
        }
    }
}
