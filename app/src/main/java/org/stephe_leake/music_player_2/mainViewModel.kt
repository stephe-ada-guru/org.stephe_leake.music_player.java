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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application : Application) : AndroidViewModel(application)
{
   private val _playlistBaseName = MutableStateFlow<String?>(null)
   val playlistBaseName: StateFlow<String?> = _playlistBaseName.asStateFlow()
   
   private val _isMediaControllerReady = MutableStateFlow(false)
   val isMediaControllerReady: StateFlow<Boolean> = _isMediaControllerReady.asStateFlow()

   val isPlayerReadyToInitialize: StateFlow<Boolean> =
      combine(_playlistBaseName, _isMediaControllerReady)
   { playlistName, controllerReady ->
        playlistName != null && controllerReady
   }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)


   // Store a reference to your utils if needed, or pass context
   // private val appUtils = utils // Assuming utils is an object or accessible

   init {
      loadPlaylistName()
   }
   
   private fun loadPlaylistName()
   {
      viewModelScope.launch {
         // Maybe this is better? doc all the reasons for utils.mainActivity(= application!?)
         // val context = getApplication<Application>().applicationContext
         utils.readPlaylistName() 
         _playlistBaseName.value = utils.tempPlaylistName // Update StateFlow with the value read by utils
      }
   }
   
   fun setMediaControllerReady(isReady: Boolean)
   {
      _isMediaControllerReady.value = isReady
   }
   
} //MainViewModel
