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
import android.content.pm.PackageManager
import android.Manifest.permission
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.coroutines.flow.firstOrNull

private const val PLAYLIST_PREFERENCES_NAME = "playlist_prefs"
val Context.playlistState : DataStore<Preferences> by preferencesDataStore(name = PLAYLIST_PREFERENCES_NAME)

class utils
{
   companion object
   {
      val millisPerMinute : Long = 60 * 1000
      val millisPerHour   : Long = 60 * millisPerMinute
      val millisPerDay    : Long = 24 * millisPerHour

      val notificationChannelId : String = "Stephe's Music notifications"
      
      //  Notification ids; all with null tag
      val notif_play_id     : Int = 1
      val notif_download_id : Int = 2

      val EXTRA_COMMAND            : String = "org.stephe_leake.stephes_music.extra.command"
      val DOWNLOAD_COMMAND         : String = "download_command"
      val RESTART_PLAYLIST_COMMAND : String = "restart_playlist_command"

      // download service commands
      val COMMAND_CANCEL_DOWNLOAD : Int = 2
      val COMMAND_DOWNLOAD        : Int = 3 // Update existing or create new playlist

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

      var appDirectory : String = ""
      // Absolute path to application-specific directory, containing
      // files used to interface with Stephe's Music manager (smm);
      // playlist files, .last files, notes files, error log.
      //
      // Set by Activity to getExternalStorageDir().

      val globalDirectory : String = "/storage/emulated/0/Music/Music"
      // Globally accessible directory where music and playlist files
      // are stored.
      // 
      // FIXME: Preferences don't work, so this needs a valid default

      var playlistBaseName : String = ""
      // Current playlist file name; relative to globalDirectory,
      // without extension (suitable for user display). Empty if no
      // playlist is current.

      val logFileExt : String = ".txt"

      val errorLogFileBaseName : String = "error_log"

      // public non-member functions

      fun playlistFileName(category : String) : String 
      // return current playlist file app-relative path
      {
         return category + ".m3u"
      }

      var tempPlaylistName : String = ""
      var playlistCount : Int = -1 // count of songs in playlist
      var playlistIndex : Int = -1 // playlist is 1 indexed.
      var playlistPos   : Long = -1
   
      object PlaylistPreferenceKeys
      {
         val NAME  = stringPreferencesKey("name")
         
         fun count(Name : String) : Preferences.Key<Int> {return intPreferencesKey(Name + "-count")}
         fun index(Name : String) : Preferences.Key<Int> {return intPreferencesKey(Name + "-index")}
         fun pos(Name : String) : Preferences.Key<Long> {return longPreferencesKey(Name + "-pos")}
      }
      
      suspend fun clearSavedState()
      {
         mainActivity!!.playlistState.edit {
            preferences ->
               preferences[PlaylistPreferenceKeys.NAME] = ""
            preferences[PlaylistPreferenceKeys.count(utils.playlistBaseName)] = 0
            preferences[PlaylistPreferenceKeys.index(utils.playlistBaseName)] = 0
            preferences[PlaylistPreferenceKeys.pos(utils.playlistBaseName)] = 0
         }
      } // clearSavedState

      suspend fun writeState(count : Int, index : Int, pos : Long)
      {
         mainActivity!!.playlistState.edit {
            preferences ->
               preferences[PlaylistPreferenceKeys.NAME] = utils.playlistBaseName
            preferences[PlaylistPreferenceKeys.count(utils.playlistBaseName)] = count
            preferences[PlaylistPreferenceKeys.index(utils.playlistBaseName)] = index
            preferences[PlaylistPreferenceKeys.pos(utils.playlistBaseName)] = pos
         }
      }// writeState

      suspend fun readPlaylistIndexPos(playlist : String)
      // Result in tempPlaylistName
      {
         val preferences = mainActivity!!.playlistState.data.firstOrNull()
         if (preferences == null)
            {
               // Never set
               playlistIndex = 1
               playlistPos = 1
            }
         else
            {
               var temp : Int? = preferences.get(PlaylistPreferenceKeys.index(playlist))
               if (temp == null)
                  {
                     // Never set
                     playlistIndex = 1
                     playlistPos = 1
                  }
               else
                  {
                     playlistIndex = temp
                     playlistPos = preferences.get(PlaylistPreferenceKeys.pos(playlist))!!
                  }
            }
      }
      
      suspend fun readPlaylistName()
      // result in utils.playlistBaseName
      {
         val preferences = mainActivity!!.playlistState.data.firstOrNull()
         if (preferences == null)
            tempPlaylistName = ""
         else
            {
               var temp : String? = preferences.get(PlaylistPreferenceKeys.NAME)
               if (temp == null)
                  {
                     // Never set
                     tempPlaylistName = ""
                  }
               else
                  {
                     tempPlaylistName = temp
                  }
            }
      }
   
      // FIXME: using this?
      fun notesFileName(category : String) : String
      {
         return utils.appDirectory + "/" + category + ".note"
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
         return appDirectory + "/" + errorLogFileBaseName + logFileExt
      }

      fun log(level : LogLevel, msg : String, logFileBaseName : String)
      {
         // The error log file is in app local storage, so we don't need file permissions.
         
         val fmt       : SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US)
         val time      : Long     = System.currentTimeMillis() // local time zone
         val timeStamp : String   = fmt.format(time)
         val levelImg  : String   = logImage (level)
         val logFile   : File     = File(errorLogFileName())
         val writer : PrintWriter = PrintWriter(FileWriter(errorLogFileName(), true)) // append
         
         if (logFile.exists() && time - logFile.lastModified() > 4 * utils.millisPerHour)
            {
               val oldLogFileName : String = logFileBaseName + "_1" + logFileExt
               val oldLogFile     : File   = File(oldLogFileName)
               
               if (oldLogFile.exists())
                  {oldLogFile.delete()}
               
               logFile.renameTo(oldLogFile)
            }
         writer.println(timeStamp + levelImg + msg)
         writer.close()
      }

      fun errorLog(context : Context?, msg : String, e : Throwable)
      {
         // programmer errors (possibly due to Android bugs :)
         log(LogLevel.Error, msg + e.toString(), utils.errorLogFileBaseName)
         if (null != context)
            Toast.makeText(context, msg + e.toString(), Toast.LENGTH_LONG).show()
      }

      fun errorLog(msg : String)
      {
         // programmer errors (possibly due to Android bugs :)
         log(LogLevel.Error, msg, errorLogFileBaseName)

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
