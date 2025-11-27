//  Abstract :
//
//  Interface to the sqlite database
//
//  References:
//
//  [1] ~/Projects/smm.main/source/create_schema.sql
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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "Song",
        indices = [
           Index(value = ["File_Name"], name = "File_Name"),
           Index(value = ["Artist"], name = "Artist"),
           Index(value = ["Album"], name = "Album"),
           Index(value = ["Title"], name = "Title"),
           Index(value = ["Last_Downloaded"], name = "Last_Downloaded"),
           Index(value = ["Album_Artist", "Album", "Title"], unique = true, name = "Song_Name")
        ])
@Suppress("PropertyName")
data class Song(
   // Names match [1] _exactly_
   @PrimaryKey
   val ID               : Int,
   val File_Name        : String?,
   val Category         : String?,
   val Artist           : String?,
   val Album_Artist     : String?,
   val Composer         : String?,
   val Album            : String?,
   val Year             : Int?,
   val Title            : String?,
   var Track            : Int?,
   var Last_Downloaded  : String?, 
   var Prev_Downloaded  : String?,
   var Play_Before      : Int?,
   var Play_After       : Int?
)
