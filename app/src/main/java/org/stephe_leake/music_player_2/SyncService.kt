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
import androidx.preference.PreferenceManager

import java.net.Socket

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.json.JSONException
import org.json.JSONObject

class SyncService : Service()
{
   private val serviceScope = CoroutineScope(Dispatchers.IO)
   private lateinit var notif : ServiceNotif

   // Note that this is a different instance from DownloadService, so
   // they have different intent filters.
   private val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   private suspend fun syncDB(serverIP: String, serverPort: Int, intentAction : String)
   {
      var conflictCount = 0
      val dao: SongDao = (application as MusicPlayerApplication).db.songDao()
      
      // Connect to the sync server on the laptop, do what it says.
      Log.d(utils.logTag, "syncDB: start")

      var clientSocket: Socket? = null
      try {
         // We don't loop here; one sync session per user start sync
         Log.d(utils.logTag, "SyncService: Connecting to $serverIP:$serverPort...")
         clientSocket = Socket(serverIP, serverPort)
         Log.d(utils.logTag, "SyncService: Connected.")

         val inputStream = clientSocket.getInputStream() 
         val outputStream = clientSocket.getOutputStream() 

         try {
            val msg = JSONObject()
            msg.put("ROLE", "COMPUTE")
            msg.put("DISPLAY_PROGRESS", "TRUE")
            
            // Action values must match Ada Books.Database_Remote Actions enums.
            when (intentAction)
            {
               utils.SYNC_DB_COMMAND ->
                  msg.put("ACTION", "SYNC_INCREMENTAL")
               
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
                  
                  Log.d(utils.logTag, "syncDB op: $operation ")
                  
                  when (operation)
                  {
                     Operations.QUIT ->
                        {
                           notif.done(if (conflictCount == 0) "" else "$conflictCount conflicts")
                           done = true
                           syncUtils.sendAck(outputStream)
                        }
                     
                     Operations.GET ->
                        {
                           val id = msg.getInt("ID")
                           val song = dao.getSong(id)
                           if (song == null)
                              syncUtils.sendError(outputStream, "invalid ID: $id")
                           else
                              syncUtils.sendData(outputStream, syncUtils.toJSON(song))
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
                                      syncUtils.toJSON(dao.getModified(msg.getInt("ID"), msg.getString("Modified"))))
                           syncUtils.sendData(outputStream, result)
                        }

                     Operations.GET_NEW ->
                        {
                           val result = JSONObject()
                           result.put("List", syncUtils.toJSON(dao.getNew(msg.getInt("ID"), msg.getInt("Max_Count"))))
                           syncUtils.sendData(outputStream, result)
                        }

                     Operations.CONFLICT ->
                        {
                           conflictCount++ // So user knows there was a conflict
                           utils.errorLog(msg.toString())
                           // So user can refer to the details later to resolve the conflict

                           syncUtils.sendAck(outputStream)
                        }

                     Operations.PROGRESS ->
                        {
                           val result = msg.getString("Label") + " " +
                           msg.getString("Current") + "/" + msg.getString("Max")
                           notif.update(result)
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
                     Operations.UPDATE ->
                        {
                           val value = msg.getJSONObject("Value")
                           
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
                                    Play_Before     = if (dataf.has("Play_Before")) dataf.getInt("Play_Before")
                                      else null,
                                    Play_After      = if (dataf.has("Play_After")) dataf.getInt("Play_After") else null)
                                 
                                 syncUtils.sendAck(outputStream)
                              }
                        }
                     
                     Operations.RENUMBER ->
                        {
                           // Match smm-database_remote-disk.adb Apply Renumber
                           val oldId = msg.getInt("Old_ID")
                           val newId = msg.getInt("New_ID")
                           val oldValue = dao.getSong(oldId)

                           if (oldValue == null)
                              {
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
                                    utils.errorLog("$newId renumbered with play_before/_after set")
                                 }
                                 
                                 syncUtils.sendAck(outputStream)
                              }
                        }
                  }
               }
               catch (e: JSONException)
               {
                  val errMsg = "'${msg.toString()}: error: " + e.message?:"" 
                  notif.error(errMsg)
                  syncUtils.sendError(outputStream, errMsg)
                  done = true
               }
               catch (e: java.net.ProtocolException)
               {
                  notif.error(e.message?:"")
                  done = true
               }
               catch (e: java.net.SocketException)
               {
                  notif.error("remote closed socket: " + (e.message?:""))
                  done = true
               }
            } // while
      }
      catch (e: Exception)
      {
         // server not found, connection reset, etc.
         Log.e(utils.logTag, "SyncService Error: ${e.message}", e)
         notif.error("error: ${e.message}")
         // Any conflicts found this round will be found again in the
         // next sync round.
      }
      finally
      {
         // Always ensure the socket is closed
         try
         {
            clientSocket?.close()
            Log.d(utils.logTag, "SyncService: Socket closed.")
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

   override fun onCreate()
   {
      super.onCreate()

      val filter = IntentFilter()
      filter.addAction(utils.SYNC_DB_COMMAND)
      registerReceiver(broadcastReceiverCommand, filter, RECEIVER_NOT_EXPORTED)

      notif = ServiceNotif(
         context = this,
         notificationId = utils.notif_sync_id,
         title = "Synchronizing music dbs ",
         showLogPendingIntent = PendingIntent.getActivity
         (this.applicationContext,
          utils.showSyncLogIntentId,
          utils.showLogIntent(this, utils.logFileName("sync")),
          PendingIntent.FLAG_IMMUTABLE),

         cancelPendingIntent = PendingIntent.getBroadcast
         (this.applicationContext,
          utils.cancelSyncIntentId,
          Intent(this, MPBroadcastReceiver::class.java).apply{action = utils.COMMAND_CANCEL_SYNC_DB},
          PendingIntent.FLAG_IMMUTABLE))

      startForeground (utils.notif_sync_id, notif.getNotif(),
                       ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
   }

   override fun onDestroy()
   {
      serviceScope.cancel()
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
             PackageManager.PERMISSION_GRANTED)
      {
         notif.cancel()
      }
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
                  Log.e(utils.logTag, "SyncService.onStartCommand Server IP: $serverIP, port: $serverPort")
                  notif.error("Server IP or port preference not set")
                  // We don't call stopSelf here; the user must dismiss the notification.
                  return START_NOT_STICKY
               }

            serviceScope.launch {
               try
               {
                  notif.initialize()
                  syncDB(serverIP, serverPort, intent.action!!)
               }
               catch (e: Exception)
               {
                  Log.e(utils.logTag, "SyncService.onStartCommand exception", e)
               }
               // We don't do stopSelf here; user must respond to notification
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
