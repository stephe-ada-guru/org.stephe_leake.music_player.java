//  Abstract :
//
//  Interface to the sqlite database
//
//  Copyright (C) 2025 Stephen Leake. All Rights Reserved.
//
//  This program is free software; you can redistribute it and/or
//  modify it under terms of the GNU General Public License as
//  published by the Free Software Foundation; either version 3, or
//  (at your option) any later version. This program is distributed in
//  the hope that it will be useful, but WITHOUT ANY WARRANTY; without
//  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
//  PARTICULAR PURPOSE. See the GNU General Public License for more
//  details. You should have received a copy of the GNU General Public
//  License distributed with this program; see file COPYING. If not,
//  write to the Free Software Foundation, 51 Franklin Street, Suite
//  500, Boston, MA 02110-1335, USA.

package org.stephe_leake.music_player_2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class SearchViewModel(private val songDao: SongDao) : ViewModel()
{
   // State for the General Search query
    private val _generalQuery = MutableStateFlow("")
    val generalQuery = _generalQuery.asStateFlow()

    // State for the Detailed Search fields
    val detailedTitle       = MutableStateFlow("")
    val detailedArtist      = MutableStateFlow("")
    val detailedAlbum       = MutableStateFlow("")
    val detailedAlbumArtist = MutableStateFlow("")
    val detailedComposer    = MutableStateFlow("")
    val detailedCategory    = MutableStateFlow("")

    // Combine detailed fields to trigger search
    private val detailedSearchParams = combine(
        detailedTitle, detailedArtist, detailedAlbum,
        detailedAlbumArtist, detailedComposer, detailedCategory
    ) { params ->
        DetailedSearchParams(params[0], params[1], params[2], params[3], params[4], params[5])
    }

    // Results from general search (updates when generalQuery changes)
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val generalSearchResults = _generalQuery.transformLatest { query ->
        if (query.isBlank()) {
            emit(emptyList())
        } else {
            songDao.generalSearch(query).collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Results from detailed search (updates when any detailed field changes)
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val detailedSearchResults = detailedSearchParams.transformLatest { params ->
        if (params.allBlank()) {
            emit(emptyList())
        } else {
            songDao.detailedSearch(
                title = params.title, artist = params.artist, album = params.album,
                albumArtist = params.albumArtist, composer = params.composer, category = params.category
            ).collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onGeneralQueryChange(newQuery: String) {
        _generalQuery.value = newQuery
    }

    // Helper data class for detailed search parameters
    data class DetailedSearchParams(
        val title: String, val artist: String, val album: String,
        val albumArtist: String, val composer: String, val category: String
    ) {
        fun allBlank() = title.isBlank() && artist.isBlank() && album.isBlank() &&
                albumArtist.isBlank() && composer.isBlank() && category.isBlank()
    }
}
