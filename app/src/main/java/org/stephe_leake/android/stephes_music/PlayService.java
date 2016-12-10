//  Abstract :
//
//  Provides background audio playback capabilities, allowing the
//  user to switch between activities without stopping playback.
//
//  Copyright (C) 2011 - 2013, 2015 - 2016 Stephen Leake.  All Rights Reserved.
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

package org.stephe_leake.android.stephes_music;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioManager;
import android.media.MediaMetadata.Builder;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.preference.MultiSelectListPreference;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Integer;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;

public class PlayService extends Service
{
   //  Internal Messages used for delays
   private static final int UNPAUSE        = 3;
   private static final int UPDATE_DISPLAY = 4;

   private static final long millisPerDay = 24 * 60 * 60 * 1000;

   enum PlayState
   {
     Idle,
     //  Media_Player has no song Loaded

     Playing,
     //  Media_Player has song, is playing it

     Paused,
     //  Media_Player has song, is not playing it, at request of user

     Paused_Transient
     //  Media_Player has song, is not playing it, at request of
     //  system (phone call, navigation announcement, etc).
   };

   enum WhatChanged
   {
      Meta,    // song or playlist
      State,   // play/pause
      Position // progress
   };

   Context context;

   final int pauseIntentId    = 1;
   final int playIntentId     = 2;
   final int prevIntentId     = 3;
   final int nextIntentId     = 4;
   final int activityIntentId = 5;

   PendingIntent nextIntent;
   PendingIntent pauseIntent;
   PendingIntent playIntent;
   PendingIntent prevIntent;

   private AudioManager audioManager;

   private MediaPlayer mediaPlayer;

   ////////// state

   static private PlayState playing;

   private String playlistBasename;
   // Relative to playlistDirectory, without extension (suitable
   // for user display). null if no playlist is current.

   java.util.LinkedList<String> playlist;
   int                          playlistPos;
   // Current position in PlayList (0 indexed; -1 if none)

   boolean haveAudioFocus;

   Timer downloadTimer;

   ////////// private methods (random order)

   private void createMediaPlayer()
   {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      mediaPlayer.setOnCompletionListener(completionListener);
      mediaPlayer.setOnErrorListener(errorListener);
   }

   private String playlistFullPath()
   // return current playlist file abs path
   {
      return utils.playlistDirectory + "/" + playlistBasename + ".m3u";
   }

   private void next()
   {
      // Not clear how we get here with playlist empty, but it seems
      // to have happened once; onCompletionListener was called before
      // anything started playing.
      if (playlist.size() == 0)
      {
         playlistPos = -1;
         stop();
      }
      else
      {
         if (playlistPos < playlist.size() - 1)
         {
            playlistPos++;

            play(playlist.get(playlistPos), 0);
         }
         else
         {
            // at end of playlist; don't wrap, because then we just
            // end up repeating the whole playlist. There isn't a good
            // way to indicate we've wrapped; it looks like there is
            // lots of music left, so we don't sync.
            stop();
         };
      }

      // for some crashes, onDestroy is not called, so we don't
      // saveState properly. So do it here.
      saveState();
   }

   private void setPlayNotif(MetaData retriever)
   {
      // Default init to keep compiler happy
      PendingIntent playPauseIntent = null;
      int playPauseIcon = 0;

      switch (playing)
      {
      case Idle:
         // We get here when reinstantiated after onDestroy
         break;

      case Playing:
         playPauseIcon = R.drawable.pause;
         playPauseIntent = pauseIntent;
         break;

      case Paused:
      case Paused_Transient:
         playPauseIcon = R.drawable.play;
         playPauseIntent = playIntent;

         break;
      }

      try
      {
         RemoteViews notifView = new RemoteViews(context.getPackageName(), R.layout.notification);
         notifView.setTextViewText(R.id.notifArtist, retriever.artist);
         notifView.setTextViewText(R.id.notifAlbum, retriever.album);
         notifView.setTextViewText(R.id.notifTitle, retriever.title);
         notifView.setOnClickPendingIntent(R.id.notifPrev, prevIntent);
         notifView.setImageViewResource(R.id.notifPlayPause, playPauseIcon);
         notifView.setOnClickPendingIntent(R.id.notifPlayPause, playPauseIntent);
         notifView.setOnClickPendingIntent(R.id.notifNext, nextIntent);

         Notification notif = new Notification.Builder(context)
            .setAutoCancel(false)
            .setContent(notifView)
            .setContentIntent(utils.activityIntent)
            .setOngoing(true)
            .setSmallIcon(R.drawable.icon) // shown in status bar
            .setShowWhen(false)
            .build()
            ;

         try
         {
            NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifManager.notify(null, utils.notif_play_id, notif);
         }
         catch (RuntimeException e)
         {
            utils.errorLog(this, "notify " + e.toString());
         }
      }
      catch (RuntimeException e)
      {
         utils.errorLog(this, "notify build " + e.toString());
      }

   }

