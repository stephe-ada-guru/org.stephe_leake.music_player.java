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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlbumInfo(val artist: String?, val name: String?, val year: Int?)

data class DetailedInfo(
   val title: String = "",
   val artist: String = "",
   val album: String = "",
   val albumArtist: String = "",
   val composer: String = "",
   val category: String = "")
{
   fun isNotBlank() : Boolean
   {
      return title.isNotBlank() ||
       artist.isNotBlank() ||
       album.isNotBlank() ||
       albumArtist.isNotBlank() ||
       composer.isNotBlank() ||
       category.isNotBlank()   
   }
}

typealias SongAlbumMap = Map<AlbumInfo, List<Song>>

@kotlinx.coroutines.ExperimentalCoroutinesApi
class SearchViewModel(private val songDao: SongDao) : ViewModel()
{
   companion object
   {
      // The view model machinery in Android ensures that this 'create' is only called once.
      class SearchViewModelFactory(
         private val songDao: SongDao) :
         ViewModelProvider.Factory
      {
         @Suppress("UNCHECKED_CAST") // FIXME: is is _not_ unchecked!
         override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
               return SearchViewModel(songDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
         }
      }
   }

   private val _groupedResults = MutableStateFlow<SongAlbumMap>(emptyMap())
   val groupedResults: StateFlow<SongAlbumMap> = _groupedResults
   
   private val songListFlow = MutableSharedFlow<Flow<List<Song>>>()

   init {groupAndDisplaySongs()}

   // Called by the General Search tab
   fun performGeneralSearch(query: String) {
      viewModelScope.launch {
         if (query.isNotBlank()) {
            songListFlow.emit(songDao.generalSearch(query))
         } else {
            _groupedResults.value = emptyMap() // Clear results if query is empty
         }
      }
   }

   // Called by the Detailed Search tab
   fun performDetailedSearch(info: DetailedInfo) {
      viewModelScope.launch {
         if (info.isNotBlank()) {
            songListFlow.emit(
               songDao.detailedSearch(
                  title = info.title, artist = info.artist, album = info.album, albumArtist = info.albumArtist,
                  composer = info.composer, category = info.category))
         } else {
            _groupedResults.value = emptyMap()
         }
      }
   }

   @kotlinx.coroutines.ExperimentalCoroutinesApi
   private fun groupAndDisplaySongs() {
      viewModelScope.launch {
         songListFlow
            .flatMapLatest { it }
            .map { songs -> 
                      songs.groupBy { song ->
                                         AlbumInfo(name = song.Album, artist = song.Album_Artist, year = song.Year)
                      }
            }
            .catch {
               // Handle any potential errors from the flow
               // FIXME: message to the user (but we are in a background task; add errorMessage state?)
               _groupedResults.value = emptyMap()
            }
            .collect { groupedMap ->
                          // Update the final UI state
                       _groupedResults.value = groupedMap
            }
      }
   } // end groupAndDisplaySongs

   fun updateSong(song: Song) {viewModelScope.launch {songDao.updateSong(song)}}    
   // No need to manually refresh the list. Since the search functions
   // return a Flow, Room will automatically push the updated data,
   // and the UI will recompose to show the change.

} // end SearchViewModel
