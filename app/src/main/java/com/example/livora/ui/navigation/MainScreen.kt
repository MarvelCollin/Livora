package com.example.livora.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.components.LocalSwipeLock
import com.example.livora.ui.daily.DailyScreen
import com.example.livora.ui.dictionary.DictionaryViewModel
import com.example.livora.ui.home.HomeScreen
import com.example.livora.ui.people.GalleryTab
import com.example.livora.ui.tools.ToolsScreen
import com.example.livora.ui.todo.TodoViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Daily("Daily", Icons.Default.Today),
    Gallery("Gallery", Icons.Default.PhotoLibrary),
    Tools("Tools", Icons.Default.Handyman)
}

@Composable
fun MainScreen(
    acViewModel: AcViewModel,
    bulbViewModel: BulbViewModel,
    todoViewModel: TodoViewModel,
    dictionaryViewModel: DictionaryViewModel,
    onNavigateToAc: () -> Unit,
    onNavigateToBulb: () -> Unit,
    onOpenTodoDetail: (String) -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPeopleRoute: (String) -> Unit,
    onOpenToolRoute: (String) -> Unit
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val swipeLock = remember { mutableStateOf(false) }

    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                if (abs(index - pagerState.currentPage) > 1) {
                                    pagerState.scrollToPage(index)
                                } else {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalSwipeLock provides swipeLock) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                userScrollEnabled = !swipeLock.value,
                key = { it }
            ) { page ->
                when (tabs[page]) {
                    MainTab.Home -> HomeScreen(
                        acViewModel = acViewModel,
                        bulbViewModel = bulbViewModel,
                        onNavigateToAc = onNavigateToAc,
                        onNavigateToBulb = onNavigateToBulb
                    )
                    MainTab.Daily -> DailyScreen(
                        todoViewModel = todoViewModel,
                        dictionaryViewModel = dictionaryViewModel,
                        onOpenTodoDetail = onOpenTodoDetail,
                        onOpenQuiz = onOpenQuiz
                    )
                    MainTab.Gallery -> GalleryTab(onNavigate = onOpenPeopleRoute)
                    MainTab.Tools -> ToolsScreen(
                        onOpenVault = onOpenVault,
                        onOpenRoute = onOpenToolRoute
                    )
                }
            }
        }
    }
}
