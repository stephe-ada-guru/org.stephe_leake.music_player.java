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

import org.stephe_leake.music_player_2.utils

class DownloadUtils
{
   companion object
   {
      val prefLogLevel : LogLevel = LogLevel.Info
   }

   val BUFFER_SIZE : Int = 8 * 1024

   // used in processDirEntry
   var playlistDir     : String = ""
   var mentionedFiles  : MutableList<String> = mutableListOf<String>()
   val logFileBaseName : String = "download_log"

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

   fun readPlaylist(playlistFilename : String, lowercase : boolean) : List<String> 
   {
      // Read playlist file, return list of files (lowercase) in it.
      val playlistFile : File = File(playlistFilename)

      var result : LinkedList<String> = LinkedList<>()

      for (i : LineIterator in FileUtils.lineIterator(playlistFile))
      {
         val line : String = i.next()
         if lowercase
            {
               result.addLast(line.toLowerCase(Locale.getDefault()))
            }
         else
            result.addLast(line)
      }
      return result;
   }

}
