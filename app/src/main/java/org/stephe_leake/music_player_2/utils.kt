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
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.coroutines.flow.firstOrNull

private object PlaylistCountsPreferenceKeys
{
   // NAME is shared with mainViewModel, but only mainViewModel writes to it.
   val NAME  = stringPreferencesKey("name")

   fun index(name : String) : Preferences.Key<Int> {return intPreferencesKey("$name-index")}
   fun pos(name : String) : Preferences.Key<Long> {return longPreferencesKey("$name-pos")}
}
      
data class PlaylistCounts(
   val index: Int = 0, // Current song in playlist, 0 indexed (same as mediaController)
   val pos: Long = 0L  // Current position in song (milliseconds)
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
      const val COMMAND_CANCEL_DOWNLOAD  : String = "org.stephe_leake.stephes_music.cancel_download"

      const val showDownloadLogIntentId : Int = 6
      const val cancelDownloadIntentId  : Int = 8

      const val logTag : String =
         // Must be shorter than 23 chars
      //  1        10        20 |
      "stephes_music"

      fun showDownloadLogIntent(context : Context): Intent
      {
         return Intent(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setDataAndType(
               FileProvider.getUriForFile(
                  context,
                  context.applicationContext.packageName + ".provider",
                  File(DownloadUtils.downloadLogFileName())),
               "text/plain")
      }
      
      var appDirectory : String = ""
      // Absolute path to application-specific directory, containing
      // nothing at the moment.
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

      suspend fun readPlaylistCounts(context: Context, category : String) : PlaylistCounts
      {
         var result = PlaylistCounts(index = 0, pos = 0)
         
         val preferences = context.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            {
               // Never set
            }
         else
            {
               val temp : Int? = preferences[PlaylistCountsPreferenceKeys.index(category)]
               if (temp == null)
                  {
                     // Never set
                  }
               else
                  {
                      result = PlaylistCounts(
                        // pos should be set here, but Gemini insists on being "safe"
                        index = temp,
                        pos = preferences[PlaylistCountsPreferenceKeys.pos(category)] ?: 0)
                  }
            }
         Log.d(logTag, "readPlaylistCounts '$category' $result")
         return result
      }
      
      suspend fun savePlaylistCounts(context: Context, category : String, index : Int, pos : Long)
      // Does not save 'category'. If pos = -1, don't save that.
      {
         Log.d(logTag, "savePlaylistCounts '$category' $index $pos")
         context.playlistPrefsState.edit {
            preferences ->
               preferences[PlaylistCountsPreferenceKeys.index(category)] = index
            if (pos != -1L)
               preferences[PlaylistCountsPreferenceKeys.pos(category)] = pos}
      }

      // This is used by DownloadService and ViewModel to get the
      // current playlist; we can't share the viewModel between
      // DownloadService and MainActivity.
      suspend fun readPlaylistName(context: Context) : String
      {
         val preferences = context.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            {
               Log.d(logTag, "playlist state prefs null")
               return ""
            }
         else
            {
               val temp : String? = preferences[PlaylistCountsPreferenceKeys.NAME]
               if (temp == null)
                  {
                     // Never set
                     Log.d(logTag, "playlist state prefs never set")
                     return ""
                  }
               else
                  {
                     Log.d(logTag, "playlist name read '$temp'")
                     return temp
                  }
            }
      }
   
      fun notesFileName(category : String) : String
      {
         return "$globalDirectory/$category.note"
      }

      fun findTextViewById (a: AppCompatActivity, id: Int) : TextView
      {
         val v : View? = a.findViewById(id)
         
         if (v == null) {throw RuntimeException("no such id $id")}
            
         if (v is TextView)
            {
               return v
            }
         else
            {
               throw RuntimeException("$id is not a TextView; it is a $v")
            }
      }

      fun logFileName(logFileBaseName : String) : String
      {
         // In global so user can read it (file provider doesn't work)
         return "$globalDirectory/$logFileBaseName$logFileExt"
      }

      fun errorLogFileName() : String
      {
         return logFileName(errorLogFileBaseName)
      }
      
      fun log(msg : String, logFileBaseName : String)
      // errors go in utils.errorLogFileBaseName, download messages in
      // DownloadUtils.downloadLogFileBaseName
      {
         Log.d(logTag, msg)

         val fmt       = SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US)
         val time      : Long     = System.currentTimeMillis() // local time zone
         val timeStamp : String   = fmt.format(time)
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
         writer.println(timeStamp + " " + msg)
         writer.close()
      }

      fun errorLog(context : Context?, msg : String, e : Throwable)
      {
         // programmer errors (possibly due to Android bugs :)
         log(msg + e.toString(), errorLogFileBaseName)
         if (null != context)
            Toast.makeText(context, msg + e.toString(), Toast.LENGTH_LONG).show()
      }

      fun errorLog(msg : String)
      {
         // programmer errors (possibly due to Android bugs :)
         log(msg, errorLogFileBaseName)

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
