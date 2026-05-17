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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "Song",
        indices = [
           Index(value = ["File_Name"], unique = true, name = "File_Name"),
           Index(value = ["Artist"], name = "Artist"),
           Index(value = ["Album"], name = "Album"),
           Index(value = ["Title"], name = "Title"),
           Index(value = ["Last_Downloaded"], name = "Last_Downloaded"),
           Index(value = ["Album_Artist", "Album", "Title"], unique = true, name = "Song_Name")
        ])
@Suppress("PropertyName")
data class Song(
   // Names match [1] _exactly_. 
   @PrimaryKey
   val ID               : Int,
   var Modified         : String, 
   var Deleted          : String?, 
   val File_Name        : String, 
   val Category         : String,
   val Artist           : String?,
   val Album_Artist     : String,
   val Composer         : String?,
   val Album            : String?,
   val Year             : Int?,
   val Title            : String, 
   var Track            : Int?,
   var Last_Downloaded  : String?, 
   var Prev_Downloaded  : String?,
   var Play_Before      : Int?,
   var Play_After       : Int?
)
{
   companion object
   {
      const val Null_ID  : Int = -1
      const val No_Track : Int = -1
      const val No_Year  : Int = -1
      const val Default_Time_String : String = "1958-01-01 00:00:00"

      fun getTime(): String
      {
         val current = LocalDateTime.now(java.time.ZoneOffset.UTC)
         val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
         return current.format(formatter)
      }

      fun fromJSON(msg: JSONObject): Song
      {
         // msg format given by smm-database.adb Get_JSON
         if (!msg.has("Data"))
            {
               return Song(
                  ID              = msg.getInt("ID"),
                  Modified        = if (msg.has("Modified")) msg.getString("Modified") else Default_Time_String,
                  Deleted         = msg.getString("Deleted"),
                  File_Name       = "",
                  Category        = "",
                  Artist          = null,
                  Album_Artist    = "",
                  Composer        = null,
                  Album           = null,
                  Year            = null,
                  Title           = "",
                  Track           = null,
                  Last_Downloaded = null, 
                  Prev_Downloaded = null,
                  Play_Before     = null,
                  Play_After      = null)
               }
         else
            {
               val dataf: JSONObject = msg.getJSONObject("Data")
               return Song(
                  ID              = msg.getInt("ID"),
                  Modified        = msg.getString("Modified"),
                  Deleted         = null, 
                  File_Name       = dataf.getString("File_Name"), 
                  Category        = dataf.getString("Category"),
                  Artist          = if (dataf.has("Artist")) dataf.getString("Artist") else null,
                  Album_Artist    = dataf.getString("Album_Artist"),
                  Composer        = if (dataf.has("Composer")) dataf.getString("Composer") else null,
                  Album           = if (dataf.has("Album")) dataf.getString("Album") else null,
                  Year            = if (dataf.has("Year")) dataf.getInt("Year") else null,
                  Title           = dataf.getString("Title"),
                  Track           = if (dataf.has("Track")) dataf.getInt("Track") else null,
                  Last_Downloaded = if (dataf.has("Last_Downloaded")) dataf.getString("Last_Downloaded") else null, 
                  Prev_Downloaded = if (dataf.has("Prev_Downloaded")) dataf.getString("Prev_Downloaded") else null,
                  Play_Before     = if (dataf.has("Play_Before")) dataf.getInt("Play_Before") else null,
                  Play_After      = if (dataf.has("Play_After")) dataf.getInt("Play_After") else null)
            }
      } // fromJSON

      fun toJSON(song: Song) : JSONObject
      // Same format as smm-database.adb Get_JSON
      {
         val result = JSONObject()
         if (song.Deleted != null)
            {
               result.put("ID", song.ID)
               result.put("Deleted", song.Deleted)
            }
         else
            {
               val dataf = JSONObject()
               dataf.put("File_Name", song.File_Name)
               dataf.put("Category", song.Category)
               if (!song.Artist.isNullOrEmpty()) {dataf.put("Artist", song.Artist)}
               dataf.put("Album_Artist", song.Album_Artist)
               if (!song.Composer.isNullOrEmpty()) {dataf.put("Composer", song.Composer)}
               if (!song.Album.isNullOrEmpty()) {dataf.put("Album", song.Album)}
               if (song.Year != null) if (song.Year != Song.No_Year) {dataf.put("Year", song.Year)}
               dataf.put("Title", song.Title)
               if (song.Track != null) if (song.Track != Song.No_Track){dataf.put("Track", song.Track)}
               if (song.Last_Downloaded != null) if (song.Last_Downloaded != Song.Default_Time_String)
                  {dataf.put("Last_Downloaded", song.Last_Downloaded)}
               if (song.Prev_Downloaded != null) if (song.Prev_Downloaded != Song.Default_Time_String)
                  {dataf.put("Prev_Downloaded", song.Prev_Downloaded)}
               if (song.Play_Before != null) if (song.Play_Before != Song.Null_ID)
                  {dataf.put("Play_Before", song.Play_Before)}
               if (song.Play_After != null) if (song.Play_After != Song.Null_ID)
                  {dataf.put("Play_After", song.Play_After)}
               
               result.put("ID", song.ID)
               result.put("Modified", song.Modified)
               result.put("Data", dataf)
            }
         return result
      }

      fun toJSON(list: List<Int>): JSONArray
      // Same format as smm.ads To_JSON
      {
         val result = JSONArray()
         for (id in list) {result.put(id)}
         return result
      }

      @JvmName("toJSONSongList")
      fun toJSON(list: List<Song>): JSONArray
      // Same format as smm-database.adb Get_JSON, for a list of songs
      {
         val result = JSONArray()
         for (song in list) {result.put(toJSON(song))}
         return result
      }
   }
}
