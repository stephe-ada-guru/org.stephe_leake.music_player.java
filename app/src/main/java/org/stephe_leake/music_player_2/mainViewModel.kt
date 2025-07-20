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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit

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
   val index: Int = -1,             // Current song in playlist (0 indexed, -1 if none)
   val pos: Long = 0L              // Current position in song (milliseconds, 0 if none)
)

class MainViewModel(application : Application) : AndroidViewModel(application)
{
   private val _playlistState = MutableStateFlow<PlaylistState>(PlaylistState())
   val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()
   
   suspend fun clearSavedState()
   // Called when a playlist reaches the end.
   {
      val current = _playlistState.value
      
      utils.mainActivity!!.playlistPrefsState.edit {
         preferences ->
            preferences[PlaylistPreferenceKeys.NAME] = ""
         if (current.baseName != "")
            {
               preferences[PlaylistPreferenceKeys.count(current.baseName)] = 0
               preferences[PlaylistPreferenceKeys.index(current.baseName)] = -1
               preferences[PlaylistPreferenceKeys.pos(current.baseName)] = 0

               _playlistState.value = PlaylistState(
                  baseName = "",
                  count = 0,
                  index = -1,
                  pos = 0)
            }
      }
   } // clearSavedState

   suspend fun writeCategory(category : String)
   {
      utils.mainActivity!!.playlistPrefsState.edit {
         preferences ->
            preferences[PlaylistPreferenceKeys.NAME] = category
      }
         
      _playlistState.value = PlaylistState(
         baseName = category,
         count = 0,
         index = -1,
         pos = 0)
   } // writeCategory
      
   suspend fun writeState(count : Int, index : Int, pos : Long)
   {
      val current = _playlistState.value

      if (current.baseName != "")
         {
            utils.mainActivity!!.playlistPrefsState.edit {
               preferences ->
                  preferences[PlaylistPreferenceKeys.NAME] = current.baseName
               preferences[PlaylistPreferenceKeys.count(current.baseName)] = count
               preferences[PlaylistPreferenceKeys.index(current.baseName)] = index
               preferences[PlaylistPreferenceKeys.pos(current.baseName)] = pos

               _playlistState.value = PlaylistState(
                  baseName = current.baseName,
                  count = count,
                  index = index,
                  pos = pos)
            }
         }
   }// writeState

   private suspend fun readPlaylistState()
   {
      val preferences = utils.mainActivity!!.playlistPrefsState.data.firstOrNull()
      if (preferences == null)
         {
            // Never set
            _playlistState.value = PlaylistState(
               baseName = "",
               count = 0,
               index = -1,
               pos = 0)
         }
      else
         {
            var temp : String? = preferences[PlaylistPreferenceKeys.NAME]
            if (temp == null)
               {
                  // Never set
                  _playlistState.value = PlaylistState(
                     baseName = "",
                     count = 0,
                     index = -1,
                     pos = 0)
               }
            else
               {
                  _playlistState.value = PlaylistState(
                     baseName = temp,
                     count = preferences[PlaylistPreferenceKeys.count(temp)]!!,
                     index = preferences[PlaylistPreferenceKeys.index(temp)]!!,
                     pos = preferences[PlaylistPreferenceKeys.pos(temp)]!!)
               }
         }
   }
   
   private val _isMediaControllerReady = MutableStateFlow(false)

   val isPlayerReadyToInitialize: StateFlow<Boolean> =
      combine(_playlistState, _isMediaControllerReady)
   {_, mediaControllerReady ->
        mediaControllerReady
   }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

   init {
      loadPlaylistState()
   }
   
   private fun loadPlaylistState()
   {
      viewModelScope.launch {
         // FIXME: Maybe this is better? doc all the reasons for utils.mainActivity(= application!?)
         // val context = getApplication<Application>().applicationContext
         readPlaylistState() 
      }
   }
   
   fun setMediaControllerReady(isReady: Boolean)
   {
      _isMediaControllerReady.value = isReady
   }
   
} //MainViewModel
