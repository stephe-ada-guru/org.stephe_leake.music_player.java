package org.stephe_leake.music_player_2

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private class FakePlayer : PlayerControl {
   override var isPlaying = false
   var pauseCount = 0
   var playCount  = 0

   override fun pause() { isPlaying = false; pauseCount++ }
   override fun play()  { isPlaying = true;  playCount++ }
}

class AudioFocusHandlerTest
{
   private lateinit var player  : FakePlayer
   private lateinit var handler : AudioFocusHandler

   @Before
   fun setUp()
   {
      player  = FakePlayer()
      handler = AudioFocusHandler(player)
   }

   @Test
   fun duck_while_playing_pauses()
   {
      player.isPlaying = true
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
      assertEquals(1, player.pauseCount)
      assertFalse(player.isPlaying)
   }

   @Test
   fun transient_loss_while_playing_pauses()
   {
      player.isPlaying = true
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS_TRANSIENT)
      assertEquals(1, player.pauseCount)
      assertFalse(player.isPlaying)
   }

   @Test
   fun duck_while_already_paused_does_nothing()
   {
      player.isPlaying = false
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
      assertEquals(0, player.pauseCount)
   }

   @Test
   fun gain_after_duck_resumes()
   {
      player.isPlaying = true
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_GAIN)
      assertEquals(1, player.playCount)
      assertTrue(player.isPlaying)
   }

   @Test
   fun gain_after_duck_resumes_only_once()
   {
      player.isPlaying = true
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_GAIN)
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_GAIN)
      assertEquals(1, player.playCount)
   }

   @Test
   fun permanent_loss_pauses_and_gain_does_not_resume()
   {
      player.isPlaying = true
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_LOSS)
      assertEquals(1, player.pauseCount)
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_GAIN)
      assertEquals(0, player.playCount)
   }

   @Test
   fun gain_without_prior_loss_does_nothing()
   {
      player.isPlaying = false
      handler.onFocusChange(AudioFocusHandler.AUDIOFOCUS_GAIN)
      assertEquals(0, player.playCount)
   }
}
