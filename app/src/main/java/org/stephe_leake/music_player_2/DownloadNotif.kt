//  Abstract :
//
//  Manage download notification.
//
//  Copyright (C) 2021 Stephen Leake. All Rights Reserved.
//
//  This program is free software; you can redistribute it and/or
//  modify it under terms of the GNU General Public License as
//  published by the Free Software Foundation; either version 3, or (at
//  your option) any later version. This program is distributed in the
//  hope that it will be useful, but WITHOUT ANY WARRANTY; without even
//  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//  PURPOSE. See the GNU General Public License for more details. You
//  should have received a copy of the GNU General Public License
//  distributed with this program; see file COPYING. If not, write to
//  the Free Software Foundation, 51 Franklin Street, Suite 500, Boston,
//  MA 02110-1335, USA.

package org.stephe_leake.music_player_2

import android.Manifest
import android.content.Context
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class DownloadNotif (
   private val context      : Context,
   showLogPendingIntentInit : PendingIntent,
   cancelIntent             : PendingIntent)
{
   var notifMem : Notification = NotificationCompat.Builder(context, utils.notificationChannelId).build()
   
   var showLogPendingIntent : PendingIntent = showLogPendingIntentInit

   var cancelAction : NotificationCompat.Action = NotificationCompat.Action.Builder(
      R.drawable.cancel, "cancel", cancelIntent).build()

   var playlistName : String = ""
   var statusText   : String = ""
   var contentText  : String = "..."
   var maxSongs     : Int = 0

   fun formatCounts() : String
   {
      return if (maxSongs == 0) "" else "$maxSongs"
   }

   fun getNotif() : Notification
   {
      return notifMem
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun initialize(playlistName : String)
   {
      this.playlistName = playlistName
      statusText = ""
      contentText = "..."
      maxSongs = 0
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   // Permission checked and requested in MainActivity
   private fun updateInternal()
   {
      notifMem = NotificationCompat.Builder(context, utils.notificationChannelId)
        .addAction (cancelAction)
        .setContentIntent(showLogPendingIntent)
        .setContentTitle("Downloading $playlistName $statusText")
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT) // make sure it shows!
        .setOngoing(true)
        .setSmallIcon(R.mipmap.download_icon) // shown in status bar
        .build()

     val notifManager : NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as
     NotificationManager
                                           
     notifManager.notify(utils.notif_download_id, notifMem)
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun done(msg : String)
   {
      statusText = "done " + formatCounts()
      contentText = msg
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun error(msg : String)
   {
      statusText = "error " + formatCounts()
      contentText = msg
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun update(max : Int)
   {
      maxSongs = max
      statusText = formatCounts()
      updateInternal()
   }

   fun cancel()
   {
      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
         .cancel(utils.notif_download_id)
      showLogPendingIntent.cancel()
   }
}
