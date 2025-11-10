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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController

import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistState(
   val baseName: String = "",      // Current playlist file name (empty if no playlist)
   val count: Int = 0,             // Count of songs in playlist
   val index: Int = 0,             // Current song in playlist (0 indexed)
   val pos: Long = 0L              // Current position in song (milliseconds, 0 if none)
)

class MainViewModel(application : Application, private val songDao: SongDao) : AndroidViewModel(application)
{
   private val _playlistState = MutableStateFlow<PlaylistState>(PlaylistState())
   val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()

   // The actual MediaController survives when MainActivity is torn
   // down (MainViewModel also survives); preserve the connection to
   // it.
   var controllerFuture: ListenableFuture<MediaController>? = null

   suspend fun clearSavedState()
   // Called when a playlist reaches the end.
   {
      val current = _playlistState.value
      
      _playlistState.value = PlaylistState(
         baseName = "",
         count = 0,
         index = 0,
         pos = 0)

      (getApplication() as Context).playlistPrefsState.edit {
         preferences ->
            preferences[PlaylistPreferenceKeys.NAME] = ""
         if (current.baseName != "")
            {
               preferences[PlaylistPreferenceKeys.count(current.baseName)] = 0
               preferences[PlaylistPreferenceKeys.index(current.baseName)] = 0
               preferences[PlaylistPreferenceKeys.pos(current.baseName)] = 0

            }
      }
   } // clearSavedState

   suspend fun writeCategory(category : String)
   {
      (getApplication() as Context).playlistPrefsState.edit {
         preferences ->
            preferences[PlaylistPreferenceKeys.NAME] = category
      }
         
      _playlistState.value = PlaylistState(
         baseName = category,
         count = 0,
         index = 0,
         pos = 0)
   } // writeCategory
      
   suspend fun writeState(count : Int, index : Int, pos : Long)
   {
      val current = _playlistState.value

      if (current.baseName != "")
         {
            _playlistState.value = PlaylistState(
               baseName = current.baseName,
               count = count,
               index = index,
               pos = pos)

            (getApplication() as Context).playlistPrefsState.edit {
               preferences ->
                  preferences[PlaylistPreferenceKeys.NAME] = current.baseName
               preferences[PlaylistPreferenceKeys.count(current.baseName)] = count
               preferences[PlaylistPreferenceKeys.index(current.baseName)] = index
               preferences[PlaylistPreferenceKeys.pos(current.baseName)] = pos

            }
         }
   }// writeState

   private suspend fun readPlaylistState()
   {
      val preferences = (getApplication() as Context).playlistPrefsState.data.firstOrNull()
      if (preferences == null)
         {
            // Never set
            _playlistState.value = PlaylistState(
               baseName = "",
               count = 0,
               index = 0,
               pos = 0)
         }
      else
         {
            var name : String? = preferences[PlaylistPreferenceKeys.NAME]
            if (name == null)
               {
                  // Never set
                  _playlistState.value = PlaylistState(
                     baseName = "",
                     count = 0,
                     index = 0,
                     pos = 0)
               }
            else
               {
                  var tempCount : Int? = preferences[PlaylistPreferenceKeys.count(name)]
                  if (tempCount == null)
                     {
                        // Name set, but not counts
                        _playlistState.value = PlaylistState(
                           baseName = name,
                           count = 0,
                           index = 0,
                           pos = 0)
                     }
               else
                  {
                     _playlistState.value = PlaylistState(
                        baseName = name,
                        count = tempCount,
                        index = preferences[PlaylistPreferenceKeys.index(name)]!!,
                        pos = preferences[PlaylistPreferenceKeys.pos(name)]!!)
                  }
               }
         }
   }
   
   private val _isMediaControllerReady = MutableStateFlow(false)
   private val _isPlaylistStateLoaded = MutableStateFlow(false)

   val isPlayerReadyToInitialize: StateFlow<Boolean> =
      combine(_isPlaylistStateLoaded, _isMediaControllerReady)
   {playlistStateLoaded, mediaControllerReady ->
       playlistStateLoaded && mediaControllerReady
   }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

   init {
      loadPlaylistState()
   }
   
   private fun loadPlaylistState()
   {
      viewModelScope.launch {
         readPlaylistState()
         _isPlaylistStateLoaded.value = true}
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

 } //MainViewModel
