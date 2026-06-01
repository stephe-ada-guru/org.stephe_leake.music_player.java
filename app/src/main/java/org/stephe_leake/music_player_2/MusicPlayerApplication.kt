//  Abstract :
//
//  Main application; global objects.
//  
//  There are other global objects in util.
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

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayerApplication : Application() {
   val db by lazy { SongDatabase.getDatabase(this) }

   // This is declared here because it is needed by MainActivity and SearchActivity. 
   val mainViewModelFactory by lazy {
      MainViewModel.Companion.MainViewModelFactory(this, this.db.songDao())}

   // searchViewModelFactory is not declared here because it is only
   // needed by SearchActivity, and because it needs an actual
   // mainViewModel reference.
}

// Define the events MainViewModel can send to MainActivity.
sealed class AppEvent
{
    data object ReloadPlaylist : AppEvent()
}

object AppEventBus
{
    private val _events = MutableSharedFlow<AppEvent>()
    val events = _events.asSharedFlow()

    suspend fun emitEvent(event: AppEvent)
 {
        _events.emit(event)
    }
}

// Playlist preferences are actually state data, so we use DataStore.
// Other preferences (given in preferences.xml) are stored in
// DefaultSharedPreferences (since that's what the UI edits). We
// declare this here because utils and mainViewModel both need it.
const val PLAYLIST_PREFERENCES_NAME = "playlist_prefs"
val Context.playlistPrefsState : DataStore<Preferences> by preferencesDataStore(
   name = PLAYLIST_PREFERENCES_NAME)

object PlaylistCountsPreferenceKeys
{
   // NAME is shared with mainViewModel, but only mainViewModel writes to it.
   val NAME  = stringPreferencesKey("name")

   fun index(name : String) : Preferences.Key<Int> {return intPreferencesKey("$name-index")}
   fun pos(name : String) : Preferences.Key<Long> {return longPreferencesKey("$name-pos")}
   fun limit(name : String) : Preferences.Key<Int> {return intPreferencesKey("$name-limit")}

   val syncIdKey = intPreferencesKey("last_sync_id")
   val syncTimeKey = stringPreferencesKey("last_sync_time")
}
      
sealed class SyncStatus {
   data object Idle : SyncStatus()
   data class Progress(val label: String) : SyncStatus()
   data class Done(val msg: String) : SyncStatus()
   data class Error(val msg: String) : SyncStatus()
}

object SyncStatusBus {
   private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
   val status: StateFlow<SyncStatus> = _status.asStateFlow()
   fun emit(s: SyncStatus) { _status.value = s }
}

// end of file
