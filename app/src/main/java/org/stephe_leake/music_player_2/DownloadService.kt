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
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class DownloadService : Service()
{
   val broadcastReceiverCommand : MPBroadcastReceiver = MPBroadcastReceiver()
   
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
      registerReceiver(broadcastReceiverCommand, filter)

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
