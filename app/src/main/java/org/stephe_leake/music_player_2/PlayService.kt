//  Abstract :
//
//  Provides User Interface to Stephe's Music Player.
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

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession

class PlayService : MediaSessionService()
{
   private lateinit var mediaSession: MediaSession
   private lateinit var player: ExoPlayer

   override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
      mediaSession
   
   ///// Lifecycle

   override fun onCreate()
   {
      super.onCreate()
      player = ExoPlayer.Builder(this).build()
      mediaSession = MediaSession.Builder(this, player).build()
   }

   override fun onDestroy()
   {
      if (::player.isInitialized)
         {
            player.release()
            // Can't set player null
         }

      mediaSession.release()
      
      super.onDestroy()
   }

}
