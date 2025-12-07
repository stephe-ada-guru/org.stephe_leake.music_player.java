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
import androidx.datastore.preferences.core.stringPreferencesKey
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController

import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private object PlaylistNamePreferenceKeys
{
   // This key is shared with utils, but only this file writes to it.
   val NAME  = stringPreferencesKey("name")
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

   private val _errorMessage = MutableStateFlow<String?>(null)
   val errorMessage: StateFlow<String?> = _errorMessage

   fun clearError()
   {
      _errorMessage.value = null
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
   fun savePlaylistCounts(index : Int, pos : Long)
   {
      val category = _playlistName.value
      if (category.isNotEmpty())  // defensive programming
         {
            viewModelScope.launch {
               try
               {
                  saveStateMutex.withLock {
                     utils.savePlaylistCounts((application as Context), category, index, pos)
                  }
               }
               catch (e: Exception)
               {
                  _errorMessage.value = "mainViewModel.savePlaylistCounts failed: ${e.message}"
               }
            }
         }
   }
      
   suspend fun writeName(name : String)
   {
      try
      {
         Log.d(utils.logTag, "savePlaylistName '$name'")
         (application as Context).playlistPrefsState.edit {
            preferences ->
               preferences[PlaylistNamePreferenceKeys.NAME] = name}
         _playlistName.value = name
      }
      catch (e: Exception)
      {
         _errorMessage.value = "mainViewModel.writeName failed: ${e.message}"
      }
   } // writeName
   
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
         try
         {
            _playlistName.value = utils.readPlaylistName(application as Context)
            _isPlaylistNameLoaded.value = true
         }
         catch (e: Exception)
         {
            _errorMessage.value = "mainViewModel.loadPlaylistName failed: ${e.message}"
         }
      }
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
         try
         {
            // This did _not_ report a corrupt db. Sigh
            Log.d(utils.logTag, "mainViewModel.getCategory '$albumArtist' '$album' '$title'")
            val song = songDao.getSong(albumArtist, album, title)
            _currentCategory.value = song?.Category
            Log.d(utils.logTag, " ... '${_currentCategory.value}'")
         }
         catch (e: Exception)
         {
            _errorMessage.value = "mainViewModel.getCategory ${e.message}"
         }
      }
   }
   
} //MainViewModel