   private void notifyChange(WhatChanged what)
   {
      // Notify the activity and the remote control that something has changed.
      //
      // 'What' must be one of the *_CHANGED constants in utils.java

      switch (what)
      {
      case Meta:
         if (playing == PlayState.Idle)
         {
            if (utils.playlistDirectory == null)
            {
               sendStickyBroadcast
                  (new Intent (utils.META_CHANGED).
                   putExtra ("artist", "").
                   putExtra ("album", "").
                   putExtra ("track", "").
                   putExtra ("duration", "0").
                   putExtra ("playlist", getResources().getString(R.string.null_playlist_directory)));

            }
            else
            {
               sendStickyBroadcast
                  (new Intent (utils.META_CHANGED).
                   putExtra ("artist", "").
                   putExtra ("album", "").
                   putExtra ("track", "").
                   putExtra ("duration", "0").
                   putExtra ("playlist", getResources().getString(R.string.null_playlist)));
            }

            MediaMetadata metadata = new MediaMetadata.Builder()
               .putString(MediaMetadata.METADATA_KEY_TITLE, "")
               .putString(MediaMetadata.METADATA_KEY_ALBUM, "")
               .putString(MediaMetadata.METADATA_KEY_ARTIST, "")
               .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, "")
               .putLong(MediaMetadata.METADATA_KEY_DURATION, 0)
               .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null)
               .build();

            mediaSession.setMetadata(metadata);

            // no notification until there is something to play
         }
         else
         {
            utils.retriever.setMetaData(context, utils.playlistDirectory, playlist.get(playlistPos));

            sendStickyBroadcast
               (new Intent (utils.META_CHANGED).putExtra
                ("playlist",
                 playlistBasename + " " + (playlistPos + 1) + " / " + playlist.size()));

            MediaMetadata metadata = new MediaMetadata.Builder()
               .putString(MediaMetadata.METADATA_KEY_TITLE, utils.retriever.title)
               .putString(MediaMetadata.METADATA_KEY_ALBUM, utils.retriever.album)
               .putString(MediaMetadata.METADATA_KEY_ARTIST, utils.retriever.artist)
               .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, utils.retriever.artist)
               .putLong(MediaMetadata.METADATA_KEY_DURATION, Integer.parseInt(utils.retriever.duration))
               // This works for the lock screen, but not for the Scion xB
               // ok if albumart is null
               .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, utils.retriever.getAlbumArt())
               .build();

            mediaSession.setMetadata(metadata);

            setPlayNotif(utils.retriever);
         }
         break;

      case State:
      case Position:
         {
            sendStickyBroadcast
               (new Intent (utils.PLAYSTATE_CHANGED).
                putExtra ("playing", playing == PlayState.Playing).
                putExtra ("position", mediaPlayer.getCurrentPosition()));

            switch (playing)
            {
            case Idle:
               mediaSession.setPlaybackState
                  (new PlaybackState.Builder()
                   .setState(PlaybackState.STATE_STOPPED, mediaPlayer.getCurrentPosition(), 1.0f, 0)
                   .build());

               // no Notification
               break;

            case Playing:
               mediaSession.setPlaybackState
                  (new PlaybackState.Builder()
                   .setState(PlaybackState.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f, 0)
                   .build());

               if (what == WhatChanged.State & utils.retriever != null)
               {
                  // normally set by previous META_CHANGED; may not be at startup
                  setPlayNotif(utils.retriever);
               }
               break;

            case Paused:
            case Paused_Transient:
               mediaSession.setPlaybackState
                  (new PlaybackState.Builder()
                   .setState(PlaybackState.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f, 0)
                   .build());

               if (what == WhatChanged.State && utils.retriever != null)
               {
                  setPlayNotif(utils.retriever);
               }
               break;
            }
         }
         break;
      }
   }

   private void pause(PlayState pausedState)
   {
      if (playing == PlayState.Playing)
      {
         mediaPlayer.pause();

         if (pausedState == PlayState.Paused)
         {
            // don't abandon for Paused_Transient
            audioManager.abandonAudioFocus(audioFocusListener);
         }

         playing = pausedState;

         notifyChange(WhatChanged.State);
         handler.removeMessages(UPDATE_DISPLAY);

         // for some crashes, onDestroy is not called, so we don't
         // saveState properly. So do it here.
         saveState();
      }
      else
      {
         if (BuildConfig.DEBUG) utils.verboseLog("pause while not playing");
      }
   }

   private void play(final String path, final int pos)
   {
      // path must be relative to playlistDirectory

      if (BuildConfig.DEBUG) utils.verboseLog("play " + path + "; at " + pos);

      try
      {
         final String absFile = utils.playlistDirectory + "/" + path;

         mediaPlayer.reset();
         playing = PlayState.Idle;

         try
         {
            mediaPlayer.setDataSource (absFile);
            mediaPlayer.prepare();
         }
         catch (IOException e)
         {
            // From SetDataSource

            // We consider this a programmer error, because it
            // probably indicates an SMM sync bug. It could also be a
            // failing sdcard.
            utils.debugLog("can't play '" + path);
            utils.debugLog(e);

            notifyChange(WhatChanged.Meta);
            return;
         }

         unpause(); // Does notifyChange(State)

         notifyChange(WhatChanged.Meta);

         if (pos != 0)
         {
            mediaPlayer.seekTo(pos);
            notifyChange(WhatChanged.State);
         }
      }
      catch (RuntimeException e)
      {
         utils.debugLog("play failed: " + e.toString());
      }
   }

   class Fail extends RuntimeException {}

   private void playList(final String filename, PlayState newState)
      throws Fail
   {
      // Start playing playlist 'filename' (absolute path).
      //
      // Lookup the current file for the playlist from
      // SharedPreferences. If non-null, start at that file.

      final File playlistFile = new File(filename);
      if (!playlistFile.canRead())
      {
         // This is an SMM error, or failing sdcard
         utils.debugLog("can't read " + filename);
         throw new Fail();
      }

      // There are two use cases where the playlist file absolute path
      // changes, but we want to treat it as the same file:
      //
      // 1) root directory moved on a phone
      //    - external sdcard to internal storage
      //
      // 2) USB stick moved to a different device
      //    - Google TV to Neo
      //    - back to Google TV in different USB port

      String tmpPlaylistBaseName = FilenameUtils.getBaseName(playlistFile.toString());

      SharedPreferences storage = getSharedPreferences(utils.preferencesName, MODE_PRIVATE);

      String currentFile = null;
      int    pos = 0;

      final String smmFileName = utils.smmDirectory + "/" + tmpPlaylistBaseName + ".last";

      if (new File(smmFileName).exists())
         try
         {
            BufferedReader reader = new BufferedReader(new FileReader(smmFileName));

            currentFile = reader.readLine();
            reader.close();
         }
         catch (IOException e) {}
      else
      {
         // We get here on a new install; smmDirectory preference not
         // set correctly, or no songs played yet.
         utils.infoLog(context, "set smmDirectory preference");
      }

      if (BuildConfig.DEBUG) utils.debugLog("start file: " + currentFile);

      try
      {
         BufferedReader               in          = new BufferedReader (new FileReader (playlistFile));
         String                       line        = in.readLine();
         java.util.LinkedList<String> tmpPlaylist = new LinkedList<String>();
         int                          songCount   = 0;
         int                          startAt     = 0;

         while (line != null)
         {
            // In SMM playlists all lines are song filepaths, relative
            // to the directory filename is in.

            if (line.length() > 0)
            {
               // Sometimes smm screws up and leaves a blank line

               if (currentFile != null && line.equals(currentFile))
               {
                  startAt = songCount;
                  pos     = storage.getInt(tmpPlaylistBaseName + keyCurrentPos, 0);
               }

               tmpPlaylist.add(line);
               songCount++;
            }

            line = in.readLine();
         }

         in.close();

         if (0 == tmpPlaylist.size())
         {
            utils.debugLog("no songs found in playlist file " + filename);
            throw new Fail();
         }

         utils.playlistDirectory = playlistFile.getParent();
         playlistBasename  = tmpPlaylistBaseName;
         playlist          = tmpPlaylist;
         playlistPos       = startAt;

         switch (newState)
         {
         case Idle:
            playing = PlayState.Idle;
            break;

         case Playing:
            play(playlist.get(playlistPos), pos);
            break;

         case Paused:
         case Paused_Transient:
            if (playing == PlayState.Idle)
            {
               // FIXME: can't go directly from Idle to Paused
               play(playlist.get(playlistPos), pos);
            }
            pause(newState);
            break;
         }

      }
      catch (Fail e)
      {
         throw e;
      }
      catch (java.io.FileNotFoundException e)
      {
         utils.debugLog("playlist file not found: " + filename + e.toString());
         throw new Fail();
      }
      catch (java.io.IOException e)
      {
         utils.debugLog("error reading playlist file: "  + filename + e.toString());
         throw new Fail();
      }
      catch (RuntimeException e)
      {
         utils.debugLog("playList failed" + e.toString());
         throw new Fail();
      }
   }

   private void previous()
   {
      Resources         res   = getResources();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      final int prevThreshold = Integer.valueOf
         (prefs.getString
          (res.getString(R.string.prev_threshold_key),
           res.getString(R.string.prev_threshold_default)));

      final int currentPos = mediaPlayer.getCurrentPosition();

      if (currentPos > prevThreshold)
      {
         // not near beginning of current track; move to beginning
         mediaPlayer.seekTo(0);
         notifyChange(WhatChanged.State);
      }
      else if (playlistPos > 0)
      {
         playlistPos--;
         play(playlist.get(playlistPos), 0);
      }
      else
      {
         // at start of playlist; indicate that to the user by not playing
         stop();
      }
   }

   private void resetPlaylist()
   {
      playlistPos = 0;
      play(playlist.get(playlistPos), 0);
   }

   // save/restore keys; global
   private static final String keyPlaylistDirectory = "playlistDirectory";
   private static final String keyPlaylistFilename  = "playlistFilename";

   // per-playlist: actual key is prefixed by the playlist filename
   // (sans directory, sans extension). Last played song is in <playlist>.last.
   private static final String keyCurrentPos  = "_currentPos";

   private void restoreState()
   {
      if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
      {
         utils.infoLog(this, "external storage not mounted; can't restore");

         setIdleNull();
         return;
      }

      // External storage may have changed since saveState() was
      // called. In particular, we assume SMM has edited the playlist
      // files and the SMM file. So we don't store playlistPos; we
      // search for the current file in the playlist.

      SharedPreferences storage = getSharedPreferences(utils.preferencesName, MODE_PRIVATE);

      utils.playlistDirectory = storage.getString(keyPlaylistDirectory, null);
      playlistBasename  = storage.getString(keyPlaylistFilename, null);

      if (BuildConfig.DEBUG) utils.verboseLog("restoreState: " + utils.playlistDirectory + ", " + playlistBasename);

      if (utils.playlistDirectory != null && playlistBasename != null)
      {
         try
         {
            playList (utils.playlistDirectory + "/" + playlistBasename + ".m3u", PlayState.Paused);
         }
         catch (Fail e)
         {
            setIdleNull();

            notifyChange(WhatChanged.Meta);
            notifyChange(WhatChanged.State);
         }
      }
      else
      {
         setIdleNull();

         notifyChange(WhatChanged.Meta);
         notifyChange(WhatChanged.State);
      }
   }

   private void saveState()
   {
      SharedPreferences storage = getSharedPreferences(utils.preferencesName, MODE_PRIVATE);
      Editor            editor  = storage.edit();

      editor.putString(keyPlaylistDirectory, utils.playlistDirectory);
      editor.putString(keyPlaylistFilename, playlistBasename);

      editor.putInt
         (playlistBasename + keyCurrentPos,
          (playing == PlayState.Idle) ? 0 : mediaPlayer.getCurrentPosition());

      editor.commit();

      writeSMMFile();
   }

   private void setIdleNull()
   {
      playing           = PlayState.Idle;
      playlistBasename  = null;
      playlist.clear();
      playlistPos       = -1;
   }

   private void stop()
   {
      mediaPlayer.reset();

      playing = PlayState.Idle;

      notifyChange(WhatChanged.State);
      handler.removeMessages(UPDATE_DISPLAY);
   }

   private void unpause()
   {
      if (!haveAudioFocus)
      {
         if (BuildConfig.DEBUG) utils.verboseLog("unpause requestAudioFocus");

         final int result = audioManager.requestAudioFocus
            (audioFocusListener,
             android.media.AudioManager.STREAM_MUSIC,
             android.media.AudioManager.AUDIOFOCUS_GAIN);

         if (result != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
         {
            utils.debugLog("can't get audio focus");
            return;
         }
      }
      else
      {
         if (BuildConfig.DEBUG) utils.verboseLog("unpause haveAudioFocus");
      }

      mediaPlayer.start();

      playing = PlayState.Playing;
      notifyChange (WhatChanged.State);

      // FIXME: should only do this if not already running. or remove any current
      handler.sendEmptyMessageDelayed(UPDATE_DISPLAY, 1000);
   }

   private void writeNote(String note)
   {
      if (playlistPos > -1)
      {
         final String noteFileName = utils.smmDirectory + "/" + playlistBasename + ".note";

         try
         {
            BufferedWriter writer = new BufferedWriter(new FileWriter(noteFileName, true)); // append

            writer.write('"' + playlist.get(playlistPos) + '"' + ' ' + note);
            writer.newLine();
            writer.close();
         }
         catch (IOException e)
         {
            utils.debugLog("can't write note file: " + e);
         }
         catch (RuntimeException e)
         {
            utils.debugLog("writeNote: " + e);
         }
      }
   }

   private void setPlaylistDirectory()
   {
      // FIXME: this can be done from activity now that playlistDirectory is in utils.
      Resources         res   = getResources();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      utils.playlistDirectory = prefs.getString
         (res.getString(R.string.playlist_directory_key),
          res.getString(R.string.playlist_directory_default));
   }

   private void setSMMDirectory()
   {
      // FIXME: this can be done from activity now that smmDirectory is in utils.
      Resources         res   = getResources();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      utils.smmDirectory = prefs.getString
         (res.getString(R.string.smm_directory_key),
          res.getString(R.string.smm_directory_default));
   }

   private void writeSMMFile()
   {
      // Record the current playing song in a file, for smm and
      // transfer to another device.

      if (playlistBasename == null)
         return;

      final String smmFileName = utils.smmDirectory + "/" + playlistBasename + ".last";

      File file = new File(smmFileName);

      if (!file.exists())
         try
         {
            file.createNewFile();
         }
         catch (IOException e)
         {
            utils.errorLog(this, "can't create smm file: " + smmFileName, e);
         }

      if (file.exists())
         try
         {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            if (playlistPos < 0)
            {
               // none played yet; write empty file
            }
            else
            {
               writer.write(playlist.get(playlistPos));
            }
            writer.newLine();
            writer.close();
         }
         catch (IOException e)
         {
            utils.errorLog(this, "can't write smm file: ", e);
         }
         catch (RuntimeException e)
         {
            utils.errorLog(this, "writeSMMFile: ", e);
         }
   }

   private int countSongsRemaining(String category, File playlistFile)
      throws IOException
   {
      // Duplicate the part of restoreState that gets playlistPos
      final String smmFileName = utils.smmDirectory + "/" + category + ".last";

      BufferedReader in        = new BufferedReader (new FileReader (playlistFile));
      String         line      = in.readLine();
      int            songCount = 0;
      int            startAt   = 0;

      String currentFile = null;

      if (new File(smmFileName).exists())
         try
         {
            BufferedReader reader = new BufferedReader(new FileReader(smmFileName));

            currentFile = reader.readLine();
            reader.close();
         }
         catch (IOException e) {}

      while (line != null)
      {
         if (new File(utils.playlistDirectory, line).canRead())
            {
               if (currentFile != null && line.equals(currentFile))
                  startAt = songCount;
               songCount++;
            }
         line = in.readLine();
      }

      in.close();

      return songCount - startAt;
   }

   ////////// nested classes

   private Handler handler = new Handler()
      {
         @Override public void handleMessage(Message msg)
         {
            switch (msg.what)
            {
            case UNPAUSE:
               if (BuildConfig.DEBUG) utils.verboseLog("PlayService handler: UNPAUSE");
               unpause();

            case UPDATE_DISPLAY:
               notifyChange(WhatChanged.Position);

               handler.sendEmptyMessageDelayed(UPDATE_DISPLAY, 1000);

            default:
               break;
            }
         }
      };

   private BroadcastReceiver broadcastReceiverCommand = new BroadcastReceiver()
      {
         // Intent filter set for ACTION_COMMAND
         @Override public void onReceive(Context context, Intent intent)
         {
            final int command = intent.getIntExtra(utils.EXTRA_COMMAND, -1);

            if (BuildConfig.DEBUG) utils.verboseLog("command: " + Integer.toString (command));

            // command alphabetical order
            switch (command)
            {
            case utils.COMMAND_RESERVED:
               // not clear what is sending this!
               break;

            case utils.COMMAND_DUMP_LOG:
               dumpLog();
               break;

            case utils.COMMAND_JUMP:
               {
                  // playlist is 0 indexed; user is 1 indexed.
                  playlistPos = -1 + intent.getIntExtra(utils.EXTRA_COMMAND_POSITION, 0);
                  play(playlist.get(playlistPos), 0);
               }
               break;

            case utils.COMMAND_NEXT:
               next();
               break;

            case utils.COMMAND_NOTE:
               writeNote(intent.getStringExtra("note"));
               break;

            case utils.COMMAND_PAUSE:
               pause(PlayState.Paused);
               break;

            case utils.COMMAND_PLAY:

               switch (PlayService.playing)
               {
               case Idle:
                  next();
                  break;

               case Playing:
                  break;

               case Paused:
                  unpause();
                  break;

               case Paused_Transient:
                  // user wants to override
                  unpause();
                  break;

               };
               break;

            case utils.COMMAND_PLAYLIST:

               try
               {
                  // User will want to resume the current playlist at some point.
                  saveState();

                  playList(intent.getStringExtra(utils.EXTRA_COMMAND_PLAYLIST), PlayState.Playing);
               }
               catch (Fail e)
               {
                  // nothing to do here.
               }
               break;

            case utils.COMMAND_PREVIOUS:
               previous();
               break;

            case utils.COMMAND_RESET_PLAYLIST:
               resetPlaylist();
               break;

            case utils.COMMAND_SAVE_STATE:
               saveState();
               break;

            case utils.COMMAND_SEEK:
               {
                  final long pos = intent.getLongExtra(utils.EXTRA_COMMAND_POSITION, 0);
                  mediaPlayer.seekTo((int)pos);
                  notifyChange(WhatChanged.Position);
               }
               break;

            case utils.COMMAND_PLAYLIST_DIRECTORY:
               setPlaylistDirectory();
               break;

            case utils.COMMAND_SMM_DIRECTORY:
               setSMMDirectory();
               break;

            case utils.COMMAND_TOGGLEPAUSE:
               switch (PlayService.playing)
               {
               case Idle:
                  break;

               case Playing:
                  pause(PlayState.Paused);
                  break;

               case Paused:
                  unpause();
                  break;

               case Paused_Transient:
                  // user wants to override
                  unpause();
                  break;

               };
               break;

            case utils.COMMAND_UPDATE_DISPLAY:
               notifyChange(WhatChanged.Meta);
               notifyChange(WhatChanged.State);
               break;

            case utils.COMMAND_QUIT:
               pause(PlayState.Paused);

               NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
               notifManager.cancel(null, utils.notif_play_id);
               break;

            default:
               utils.errorLog
                  (context, "broadcastReceiverCommand.onReceive: unknown command: " + Integer.toString(command) +
                   ", " + intent.getExtras());

            }
         }
      };

   private BroadcastReceiver broadcastReceiverBTConnect = new BroadcastReceiver()
      {
         // Intent filter set for ACTION_SCO_AUDIO_STATE_UPDATED
         @Override public void onReceive(Context context, Intent intent)
         {
            final int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -2);

            if (BuildConfig.DEBUG) utils.verboseLog("bluetooth state: " + state);

            switch (state)
            {
            case AudioManager.SCO_AUDIO_STATE_CONNECTED:
               // Assume it's a smart remote control; tell it our
               // state to start the control connection.
               notifyChange(WhatChanged.State);

            default:
               // just ignore.

            }
         }
      };

   private MediaSession mediaSession;
   // Sends data to car remote, and cover art to lock screen.
   //
   // To get both controls and metadata with my phone and car requires (apparently):
   // MediaSession.Callback registered
   // MediaButtonReciever in AndroidManifest, but _not_ registered with the audio manager

   private MediaSession.Callback mediaCallback = new MediaSession.Callback()
      {
         @Override public void onPause() { pause(PlayState.Paused);}

         @Override
         public void onPlay()
         {
            switch (PlayService.playing)
            {
            case Idle:
               next();
               break;

            case Playing:
               break;

            case Paused:
               unpause();
               break;

            case Paused_Transient:
               // user wants to override
               unpause();
               break;

            };
         }

         @Override public void onSkipToNext() { next();}

         @Override public void onSkipToPrevious() { previous();}
      };

   private OnAudioFocusChangeListener audioFocusListener = new OnAudioFocusChangeListener()
      {
          public void onAudioFocusChange(int focusChange)
          {
             if (focusChange == AudioManager.AUDIOFOCUS_LOSS)
             {
                haveAudioFocus = false;
                pause(PlayState.Paused);
             }
             else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ||
                      focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
             {
                haveAudioFocus = false;

                pause(PlayState.Paused_Transient);
             }
             else if (focusChange == AudioManager.AUDIOFOCUS_GAIN)
             {
                haveAudioFocus = true;

                switch (playing)
                {
                case Idle:
                   break;

                case Playing:
                   break;

                case Paused:
                   unpause();

                case Paused_Transient:
                   // Most likely after a Navigator message; give
                   // listener time to process it.
                   handler.sendEmptyMessageDelayed(UNPAUSE, 1000);
                }
             }
             else
             {
                utils.debugLog("Unknown onAudioFocusChange code " + focusChange);
             }
          }
       };

   MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener()
      {
         public void onCompletion(MediaPlayer mp)
         {
            next();
         }
      };

   MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener()
      {
         public boolean onError(MediaPlayer mp, int what, int extra)
         {
            if (BuildConfig.DEBUG) utils.verboseLog("MediaPlayer onError: " + what + "," + extra);

            switch (what)
            {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED: // = 100
               if (BuildConfig.DEBUG) utils.verboseLog("recreating MediaPlayer");

               mediaPlayer.release();
               createMediaPlayer();
               // Since we don't know why it died, just trying again seems
               // problematic, but it is the most user friendly if it
               // works. This will _not_ be easy to debug!
               if (playing == PlayState.Playing)
               {
                  play(playlist.get(playlistPos), 0);
               };

               return true;

            default:
               if (BuildConfig.DEBUG) utils.verboseLog("unknown MediaPlayer error code");
               // onCompletion will _not_ be called
               return true;
            }
         }
      };

   private TimerTask downloadTimerTask = new TimerTask()
      {
         public void run()
         {
            startService
               (new Intent(utils.ACTION_COMMAND, null, context, DownloadService.class)
                .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_DOWNLOAD));
         }
      };

   ////////// PlayService lifetime methods
   @Override public IBinder onBind(Intent intent)
   {
      return null;
   }

   @Override public void onCreate()
   {
      super.onCreate();

      context = this;

      setSMMDirectory();

      IntentFilter filter = new IntentFilter();
      filter.addAction(utils.ACTION_COMMAND);
      registerReceiver(broadcastReceiverCommand, filter);

      filter = new IntentFilter();
      filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
      registerReceiver(broadcastReceiverBTConnect, filter);

      utils.activityIntent = PendingIntent.getActivity
         (context.getApplicationContext(),
          activityIntentId,
          new Intent(context, activity.class),
          0);

      prevIntent = PendingIntent.getBroadcast
         (context.getApplicationContext(),
          prevIntentId,
          new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PREVIOUS), 0);

      nextIntent = PendingIntent.getBroadcast
         (context.getApplicationContext(),
          nextIntentId,
          new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_NEXT), 0);

      pauseIntent = PendingIntent.getBroadcast
         (context.getApplicationContext(),
          pauseIntentId,
          new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PAUSE), 0);

      playIntent = PendingIntent.getBroadcast
         (context.getApplicationContext(),
          playIntentId,
          new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAY), 0);

      createMediaPlayer();

      audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

      mediaSession = new MediaSession(context, "stephes media session");

      mediaSession.setFlags
         (MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

      mediaSession.setCallback(mediaCallback);
      mediaSession.setActive(true);

      utils.retriever = new MetaData();

      playlist       = new LinkedList<String>();
      playlistPos    = -1;
      playing        = PlayState.Idle;
      haveAudioFocus = false;

      restoreState();

      {
         GregorianCalendar time = new GregorianCalendar(); // holds current time
         time.add(Calendar.DAY_OF_MONTH, 1); // tomorrow
         time.set(Calendar.HOUR_OF_DAY, 2);
         time.set(Calendar.MINUTE, 30); // 2:30 AM

         downloadTimer = new Timer();
         downloadTimer.scheduleAtFixedRate(downloadTimerTask, time.getTime(), millisPerDay);
      }
   }

   @Override public void onDestroy()
   {
      if (BuildConfig.DEBUG) utils.verboseLog("onDestroy");

      // Android sometimes restarts this service even though we have
      // quit and the user did not request it. So if we save
      // PlayState.playing, we will start playing when the user did
      // not request it, and without an activity to control us! So
      // force idle.

      pause(PlayState.Idle); // does saveState()

      NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      notifManager.cancel(null, utils.notif_play_id);
      prevIntent.cancel();
      nextIntent.cancel();
      playIntent.cancel();
      pauseIntent.cancel();

      mediaPlayer.reset();
      mediaPlayer.release();
      mediaPlayer = null;

      audioManager.abandonAudioFocus(audioFocusListener);

      mediaSession.release();

      handler.removeCallbacksAndMessages(null);

      unregisterReceiver(broadcastReceiverCommand);

      unregisterReceiver(broadcastReceiverBTConnect);

      super.onDestroy();
   }

   @Override public int onStartCommand(Intent intent, int flags, int startId)
   {
      if (intent == null)
      {
         // intent is null if the service is restarted by Android
         // after a crash.
         if (BuildConfig.DEBUG) utils.verboseLog("onStartCommand null intent");
      }
      else if (intent.getAction() != null)
      {
         utils.debugLog("onStartCommand got unexpected intent: " + intent);
      }
      return START_STICKY;
    }

   private void dumpLog()
   {
      final String logFilename = utils.smmDirectory + "/debug.log";

      try
      {
         PrintWriter writer = new PrintWriter(new FileWriter(logFilename));

         dump(null, writer, null);
         utils.debugClear();
         writer.close();
         utils.infoLog(this, "log written to " + logFilename);
      }
      catch (java.io.IOException e)
      {
         utils.errorLog(this, "can't write log to " + logFilename, e);
      }
   }

   @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args)
   {
      writer.println("playing            : " + playing);
      writer.println("playlistDirectory  : " + utils.playlistDirectory);
      writer.println("smmDirectory       : " + utils.smmDirectory);
      writer.println("playlistBasename   : " + playlistBasename);
      writer.println("playlist size      : " + playlist.size());
      writer.println("currentFile        : " + playlist.get(playlistPos));
      writer.println("playlistPos        : " + playlistPos);

      SharedPreferences storage = getSharedPreferences(utils.preferencesName, MODE_PRIVATE);

      writer.println("storage." + keyPlaylistDirectory + ": " + storage.getString(keyPlaylistDirectory, ""));
      writer.println("storage." + keyPlaylistFilename + ": " + storage.getString(keyPlaylistFilename, ""));

      utils.debugDump(writer);

      utils.debugClear();
    }
}
