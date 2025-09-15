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

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

// Playlist preferences are actually state data, so we use DataStore.
// Other preferences (given in preferences.xml) are stored in
// DefaultSharedPreferences (since that's what the UI edits).
private const val PLAYLIST_PREFERENCES_NAME = "playlist_prefs"
val Context.playlistPrefsState : DataStore<Preferences> by preferencesDataStore(name = PLAYLIST_PREFERENCES_NAME)

object PlaylistPreferenceKeys
{
   val NAME  = stringPreferencesKey("name")
   
   fun count(Name : String) : Preferences.Key<Int> {return intPreferencesKey(Name + "-count")}
   fun index(Name : String) : Preferences.Key<Int> {return intPreferencesKey(Name + "-index")}
   fun pos(Name : String) : Preferences.Key<Long> {return longPreferencesKey(Name + "-pos")}
}
      
data class PlaylistCounts(
   val count: Int = 0, // Count of songs in playlist; 0 if unknown
   val index: Int = 0, // Current song in playlist (1-indexed, 0 if none)
   val pos: Long = 0L  // Current position in song (milliseconds, 0 if none)
)

class utils
{
   companion object
   {
      const val millisPerMinute : Long = 60 * 1000
      const val millisPerHour   : Long = 60 * millisPerMinute

      const val notificationChannelId : String = "Stephe's Music notifications"
      
      //  Notification ids; all with null tag
      const val notif_download_id : Int = 1

      const val EXTRA_PLAYLIST_CATEGORY  : String = "PLAYLIST_CATEGORY"
      const val DOWNLOAD_COMMAND         : String = "download_command" // Update existing or create new playlist
      const val RESTART_PLAYLIST_COMMAND : String = "restart_playlist_command"
      const val COMMAND_CANCEL_DOWNLOAD  : String = "org.stephe_leake.stephes_music.cancel_download"

      // FIXME: not implemented
      // val RESULT_TEXT_SCALE : Int         = Activity.RESULT_FIRST_USER + 1

      const val showDownloadLogIntentId : Int = 6
      const val cancelDownloadIntentId  : Int = 8

      const val logTag : String =
         // Must be shorter than 23 chars
      //  1        10        20 |
      "stephes_music"

      var showDownloadLogIntent : Intent = Intent(Intent.ACTION_VIEW)
      var showErrorLogIntent    : Intent = Intent(Intent.ACTION_VIEW)

      // We need this because there is not always a way to get it
      // programatically.
      var mainActivity: AppCompatActivity? = null

      var appDirectory : String = ""
      // Absolute path to application-specific directory, containing
      // notes files.
      //
      // Set by Activity to getExternalStorageDir().

      const val globalDirectory : String = "/storage/emulated/0/Music/Music"
      // Globally accessible directory where music, playlist, log files
      // are stored.

      const val logFileExt : String = ".txt"

      const val errorLogFileBaseName : String = "error_log"

      fun playlistFileName(category : String) : String 
      // return 'category' playlist file absolute path
      {
         // In global so user can look at it to see what's been played recently
         return "$globalDirectory/$category.m3u"
      }

      suspend fun readPlaylistCounts(playlist : String) : PlaylistCounts
      {
         val preferences = mainActivity!!.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            {
               // Never set
               return PlaylistCounts(
                  count = 0,
                  index = 0,
                  pos = 0)
            }
         else
            {
               var temp : Int? = preferences[PlaylistPreferenceKeys.index(playlist)]
               if (temp == null)
                  {
                     // Never set
                     return PlaylistCounts(
                        count = 0,
                        index = 0,
                        pos = 0)
                  }
               else
                  {
                     return PlaylistCounts(
                        count = preferences[PlaylistPreferenceKeys.count(playlist)]!!,
                        index = temp,
                        pos = preferences[PlaylistPreferenceKeys.pos(playlist)]!!)
                  }
            }
      }
      
      suspend fun savePlaylistCounts(category : String, count : Int, index : Int, pos : Long)
      {
         mainActivity!!.playlistPrefsState.edit {
            preferences ->
               preferences[PlaylistPreferenceKeys.NAME] = category
               preferences[PlaylistPreferenceKeys.count(category)] = count
               preferences[PlaylistPreferenceKeys.index(category)] = index
               preferences[PlaylistPreferenceKeys.pos(category)] = pos}
      }
      
      suspend fun readPlaylistName() : String
      {
         val preferences = mainActivity!!.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            return ""
         else
            {
               var temp : String? = preferences[PlaylistPreferenceKeys.NAME]
               if (temp == null)
                  {
                     // Never set
                     return ""
                  }
               else
                  {
                     return temp
                  }
            }
      }
   
      fun notesFileName(category : String) : String
      {
         return appDirectory + "/" + category + ".note"
      }

      fun findTextViewById (a: AppCompatActivity, id: Int) : TextView
      {
         val v : View? = a.findViewById(id)
         
         if (v == null) {throw RuntimeException("no such id " + id)}
            
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
      
      fun logFileName(logFileBaseName : String) : String
      {
         // In global so user can read it (file provider doesn't work)
         return globalDirectory + "/" + logFileBaseName + logFileExt
      }

      fun errorLogFileName() : String
      {
         return logFileName(errorLogFileBaseName)
      }
      
      fun log(level : LogLevel, msg : String, logFileBaseName : String)
      // errors go in utils.errorLogFileBaseName, download messages in
      // DownloadUtils.downloadLogFileBaseName
      {
         val fmt       = SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US)
         val time      : Long     = System.currentTimeMillis() // local time zone
         val timeStamp : String   = fmt.format(time)
         val levelImg  : String   = logImage (level)
         val logFile   = File(logFileName(logFileBaseName))
         val writer    = PrintWriter(FileWriter(logFileName(logFileBaseName), true)) // append
         
         if (logFile.exists() && time - logFile.lastModified() > 4 * millisPerHour)
            {
               val oldLogFileName : String = globalDirectory + "/" + logFileBaseName + "_1" + logFileExt
               val oldLogFile     = File(oldLogFileName)
               
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
         log(LogLevel.Error, msg + e.toString(), errorLogFileBaseName)
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

   }
}
