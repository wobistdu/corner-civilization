package com.example.cornercivilization.p37f776dc.ui.fire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository
import java.io.File

class FireSliceViewModelFactory(private val directory: File) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FireSliceViewModel::class.java))
        return FireSliceViewModel(FireSliceRepository(directory)) as T
    }
}
