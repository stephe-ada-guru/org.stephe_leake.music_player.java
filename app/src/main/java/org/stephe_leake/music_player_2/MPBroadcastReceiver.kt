//  Abstract :
//
//  For Stephe's Music Player.
//
//  Copyright (C) 2025 Stephen Leake.  All Rights Reserved.
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import org.stephe_leake.music_player_2.DownloadService
import org.stephe_leake.music_player_2.utils

class MPBroadcastReceiver : android.content.BroadcastReceiver()
{
   // Intent filter set for utils.DOWNLOAD_COMMAND
   override fun onReceive(context : Context, intent : Intent)
   {
      when (intent.getIntExtra(utils.EXTRA_COMMAND, -1))
      {
         utils.COMMAND_CANCEL_DOWNLOAD ->
            {
               // Stop download service; intent must match startService
               // call in MainActivity new_playlist.
               utils.mainActivity!!.stopService (
                  Intent (/* packageContext = */ utils.mainActivity,
                          /* cls            = */ DownloadService::class.java))
            }
         
         // else just ignore.
         }
   }
}
