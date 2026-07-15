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

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession

class PlayService : MediaSessionService()
{
   private lateinit var mediaSession: MediaSession
   private lateinit var player: ExoPlayer
   private lateinit var audioManager: AudioManager
   private lateinit var audioFocusRequest: AudioFocusRequest
   private lateinit var audioFocusHandler: AudioFocusHandler

   override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
      mediaSession

   ///// Lifecycle

   override fun onCreate()
   {
      super.onCreate()

      val audioAttributes = AudioAttributes.Builder()
         .setUsage(C.USAGE_MEDIA)
         .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
         .build()

      // By default ExoPlayer processes
      // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK by reducing volume; I find
      // that I still focus on the music, and miss the announcement
      // (for example, from my fitness app about being in the wrong
      // heart rate zone). So we handle audio focus events directly,
      // and always pause.
      player = ExoPlayer.Builder(this)
         .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
         .build()

      audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

      audioFocusHandler = AudioFocusHandler(object : PlayerControl {
         override val isPlaying get() = player.isPlaying
         override fun pause() = player.pause()
         override fun play() = player.play()
      })

      audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
         .setAudioAttributes(
            android.media.AudioAttributes.Builder()
               .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
               .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
               .build())
         .setWillPauseWhenDucked(true)  // disable auto-duck; deliver callback so we can pause instead
         .setOnAudioFocusChangeListener { focusChange -> audioFocusHandler.onFocusChange(focusChange) }
         .build()

      player.addListener(object : Player.Listener {
         override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
               audioManager.requestAudioFocus(audioFocusRequest)
               DuckStatusBus.emit(false)
            } else if (audioFocusHandler.pausedByFocusLoss) {
               DuckStatusBus.emit(true)
            } else {
               audioManager.abandonAudioFocusRequest(audioFocusRequest)
               DuckStatusBus.emit(false)
            }
         }
      })

      mediaSession = MediaSession.Builder(this, player)
         .setSessionActivity(
            PendingIntent.getActivity(
               this,
               0, // Request code
               Intent(this, MainActivity::class.java),
               PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!)
         .build()
   }

   //  WORKAROUND: Media3 sometimes tries to call
   //  startForegroundService when the player state changes, which
   //  causes Android 12+ to throw
   //  ForegroundServiceStartNotAllowedException. This catches that
   //  exception.
   override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean)
   {
      try {
         super.onUpdateNotification(session, startInForegroundRequired)
      } catch (_: ForegroundServiceStartNotAllowedException) {
         // Player state changed while app was in the background; the service
         // is already running so no action is needed.
      }
   }

   override fun onDestroy()
   {
      if (::player.isInitialized)
         {
            player.release()
         }

      if (::mediaSession.isInitialized)
         {
            mediaSession.release()
         }

      if (::audioFocusRequest.isInitialized)
         {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
         }

      super.onDestroy()
   }

}
