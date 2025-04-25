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

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.media.MediaScannerConnection
import android.os.IBinder
import androidx.preference.PreferenceManager

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
      val smmFileName : String = utils.smmDirectory + "/" + category + ".last"

      var inFile    : BufferedReader = BufferedReader (FileReader (playlistFile))
      var line      : String?        = inFile.readLine()
      var songCount : Int            = 0
      var startAt   : Int            = 0

      var currentFile : String = ""

      if (File(smmFileName).exists()) try
         {
            var reader : BufferedReader = BufferedReader(FileReader(smmFileName))

            currentFile = reader.readLine()
            reader.close()
         }
      catch (ignored : IOException) {}

      while (line != null)
         {
            if (File(utils.smmDirectory, line).canRead())
               {
                  if (line.equals(currentFile))
                     startAt = songCount
                  songCount++
               }
            line = inFile.readLine()
         }

      inFile.close()

      return songCount - startAt - 1
   }

   private fun updatePlaylist (context         : Context,
                               playlistAbsName : String,
                               notif           : DownloadNotif)
   {
      var res                : Resources           = getResources()
      var prefs              : SharedPreferences   = PreferenceManager.getDefaultSharedPreferences(this)
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

      var serverIP        : String?     = prefs.getString (res.getString(R.string.server_IP_key), null)
      var playlistFile    : File        = File(playlistAbsName)
      var playlistDirFile : File        = File(FilenameUtils.getPath(playlistFile.getPath()))
      var category        : String      = FilenameUtils.getBaseName(playlistAbsName)
      var status          : StatusCount = StatusCount()

      if (serverIP == null || serverIP.equals(""))
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
                     DownloadUtils.cleanPlaylist(
                        context, category, playlistDirFile.getAbsolutePath(), utils.smmDirectory)

                     if (utils.playlistAbsPath().equals(playlistAbsName))
                        {
                           // Restart playlist to show song position, count
                           sendBroadcast(
                              Intent (utils.ACTION_PLAY_COMMAND)
                                 .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAYLIST)
                                 .putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistAbsName)
                                 .putExtra(utils.EXTRA_COMMAND_STATE, PlayState.Paused.toInt()))
                        }

                     status.status = DownloadUtils.sendNotes(context, serverIP, category, utils.smmDirectory)
                     if (status.status != ProcessStatus.Success)
                        return
                  }
               else
                  {
                     File(playlistDirFile, category).mkdir()
                     playlistFile.createNewFile()
                  }

               // This edits the playlist
               newSongs = DownloadUtils.getNewSongsList(
                  context, serverIP, category, songCount, newSongCount, overSelectRatio, -1)

               if (newSongs.status != ProcessStatus.Success)
                  {
                     notif.Error("get song list from server failed")
                     return
                  }

               if (utils.playlistAbsPath().equals(playlistAbsName))
                  {
                     // Restart playlist to show song position, count
                     sendBroadcast(
                        Intent (utils.ACTION_PLAY_COMMAND)
                           .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAYLIST)
                           .putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistAbsName)
                           .putExtra(utils.EXTRA_COMMAND_STATE, PlayState.Paused.toInt()))
                  }

               notif.Done("")
               DownloadUtils.log(context, LogLevel.Info, category + ": update done\n\n")

            }
         else
            {
               notif.Done("no update needed")
               DownloadUtils.log(context, LogLevel.Info, category + ": no update needed\n\n")
            }
      }
      catch (e : IOException)
      {
         // something is screwed up
         notif.Error("error: " + e.toString())
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

      val notif : DownloadNotif = DownloadNotif(
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

}
