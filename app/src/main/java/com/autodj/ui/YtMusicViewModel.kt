package com.autodj.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autodj.remote.YtCategory
import com.autodj.remote.YtCategorySection
import com.autodj.remote.YtMusicBrowse
import com.autodj.remote.YtShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Something fetched for the YT Music page. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val reason: String) : Loadable<Nothing>
}

/**
 * The YT Music page: YouTube Music's mood and genre categories, and the curated playlists
 * behind the one picked. A category's shelves are fetched once per launch.
 */
class YtMusicViewModel : ViewModel() {
    private val browse = YtMusicBrowse()
    private val shelfCache = HashMap<String, List<YtShelf>>()

    private val _categories = MutableStateFlow<Loadable<List<YtCategorySection>>>(Loadable.Loading)
    val categories: StateFlow<Loadable<List<YtCategorySection>>> = _categories

    private val _selected = MutableStateFlow<YtCategory?>(null)
    val selected: StateFlow<YtCategory?> = _selected

    private val _shelves = MutableStateFlow<Loadable<List<YtShelf>>>(Loadable.Loading)
    val shelves: StateFlow<Loadable<List<YtShelf>>> = _shelves

    init {
        loadCategories()
    }

    fun loadCategories() {
        _categories.value = Loadable.Loading
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { browse.categories() } }
            _categories.value = result.fold(
                { if (it.isEmpty()) Loadable.Failed("YouTube Music sent no categories") else Loadable.Ready(it) },
                { Loadable.Failed(it.message ?: it.javaClass.simpleName) },
            )
            // A DJ app opens on the party playlists when YouTube offers them.
            val all = result.getOrNull().orEmpty().flatMap { it.categories }
            (all.firstOrNull { it.title.equals("Party", ignoreCase = true) } ?: all.firstOrNull())?.let(::select)
        }
    }

    fun select(category: YtCategory) {
        _selected.value = category
        shelfCache[category.params]?.let {
            _shelves.value = Loadable.Ready(it)
            return
        }
        _shelves.value = Loadable.Loading
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { browse.shelves(category) } }
            result.getOrNull()?.let { shelfCache[category.params] = it }
            // Only if the user hasn't moved on to another category meanwhile.
            if (_selected.value != category) return@launch
            _shelves.value = result.fold(
                { if (it.isEmpty()) Loadable.Failed("No playlists in ${category.title}") else Loadable.Ready(it) },
                { Loadable.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}
