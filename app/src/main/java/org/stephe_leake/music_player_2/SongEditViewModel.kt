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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SongEditViewModel(private val songDao: SongDao) : ViewModel()
{
   companion object
   {
      // The view model machinery in Android ensures that this 'create' is only called once.
      class SongEditViewModelFactory(
         private val songDao: SongDao) :
         ViewModelProvider.Factory
      {
         @Suppress("UNCHECKED_CAST")
         override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SongEditViewModel::class.java)) {
               return SongEditViewModel(songDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
         }
      }
   }

   var songState by mutableStateOf<Song?>(null)
      private set

   val _isSongLoaded = MutableStateFlow(false)
   val isSongLoaded : StateFlow<Boolean> = _isSongLoaded.asStateFlow()

   fun loadSong(id: Int)
   {
      _isSongLoaded.value = false // In case this viewModel is still active from a previous SongEdit
      
      viewModelScope.launch {
         songState = songDao.getSong(id)
         _isSongLoaded.value = true
      }
   }

   fun isValid() : Boolean
   {
      return songState != null
   }
   
   fun updateField(update: (Song) -> Song)
   {
      songState = songState?.let { update(it) }
   }

   suspend fun save()
   {
      songState?.let {
         it.Modified = Song.getTime() // Update modified timestamp
         songDao.updateSong(it)}
   }
}
