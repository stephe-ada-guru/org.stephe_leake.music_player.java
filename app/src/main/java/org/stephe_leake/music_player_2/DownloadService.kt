//  Abstract :
//
//  Provides User Interface to Stephe's Music Player.
//
//  Copyright (C) 2011 - 2013, 2015 - 2019, 2021, 2024 Stephen Leake.  All Rights Reserved.
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

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.IBinder
import androidx.annotation.RequiresPermission
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

import org.apache.commons.io.FilenameUtils

class DownloadService : Service()
{
   private val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()
   private lateinit var notif           : DownloadNotif
   
   ////////// private methods (alphabetical order)

   fun countSongsRemaining(category : String, playlistFile : File) : Int 
   {
      // Duplicate the part of restoreState that gets playlistPos
      val lastFileName : String = utils.lastFileName(category)

      var inFile    : BufferedReader = BufferedReader (FileReader (playlistFile))
      var line      : String?        = inFile.readLine()
      var songCount : Int            = 0
      var startAt   : Int            = 0

      var currentFile : String = ""

      if (File(lastFileName).exists()) try
         {
            var reader : BufferedReader = BufferedReader(FileReader(lastFileName))

            currentFile = reader.readLine()
            reader.close()
         }
      catch (ignored : IOException) {}

      while (line != null)
         {
            if (File(utils.globalDirectory, line).canRead())
               {
                  if (line == currentFile)
                     startAt = songCount
                  songCount++
               }
            line = inFile.readLine()
         }

      inFile.close()

      return songCount - startAt - 1
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   private fun updatePlaylist (playlistFileName : String)
   {
      var res                : Resources           = getResources()
      var prefs              : SharedPreferences   = utils.mainActivity!!.getPreferences(Context.MODE_PRIVATE)
      var songCountMaxStr    : String?             =
         prefs.getString(res.getString(R.string.song_count_max_key),
                         res.getString(R.string.song_count_max_default))
      var newSongFractionStr : String?             =
         prefs.getString (res.getString(R.string.new_song_fraction_key),
                          res.getString(R.string.new_song_fraction_default))
      var overSelectRatioStr : String?             =
         prefs.getString (res.getString(R.string.over_select_ratio_key),
                          res.getString(R.string.over_select_ratio_default))
      var songCountThreshStr : String?             =
         prefs.getString (res.getString(R.string.song_count_threshold_key),
                          res.getString(R.string.song_count_threshold_default))

      var serverIP        : String      = prefs.getString (res.getString(R.string.server_IP_key),
                                                           res.getString(R.string.server_IP_default))!!
      var playlistFile    : File        = File(playlistFileName)
      var playlistDirFile : File        = File(FilenameUtils.getPath(playlistFile.getPath()))
      var category        : String      = FilenameUtils.getBaseName(playlistFileName)
      var status          : StatusCount = StatusCount()

      if (serverIP == "")
         {
            notif.Error("Server IP preference not set")
            return
         }

      // File.exists throws IOException ENOENT if the directory does not exist!
      playlistDirFile.mkdirs()

      try
      {
         val songsRemaining  : Int   = if (playlistFile.exists()) {countSongsRemaining(category, playlistFile)} else 0
         val songCountMax    : Int   = Integer.parseInt(songCountMaxStr!!)
         val songCountThresh : Int   = Integer.parseInt(songCountThreshStr!!)
         val overSelectRatio : Float = overSelectRatioStr!!.toFloat()

         if (songsRemaining < songCountMax - songCountThresh)
            {
               var newSongs          : StatusStrings 
               var songCount         : Int   = songCountMax - songsRemaining
               var newSongCountFloat : Float = songCount * newSongFractionStr!!.toFloat()
               var newSongCount      : Int   = newSongCountFloat.toInt()

               if (playlistFile.exists())
                  {
                     DownloadUtils.cleanPlaylist(category)

                     if (utils.playlistFileName(category) == playlistFileName)
                        {
                           // Restart playlist to show song position, count
                           sendBroadcast(
                              Intent (utils.ACTION_PLAY_COMMAND)
                                 .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAYLIST)
                                 .putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistFileName)
                                 .putExtra(utils.EXTRA_COMMAND_STATE, PlayState.Paused.toInt()))
                        }

                     status.status = DownloadUtils.sendNotes(serverIP, category)
                     if (status.status != ProcessStatus.Success)
                        return
                  }
               else
                  {
                     File(playlistDirFile, category).mkdir()
                     playlistFile.createNewFile()
                  }

               newSongs = DownloadUtils.getNewSongsList(
                  serverIP, category, songCount, newSongCount, overSelectRatio, -1)

               if (newSongs.status != ProcessStatus.Success)
                  {
                     notif.Error("get song list from server failed")
                     return
                  }

               notif.Update(newSongs.strings.size, newSongCount)

               // Add all songs to playlist, get any missing songs
               // (should all be on phone already, but this handles
               // new music).
               status = DownloadUtils.getSongs(newSongs.strings, category)

               if (status.status != ProcessStatus.Success)
                  {
                     return
                  }

               if (utils.playlistFileName(utils.playlistBaseName) == playlistFileName)
                  {
                     // Restart playlist to show song position, count
                     sendBroadcast(
                        Intent (utils.ACTION_PLAY_COMMAND)
                           .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAYLIST)
                           .putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistFileName)
                           .putExtra(utils.EXTRA_COMMAND_STATE, PlayState.Paused.toInt()))
                  }

