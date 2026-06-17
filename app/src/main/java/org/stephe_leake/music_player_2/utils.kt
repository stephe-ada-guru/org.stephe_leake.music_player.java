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
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.coroutines.flow.firstOrNull

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

      const val playlistNoLimit : Int = Int.MAX_VALUE
      const val limitDontSave   : Int = -1
      const val posDontSave     : Long = -1L

      const val notificationChannelId : String = "Stephe's Music notifications"
      
      //  Notification ids; all with null tag
      const val notif_download_id : Int = 1
      const val notif_sync_id     : Int = 2

      const val EXTRA_PLAYLIST_CATEGORY  : String = "PLAYLIST_CATEGORY"
      const val EXTRA_PLAYLIST_LIMIT     : String = "PLAYLIST_LIMIT"
      const val DOWNLOAD_COMMAND         : String = "download_command" // Update existing or create new playlist
      const val COMMAND_CANCEL_DOWNLOAD  : String = "org.stephe_leake.stephes_music.cancel_download"
      const val SYNC_DB_COMMAND          : String = "org.stephe_leake.stephes_music.sync_db"
      const val INIT_DB_COMMAND          : String = "org.stephe_leake.stephes_music.init_db"
      const val RESUME_INIT_DB_COMMAND   : String = "org.stephe_leake.stephes_music.resume_init_db"
      const val COMMAND_CANCEL_SYNC_DB   : String = "org.stephe_leake.stephes_music.cancel_sync_db"
      const val STOP_SERVICE_COMMAND     : String = "org.stephe_leake.stephes_music.stop_service"

      const val showDownloadLogIntentId      : Int = 6
      const val cancelDownloadIntentId       : Int = 8
      const val showSyncLogIntentId          : Int = 9
      const val cancelSyncIntentId           : Int = 10
      const val showSyncProgressIntentId     : Int = 11
      const val showDownloadProgressIntentId : Int = 12

      const val logTag : String =
         // Must be shorter than 23 chars
      //  1        10        20 |
      "stephes_music"

      fun showLogIntent(context : Context, filename: String): Intent
      {
         return Intent(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setDataAndType(
               FileProvider.getUriForFile(
                  context,
                  context.applicationContext.packageName + ".provider",
                  File(filename)),
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

      const val debugLogFileBaseName: String = "debug_log"
      const val errorLogFileBaseName: String = "error_log"

      fun playlistFileName(category: String) : String 
      // return 'category' playlist file absolute path
      {
         // In global so user can look at it to see what's been played recently
         return "$globalDirectory/$category.m3u"
      }

      fun songFileName(relFileName: String) : String 
      // return absolute file name; relFileName must be relative to utils.globalDirectory.
      {
         return "$globalDirectory/$relFileName"
      }

      fun viewPDF(context: Context, absFileName : String)
      {
         val file = File(absFileName)
         val contentUri: Uri? =  FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.provider",
            file)

         val intent: Intent = Intent(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setDataAndType(contentUri, "application/pdf")
               
         context.startActivity(intent)
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
         debugLog("readPlaylistCounts '$category' $result")
         return result
      }

      suspend fun readPlaylistLimit(context: Context, category : String) : Int
      // Returns limitDontSave if never set
      {
         var result = limitDontSave
         
         val preferences = context.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            {
               // Never set
            }
         else
            {
               val temp : Int? = preferences[PlaylistCountsPreferenceKeys.limit(category)]
               if (temp == null)
                  {
                     // Never set
                  }
               else
                  {
                      result = preferences[PlaylistCountsPreferenceKeys.limit(category)] ?: limitDontSave
                  }
            }
         return result
      }
   
      suspend fun savePlaylistCounts(context: Context, category : String, index : Int, pos : Long, limit : Int)
      // Does not save 'category'. If pos or limit =
      // *DontSave, don't save those.
      {
         context.playlistPrefsState.edit {
            preferences ->
               preferences[PlaylistCountsPreferenceKeys.index(category)] = index
            if (pos != posDontSave)
               preferences[PlaylistCountsPreferenceKeys.pos(category)] = pos
            if (limit != limitDontSave)
               preferences[PlaylistCountsPreferenceKeys.limit(category)] = limit}
      }

      // This is used by DownloadService and ViewModel to get the
      // current playlist; we can't share the viewModel between
      // DownloadService and MainActivity.
      suspend fun readPlaylistName(context: Context) : String
      {
         val preferences = context.playlistPrefsState.data.firstOrNull()
         if (preferences == null)
            {
               utils.debugLog("readPlaylistName: playlist state prefs null")
               return ""
            }
         else
            {
               val temp : String? = preferences[PlaylistCountsPreferenceKeys.NAME]
               if (temp == null)
                  {
                     // Never set
                     utils.debugLog("readPlaylistName: playlist state prefs never set")
                     return ""
                  }
               else
                  {
                     utils.debugLog("readPlaylistName: playlist name read '$temp'")
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

      fun debugLogFileName() : String
      {
         return logFileName(debugLogFileBaseName)
      }
      
      fun errorLogFileName() : String
      {
         return logFileName(errorLogFileBaseName)
      }
      
      fun log(msg : String, logFileBaseName : String)
      {
         Log.d(logTag, msg)

         val fmt       = SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US)
         val time      : Long     = System.currentTimeMillis() // local time zone
         val timeStamp : String   = fmt.format(time)
         val logFile   = File(logFileName(logFileBaseName))
         
         if (logFile.exists() && time - logFile.lastModified() > 4 * millisPerHour)
            {
               val oldLogFileName : String = globalDirectory + "/" + logFileBaseName + "_1" + logFileExt
               val oldLogFile     = File(oldLogFileName)
               
               if (oldLogFile.exists())
                  {oldLogFile.delete()}
               
               logFile.renameTo(oldLogFile)
            }
         
         val writer = PrintWriter(FileWriter(logFileName(logFileBaseName), true)) // append
         writer.println(timeStamp + " " + msg)
         writer.flush()
         writer.close()
      }

      fun debugLog(msg: String)
      {
         // debugging stuff
         log(msg, debugLogFileBaseName)
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
      }

      fun alertLog(context : Context, msg : String)
      {
         // Messages containing info user needs time to read; requires explicit dismissal.
         //
         // Cannot be called from a service
         AlertDialog.Builder(context).setMessage(msg).setPositiveButton(R.string.Ok, null).show()
      }

   }
}
