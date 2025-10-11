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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao
{
    @Update
    suspend fun updateSong(song: Song)

    @Query("""
        SELECT * FROM Song WHERE
        artist LIKE '%' || :query || '%' OR
        album LIKE '%' || :query || '%' OR
        album_artist LIKE '%' || :query || '%' OR
        composer LIKE '%' || :query || '%' OR
        title LIKE '%' || :query || '%' OR
        category LIKE '%' || :query || '%'
    """)
    suspend fun generalSearch(query: String): Flow<List<Song>>

    // Detailed search with specific fields
    @Query("""
        SELECT * FROM Song WHERE
        (:title = '' OR title LIKE '%' || :title || '%') AND
        (:artist = '' OR artist LIKE '%' || :artist || '%') AND
        (:album = '' OR album LIKE '%' || :album || '%') AND
        (:albumArtist = '' OR albumArtist LIKE '%' || :albumArtist || '%') AND
        (:composer = '' OR composer LIKE '%' || :composer || '%') AND
        (:category = '' OR category LIKE '%' || :category || '%')
    """)
    fun detailedSearch(
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        category: String
    ): Flow<List<Song>>
}
