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

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter 
import java.net.Socket

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.json.JSONArray
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
      var dao: SongDao = (application as MusicPlayerApplication).db.songDao()
      
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
            if (intentAction == utils.SYNC_DB_COMMAND)
               msg.put("ACTION", "SYNC_INCREMENTAL")
            else if (intentAction == utils.RESUME_INIT_DB_COMMAND)
               msg.put("ACTION", "RESUME_INIT_REMOTE")
            else if (intentAction == utils.INIT_DB_COMMAND)
               msg.put("ACTION", "INIT_REMOTE")

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
               try
               {
                  val msg       = JSONObject(syncUtils.readString(inputStream))
                  val operation = Operations.valueOf(msg.getString("Operation"))
                  
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
                              var song = dao.getSong(id)
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
                              val result = msg.get("Label") + " " + msg.get("Current") + "/" + msg.get("Max")
                              notif.update(result)
                              syncUtils.sendAck(outputStream)
                           }

                        Operations.INSERT,
                        Operations.UPDATE,
                        Operations.RENUMBER ->
                           {
                              // FIXME: need dao functions for these
                            try
                            {
                            } catch (e: SQLiteConstraintException)
                            {
                               // This happens when resuming init; some records are repeated. Just ignore.
                            }
                            syncUtils.sendAck(outputStream)
                        }
                    }
                }
               catch (e: JSONException)
               {
                  notif.error("error: " + e.message)
                  syncUtils.sendError(outputStream, e.message?:"")
                  done = true
               }
               catch (e: java.net.ProtocolException)
               {
                  notif.error(e.message?:"")
                  done = true
                }
               catch (e: java.net.SocketException)
               {
                  notif.error("remote closed socket: " + e.message?:"")
                  done = true
               }
            }
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
   }
   
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
         showLogPendingIntentInit = PendingIntent.getActivity
         (this.applicationContext,
          utils.showSyncLogIntentId,
          utils.showLogIntent(this, utils.logFileName("sync")),
          PendingIntent.FLAG_IMMUTABLE),

         cancelPendingIntent = PendingIntent.getBroadcast
         (this.applicationContext,
          utils.cancelSyncIntentId,
          Intent(this, MPBroadcastReceiver::class.java).apply{action = utils.COMMAND_CANCEL_DOWNLOAD},
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
            var serverPort = prefs.getInt (res.getString(R.string.server_Port_key), 0)
            
            if (serverIP.isNullOrEmpty() || serverPort == 0)
               {
                  notif.error("Server IP or port preference not set")
                  return START_NOT_STICKY
               }

            serviceScope.launch {
               notif.initialize()
               syncDB(serverIP, serverPort, intent.action!!)
               notif.done("")
               stopSelf()
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
