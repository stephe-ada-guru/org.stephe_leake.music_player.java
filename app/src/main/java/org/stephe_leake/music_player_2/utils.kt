//  Abstract :
//
//  misc stuff
//
//  Copyright (C) 2011 - 2013, 2015 - 2018, 2021, 2024 Stephen Leake.  All Rights Reserved.
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

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale

class utils
{
   companion object
   {
      val millisPerMinute : Long = 60 * 1000
      val millisPerHour   : Long = 60 * millisPerMinute
      val millisPerDay    : Long = 24 * millisPerHour
      
      //  Notification ids; all with null tag
      val notif_play_id     : Int = 1
      val notif_download_id : Int = 2
      
      //  Commands to play and download, sent via broadcast Intent actions. Alphabetical order
      //  Only one action, so we can add commands without adding to the reciever filter.
      val ACTION_PLAY_COMMAND     : String = "org.stephe_leake.stephes_music.action.play_command"
      val ACTION_DOWNLOAD_COMMAND : String = "org.stephe_leake.stephes_music.action.download_command"

      // according to android docs, extra field names must inlude the package prefix (no explanation of why)
      val EXTRA_COMMAND          : String = "org.stephe_leake.stephes_music.extra.command"
      val EXTRA_COMMAND_POSITION : String = "org.stephe_leake.stephes_music.action.command_position"
      val EXTRA_COMMAND_PLAYLIST : String = "org.stephe_leake.stephes_music.action.command_playlist"
      val EXTRA_COMMAND_STATE    : String = "org.stephe_leake.stephes_music.action.command_state"
      
      // play service commands
      const val COMMAND_JUMP           : Int = 4
      const val COMMAND_NEXT           : Int = 5
      const val COMMAND_NOTE           : Int = 6
      const val COMMAND_PAUSE          : Int = 7
      const val COMMAND_PLAY           : Int = 8
      const val COMMAND_PLAYLIST       : Int = 9 // playlist  string (abs file name)
      const val COMMAND_PREVIOUS       : Int = 10
      const val COMMAND_QUIT           : Int = 11
      const val COMMAND_RESET_PLAYLIST : Int = 12
      const val COMMAND_SAVE_STATE     : Int = 13
      const val COMMAND_SEEK           : Int = 14 // position  int (milliseconds)
      const val COMMAND_TOGGLEPAUSE    : Int = 15
      const val COMMAND_UPDATE_DISPLAY : Int = 16

      // download service commands
      val COMMAND_CANCEL_DOWNLOAD : Int = 2
      val COMMAND_DOWNLOAD        : Int = 3

      // sub-activity result codes
      val RESULT_TEXT_SCALE : Int         = Activity.RESULT_FIRST_USER + 1

      val pauseIntentId           : Int = 1
      val playIntentId            : Int = 2
      val prevIntentId            : Int = 3
      val nextIntentId            : Int = 4
      val activityIntentId        : Int = 5
      val showDownloadLogIntentId : Int = 6
      val showErrorLogIntentId    : Int = 7
      val cancelDownloadIntentId  : Int = 8

      val logTag : String =
         // Must be shorter than 23 chars
      //  1        10        20 |
      "stephes_music"

      // objects

      var showDownloadLogIntent : Intent = Intent(Intent.ACTION_VIEW)
      var showErrorLogIntent    : Intent = Intent(Intent.ACTION_VIEW)
      var cancelDownloadIntent  : Intent = Intent(Intent.ACTION_VIEW)
      
      var mainActivity: AppCompatActivity? = null


      ////////// Shared objects

      // preferences don't work, so this needs a valid default
      val smmDirectory : String = "/storage/emulated/0/Music/Music"

      var playlistBasename : String = ""
      // Current playlist file name; relative to smmDirectory, without
      // extension (suitable for user display). Empty if no playlist is
      // current.

      val logFileExt : String = ".txt"

      val errorLogFileBaseName : String = "error_log"

      // public non-member functions

      fun playlistAbsPath() : String 
      // return current playlist file abs path
      {
         return utils.smmDirectory + "/" + utils.playlistBasename + ".m3u"
      }

      fun findTextViewById (a: AppCompatActivity, id: Int) : TextView
      {
         val v : View? = a.findViewById(id)
         
         if (v == null) throw RuntimeException("no such id " + id)
            
            if (v is TextView)
            {
               return v
            }
         else
            {
               throw RuntimeException(id.toString() + " is not a TextView; it is a " + v.toString())
            }
      }

      fun logImage (item : LogLevel) : String
      {
         return when (item)
         {
            LogLevel.Verbose-> ""
            LogLevel.Info -> ""
            LogLevel.Error -> "ERROR: "
            LogLevel.Debug -> "Debug: "
         }
      }
      
      fun errorLogFileName() : String
      {
         return utils.smmDirectory + "/" + errorLogFileBaseName + logFileExt
      }

      fun log(context : Context, level : LogLevel, msg : String, logFileBaseName : String)
      {
         val fmt       : SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US)
         val time      : Long     = System.currentTimeMillis() // local time zone
         val timeStamp : String   = fmt.format(time)
         val levelImg  : String   = logImage (level)
         val logFileName : String = utils.smmDirectory + "/" + logFileBaseName + logFileExt
         val logFile     : File   = File(logFileName)
         val writer : PrintWriter = PrintWriter(FileWriter(logFileName, true)) // append

         if (logFile.exists() && time - logFile.lastModified() > 4 * utils.millisPerHour)
            {
               val oldLogFileName : String = utils.smmDirectory + "/" + logFileBaseName + "_1" + logFileExt
               val oldLogFile     : File   = File(oldLogFileName)

               if (oldLogFile.exists())
                  {oldLogFile.delete()}

               logFile.renameTo(oldLogFile)
            }

         writer.println(timeStamp + levelImg + msg)
         writer.close()
      }

      fun errorLog(context : Context, msg : String, e : Throwable)
      {
         // programmer errors (possibly due to Android bugs :)
         log(context, LogLevel.Error, msg + e.toString(), utils.errorLogFileBaseName)
         if (null != context)
            Toast.makeText(context, msg + e.toString(), Toast.LENGTH_LONG).show()
      }

      fun errorLog(context : Context, msg : String)
      {
         // programmer errors (possibly due to Android bugs :)
         log (context, LogLevel.Error, msg, errorLogFileBaseName)

         // This can crash due to lack of resources; happens when run on new device.
         // Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
      }

      fun alertLog(context : Context, msg : String)
      {
         // Messages containing info user needs time to read; requires explicit dismissal.
         //
         // Cannot be called from a service
         Log.i(logTag, msg)
         AlertDialog.Builder(context).setMessage(msg).setPositiveButton(R.string.Ok, null).show()
      }

      fun alertLog(context : Context, msg : String, e : Throwable)
      {
         // Messages containing info user needs time to read; requires explicit dismissal.
         //
         // Cannot be called from a service
         Log.e(logTag, msg)
         AlertDialog.Builder(context).setMessage(msg + e.toString()).setPositiveButton(R.string.Ok, null).show()
      }
   }
}
