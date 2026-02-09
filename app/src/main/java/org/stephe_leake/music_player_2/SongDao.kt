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
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao
{
    class SongNotFound(msg: String): Exception(msg)
   
   @Update // update others where ID = :song.id
   suspend fun updateSong(song: Song)
   // User must set song.Modified appropriately

   @Insert // aborts on index conflict
   suspend fun insertSong(song: Song)

   suspend fun updateSong
   (ID               : Int,
    Modified         : String? = null, 
    Deleted          : String? = null, 
    File_Name        : String? = null, 
    Category         : String? = null,
    Artist           : String? = null,
    Album_Artist     : String? = null,
    Composer         : String? = null,
    Album            : String? = null,
    Year             : Int? = null,
    Title            : String? = null, 
    Track            : Int? = null,
    Last_Downloaded  : String? = null, 
    Prev_Downloaded  : String? = null,
    Play_Before      : Int? = null,
    Play_After       : Int? = null) 
   {
      // Room doesn't let us construct an sql statement as we do in Ada.
      val valueCheck : Song? = this.getSong(ID)
      if (valueCheck == null)
         {
            throw SongNotFound("$ID song not found")
         }
      else
         {
            val value = valueCheck.copy(
               Modified        = Modified        ?: valueCheck.Modified,
               Deleted         = Deleted         ?: valueCheck.Deleted,
               File_Name       = File_Name       ?: valueCheck.File_Name,
               Category        = Category        ?: valueCheck.Category,
               Artist          = Artist          ?: valueCheck.Artist,
               Album_Artist    = Album_Artist    ?: valueCheck.Album_Artist,
               Composer        = Composer        ?: valueCheck.Composer,
               Album           = Album           ?: valueCheck.Album,
               Year            = Year            ?: valueCheck.Year,
               Title           = Title           ?: valueCheck.Title,
               Track           = Track           ?: valueCheck.Track,
               Last_Downloaded = Last_Downloaded ?: valueCheck.Last_Downloaded,
               Prev_Downloaded = Prev_Downloaded ?: valueCheck.Prev_Downloaded,
               Play_Before     = Play_Before     ?: valueCheck.Play_Before,
               Play_After      = Play_After      ?: valueCheck.Play_After
            )
            updateSong(value)
         }
   } // updateSong
   
   @Query ("DELETE FROM Song WHERE ID = :id")
   suspend fun reallyDeleteSong(id: Int)
   
   @Query("""
          SELECT * FROM Song WHERE
          (:albumArtist = '' OR Album_Artist = :albumArtist) AND
          (:album = '' OR Album = :album) AND
          (:title = '' OR Title = :title)
          LIMIT 1
          """)
   suspend fun getSong(albumArtist : String, album: String, title : String) : Song?

   @Query("SELECT * FROM Song WHERE (ID = :id) LIMIT 1 ")
   suspend fun getSong(id : Int) : Song?

   @Query("SELECT MAX (ID) FROM Song")
   suspend fun getLastId() : Int

   @Query("""
          SELECT ID FROM Song WHERE ID > :id
          ORDER BY ID
          LIMIT :maxCount
          """)
   suspend fun getNew(id : Int, maxCount : Int) : List<Int>

   @Query("""
          SELECT ID FROM Song WHERE ID <= :id
          AND (Modified > :modified or Deleted > :modified)
          ORDER BY ID
          """)
   suspend fun getModified(id : Int, modified : String) : List<Int>

   @Query("""
          SELECT * FROM Song WHERE
          Artist LIKE '%' || :query || '%' OR
          Album LIKE '%' || :query || '%' OR
          Album_Artist LIKE '%' || :query || '%' OR
          Composer LIKE '%' || :query || '%' OR
          Title LIKE '%' || :query || '%' OR
          Category LIKE '%' || :query || '%'
          ORDER BY Album_Artist, Album, Title ASC
          """)
   // No 'suspend' here because it returns a Flow, which means Room
   // is already running it in a background task.
   fun generalSearch(query: String): Flow<List<Song>>

   @Query("""
          SELECT * FROM Song WHERE
          (:title = '' OR Title LIKE '%' || :title || '%') AND
          (:artist = '' OR Artist LIKE '%' || :artist || '%') AND
          (:album = '' OR Album LIKE '%' || :album || '%') AND
          (:albumArtist = '' OR Album_Artist LIKE '%' || :albumArtist || '%') AND
          (:composer = '' OR Composer LIKE '%' || :composer || '%') AND
          (:category = '' OR Category LIKE '%' || :category || '%')
          ORDER BY Album_Artist, Album, Title ASC
          """)
   // Returns Flow, no 'suspend'
   fun detailedSearch(
      title: String,
      artist: String,
      album: String,
      albumArtist: String,
      composer: String,
      category: String
   ): Flow<List<Song>>
}
