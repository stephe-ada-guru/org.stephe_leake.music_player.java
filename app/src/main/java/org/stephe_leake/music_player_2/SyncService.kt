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

import android.app.Service
import android.content.Intent
import android.util.Log
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

class SyncService : Service()
{
   private val serviceScope = CoroutineScope(Dispatchers.IO)

   // Note that this is a different instance from DownloadService, so
   // they have different intent filters.
   private val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()

   private suspend fun syncDB(serverIP: String, serverPort: Int, intentAction : String)
   {
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
            else if (intent.getAction() == utils.INIT_DB_COMMAND)
               msg.put("ACTION", "INIT_REMOTE")

            sendString(msg.toString(outputStream))
            syncUtils.checkAck()
            }
         catch (e: JSONException)
         {
            sendError("bad value: " + e.message)
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
                     QUIT ->
                        {
                           sendUserMsg("Finished")
                           done = true
                           sendAck(outputStream)
                        }
                     
                        GET -> sendData(outputStream, syncUtils.toJSON(dao.getSong(msg.getInt("ID"))))

                        GET_LAST_ID ->
                        {
                           result : JSONObject
                           result.put("ID", dao.getLastId())
                           sendData(outputStream, result)
                        }
                     
                        GET_MODIFIED -> sendData(
                            dao.getModified(
                                msg.getInt("ID"),
                                msg.getString("Modified")
                            )
                        )

                        GET_DATA_NEW -> sendData(
                            db.getNew(
                                TableName.valueOf(msg.getString("Table")),
                                msg.getInt("Sync_ID")
                            )
                        )

                        GET_LINK_MODIFIED -> sendData(
                            db.getModified(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1")),
                                msg.getInt("Sync_ID"),
                                msg.getString("Sync_Time")
                            )
                        )

                        GET_LINK_NEW -> sendData(
                            db.getNew(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1")),
                                msg.getInt("Sync_ID")
                            )
                        )

                        CONFLICT_DATA, CONFLICT_LINK -> {
                            sendUserConflict(msg)
                            sendAck()
                        }

                        PROGRESS -> {
                            sendUserProgress(msg)
                            sendAck()
                        }

                        INSERT_DATA, INSERT_LINK, UPDATE_DATA, UPDATE_LINK, RENUMBER_DATA, RENUMBER_LINK -> {
                            try {
                                db.apply(msg)
                            } catch (e: SQLiteConstraintException) {
                                // This happens when resuming init; some records are repeated. Just ignore.
                            }
                            sendAck()
                        }
                    }
                } catch (e: JSONException) {
                    sendUserMsg("bad value: " + e.message)
                    sendError("bad value: " + e.message)
                } catch (e: ProtocolError) {
                    sendUserMsg("remote protocol error: " + e.message)
                    done = true
                } catch (e: SocketClosed) {
                    sendUserMsg("remote closed socket: " + e.message)
                    done = true
                } catch (e: IOException) {
                    sendUserMsg("remote closed socket: " + e.message)
                    done = true
                }
            }

            server!!.close()
        } catch (e: IOException) {
            sendUserMsg("error: " + e.toString())
        } catch (e: RuntimeException) {
            sendUserMsg("error: " + e.toString())
            sendError("error: " + e.toString())
            try {
                checkAck()
                server!!.close()
            } catch (f: JSONException) {
            } catch (g: IOException) {
            }
        }
    }
            }

      } catch (e: Exception) {
         // Handle exceptions: server not found, connection reset, etc.
         Log.e(utils.logTag, "SyncService Error: ${e.message}", e)
         // Optionally, update the notification to show an error state.
         // notif.error("Connection failed: ${e.message}")
      } finally {
         // Always ensure the socket is closed
         try {
            clientSocket?.close()
            Log.d(utils.logTag, "SyncService: Socket closed.")
         } catch (e: Exception) {
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
         title = "Synchronizing music dbs "
         showLogPendingIntentInit = PendingIntent.getActivity
         (this.applicationContext,
          utils.showSyncLogIntentId,
          utils.showSyncLogIntent(this),
          PendingIntent.FLAG_IMMUTABLE),

         cancelIntent = PendingIntent.getBroadcast
         (this.applicationContext,
          utils.cancelSyncIntentId,
          Intent(this, MPBroadcastReceiver::class.java).apply{action = utils.COMMAND_CANCEL_DOWNLOAD},
          PendingIntent.FLAG_IMMUTABLE))

      startForeground (utils.notif_sync_id, notif.getNotif(),
                       android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
   }

   override fun onDestroy()
   {
      serviceScope.cancel()
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
             android.content.pm.PackageManager.PERMISSION_GRANTED)
      {
         notif.cancel()
      }
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
      else if (intent.action == utils.SYNC_DB_COMMAND ||
                  intent.action == utils.INIT_DB_COMMAND ||
               intent.action == utils.RESUME_INIT_DB_COMMAND)
         {
            val prefs      = PreferenceManager.getDefaultSharedPreferences(this)
            val serverIP   = prefs.getString (res.getString(R.string.server_IP_key), null)
            var serverPort = prefs.getInt (res.getString(R.string.server_Port_key), null)
            
            if (serverIP.isNullOrEmpty() || serverPort == null)
               {
                  notif.error("Server IP or port preference not set")
                  return START_NOT_STICKY
               }

            serviceScope.launch {
               notif.initialize()
               syncDB(serverIP, serverPort, intent.action)
               notif.done()
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
    public override fun onHandleIntent(intent: Intent) {
        // This runs in the main activity process, in a separate thread.
        var done = false

        verbosity = intent.getIntExtra(Common.VERBOSITY, 0)

        try {




            while (!done) {
                try {
                    val msgString = syncUtils.readString(inputStream)
                    val msg = JSONObject(msgString)
                    val operation = Operations.valueOf(msg.getString("Operation"))

                    when (operation)
                    {
                        QUIT -> {
                            sendUserMsg("Finished")
                            done = true
                            sendAck()
                        }

                        GET_DATA_DATA -> sendData(
                            db.getData(
                                TableName.valueOf(msg.getString("Table")),
                                msg.getInt("ID")
                            )
                        )

                        GET_LINK_DATA -> sendData(
                            db.getData(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1")), msg.getInt("ID")
                            )
                        )

                        GET_DATA_LAST_ID -> sendData(db.getLastId(TableName.valueOf(msg.getString("Table"))))
                        GET_LINK_LAST_ID -> sendData(
                            db.getLastId(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1"))
                            )
                        )

                        GET_DATA_MODIFIED -> sendData(
                            db.getModified(
                                TableName.valueOf(msg.getString("Table")),
                                msg.getInt("Sync_ID"),
                                msg.getString("Sync_Time")
                            )
                        )

                        GET_DATA_NEW -> sendData(
                            db.getNew(
                                TableName.valueOf(msg.getString("Table")),
                                msg.getInt("Sync_ID")
                            )
                        )

                        GET_LINK_MODIFIED -> sendData(
                            db.getModified(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1")),
                                msg.getInt("Sync_ID"),
                                msg.getString("Sync_Time")
                            )
                        )

                        GET_LINK_NEW -> sendData(
                            db.getNew(
                                TableName.valueOf(msg.getString("Table_0")),
                                TableName.valueOf(msg.getString("Table_1")),
                                msg.getInt("Sync_ID")
                            )
                        )

                        CONFLICT_DATA, CONFLICT_LINK -> {
                            sendUserConflict(msg)
                            sendAck()
                        }

                        PROGRESS -> {
                            sendUserProgress(msg)
                            sendAck()
                        }

                        INSERT_DATA, INSERT_LINK, UPDATE_DATA, UPDATE_LINK, RENUMBER_DATA, RENUMBER_LINK -> {
                            try {
                                db.apply(msg)
                            } catch (e: SQLiteConstraintException) {
                                // This happens when resuming init; some records are repeated. Just ignore.
                            }
                            sendAck()
                        }
                    }
                } catch (e: JSONException) {
                    sendUserMsg("bad value: " + e.message)
                    sendError("bad value: " + e.message)
                } catch (e: ProtocolError) {
                    sendUserMsg("remote protocol error: " + e.message)
                    done = true
                } catch (e: SocketClosed) {
                    sendUserMsg("remote closed socket: " + e.message)
                    done = true
                } catch (e: IOException) {
                    sendUserMsg("remote closed socket: " + e.message)
                    done = true
                }
            }

            server!!.close()
        } catch (e: IOException) {
            sendUserMsg("error: " + e.toString())
        } catch (e: RuntimeException) 
    }
}
