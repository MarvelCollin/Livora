package com.example.livora.ui.people

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object PeopleRoutes {
    const val ARG_ID = "id"
    const val ARG_KEY = "key"
    const val ARG_PERSON = "personId"

    const val PERSON = "people/person/{id}"
    const val SUGGESTIONS = "people/suggestions/{id}"
    const val RECOGNITION = "people/recognition/{id}"
    const val FOLDER = "people/folder/{key}"
    const val ENROLL = "people/enroll?personId={personId}"
    const val MERGE = "people/merge"
    const val SETTINGS = "people/settings"

    fun person(id: Long) = "people/person/$id"
    fun suggestions(id: Long) = "people/suggestions/$id"
    fun recognition(id: Long) = "people/recognition/$id"
    fun folder(key: String) = "people/folder/${Uri.encode(key)}"
    fun enroll(personId: Long? = null) = if (personId == null) "people/enroll" else "people/enroll?personId=$personId"
}

@Composable
fun PeopleTab(onNavigate: (String) -> Unit) {
    val peopleViewModel: PeopleViewModel = viewModel()
    val foldersViewModel: FoldersViewModel = viewModel()
    PeopleScreen(
        viewModel = peopleViewModel,
        foldersViewModel = foldersViewModel,
        onOpenPerson = { onNavigate(PeopleRoutes.person(it)) },
        onOpenFolder = { onNavigate(PeopleRoutes.folder(it)) },
        onAddPerson = { onNavigate(PeopleRoutes.enroll()) },
        onOpenMerge = { onNavigate(PeopleRoutes.MERGE) },
        onOpenSettings = { onNavigate(PeopleRoutes.SETTINGS) }
    )
}

fun NavGraphBuilder.peopleGraph(navController: NavHostController) {
    val idArgument = listOf(navArgument(PeopleRoutes.ARG_ID) { type = NavType.LongType })
    composable(PeopleRoutes.PERSON, arguments = idArgument) {
        val viewModel: PersonDetailViewModel = viewModel()
        val foldersViewModel: FoldersViewModel = viewModel()
        PersonDetailScreen(
            viewModel = viewModel,
            foldersViewModel = foldersViewModel,
            onBack = { navController.popBackStack() },
            onOpenSuggestions = { navController.navigate(PeopleRoutes.suggestions(viewModel.personId)) },
            onOpenRecognition = { navController.navigate(PeopleRoutes.recognition(viewModel.personId)) },
            onAddReferences = { navController.navigate(PeopleRoutes.enroll(viewModel.personId)) }
        )
    }
    composable(PeopleRoutes.SUGGESTIONS, arguments = idArgument) {
        val viewModel: SuggestionsViewModel = viewModel()
        SuggestionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
    composable(PeopleRoutes.RECOGNITION, arguments = idArgument) {
        val viewModel: RecognitionViewModel = viewModel()
        RecognitionScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onAddReferences = { navController.navigate(PeopleRoutes.enroll(viewModel.personId)) },
            onOpenSuggestions = { navController.navigate(PeopleRoutes.suggestions(viewModel.personId)) }
        )
    }
    composable(
        PeopleRoutes.FOLDER,
        arguments = listOf(navArgument(PeopleRoutes.ARG_KEY) { type = NavType.StringType })
    ) {
        val viewModel: FolderDetailViewModel = viewModel()
        FolderDetailScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onUseAsReferences = { navController.navigate(PeopleRoutes.enroll()) }
        )
    }
    composable(
        PeopleRoutes.ENROLL,
        arguments = listOf(navArgument(PeopleRoutes.ARG_PERSON) { type = NavType.LongType; defaultValue = -1L })
    ) {
        val viewModel: EnrollViewModel = viewModel()
        EnrollScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDone = { id ->
                if (viewModel.personId != null) {
                    navController.popBackStack()
                } else {
                    navController.navigate(PeopleRoutes.person(id)) {
                        popUpTo(PeopleRoutes.ENROLL) { inclusive = true }
                    }
                }
            }
        )
    }
    composable(PeopleRoutes.MERGE) {
        val viewModel: MergeSuggestionsViewModel = viewModel()
        MergeSuggestionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
    composable(PeopleRoutes.SETTINGS) {
        val viewModel: PeopleSettingsViewModel = viewModel()
        PeopleSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
}
