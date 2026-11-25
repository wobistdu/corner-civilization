package com.example.cornercivilization.p37f776dc.ui.world

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cornercivilization.p37f776dc.data.repository.AtomicFileWorldRepository
import com.example.cornercivilization.p37f776dc.data.repository.LoadResult
import com.example.cornercivilization.p37f776dc.data.repository.SaveResult
import com.example.cornercivilization.p37f776dc.data.repository.WorldSnapshot
import com.example.cornercivilization.p37f776dc.domain.model.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

class WorldViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val directory = File(context.applicationContext.filesDir, "civilization")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorldViewModel::class.java))
        val repository = AtomicFileWorldRepository(directory)
        val store = object : WorldStore {
            override suspend fun load(): WorldLoad = withContext(Dispatchers.IO) {
                when (val result = repository.load()) {
                    is LoadResult.Loaded -> WorldLoad.Loaded(result.snapshot.state)
                    LoadResult.Empty -> WorldLoad.Empty
                    is LoadResult.Corrupt -> WorldLoad.Failed(result.reason, canRebuild = true)
                    is LoadResult.UnsupportedVersion -> WorldLoad.Failed("存档来自暂不支持的版本 ${result.version}，请保留存档。")
                }
            }
            override suspend fun save(state: WorldState): String? = withContext(Dispatchers.IO) {
                when (val result = repository.save(WorldSnapshot(state, ZoneId.systemDefault().id))) {
                    is SaveResult.Saved -> if (result.revision == state.revision) null else "存档版本未能确认，请重试保存。"
                    is SaveResult.Failed -> result.reason
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return WorldViewModel(store) as T
    }
}
