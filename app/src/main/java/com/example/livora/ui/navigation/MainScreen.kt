package com.example.livora.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.dictionary.DictionaryScreen
import com.example.livora.ui.dictionary.DictionaryViewModel
import com.example.livora.ui.home.HomeScreen
import com.example.livora.ui.todo.TodoScreen
import com.example.livora.ui.tools.ToolsScreen
import com.example.livora.ui.todo.TodoViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Tasks("Tasks", Icons.Default.Checklist),
    Dictionary("Dictionary", Icons.Default.Translate),
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
    onOpenVault: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries

    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tabs[selectedTab]) {
                MainTab.Home -> HomeScreen(
                    acViewModel = acViewModel,
                    bulbViewModel = bulbViewModel,
                    onNavigateToAc = onNavigateToAc,
                    onNavigateToBulb = onNavigateToBulb
                )
                MainTab.Tasks -> TodoScreen(
                    viewModel = todoViewModel,
                    onOpenDetail = onOpenTodoDetail
                )
                MainTab.Dictionary -> DictionaryScreen(
                    viewModel = dictionaryViewModel,
                    onOpenQuiz = onOpenQuiz
                )
                MainTab.Tools -> ToolsScreen(onOpenVault = onOpenVault)
            }
        }
    }
}
