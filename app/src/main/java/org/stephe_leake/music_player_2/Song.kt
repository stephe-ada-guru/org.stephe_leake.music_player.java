//  Abstract :
//
//  Interface to the sqlite database
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
import androidx.room.PrimaryKey

@Entity(tableName = "Song")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val ID: Int = 0,
    val file_Name: String,
    val category: String,
    val artist: String,
    val albumArtist: String,
    val composer: String,
    val album: String,
    val year: String,
    val title: String,
    var track: Int,
    var lastDownloaded : String, // FIXME: CHAR[19]
    var prevDownloaded : String, // FIXME: CHAR[19]
    var playBefore : Int,
    var playAfter : Int
)
