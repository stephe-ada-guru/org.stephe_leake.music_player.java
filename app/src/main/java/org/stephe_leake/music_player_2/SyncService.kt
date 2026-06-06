//  Abstract :
//
//  Synchronize phone and laptop databases
//
//  Copyright (C) 2026 Stephen Leake.  All Rights Reserved.
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
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteConstraintException
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.preferences.core.edit
import androidx.preference.PreferenceManager

import java.net.Socket

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

import org.json.JSONException
import org.json.JSONObject

class SyncService : Service()
{
   private val serviceScope = CoroutineScope(Dispatchers.IO)

   @Volatile
   private var syncDone = false

   // Note that this is a different instance from DownloadService, so
   // they have different intent filters.
   private val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()

   private suspend fun updateOne(dao: SongDao, value: JSONObject)
   {   
      // value format given by smm-database.adb Get_JSON
      if (value.has("Deleted"))
         {
            dao.updateSong(
               ID = value.getInt("ID"),
               Deleted = if (value.has("Deleted")) value.getString("Deleted") else null)
         }
      else
         {
            val dataf: JSONObject = value.getJSONObject("Data")
            dao.updateSong(
               ID = value.getInt("ID"),
               Modified        = if (value.has("Modified")) value.getString("Modified") else null,
               File_Name       = dataf.getString("File_Name"), 
               Category        = dataf.getString("Category"),
               Artist          = if (dataf.has("Artist")) dataf.getString("Artist") else null,
               Album_Artist    = dataf.getString("Album_Artist"),
               Composer        = if (dataf.has("Composer")) dataf.getString("Composer") else null,
               Album           = if (dataf.has("Album")) dataf.getString("Album") else null,
               Year            = if (dataf.has("Year")) dataf.getInt("Year") else null,
               Title           = dataf.getString("Title"),
               Track           = if (dataf.has("Track")) dataf.getInt("Track") else null,
               Last_Downloaded = if (dataf.has("Last_Downloaded"))
               dataf.getString("Last_Downloaded") else null, 
               Prev_Downloaded = if (dataf.has("Prev_Downloaded"))
               dataf.getString("Prev_Downloaded") else null,
               Play_Before     = if (dataf.has("Play_Before")) dataf.getInt("Play_Before") else null,
               Play_After      = if (dataf.has("Play_After")) dataf.getInt("Play_After") else null)
         }
   }
   
   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   private suspend fun syncDB(serverIP: String, serverPort: Int, intentAction : String)
   {
      var conflictCount = 0
      var errorCount = 0
      val dao: SongDao = (application as MusicPlayerApplication).db.songDao()
      
      // Connect to the sync server on the laptop, do what it says.

      var clientSocket: Socket? = null
      try {
         // We don't loop here; one sync session per user start sync
         syncUtils.log("SyncService: Connecting to $serverIP:$serverPort...")
         clientSocket = Socket(serverIP, serverPort)
         syncUtils.log("SyncService: Connected.")

         val inputStream = clientSocket.getInputStream() 
         val outputStream = clientSocket.getOutputStream() 

         val res        = resources
         val prefs      = PreferenceManager.getDefaultSharedPreferences(this)
         val computeInterval = prefs.getInt(res.getString(R.string.progress_compute_interval_key), 100)
         val applyInterval = prefs.getInt(res.getString(R.string.progress_apply_interval_key), 100)
         val progressPrefs = JSONObject()
         progressPrefs.put ("Compute_Changes_Interval", computeInterval)
         progressPrefs.put ("Apply_Changes_Interval", applyInterval)
            
         val msg = JSONObject()
         msg.put("ROLE", "COMPUTE")
         msg.put("DISPLAY_PROGRESS", progressPrefs)
            
         try {
            // Action values must match Ada Books.Database_Remote Actions enums.
            when (intentAction)
            {
               utils.SYNC_DB_COMMAND ->
                  {
                     var lastSyncId : Int? = null
                     var lastSyncTime : String? = null
                     val prefs = this@SyncService.playlistPrefsState.data.firstOrNull()
                     if (prefs == null)
                        {
                           // Never set
                           lastSyncId = Song.Null_ID
                           lastSyncTime = Song.Default_Time_String
                        }
                     else
                        {
                           lastSyncId = prefs[PlaylistCountsPreferenceKeys.syncIdKey]
                           lastSyncTime = prefs[PlaylistCountsPreferenceKeys.syncTimeKey]
                           if ((lastSyncId == null) or (lastSyncTime == null))
                              {
                                 // Never set
                                 lastSyncId = Song.Null_ID
                                 lastSyncTime = Song.Default_Time_String
                              }
                       }
                     msg.put("ACTION", "SYNC_INCREMENTAL")
                     msg.put("SYNC_ID", lastSyncId)
                     msg.put("SYNC_TIME", lastSyncTime)
                  }
               
               utils.RESUME_INIT_DB_COMMAND ->
                  {
                     msg.put("ACTION", "RESUME_INIT_REMOTE")
                     msg.put("SYNC_ID", dao.getLastId())
                  }
               
               utils.INIT_DB_COMMAND ->
                  msg.put("ACTION", "INIT_REMOTE")
               
               else -> throw java.net.ProtocolException("unexpected intentAction '$intentAction'")
               }

            syncUtils.sendString(outputStream, msg.toString())
            syncUtils.checkAck(inputStream)
         }
         catch (e: JSONException)
         {
            syncUtils.sendError(outputStream, e.message?: "")
            return
         }

         var done = false
         while (!done)
            {
               val msg = JSONObject(syncUtils.readString(inputStream))
               try
               {
                  val operation = Operations.valueOf(msg.getString("Operation"))
                  
                  when (operation)
                  {
                     Operations.QUIT ->
                        {
                           syncUtils.log("quit")
                           syncDone = true
                           done = true
                           syncUtils.sendAck(outputStream)
                           SyncStatusBus.emit(SyncStatus.Done(if (conflictCount == 0) ""
                                                              else "$conflictCount conflicts"))
                        }
                     
                     Operations.GET ->
                        {
                           val id = msg.getInt("ID")
                           val song = dao.getSong(id)
                           if (song == null)
                              {
                                 errorCount++
                                 syncUtils.sendError(outputStream, "invalid ID: $id")
                              }
                           else
                              syncUtils.sendData(outputStream, Song.toJSON(song))
                        }

                     Operations.GET_LAST_ID ->
                        {
                           val result = JSONObject()
                           result.put("ID", dao.getLastId())
                           syncUtils.sendData(outputStream, result)
                        }
                     
                     Operations.GET_MODIFIED ->
                        {
                           val result = JSONObject()
                           result.put("List",
                                      Song.toJSON(dao.getModified(msg.getInt("ID"), msg.getString("Modified"))))
                           syncUtils.sendData(outputStream, result)
                        }

                     Operations.GET_MODIFIED_WITH_DATA ->
                        {
                           val result = JSONObject()
                           result.put("List",
                                      Song.toJSON(dao.getModifiedWithData(msg.getInt("ID"), msg.getString("Modified"))))
                           syncUtils.sendData(outputStream, result)
                        }

                     Operations.GET_NEW ->
                        {
                           val result = JSONObject()
                           result.put("List", Song.toJSON(dao.getNew(msg.getInt("ID"), msg.getInt("Max_Count"))))
                           syncUtils.sendData(outputStream, result)
                        }

                     Operations.CONFLICT ->
                        {
                           conflictCount++ // So user knows there was a conflict
                           syncUtils.log(msg.toString())
                           // So user can refer to the details later to resolve the conflict

                           syncUtils.sendAck(outputStream)
                        }

                     Operations.PROGRESS ->
                        {
                           val result = msg.getString("Label") + " " +
                              msg.getString("Current") + "/" + msg.getString("Max")
                           SyncStatusBus.emit(SyncStatus.Progress(result))
                           syncUtils.sendAck(outputStream)
                        }

                     Operations.INSERT ->
                        {
                           try
                           {
                              dao.insertSong(Song.fromJSON(msg.getJSONObject("Value")))
                           } catch (_: SQLiteConstraintException)
                           {
                              // This happens when resuming init; some records are repeated. Just ignore.
                           }
                           syncUtils.sendAck(outputStream)
                        }
                     
                     Operations.INSERT_BATCH ->
                        {
                           val values = msg.getJSONArray("Value")
                           try
                           {
                              for (i in 0 until values.length())
                                 dao.insertSong(Song.fromJSON(values.getJSONObject(i)))
                           } catch (_: SQLiteConstraintException)
                           {
                              // This happens when resuming init; some records are repeated. Just ignore.
                           }
                           syncUtils.sendAck(outputStream)
                        }
                     
                     Operations.UPDATE ->
                        {
                           updateOne (dao, msg.getJSONObject("Value"));            
                           syncUtils.sendAck(outputStream)
                        }
                     
                     Operations.UPDATE_BATCH ->
                        {
                           val values = msg.getJSONArray("Value")
                           for (i in 0 until values.length())
                              updateOne(dao, values.getJSONObject(i))
                           
                           syncUtils.sendAck(outputStream)
                        }
                     
                     Operations.RENUMBER ->
                        {
                           syncUtils.log(msg.toString())
                           // Match smm-database_remote-disk.adb Apply Renumber
                           val oldId = msg.getInt("Old_ID")
                           val newId = msg.getInt("New_ID")
                           val oldValue = dao.getSong(oldId)

                           if (oldValue == null)
                              {
                                 errorCount++
                                 syncUtils.sendError(outputStream, "$oldId not found")
                              }
                           else
                              {
                                 val newValue = oldValue.copy(ID = newId)
                                 dao.reallyDeleteSong(oldId)
                                 dao.insertSong(newValue)
                                 
                                 if ((newValue.Play_Before != Song.Null_ID) or
                                         (newValue.Play_After != Song.Null_ID))
                                 {
                                    conflictCount++ // not really a conflict, but close enough
                                    syncUtils.log("$newId renumbered with play_before/_after set")
                                 }
                                 
                                 syncUtils.sendAck(outputStream)
                              }
                        }
                  }
               }
               catch (e: JSONException)
               {
                  val errMsg = "${msg}: error: " + (e.message ?: "")
                  errorCount++
                  SyncStatusBus.emit(SyncStatus.Error(errMsg))
                  syncUtils.log(errMsg.toString())
                  syncUtils.sendError(outputStream, errMsg)
                  done = true
               }
               catch (e: java.net.ProtocolException)
               {
                  errorCount++
                  SyncStatusBus.emit(SyncStatus.Error(e.message ?: ""))
                  syncUtils.log("ProtocolException: ${e.message ?: ""}")
                  done = true
               }
               catch (e: java.net.SocketException)
               {
                  val emsg = "remote closed socket: ${e.message ?: ""}"
                  errorCount++
                  SyncStatusBus.emit(SyncStatus.Error(emsg))
                  syncUtils.log(emsg)
                  done = true
               }
            } // while

         if (intentAction == utils.SYNC_DB_COMMAND && conflictCount == 0 && errorCount == 0)
            {
               val lastId = dao.getLastId()
               val syncTime = Song.getTime()
               
               syncUtils.log("update sync ID $lastId, time $syncTime")
               this@SyncService.playlistPrefsState.edit {
                  prefs ->
                     prefs[PlaylistCountsPreferenceKeys.syncIdKey] = lastId
                  prefs[PlaylistCountsPreferenceKeys.syncTimeKey] = syncTime}
            }
         else
            {
               syncUtils.log("_not_ update sync ID, time; conflicts $conflictCount errors $errorCount")
            }
      }
      catch (e: Exception)
      {
         // server not found, connection reset, etc.
         val emsg = "SyncService Error: ${e.message ?: ""}"
         SyncStatusBus.emit(SyncStatus.Error(emsg))
         syncUtils.log(emsg)
         // Any conflicts found this round will be found again in the
         // next sync round.
      }
      finally
      {
         // Always ensure the socket is closed
         try
         {
            clientSocket?.close()
            syncUtils.log("SyncService: Socket closed.")
         }
         catch (e: Exception)
         {
            Log.e(utils.logTag, "SyncService: Error closing socket.", e)
         }
      }
   } // syncDB
      
