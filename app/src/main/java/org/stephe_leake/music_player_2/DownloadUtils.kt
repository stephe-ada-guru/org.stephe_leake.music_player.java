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

import android.media.MediaScannerConnection
import android.content.Context

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
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.FileFileFilter
import org.apache.commons.io.filefilter.OrFileFilter
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter

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
      val prefLogLevel : LogLevel = LogLevel.Info
      val BUFFER_SIZE : Int = 8 * 1024

      // used in processDirEntry
      var playlistDir     : String = ""
      var mentionedFiles  : MutableList<String> = mutableListOf<String>()
      val logFileBaseName : String = "download_log"

      lateinit var httpClient : OkHttpClient 

      fun logFileName() : String
      {
         return utils.smmDirectory + "/" + logFileBaseName + utils.logFileExt
      }

      fun log(context : Context, level : LogLevel, msg : String)
      {
         if (level >= prefLogLevel)
            {
               utils.log(context, level, msg, logFileBaseName)
            }
      }

      fun ensureHttpClient()
      {
         if (null == httpClient)
            {
               httpClient = OkHttpClient.Builder()
                  .retryOnConnectionFailure(true)
                  .connectTimeout(5, TimeUnit.MINUTES) // don't have to handle retry at higher level
                  .build();
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

      fun prunePlaylist(context          : Context,
                        playlistFilename : String,
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
            val lastPlayed : String           = input.readLine()
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

      public fun cleanPlaylist(context : Context, category : String, playlistDir : String, smmDir : String)
      {
         // Delete lines in category.m3u that are before song in
         // SMM_Dir/category.last.
         //
         // Directory names end in '/'

         // We can't declare a File object for lastFile; that prevents
         // delete in prunePlaylist.
         val playlistFilename : String = FilenameUtils.concat(playlistDir, category + ".m3u")
         val lastFilename     : String = FilenameUtils.concat(smmDir, category + ".last")

         try
         {
            // getPath() returns empty string if file does not exist
            if ("" != FilenameUtils.getPath(playlistFilename))
               if ("" != FilenameUtils.getPath(lastFilename))
               {
                  val deleteCount : Int = prunePlaylist(context, playlistFilename, lastFilename);
                  log(context, LogLevel.Info, category + " playlist cleaned: " + deleteCount + " songs deleted")
               }
         }
         catch (e : IOException)
         {
            // from prunePlaylist (which calls readPlaylist)
            log(context, LogLevel.Error, "cannot read/write playlist '" + playlistFilename + "'")
         }
      }
      
      fun getNewSongsList(context  : Context,
                          serverIP : String,
                          category : String,
                          count    : Int,
                          newCount : Int,
                          overSelectRatio : Float,
                          randomSeed      : Int)
         : StatusStrings
      // randomSeed = -1 means randomize; other values used in unit tests.
      {
         val url = "http://" + serverIP + ":8080/download?" +
         "category=" + category +
         "&count=" + count.toString() +
         "&new_count=" + newCount.toString() +
         "&over_select_ratio=" + overSelectRatio.toString() +
         (if (-1 == randomSeed) "" else "&seed=$randomSeed")

         val request : Request = Request.Builder().url(url).build()
         val result  : StatusStrings = StatusStrings()

         ensureHttpClient()

         try
         {
            val response : Response = httpClient.newCall(request).execute()

            try
            {
               result.strings = response.body!!.string().split("\r\n")
            }
            catch (e: IOException) {
               // From response.body()
               log(context, LogLevel.Error, "getNewSongsList request has no body: " + e.toString())
               result.status = ProcessStatus.Fatal
            }
         }
         catch (e: IOException) {
            // From httpClient.newCall; connection failed after retry
            log(context, LogLevel.Error, "getNewSongsList '" + url + "': http request failed: " + e.toString())
            result.status = ProcessStatus.Retry
         }

         log(context, LogLevel.Info, "getNewSongsList: " + result.strings.size.toString() + " songs")
         return result
      }

      fun sendNotes(context  : Context,
                    serverIP : String,
                    category : String,
                    smmDir   : String)
         : ProcessStatus
      {
         val noteFile : File = File(smmDir, "$category.note")
         var status   : ProcessStatus = ProcessStatus.Success

         if (noteFile.exists())
            {
               val url = "http://$serverIP:8080/remote_cache/$category.note"
               var data = ""

               try
               {
                  val noteReader: LineNumberReader = LineNumberReader(FileReader(noteFile))
                  var line: String = noteReader.readLine()
                  
                  while (line != null) {
                     // Doc for LineNumberReader says 'line' includes line terminators, but it doesn't.
                     data = data + line + "\r\n"
                     line = noteReader.readLine()
                  }
               }
               catch (e: FileNotFoundException) {} // from noteReader constructor; can't get here
               catch (e: IOException) {} // from noteReader.readLine; can't get here

               {
                  val body    : TextBody = TextBody(data)
                  val request : Request  = Request.Builder()
                     .url(url)
                     .put(body)
                     .build()

                  ensureHttpClient()
                  try
                  {
                     val response : Response =httpClient.newCall(request).execute()
                     
                     if (200 != response.code)
                        {
                           status = ProcessStatus.Fatal // something wrong with server
                           log(context, LogLevel.Error, "put notes failed " + response.message)
                        }
                     else
                        log(context, LogLevel.Info, "$category sendNotes")
                  }
                  catch (e: IOException) {
                     // From httpClient.newCall; connection failed after retry
                     log(context, LogLevel.Error, category + " sendNotes http request failed: " + e.toString())
                     status = ProcessStatus.Retry // retry after delay
                  }
               }

               noteFile.delete()
            }

         return status
      }

   } // companion
}
