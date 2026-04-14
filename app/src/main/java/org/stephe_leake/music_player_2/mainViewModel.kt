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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController

import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
   
   // The actual MediaController survives when MainActivity is torn
   // down (MainViewModel also survives); preserve the connection to
   // it.
   var controllerFuture: ListenableFuture<MediaController>? = null

   // We need this here to use viewModelScope; see comment in
   // MainActivity.kt updateDisplay where this is called.
   fun savePlaylistCounts(category : String?, index : Int, pos : Long)
   {
      if (category != null && category.isNotEmpty())
         {
            viewModelScope.launch {
               try
               {
                  utils.savePlaylistCounts((application as Context), category, index, pos,
                                            limit = utils.limitDontSave)
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
         utils.debugLog("mainViewModel.writeName '$name'")
         (application as Context).playlistPrefsState.edit {
            preferences ->
               preferences[PlaylistNamePreferenceKeys.NAME] = name}
      }
      catch (e: Exception)
      {
         _errorMessage.value = "mainViewModel.writeName failed: ${e.message}"
      }
   } // writeName
   
   // We need a state flow for the playlist name to resolve a race
   // condition at startup. This is _not_ the definitive value for the
   // current playlist name; that is stored in each mediaItem.extras
   // "PlaylistName".
   private val _playlistName = MutableStateFlow("")

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

   fun getPlaylistName() : String
   // Can only be called after isPlayerReadyToInitialize is true, and
   // only to get the name just read from the datastore.
   {
      val result = _playlistName.value

      // Ensure startup reads from DataStore, not _playlistName
      _playlistName.value = ""
      _isPlaylistNameLoaded.value = false
      
      return result
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
   
   // Category is updated by an async db fetch. It contains the
   // current playlist name and other things; it is _not_ the
   // definitive source of the current playlist name.
   private val _currentCategory = mutableStateOf<String?>(null)
   val currentCategory: State<String?> = _currentCategory
   
   fun getCategory(albumArtist : String, album : String, title: String) {
      viewModelScope.launch {
         try
         {
            // This did _not_ report a corrupt db. Sigh
            val song = songDao.getSong(albumArtist, album, title)
            _currentCategory.value = song?.Category
         }
         catch (e: Exception)
         {
            _errorMessage.value = "mainViewModel.getCategory ${e.message}"
         }
      }
   }
   
} //MainViewModel