               notif.Done("")
               DownloadUtils.log(LogLevel.Info, category + ": update done\n\n")

            }
         else
            {
               notif.Done("no update needed")
               DownloadUtils.log(LogLevel.Info, category + ": no update needed\n\n")
            }
      }
      catch (e : IOException)
      {
         // something is screwed up
         notif.Error("error: " + e.toString())
      }
   }

   internal inner class DownloadRun(private val notif   : DownloadNotif,
                                    private val playlist: String)
      : Runnable
   {
      @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
      override fun run()
      {
         notif.setName(FilenameUtils.getBaseName(playlist))
         updatePlaylist(playlist)
      }
   }

   ////////// service lifetime methods
   override fun onBind(intent: Intent): IBinder?
   {
      return null
   }

   override fun onCreate()
   {
      super.onCreate()

      val filter : IntentFilter = IntentFilter()
      filter.addAction(utils.ACTION_DOWNLOAD_COMMAND)
      registerReceiver(broadcastReceiverCommand, filter, RECEIVER_NOT_EXPORTED)

      notif = DownloadNotif(
         context = this,
         showLogPendingIntentInit = PendingIntent.getActivity
         (this.getApplicationContext(),
          utils.showDownloadLogIntentId,
          utils.showDownloadLogIntent,
          PendingIntent.FLAG_IMMUTABLE),

         cancelIntent = PendingIntent.getBroadcast
         (this.getApplicationContext(),
          utils.cancelDownloadIntentId,
          utils.cancelDownloadIntent,
          PendingIntent.FLAG_IMMUTABLE))

      startForeground (utils.notif_download_id, notif.getNotif(),
                       android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
   }

   override fun onDestroy()
   {
      notif.Cancel()
      unregisterReceiver(broadcastReceiverCommand)
      super.onDestroy()
   }
   
   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
   {
      if (intent == null)
         {
            // intent is null if the service is restarted by Android
            // after a crash.
            return START_NOT_STICKY
         }
      else if (intent.getAction() == utils.ACTION_DOWNLOAD_COMMAND)
         {
            try {
               // FIXME: Use DataStore<Preferences>
               // val res   : Resources = getResources()
               // val prefs : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

               // DownloadUtils.prefLogLevel = LogLevel.valueOf(
               //    prefs.getString(res.getString(R.string.log_level_key),
               //                    LogLevel.Info.toString())!!)

               val runner : DownloadRun = DownloadRun(notif, utils.playlistFileName(utils.playlistBaseName))
               Thread(runner).start()

               return START_NOT_STICKY
            }
            catch (e: Exception)
            {
               utils.errorLog(this, "DownloadService::onStartCommand: ", e)
               return START_NOT_STICKY
            }
         }
      else
         {
            utils.errorLog("DownloadService::onStartCommand got bad intent: $intent")
            return START_NOT_STICKY
         }
   }
}
