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

class MusicPlayerApplication : Application() {
   val db by lazy { SongDatabase.getDatabase(this) }

   // This is declared here because it is needed by MainActivity and SearchActivity. 
   val mainViewModelFactory by lazy {
      MainViewModel.Companion.MainViewModelFactory(this, this.db.songDao())}

   // searchViewModelFactory is not declared here because it is only
   // needed by SearchActivity, and because it needs an actual
   // mainViewModel reference.
}

