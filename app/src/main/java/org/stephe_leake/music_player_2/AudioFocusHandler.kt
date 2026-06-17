//  Abstract :
//
//  Audio focus listener for Stephe's Music Player. Split out for unit
//  testing.
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

interface PlayerControl {
   val isPlaying: Boolean
   fun pause()
   fun play()
}

// Handles AudioManager focus change events. Instead of ducking
// (reducing volume), we pause so that announcements from fitness apps
// etc. are not missed. The focusChange values match
// android.media.AudioManager constants.
class AudioFocusHandler(private val player: PlayerControl)
{
   companion object {
      const val AUDIOFOCUS_GAIN                  =  1
      const val AUDIOFOCUS_LOSS                  = -1
      const val AUDIOFOCUS_LOSS_TRANSIENT        = -2
      const val AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3
   }

   private var pausedByFocusLoss = false

   fun onFocusChange(focusChange: Int)
   {
      when (focusChange) {
         AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
         AUDIOFOCUS_LOSS_TRANSIENT -> {
            if (player.isPlaying) {
               pausedByFocusLoss = true
               player.pause()
            }
         }
         AUDIOFOCUS_LOSS -> {
            // Some other app has grabbed focus long-term; don't
            // resume here when that app exits.
            pausedByFocusLoss = false
            player.pause()
         }
         AUDIOFOCUS_GAIN -> {
            if (pausedByFocusLoss) {
               pausedByFocusLoss = false
               player.play()
            }
         }
      }
   }
}
