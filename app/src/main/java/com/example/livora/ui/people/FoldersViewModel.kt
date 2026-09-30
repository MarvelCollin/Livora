package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.media.MediaAccess
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FoldersViewModel(application: Application) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)

    val state: StateFlow<FoldersState> = services.folders.state

    fun refresh() {
        if (!MediaAccess.hasAnyAccess(getApplication())) return
        viewModelScope.launch { services.folders.refresh() }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val folder = services.folders.createFolder(name)
            if (folder == null) {
                Toaster.error("Enter a folder name")
            } else {
                Toaster.success("Created ${folder.name}. It appears in your gallery when the first photo is added.")
            }
        }
    }
}