   ////////// service lifetime methods; parent Service is abstract
   override fun onBind(intent: Intent): IBinder?
   {
      return null
   }

   private fun buildForegroundNotif(): Notification
   {
      // A background service requires a notification
      val tapIntent = Intent(this, SyncProgressActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      val tapPI = PendingIntent.getActivity(
         this, utils.showSyncProgressIntentId, tapIntent, PendingIntent.FLAG_IMMUTABLE)
      return NotificationCompat.Builder(this, utils.notificationChannelId)
         .setContentTitle("Synchronizing music dbs")
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
      filter.addAction(utils.SYNC_DB_COMMAND)
      registerReceiver(broadcastReceiverCommand, filter, RECEIVER_NOT_EXPORTED)

      startForeground(utils.notif_sync_id, buildForegroundNotif(),
                      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
   }

   override fun onDestroy()
   {
      serviceScope.cancel()
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      unregisterReceiver(broadcastReceiverCommand)
      super.onDestroy()
   }
   
   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
   {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
             PackageManager.PERMISSION_GRANTED)
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
      else if (intent.action == utils.SYNC_DB_COMMAND ||
                  intent.action == utils.INIT_DB_COMMAND ||
               intent.action == utils.RESUME_INIT_DB_COMMAND)
         {
            val res        = resources
            val prefs      = PreferenceManager.getDefaultSharedPreferences(this)
            val serverIP   = prefs.getString (res.getString(R.string.server_IP_key), null)
            val serverPortStr = prefs.getString (res.getString(R.string.server_Port_key), "0")
            val serverPort = runCatching {Integer.decode(serverPortStr!!)}.getOrDefault(0)
            
            if (serverIP.isNullOrEmpty() || serverPort == 0)
               {
                  SyncStatusBus.emit(SyncStatus.Error("Server IP or port preference not set"))
                  return START_NOT_STICKY
               }

            SyncStatusBus.emit(SyncStatus.Progress("Connecting to $serverIP:$serverPort..."))
            serviceScope.launch {
               syncDone = false
               try
               {
                  syncDB(serverIP, serverPort, intent.action!!)
               }
               catch (e: Exception)
               {
                  Log.e(utils.logTag, "SyncService.onStartCommand exception", e)
               }
               if (syncDone)
                  stopSelf()
               // else: error or cancel; activity shows result until user dismisses
            }

            return START_NOT_STICKY
         }
      else if (intent.action == utils.STOP_SERVICE_COMMAND)
         {
            stopSelf()
            return START_NOT_STICKY
         }
      else
         {
            utils.errorLog("SyncService::onStartCommand got bad intent: $intent")
            return START_NOT_STICKY
         }
   }
}
