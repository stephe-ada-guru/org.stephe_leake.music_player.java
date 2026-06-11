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
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.concurrent.futures.await
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceManager

import java.io.File
import java.io.IOException

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.apache.commons.io.FilenameUtils

class DownloadService : Service()
{
   private val serviceScope = CoroutineScope(Dispatchers.IO)

   private val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()

   ////////// private methods (alphabetical order)

   suspend fun countSongsRemaining(category : String) : Int
   // Number of unplayed songs in 'category' playlist file
   {
      val counts = utils.readPlaylistCounts(this, category)
      var total = 0

      val playlistFile = File(utils.playlistFileName(category))

      playlistFile.forEachLine{total += 1}

      return total - counts.index
   }

   private suspend fun getCurrentPosition(): Long
   {
      val sessionToken = SessionToken(this, ComponentName(this, PlayService::class.java))
      val browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()

      return try {
         val browser = browserFuture.await()
         val pos = browser.currentPosition
         browser.release()
         pos
      } catch (e: Exception)
      {
         utils.errorLog("DownloadService: Failed to get position: $e")
         utils.posDontSave
      }
   }

   private suspend fun updatePlaylist(category : String, limit : Int)
   {
      Log.d(utils.logTag, "updatePlaylist '$category'")
      val res                = resources
      val prefs              : SharedPreferences   = PreferenceManager.getDefaultSharedPreferences(this)
      val songCountMaxStr    : String?             =
         prefs.getString(res.getString(R.string.song_count_max_key),
                         res.getString(R.string.song_count_max_default))
      val newSongFractionStr : String?             =
         prefs.getString (res.getString(R.string.new_song_fraction_key),
                          res.getString(R.string.new_song_fraction_default))
      val overSelectRatioStr : String?             =
         prefs.getString (res.getString(R.string.over_select_ratio_key),
                          res.getString(R.string.over_select_ratio_default))
      val songCountThreshStr : String?             =
         prefs.getString (res.getString(R.string.song_count_threshold_key),
                          res.getString(R.string.song_count_threshold_default))

      val serverIP        = prefs.getString (res.getString(R.string.server_IP_key), null)
      val playlistFile    = File(utils.playlistFileName(category))
      val playlistDirFile = File(FilenameUtils.getPath(playlistFile.path))

      if (serverIP == null || serverIP == "")
         {
            DownloadStatusBus.emit(DownloadStatus.Error("Server IP preference not set"))
            return
         }

      // File.exists throws IOException ENOENT if the directory does not exist!
      playlistDirFile.mkdirs()

      try
      {
         val songsRemaining  : Int   = if (playlistFile.exists()) {countSongsRemaining(category)} else 0
         val savedLimit      : Int   = utils.readPlaylistLimit(this, category)
         val songCountMax    : Int   = if (limit == utils.limitDontSave)
         {if (savedLimit == utils.limitDontSave)
          {Integer.parseInt(songCountMaxStr!!)}
          else savedLimit}
         else limit
         val songCountThresh : Int   = Integer.parseInt(songCountThreshStr!!)
         val overSelectRatio : Float = overSelectRatioStr!!.toFloat()

         if (songsRemaining < songCountMax - songCountThresh)
            {
               var newSongs          : StatusStrings
               val songCount         : Int   = songCountMax - songsRemaining
               val newSongCountFloat : Float = songCount * newSongFractionStr!!.toFloat()
               val newSongCount      : Int   = newSongCountFloat.toInt()

               if (playlistFile.exists())
                  {
                     DownloadUtils.cleanPlaylist(this, category)

                     DownloadUtils.sendNotes(serverIP, category)
                     // sendNotes already reported any error; don't
                     // need to abort update for this.
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
                     DownloadStatusBus.emit(DownloadStatus.Error("get song list from server failed"))
                     return
                  }

               DownloadStatusBus.emit(DownloadStatus.Progress("${newSongs.strings.size} songs"))

               // Add all songs to playlist, log any missing songs
               // (should all be on phone already, but this handles
               // new music).
               val status = DownloadUtils.getSongs(newSongs.strings, category)

               if (utils.readPlaylistName(this) == category)
                  {
                     // Restart playlist to show song position, count
                     utils.savePlaylistCounts(
                        this, category, index = 0, pos = getCurrentPosition(), limit = songCountMax)
                     Log.d(utils.logTag, "DownloadService.updatePlaylist send ReloadPlaylist")
                     serviceScope.launch {AppEventBus.emitEvent(AppEvent.ReloadPlaylist)}
                  }
               else
                  utils.savePlaylistCounts(this, category, index = 0, pos = utils.posDontSave, limit = songCountMax)

               if (status.status != ProcessStatus.Success)
                  {
                     DownloadStatusBus.emit(DownloadStatus.Error("check local/get songs from server failed"))
                     return
                  }

               // Count of songs not found locally
               val missing = newSongs.strings.size - status.count
               val msg = if (missing>0) "$missing songs not found locally" else ""

               DownloadStatusBus.emit(DownloadStatus.Done(msg))
               DownloadUtils.log("$category : update done\n$msg\n")
            }
         else
            {
               DownloadStatusBus.emit(DownloadStatus.Done("no update needed"))
               DownloadUtils.log("$category : no update needed\n\n")
            }
      }
      catch (e : IOException)
      {
         DownloadStatusBus.emit(DownloadStatus.Error("error: ${e.toString()}"))
      }
   }

