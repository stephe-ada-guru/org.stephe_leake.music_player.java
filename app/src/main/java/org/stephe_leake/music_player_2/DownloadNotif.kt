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

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent

class DownloadNotif
   constructor (val context: Context, showLogPendingIntentInit : PendingIntent, cancelIntent : PendingIntent)
{
   val channelId : String = "Stephe's Music download service"

   var notif   : Notification = Notification.Builder(context, channelId).build()
   init {
      val channel : NotificationChannel = NotificationChannel(
         channelId, "Stephe's Music download channel", NotificationManager.IMPORTANCE_LOW)
      
      channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)

      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
         .createNotificationChannel(channel)

      update()
   }

   var showLogPendingIntent : PendingIntent = showLogPendingIntentInit
   
   var cancelAction : Notification.Action = Notification.Action.Builder(
      R.drawable.cancel, "cancel", cancelIntent).build()

   var playlistName : String = ""
   var statusText   : String = ""
   var contentText  : String = "..."
   var maxSongs     : Int = 0
   var currentSongs : Int = 0

   fun formatCounts() : String
   {
      if (maxSongs == 0)
         return ""
      else
         return "$currentSongs/$maxSongs"
   }

   fun getNotif() : Notification
   {
      return notif
   }

   fun setName(playlistName : String)
   {
      this.playlistName = playlistName
   }

   fun update()
   {
      notif = Notification.Builder(context, channelId)
        .addAction (cancelAction)
        .setAutoCancel(true) // doesn't work
        .setContentIntent(showLogPendingIntent)
        .setContentTitle("Downloading " + playlistName + " " + statusText)
        .setContentText(contentText)
        .setOngoing(true)
        .setProgress(maxSongs, currentSongs, maxSongs==0)
        .setSmallIcon(R.drawable.download_icon) // shown in status bar
        .build()

     val notifManager : NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                           
     notifManager.notify(utils.notif_download_id, notif)
   }

   fun Done(msg : String)
   {
      statusText = "done " + formatCounts()
      contentText = msg
      update()
   }

   fun Error(msg : String)
   {
      statusText = "error " + formatCounts()
      contentText = msg
      update()
   }

   fun Update(max : Int, current : Int)
   {
      maxSongs = max
      currentSongs = current
      statusText = formatCounts()
      update()
   }

   fun Cancel()
   {
      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
         .cancel(utils.notif_download_id)
      showLogPendingIntent.cancel()
   }
}
