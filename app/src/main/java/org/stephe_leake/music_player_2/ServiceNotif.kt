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
   private fun updateInternal(ongoing: Boolean = true, showCancelAction: Boolean = true)
   {
      val wrappableContentText = contentText
         .replace("/", "/\u200B")
         .replace(".", ".\u200B")
         .replace("_", "_\u200B")

      val wrappableStatusText = statusText
         .replace("/", "/\u200B")
         .replace(".", ".\u200B")
         .replace("_", "_\u200B")

      val builder = NotificationCompat.Builder(context, utils.notificationChannelId)

      if (showCancelAction)
         {
            val cancelAction = NotificationCompat.Action.Builder(
               R.drawable.cancel, "cancel", cancelPendingIntent).build()
            builder.addAction(cancelAction)
         }

      builder
         .setContentIntent(showLogPendingIntent)
         .setContentTitle(title)
         .setContentText(contentText)
         .setStyle(NotificationCompat.BigTextStyle().bigText(wrappableStatusText + wrappableContentText))
         .setPriority(NotificationCompat.PRIORITY_DEFAULT) // make sure it shows!
         .setOngoing(ongoing)
         .setSmallIcon(R.mipmap.download_icon) // shown in status bar

      notifMem = builder.build()

      val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notifManager.notify(notificationId, notifMem)
   }

   @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
   fun done(msg : String)
   {
      statusText = "done"
      contentText = msg
      updateInternal(ongoing = false, showCancelAction = false)
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
      contentText = ""
      updateInternal()
   }

   fun cancel()
   {
      (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
         .cancel(notificationId)
      showLogPendingIntent.cancel()
      cancelPendingIntent.cancel()
   }
}
