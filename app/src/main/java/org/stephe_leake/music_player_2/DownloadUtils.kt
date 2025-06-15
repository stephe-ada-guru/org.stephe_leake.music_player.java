//  Abstract :
//
//  Utilities for downloading from smm_server
//
//  Copyright (C) 2016 - 2019, 2021 Stephen Leake. All Rights Reserved.
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

import android.database.Cursor
import android.provider.MediaStore

import java.io.BufferedInputStream
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.LineNumberReader
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit

import kotlin.collections.MutableList

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.LineIterator

// filefilter is a package, not a class
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.FileFileFilter
import org.apache.commons.io.filefilter.OrFileFilter
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter

import org.json.JSONObject
import org.json.JSONTokener

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

import org.stephe_leake.music_player_2.utils

class DownloadUtils
{
   companion object
   {
      var prefLogLevel : LogLevel = LogLevel.Info

      // used in processDirEntry
      var playlistDir     : String = ""
      var mentionedFiles  : MutableList<String> = mutableListOf<String>()
      val downloadLogFileBaseName : String = "download_log"

      var httpClient : OkHttpClient? = null

      fun downloadLogFileName() : String
      {
         return utils.appDirectory + "/" + downloadLogFileBaseName + utils.logFileExt
      }

      fun log(level : LogLevel, msg : String)
      {
         if (level >= prefLogLevel)
            {
               utils.log(level, msg, downloadLogFileBaseName)
            }
      }

      fun ensureHttpClient()
      {
         if (null == httpClient)
            {
               httpClient = OkHttpClient.Builder()
                  .retryOnConnectionFailure(true)
                  .connectTimeout(5, TimeUnit.MINUTES) // don't have to handle retry at higher level
                  .build()
            }
      }

      fun readPlaylist(playlistFilename : String, lowercase : Boolean) : List<String> 
      {
         // Read playlist file, return list of files (lowercase) in it.
         val playlistFile : File = File(playlistFilename)

         var result : LinkedList<String> = LinkedList<String>()

         for (line : String in FileUtils.lineIterator(playlistFile))
            {
               if (lowercase)
                  {
                     result.addLast(line.lowercase(Locale.getDefault()))
                  }
               else
                  result.addLast(line)
            }
         return result;
      }

      fun prunePlaylist(playlistFilename : String,
                        lastFilename     : String)
         : Int
      // Delete lines from start of playlist file up to but not including
      // line in last file; that song is currently being played.
      //
      // Return delete count.
      //
      // throws IOException if can't read or write playlistFilename
      {
         var deleteCount : Int = 0

         try {
            val lines      : List<String>     = readPlaylist(playlistFilename, false)
            val input      : LineNumberReader = LineNumberReader(FileReader(lastFilename))
            val lastPlayed : String?          = input.readLine()
            var found      : Boolean          = false

            input.close()

            if (null == lastPlayed)
               {
                  // last file is empty; nothing to delete
               }
            else
               {
                  // Check if lastPlayed is in playlist
                  for (line in lines)
                     {
                        if (!found)
                           found = line == lastPlayed
                     }

                  if (found)
                     {
                        val output : FileWriter = FileWriter(playlistFilename) // Erases file

                        found = false

                        for (line in lines)
                           {
                              if (!found)
                                 {
                                    found = line == lastPlayed
                                 }
                              if (found)
                                 output.write(line + "\n")
                              else
                                 deleteCount++
                           }
                        output.close()
                     }
               }
         }
         catch (e: FileNotFoundException)
         { // from 'FileReader(lastFilename)'; file not found; same as empty; do nothing
         }

         return deleteCount
      }

      public fun cleanPlaylist(category : String)
      {
         // Delete lines in category.m3u that are before song in
         // appDir/category.last.
         //
         // Directory names end in '/'

         // We can't declare a File object for lastFile; that prevents
         // delete in prunePlaylist.
         val playlistFilename : String = utils.playlistFileName(category)
         val lastFilename     : String = utils.lastFileName (category)

         try
         {
            // getPath() returns empty string if file does not exist
            if ("" != FilenameUtils.getPath(playlistFilename))
               if ("" != FilenameUtils.getPath(lastFilename))
               {
                  val deleteCount : Int = prunePlaylist(playlistFilename, lastFilename)
                  log(LogLevel.Info, category + " playlist cleaned: " + deleteCount + " songs deleted")
               }
         }
         catch (e : IOException)
         {
            // from prunePlaylist (which calls readPlaylist)
            log(LogLevel.Error, "cannot read/write playlist '" + playlistFilename + "'")
         }
      }
      
      fun getNewSongsList(serverIP : String,
                          category : String,
                          count    : Int,
                          newCount : Int,
                          overSelectRatio : Float,
                          randomSeed      : Int)
         : StatusStrings
      // randomSeed = -1 means randomize; other values used in unit tests.
      {
         // The web server is only visible from my local network, and
         // does not have an ssl certificate, so we use plaintext.
         val url = "http://" + serverIP + ":8080/get_new_songs_list?" +
         "API=2" + 
         "&category=" + category +
         "&count=" + count.toString() +
         "&new_count=" + newCount.toString() +
         "&over_select_ratio=" + overSelectRatio.toString() +
         "&record_downloaded=" + if (utils.playlistBaseName == "instrumental" || utils.playlistBaseName == "vocal")
             "true" else "false" +
         (if (-1 == randomSeed) "" else "&seed=$randomSeed")

         val request : Request = Request.Builder().url(url).build()
         val result  : StatusStrings = StatusStrings()

         ensureHttpClient()

         try
         {
            val response : Response = httpClient!!.newCall(request).execute()

            if (response.code < 200 || response.code > 299)
               {
                  log(LogLevel.Error, "getNewSongsList server error: " + response.message)
                  result.status = ProcessStatus.Fatal
               }
            else if (response.body == null)
               {
                  log(LogLevel.Error, "getNewSongsList request has no body")
                  result.status = ProcessStatus.Fatal
               }
            else
               {
                  result.strings = response.body!!.string().split("\r\n")
                  response.close()
               }
         }
         catch (e: IOException) {
            // From httpClient.newCall; connection failed after retry
            log(LogLevel.Error, "getNewSongsList '" + url + "': http request failed: " + e.toString())
            result.status = ProcessStatus.Retry
         }

         log(LogLevel.Info, "getNewSongsList: " + result.strings.size.toString() + " songs")
         return result
      } // getNewSongsList

      fun getSongs(songs    : List<String>,
                   category : String)
         : StatusCount
      {
         val playlistFile   : File = File(utils.playlistFileName(category))
         val playlistWriter : FileWriter
         val result         : StatusCount = StatusCount()
         // var metaStatus     : ProcessStatus
         // var fileStatus     : StatusCount
         var newSongs = 0

         try
         {
            playlistWriter = FileWriter(playlistFile, true) // append
         }
         catch (e: IOException)
         {
            log(LogLevel.Error, "cannot open '" + playlistFile.getAbsolutePath() + "' for append.")
            result.status = ProcessStatus.Fatal
            return result
         }

         try
         {
            for (song in songs)
               {
                  val data : JSONObject = JSONTokener(song).nextValue() as JSONObject
                  val Album_Artist = data.getString("Album_Artist") // FIXME: Album_Artist may be empty- don't match?
                  val Album = data.getString("Album")
                  val Title = data.getString("Title")
                  val Filename = data.getString("File_Name")

                  var cursor : Cursor? = utils.mainActivity!!.contentResolver.query(
                     MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                     /* projection */ arrayOf(MediaStore.Audio.AlbumColumns.ARTIST,
                                              MediaStore.Audio.AlbumColumns.ALBUM,
                                              MediaStore.Audio.AudioColumns.TITLE),
                     /* selection  */ "${MediaStore.Audio.AlbumColumns.ARTIST} = ? AND " + 
                     "${MediaStore.Audio.AlbumColumns.ALBUM} = ? AND " +
                     "${MediaStore.Audio.AudioColumns.TITLE} = ?",
                     /* selectionArgs */ arrayOf(Album_Artist.slice(1 .. Album_Artist.length - 1),
                                                 Album.slice(1 .. Album.length - 1),
                                                 Title.slice(1 .. Title.length - 1)),
                     /* sortOrder */ null)
                  
                  // The syntax that gemini gives for .use is _not_ correct!
                  if (cursor == null || !cursor.moveToFirst())
                     {
                        // not found; we can't download it to a specific directory, so tell the user to download it
                        // FIXME: use Files mediaStore interface?
                        result.status = ProcessStatus.Retry
                        log(LogLevel.Info, "not found '" + Filename + "'")
                     }
                  cursor?.close()

                  // Write even if not found; user will download the song later.
                  playlistWriter.write("$song\n")    
               }
         }
         catch (e: IOException)
         {
            // From playlistWriter.write
            log(LogLevel.Error, "cannot append to '" + playlistFile.getAbsolutePath() + "'; disk full?")
            result.status = ProcessStatus.Fatal // non-recoverable
         }
         finally
         {
            try
            {
               playlistWriter.close()
            } catch (e: IOException) {
               // probably from flush cache
               log(LogLevel.Error, "cannot close '" + playlistFile.getAbsolutePath() + "'; disk full?")
               result.status = ProcessStatus.Fatal // non-recoverable
            }
         }

         log(LogLevel.Info,
             result.count.toString() + " songs added to " + category + ", " + newSongs + " new.")

         return result

         // File objects hold the corresponding disk file locked; later
         // unit test cannot delete them.
      } // getSongs

      fun readNotes(noteFile : File)
         : String
      {
         var data : String = ""
         
         if (noteFile.exists())
            {
               try
               {
                  val noteReader : LineNumberReader = LineNumberReader(FileReader(noteFile))
                  var line : String? = noteReader.readLine()
                  
                  while (line != null) {
                     // Doc for LineNumberReader says 'line' includes line terminators, but it doesn't.
                     data = data + line + "\r\n"
                     line = noteReader.readLine()
                  }
               }
               catch (e: FileNotFoundException) {} // from noteReader constructor; can't get here
               catch (e: IOException) {} // from noteReader.readLine; can't get here
            }
         return data
      }
   
      fun sendNotes(serverIP : String,
                    category : String)
         : ProcessStatus
      {
         var status   : ProcessStatus = ProcessStatus.Success
         val url      : String = "http://$serverIP:8080/remote_cache/$category.note"
         val noteFile : File   = File(utils.notesFileName(category))
         var data     : String = readNotes(noteFile)

         if (data.length > 0)
            {
               val body    : TextBody = TextBody(data)
               val request : Request  = Request.Builder()
                  .url(url)
                  .put(body)
                  .build()
               
               ensureHttpClient()
               try
               {
                  val response : Response =httpClient!!.newCall(request).execute()
                  
                  if (200 != response.code)
                     {
                        status = ProcessStatus.Fatal // something wrong with server
                        log(LogLevel.Error, "put notes failed " + response.message)
                     }
                  else
                     log(LogLevel.Info, "$category sendNotes")
               }
               catch (e: IOException) {
                  // From httpClient.newCall; connection failed after retry
                  log(LogLevel.Error, category + " sendNotes http request failed: " + e.toString())
                  status = ProcessStatus.Retry // retry after delay
               }

               noteFile.delete()
            }
         
         return status
      }

   } // companion
}
