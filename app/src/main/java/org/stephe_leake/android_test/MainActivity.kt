//  Abstract :
//
//  Generate audio focus events for testing.
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

package org.stephe_leake.android_test

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity()
{
   private lateinit var audioManager     : AudioManager
   private lateinit var audioFocusRequest : AudioFocusRequest
   private lateinit var statusText        : TextView
   private lateinit var duckButton        : Button
   private lateinit var releaseButton     : Button

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.activity_main)

      statusText    = findViewById(R.id.status_text)
      duckButton    = findViewById(R.id.duck_button)
      releaseButton = findViewById(R.id.release_button)

      audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

      audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
         .setAudioAttributes(
            AudioAttributes.Builder()
               .setUsage(AudioAttributes.USAGE_MEDIA)
               .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
               .build())
         .setOnAudioFocusChangeListener { focusChange ->
            // We don't expect to lose focus since nothing else requests
            // AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for testing.
            statusText.text = "Focus changed: $focusChange"
         }
         .build()

      duckButton.setOnClickListener {
         val result = audioManager.requestAudioFocus(audioFocusRequest)
         if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            statusText.text = "Ducking"
            duckButton.isEnabled = false
            releaseButton.isEnabled = true
         } else {
            statusText.text = "Request failed: $result"
         }
      }

      releaseButton.setOnClickListener {
         audioManager.abandonAudioFocusRequest(audioFocusRequest)
         statusText.text = "Idle"
         duckButton.isEnabled = true
         releaseButton.isEnabled = false
      }
   }

   override fun onDestroy()
   {
      if (releaseButton.isEnabled)
         audioManager.abandonAudioFocusRequest(audioFocusRequest)
      super.onDestroy()
   }
}