   ////////// service lifetime methods; parent Service is abstract
   override fun onBind(intent: Intent): IBinder?
   {
      return null
   }

   private fun buildForegroundNotif(): Notification
   {
      val tapIntent = Intent(this, DownloadProgressActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      val tapPI = PendingIntent.getActivity(
         this, utils.showDownloadProgressIntentId, tapIntent, PendingIntent.FLAG_IMMUTABLE)
      return NotificationCompat.Builder(this, utils.notificationChannelId)
         .setContentTitle("Updating playlist")
         .setContentText("Tap to view progress")
         .setSmallIcon(R.mipmap.download_icon)
         .setOngoing(true)
         .setContentIntent(tapPI)
         .build()
   }

   override fun onCreate()
   {
      super.onCreate()

      val filter = IntentFilter()
      filter.addAction(utils.DOWNLOAD_COMMAND)
      registerReceiver(broadcastReceiverCommand, filter, RECEIVER_NOT_EXPORTED)

      startForeground(utils.notif_download_id, buildForegroundNotif(),
                      android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
   }

   override fun onDestroy()
   {
      Log.d(utils.logTag, "DownloadService destroyed")
      serviceScope.cancel()
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      unregisterReceiver(broadcastReceiverCommand)
      super.onDestroy()
   }

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
   {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
             android.content.pm.PackageManager.PERMISSION_GRANTED)
      {
         // The media controller also requires POST_NOTIFICATIONS, so
         // there's no point in continuing here; the user _must_ grant
         // this permission to use this app.
         return START_NOT_STICKY
      }

      if (intent == null)
         {
            // intent is null if the service is restarted by Android
            // after a crash.
            return START_NOT_STICKY
         }
      else if (intent.action == utils.DOWNLOAD_COMMAND)
         {
            try {
                  val category = intent.getStringExtra(utils.EXTRA_PLAYLIST_CATEGORY)!!
                  val limit = intent.getIntExtra(utils.EXTRA_PLAYLIST_LIMIT, utils.limitDontSave)

                  serviceScope.launch {
                     DownloadStatusBus.emit(DownloadStatus.Idle)
                     updatePlaylist(category, limit)
                     stopSelf()
                  }

                  return START_NOT_STICKY
            }
            catch (e: Exception)
            {
               utils.errorLog(this, "DownloadService::onStartCommand: ", e)
               return START_NOT_STICKY
            }
         }
      else if (intent.action == utils.STOP_SERVICE_COMMAND)
         {
            stopSelf()
            return START_NOT_STICKY
         }
      else
         {
            utils.errorLog("DownloadService::onStartCommand got bad intent: $intent")
            return START_NOT_STICKY
         }
   }
}
