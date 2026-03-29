//  Abstract :
//
//  Generic notification for Stephe's Music Player.
//
//  Copyright (C) 2026 Stephen Leake. All Rights Reserved.
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

class ServiceNotif (
   private val context              : Context,
   private val notificationId       : Int,
   private val title                : String,
   private val showLogPendingIntent : PendingIntent,
   private val cancelPendingIntent  : PendingIntent)
{
   var notifMem : Notification = NotificationCompat.Builder(context, utils.notificationChannelId).build()

   var statusText   : String = ""
   var contentText  : String = "..."

   fun getNotif() : Notification
   {
      return notifMem
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun initialize()
   {
      statusText = ""
      contentText = "..."
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   // Permission checked and requested in MainActivity
   private fun updateInternal()
   {
      utils.debugLog("ServiceNotif: $statusText $contentText")
      
      val wrappableContentText = contentText
         .replace("/", "/\u200B")
         .replace(".", ".\u200B")
         .replace("_", "_\u200B")

      val wrappableStatusText = statusText
         .replace("/", "/\u200B")
         .replace(".", ".\u200B")
         .replace("_", "_\u200B")
      
      val cancelAction = NotificationCompat.Action.Builder(
         R.drawable.cancel, "cancel", cancelPendingIntent).build()
      
      val notifMem = NotificationCompat.Builder(context, utils.notificationChannelId)
         .addAction(cancelAction)
         .setContentIntent(showLogPendingIntent)
         .setContentTitle(title + statusText)
         .setContentText(contentText)
         .setStyle(NotificationCompat.BigTextStyle().bigText(wrappableContentText + wrappableStatusText))
         .setPriority(NotificationCompat.PRIORITY_DEFAULT) // make sure it shows!
         .setOngoing(true)
         .setSmallIcon(R.mipmap.download_icon) // shown in status bar
         .build()
      
      val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notifManager.notify(notificationId, notifMem)
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun done(msg : String)
   {
      statusText = "done $statusText"
      contentText = msg
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun error(msg : String)
   {
      statusText = "error"
      contentText = msg
      updateInternal()
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun update(progress : String)
   {
      statusText = progress
      updateInternal()
   }

   fun cancel()
   {
      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
         .cancel(utils.notif_sync_id)
      showLogPendingIntent.cancel()
      cancelPendingIntent.cancel() // FIXME: not needed since that is what triggered cancel?
   }
}
