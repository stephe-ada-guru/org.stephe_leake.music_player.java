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

import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.LineNumberReader
import java.util.Locale
import java.util.concurrent.TimeUnit

import kotlin.collections.MutableList

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FileUtils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class DownloadUtils
{
   companion object
   {
      var prefLogLevel : LogLevel = LogLevel.Info

      const val downloadLogFileBaseName = "download_log"

      var httpClient : OkHttpClient? = null

      fun downloadLogFileName() : String
      {
         return utils.logFileName(downloadLogFileBaseName)
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

      fun readPlaylist(playlistFilename : String, lowercase : Boolean) : MutableList<String> 
      {
         // Read playlist file, return list of files (lowercase) in it.
         val playlistFile = File(playlistFilename)

         var result : MutableList<String> = mutableListOf<String>()

         for (line : String in FileUtils.lineIterator(playlistFile))
            {
               if (lowercase)
                  {
                     result.add(line.lowercase(Locale.getDefault()))
                  }
               else
                  result.add(line)
            }
         return result
      }

      fun prunePlaylist(playlistFilename : String,
                        lastIndex        : Int) : Int
      // Delete lines from start of playlist file up to but not including
      // line lastIndex; that song is currently being played.
      // Return count of lines remaining.
      {
         try {
            val lines  : MutableList<String> = readPlaylist(playlistFilename, false)
            val output = FileWriter(playlistFilename) // Erases file
            repeat (minOf (lastIndex, lines.count()))
            {
               lines.removeAt(0)
            }
            
            for (line in lines)
               {
                  output.write(line + "\n")
               }
            output.close()
            return lines.size
         }
         catch (_: FileNotFoundException)
         { // from 'FileReader(lastFilename)'; file not found; same as empty; do nothing
           return 0
         }
      }

      suspend fun cleanPlaylist(category : String) : Int
      {
         // Delete lines in category.m3u that are before preferences(category).index
         // Return count of lines remaining.

         val playlistFileName : String = utils.playlistFileName(category)
         var counts = utils.readPlaylistCounts(category)

         try
         {
            // getPath() returns empty string if file does not exist
            if ("" != FilenameUtils.getPath(playlistFileName))
               if (counts.index > 0)
               {
                  return prunePlaylist(playlistFileName, counts.index)
               }
            else
               {
                  return 0
               }
         }
         catch (_ : IOException)
         {
            // from prunePlaylist
            return 0            
         }
         return 0 // keep the compiler happy
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
         val url = "http://" + serverIP + ":/app/smm/get_new_songs_list?" +
         "API=2" + 
         "&category=" + category +
         "&count=" + count.toString() +
         "&new_count=" + newCount.toString() +
         "&over_select_ratio=" + overSelectRatio.toString() +
         "&record_downloaded=" + if (category == "instrumental" || category == "vocal")
             "true" else "false" +
         (if (-1 == randomSeed) "" else "&seed=$randomSeed")

         val request : Request = Request.Builder().url(url).build()
         val result  = StatusStrings()

         ensureHttpClient()

         try
         {
            val response : Response = httpClient!!.newCall(request).execute()

            if (response.code < 200 || response.code > 299)
               {
                  log(LogLevel.Error, "getNewSongsList server error: ${response.code}:${response.message}")
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
         val playlistFile   = File(utils.playlistFileName(category))
         val playlistWriter : FileWriter
         val result         = StatusCount()
         var newSongs       = 0

         try
         {
            playlistWriter = FileWriter(playlistFile, true) // append
         }
         catch (_: IOException)
         {
            log(LogLevel.Error, "cannot open '" + playlistFile.absolutePath + "' for append.")
            result.status = ProcessStatus.Fatal
            return result
         }

         try
         {
            for (song in songs)
               {
                  // Searching MediaStore.Audio on metadata is not
                  // reliable, so we use direct file access.
                  val FileName = utils.globalDirectory + "/" + song
                  val file = File (FileName)
                  
                  if (!file.exists())
                     {
                        newSongs++
                        // not found; we can't download it to a specific directory, so tell the user to download it
                        // IMPROVME: with MANAGE_EXTERNAL_STORAGE, can write song file to correct directory
                        result.status = ProcessStatus.Retry
                        log(LogLevel.Info, "not found '" + FileName + "'")
                     }

                  // Write even if not found; user will download the song later.
                  playlistWriter.write("$song\n")    
               }
         }
         catch (_: IOException)
         {
            // From playlistWriter.write
            log(LogLevel.Error, "cannot append to '" + playlistFile.absolutePath + "'; disk full?")
            result.status = ProcessStatus.Fatal // non-recoverable
         }
         finally
         {
            try
            {
               playlistWriter.close()
            }
            catch (_: IOException)
            {
               // probably from flush cache
               log(LogLevel.Error, "cannot close '" + playlistFile.absolutePath + "'; disk full?")
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
         var data = ""
         
         if (noteFile.exists())
            {
               try
               {
                  val noteReader = LineNumberReader(FileReader(noteFile))
                  var line : String? = noteReader.readLine()
                  
                  while (line != null) {
                     // Doc for LineNumberReader says 'line' includes line terminators, but it doesn't.
                     data = data + line + "\r\n"
                     line = noteReader.readLine()
                  }
               }
               catch (_: FileNotFoundException) {} // from noteReader constructor; can't get here
               catch (_: IOException) {} // from noteReader.readLine; can't get here
            }
         return data
      }
   
      fun sendNotes(serverIP : String,
                    category : String)
         : ProcessStatus
      {
         var status   : ProcessStatus = ProcessStatus.Success
         val url      = "http://${serverIP}/app/smm/${category}.note"
         val noteFile = File(utils.notesFileName(category))
         var data     = readNotes(noteFile)

         if (data.isNotEmpty())
            {
               val body    = TextBody(data)
               val request = Request.Builder()
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
                     log(LogLevel.Info, "${category} sendNotes")
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
