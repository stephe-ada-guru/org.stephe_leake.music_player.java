//  Abstract :
//
//  Model for main view
//
//  Copyright (C) 2011 - 2013, 2015 - 2018, 2021, 2024 Stephen Leake.  All Rights Reserved.
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

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController

import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Define the events MainViewModel can send to MainActivity.
sealed class PlayerEvent
{
    data object ReloadPlaylist : PlayerEvent()
    data class PlaySong(val song: Song) : PlayerEvent()
}

class MainViewModel(private val application : Application, private val songDao: SongDao) : ViewModel()
{
   companion object
   {
      // The view model machinery in Android ensures that this 'create' is only called once.
      class MainViewModelFactory(
         private val application: Application,
         private val songDao: SongDao) : ViewModelProvider.Factory
      {
         @Suppress("UNCHECKED_CAST")
         override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
               return MainViewModel(application, songDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
         }
      }
   }

   // We need a state flow for the playlist name to resolve a race
   // condition at startup.
   private val _playlistName = MutableStateFlow<String>("")
   val playlistName: StateFlow<String> = _playlistName.asStateFlow()

   // The actual MediaController survives when MainActivity is torn
   // down (MainViewModel also survives); preserve the connection to
   // it.
   var controllerFuture: ListenableFuture<MediaController>? = null

   // We need a mutex to serialize savePlaylistCounts calls
   private val saveStateMutex = Mutex()

   // We need this here to use viewModelScope; see comment in
   // MainActivity.kt updateDisplay where this is called.
   fun savePlaylistCounts(count : Int, index : Int, pos : Long)
   {
      val category = _playlistName.value
      if (category.isNotEmpty())  // defensive programming
         {
            viewModelScope.launch {
               saveStateMutex.withLock {
                  utils.savePlaylistCounts((application as Context), category, count, index, pos)
               }
            }
         }
   }
      
   suspend fun writeName(name : String)
   {
      (application as Context).playlistPrefsState.edit {
         preferences ->
            preferences[PlaylistPreferenceKeys.NAME] = name
      }
      
      _playlistName.value = name
   } // writeCategory
   
   private val _isMediaControllerReady = MutableStateFlow(false)
   private val _isPlaylistNameLoaded = MutableStateFlow(false)

   val isPlayerReadyToInitialize: StateFlow<Boolean> =
      combine(_isPlaylistNameLoaded, _isMediaControllerReady)
   {playlistNameLoaded, mediaControllerReady ->
       playlistNameLoaded && mediaControllerReady
   }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

   init {
      loadPlaylistName()
   }
   
   private fun loadPlaylistName()
   {
      viewModelScope.launch {
         _playlistName.value = utils.readPlaylistName(application as Context)
         _isPlaylistNameLoaded.value = true}
   }
   
   fun setMediaControllerReady(isReady: Boolean)
   {
      _isMediaControllerReady.value = isReady
   }
   
   // Category is updated by an async db fetch
   private val _currentCategory = mutableStateOf<String?>(null)
   val currentCategory: State<String?> = _currentCategory
   
   fun getCategory(albumArtist : String, album : String, title: String) {
      viewModelScope.launch {
         val song = songDao.getSong(albumArtist, album, title)
         _currentCategory.value = song?.Category
      }
   }

   private val _playerEvent = MutableSharedFlow<PlayerEvent>()
   val playerEvent: SharedFlow<PlayerEvent> = _playerEvent.asSharedFlow()

   fun reloadPlaylist()
   {
        viewModelScope.launch {
            // This should not be necessary, but it may be possible
            // for the DownloadService to get out of sync with
            // MainViewModel.
            _playlistName.value = utils.readPlaylistName(application as Context)

            _playerEvent.emit(PlayerEvent.ReloadPlaylist)
        }
   }

   fun playSong(song: Song)
   {
      viewModelScope.launch {_playerEvent.emit(PlayerEvent.PlaySong(song))}
   }
   
} //MainViewModel
