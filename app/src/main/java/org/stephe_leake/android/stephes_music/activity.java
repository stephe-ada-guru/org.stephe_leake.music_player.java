//  Abstract :
//
//  Provides User Interface to Stephe's Music Player.
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.lang.Class;
import java.lang.Float;
import java.lang.Integer;

public class activity extends android.app.Activity
{
   // constants
   private static final int maxProgress = 1000;

   private static final int DIALOG_PLAY_PLAYLIST     = 1;
   private static final int DIALOG_DOWNLOAD_PLAYLIST = 2;

   private static final int MENU_DUMP_LOG          = 0;
   private static final int MENU_PREFERENCES       = 1;
   private static final int MENU_QUIT              = 2;
   private static final int MENU_RESET_PLAYLIST    = 3;
   private static final int MENU_SHARE             = 4;
   private static final int MENU_COPY              = 5;
   private static final int MENU_LINER             = 6;
   private static final int MENU_DOWNLOAD_PLAYLIST = 7;

   private static final int RESULT_PREFERENCES = 1;

   // Main UI members

   private ImageView   albumArt;
   private TextView    artistTitle;
   private TextView    albumTitle;
   private TextView    songTitle;
   private TextView    currentTime;
   private TextView    totalTime;
   private ImageButton playPauseButton;
   private SeekBar     progressBar;
   private TextView    playlistTitle;
   private WakeLock    wakeLock;

   // Cached values
   private long trackDuration = 0; // track duration in milliseconds
   private float defaultTextViewTextSize; // set in onCreate

   ////////// local utils

   private float getTextViewTextScale()
   {
      Resources         res   = getResources();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      String scale            = prefs.getString
             (res.getString(R.string.text_scale_key),
              res.getString(R.string.text_scale_default));
      try
      {
         return Float.valueOf(scale);
      }
      catch (NumberFormatException e)
      {
         utils.errorLog(this, "invalid text_scale preference: " + scale);
         return 1.3f;
      }
   };

   ////////// UI listeners (alphabetical by listener name; some defined in main.xml)

   public void onClickNote(View v)
   {
      sendBroadcast
         (new Intent
          (utils.ACTION_COMMAND)
          .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_NOTE)
          .putExtra("note", ((String)((Button)v).getText()).replace('\n', ' ')));
   };

   private ImageButton.OnClickListener nextListener = new ImageButton.OnClickListener()
      {
         @Override public void onClick(View v)
         {
            sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_NEXT));
         }
      };

   private TextView.OnClickListener playlistListener = new TextView.OnClickListener()
      {
         // showDialog is deprecated; for some reason, the compiler
         // insists on putting the suppress here.
         //
         // Waiting until it actually disappears; the fix will
         // probably be different by then.
         @SuppressWarnings("deprecation")
         @Override public void onClick(View v)
         {
            showDialog(DIALOG_PLAY_PLAYLIST);
         }
      };

   private ImageButton.OnClickListener playPauseListener = new ImageButton.OnClickListener()
      {
         @Override public void onClick(View v)
         {
            sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_TOGGLEPAUSE));
         }
      };

   private ImageButton.OnClickListener prevListener = new ImageButton.OnClickListener()
      {
         @Override public void onClick(View v)
         {
            sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PREVIOUS));
         }
      };

   private OnSeekBarChangeListener progressListener = new OnSeekBarChangeListener()
      {
         // The system generates events very fast; that leads to a
         // stuttering sound. So add some time hysteresis.

         private long lastTime = 0;

         public void onStartTrackingTouch(SeekBar bar)
         {
         }

         public void onProgressChanged(SeekBar bar, int progress, boolean fromuser)
         {
            if (!fromuser) return;

            final long currentTime = System.currentTimeMillis();

            if (currentTime > lastTime + 100) // 0.1 seconds
            {
               lastTime = currentTime;

               sendBroadcast
                  (new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_SEEK).
                   putExtra(utils.EXTRA_COMMAND_POSITION, (trackDuration * progress / maxProgress)));
            }
         }

         public void onStopTrackingTouch(SeekBar bar)
         {
         }
      };

   ////////// Broadcast reciever

   private BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
      {
         // see utils.java constants for list of intents

         @Override public void onReceive(Context context, Intent intent)
         {
            final String action = intent.getAction();

            try
            {
               if (action.equals(utils.META_CHANGED))
               {
                  if (BuildConfig.DEBUG) utils.verboseLog("activity.onReceive META");

                  albumArt.setImageBitmap(utils.retriever.getAlbumArt()); // Ok if null

                  // On first start, with no playlist selected, these
                  // are all empty strings except playlist, which
                  // contains R.string.null_playlist or null_playlist_directory.
                  playlistTitle.setText(intent.getStringExtra("playlist"));
                  artistTitle.setText(utils.retriever.artist);
                  albumTitle.setText(utils.retriever.album);
                  songTitle.setText(utils.retriever.title);

                  trackDuration = Long.valueOf(utils.retriever.duration);

                  totalTime.setText(utils.makeTimeString(activity.this, trackDuration));
               }
               else if (action.equals(utils.PLAYSTATE_CHANGED))
               {
                  final boolean playing = intent.getBooleanExtra("playing", false);
                  final int currentPos = intent.getIntExtra("position", 0);

                  if (BuildConfig.DEBUG) utils.verboseLog("activity.onReceive PLAYSTATE");

                  if (playing)
                  {
                     playPauseButton.setImageResource(R.drawable.pause);
                  }
                  else
                  {
                     playPauseButton.setImageResource(R.drawable.play);
                  }

                  currentTime.setText(utils.makeTimeString(activity.this, currentPos));

                  if (trackDuration != 0)
                  {
                     progressBar.setProgress((int)(maxProgress * (long)currentPos/trackDuration));
                  }
               }
               else
               {
                  utils.errorLog (activity.this, "broadcastReceiver got unexpected intent: " + intent.toString());
               }
            }
            catch (RuntimeException e)
            {
               utils.debugLog("activity.broadcastReceiver: " + e);
            }
         }
      };

   ////////// Activity lifetime methods (in lifecycle order)

   @Override public void onCreate(Bundle savedInstanceState)
   {
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

      final float scale = getTextViewTextScale();

      final Intent intent = getIntent();

      try
      {
         super.onCreate(savedInstanceState);

         setContentView(R.layout.main);

         startService (new Intent(this, service.class));

         // Set up displays, top to bottom left to right

         albumArt    = (ImageView)findViewById(R.id.albumArt);
         artistTitle = utils.findTextViewById(this, R.id.artistTitle);
         albumTitle  = utils.findTextViewById(this, R.id.albumTitle);
         songTitle   = utils.findTextViewById(this, R.id.songTitle);

         defaultTextViewTextSize = artistTitle.getTextSize();
         artistTitle.setTextSize(scale * defaultTextViewTextSize);
         albumTitle.setTextSize(scale * defaultTextViewTextSize);
         songTitle.setTextSize(scale * defaultTextViewTextSize);

         // FIXME: set button text size from preference. find
         // notes_buttons_* linear layout(s), iterate over children

         ((ImageButton)findViewById(R.id.prev)).setOnClickListener(prevListener);

         playPauseButton = (ImageButton)findViewById(R.id.play_pause);
         playPauseButton.setOnClickListener(playPauseListener);
         playPauseButton.requestFocus();

         ((ImageButton)findViewById(R.id.next)).setOnClickListener(nextListener);

         currentTime = (TextView)findViewById(R.id.currenttime);
         totalTime   = (TextView)findViewById(R.id.totaltime);

         progressBar = (SeekBar) findViewById(android.R.id.progress);
         progressBar.setOnSeekBarChangeListener(progressListener);
         progressBar.setMax(maxProgress);

         playlistTitle = utils.findTextViewById(this, R.id.playlistTitle);
         playlistTitle.setTextSize(scale * defaultTextViewTextSize);
         playlistTitle.setOnClickListener(playlistListener);

         if (intent.getAction() == null || // destroyed/restored (ie for screen rotate)
             intent.getAction().equals(Intent.ACTION_MAIN)) // launched directly by user
         {
            sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_UPDATE_DISPLAY));
         }
         else
         {
            utils.errorLog(this, "onCreate got unexpected intent: " + intent.toString());
         }
      }
      catch (RuntimeException e)
      {
         utils.errorLog(this, "onCreate: That does not compute: " + e.getMessage(), e);
         finish();
      }
   }

   @Override protected void onResume()
   {
      super.onResume();

      if (BuildConfig.DEBUG) utils.verboseLog("activity.onResume");

      try
      {
         Resources         res   = getResources();
         SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
         IntentFilter      f     = new IntentFilter();
         f.addAction(utils.META_CHANGED);
         f.addAction(utils.PLAYSTATE_CHANGED);
         registerReceiver(broadcastReceiver, f);
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_UPDATE_DISPLAY));

         if (prefs.getBoolean (res.getString(R.string.always_on_key), false))
         {
            wakeLock.acquire();
         }
      }
      catch (RuntimeException e)
      {
         utils.errorLog(this, "onResume: ", e);
      }
   }

   @Override protected void onPause()
   {
      super.onPause();

      if (BuildConfig.DEBUG) utils.verboseLog("activity.onPause");

      try
      {
         Resources         res   = getResources();
         SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
         if (prefs.getBoolean (res.getString(R.string.always_on_key), false))
         {
            wakeLock.release();
         }
         unregisterReceiver(broadcastReceiver);
      }
      catch (RuntimeException e)
      {
         utils.errorLog(this, "onPause: ", e);
      }
   }

   ////////// key handling

   @Override public boolean onKeyDown(int keyCode, KeyEvent event)
   {
      boolean handled = false; // don't terminate event processing; let MediaEventReceivers get it

      switch (keyCode)
      {
         // Alphabetical keycode order
      case KeyEvent.KEYCODE_MEDIA_NEXT:
      case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
         // Google TV Remote app has fast forward button but not next
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_NEXT));
         handled = true; // terminate event processing; MediaEventReceivers won't get it
         break;

      case KeyEvent.KEYCODE_MEDIA_PAUSE:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PAUSE));
         handled = true;
         break;

      case KeyEvent.KEYCODE_MEDIA_PLAY:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAY));
         handled = true;
         break;

      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_TOGGLEPAUSE));
         handled = true;
         break;

      case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
      case KeyEvent.KEYCODE_MEDIA_REWIND:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PREVIOUS));
         handled = true;
         break;

      case KeyEvent.KEYCODE_MEDIA_STOP:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PAUSE));
         handled = true;
         break;

      default:
      }
      return handled;
   };


   ////////// playlist dialogs

   // Need an external reference
   AlertDialog dialogPlayPlaylist;


   // onCreateDialog is deprecated
   //
   // Waiting until it actually disappears; the fix will
   // probably be different by then.
   @SuppressWarnings("deprecation")
   @Override protected Dialog onCreateDialog(int id, Bundle args)
   {
      switch (id)
      {
      case DIALOG_PLAY_PLAYLIST:
         {
            try
            {
               Resources res = getResources();
               SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

               final File playlistDir = new File
                  (prefs.getString
                   (res.getString(R.string.playlist_directory_key),
                    res.getString(R.string.playlist_directory_default)));

               final FileExtFilter playlistFilter = new FileExtFilter(".m3u");
               final String[] playlists           = playlistDir.list(playlistFilter);

               if (playlists == null || playlists.length == 0)
               {
                  utils.alertLog(this, "no playlists found in " + playlistDir);
                  return null;
               }

               dialogPlayPlaylist = new AlertDialog.Builder(this)
                  .setTitle(R.string.dialog_play_playlist)
                  .setItems
                  (playlists,
                   new DialogInterface.OnClickListener()
                   {
                      public void onClick(DialogInterface dialogInt, int which)
                      {
                         try
                         {
                            final android.widget.ListView listView = dialogPlayPlaylist.getListView();
                            final String filename = (String)listView.getAdapter().getItem(which);
                            sendBroadcast
                               (new Intent (utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_PLAYLIST).
                                putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistDir.getAbsolutePath() + "/" + filename));
                         }
                         catch (Exception e)
                         {
                            utils.alertLog(activity.this, "play playlist dialog onClick: ", e);
                         }
                      };
                   }
                   ).create();

               return dialogPlayPlaylist;
            }
            catch (Exception e)
            {
               utils.alertLog(this, "create play playlist dialog failed ", e);
               return null;
            }
         }

      case DIALOG_DOWNLOAD_PLAYLIST:
         {
            try
            {
               Resources res = getResources();
               SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

               final File playlistDir = new File
                  (prefs.getString
                   (res.getString(R.string.playlist_directory_key),
                    res.getString(R.string.playlist_directory_default)));

               final FileExtFilter playlistFilter = new FileExtFilter(".m3u");
               final String[] playlists           = playlistDir.list(playlistFilter);

               View view = getLayoutInflater().inflate(R.layout.dialog_download, null);
               final ListView listView = (ListView)view.findViewById(R.id.list_view);
               final EditText textView = (EditText)view.findViewById(R.id.text_view);

               if (null != playlists)
                  // playlists is null before user has downloaded any.
                  listView.setAdapter(new android.widget.ArrayAdapter(this, R.layout.list_dialog_element, playlists));

               AlertDialog dialog = new AlertDialog.Builder(this)
                  .setTitle(R.string.dialog_download_playlist)
                  .setView(view)
                  .setPositiveButton(R.string.download, new DialogInterface.OnClickListener()
                   {
                      public void onClick(DialogInterface dialog, int which)
                      {
                         String filename = null;

                         if (-1 == which)
                            // User entered new playlist name in edit box
                            filename = textView.getText().toString();
                         else
                            // User clicked list
                            filename = (String)listView.getAdapter().getItem(which);

                         sendBroadcast
                            (new Intent (utils.ACTION_COMMAND)
                             .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_DOWNLOAD)
                             .putExtra(utils.EXTRA_COMMAND_PLAYLIST, playlistDir.getAbsolutePath() +
                                       "/" + filename));
                      };
                   }
                   ).create();

               return dialog;
            }
            catch (Exception e)
            {
               utils.alertLog(this, "create download playlist dialog failed ", e);
               return null;
            }
         }

      default:
         utils.errorLog(this, "unknown dialog id " + id);
         return null;
      }
   }

   ////////// Menu

   @Override public boolean onCreateOptionsMenu(Menu menu)
   {
      super.onCreateOptionsMenu(menu);
      menu.add(0, MENU_QUIT, 0, R.string.menu_quit);
      menu.add(0, MENU_SHARE, 0, R.string.menu_share);
      menu.add(0, MENU_LINER, 0, R.string.menu_liner);
      menu.add(0, MENU_COPY, 0, R.string.menu_copy);
      menu.add(0, MENU_RESET_PLAYLIST, 0, R.string.menu_reset_playlist);
      menu.add(0, MENU_DOWNLOAD_PLAYLIST, 0, R.string.menu_download_playlist);
      menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences);
      menu.add(0, MENU_DUMP_LOG, 0, R.string.menu_dump_log);
      return true; // display menu
   }

   @Override public boolean onOptionsItemSelected(MenuItem item)
   {
      switch (item.getItemId())
      {
      case MENU_QUIT:
         sendBroadcast
            (new Intent
             (utils.ACTION_COMMAND)
             .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_QUIT));

         stopService
            (new Intent().setComponent(new ComponentName (this, utils.serviceClassName)));

         finish();
         break;

      case MENU_DUMP_LOG:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_DUMP_LOG));
         break;

      case MENU_DOWNLOAD_PLAYLIST:
         {
            Resources         res      = getResources();
            SharedPreferences prefs    = getSharedPreferences(utils.serviceClassName, MODE_PRIVATE);
            String            serverIP = prefs.getString (res.getString(R.string.server_IP_key), null);

            if (null == serverIP)
               utils.alertLog(this, "set Server IP in preferences");
            else
               showDialog(DIALOG_DOWNLOAD_PLAYLIST);
         }
         break;

      case MENU_PREFERENCES:
         startActivityForResult (new Intent(this, preferences.class), RESULT_PREFERENCES);
         break;

      case MENU_RESET_PLAYLIST:
         sendBroadcast(new Intent(utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_RESET_PLAYLIST));
         break;

      case MENU_SHARE:
         {
            utils.verboseLog("sharing " + utils.retriever.musicUri.toString());

            Intent intent = new Intent()
               .setAction(Intent.ACTION_SEND)
               .putExtra(Intent.EXTRA_STREAM, utils.retriever.musicUri)
               .setType("audio/mp3");

            startActivity(Intent.createChooser(intent, "Share song via ..."));
         }
         break;

      case MENU_COPY:
         {
            ClipboardManager clipManage = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            clipManage.setPrimaryClip
               (ClipData.newPlainText
                ("song", artistTitle.getText() + " " + albumTitle.getText() + " " + songTitle.getText()));
         }
         break;

      case MENU_LINER:
         {
            Intent intent = new Intent(Intent.ACTION_VIEW)
               .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
               .setDataAndType(utils.retriever.linerUri, "application/pdf");

            startActivity(Intent.createChooser(intent, "Show liner notes via ..."));
         }
         break;

      default:
         utils.errorLog
            (this, "activity.onOptionsItemSelected: unknown MenuItemId " + item.getItemId());
      }
      return false; // continue menu processing
   }

   @Override protected void onActivityResult (int requestCode, int resultCode, Intent data)
   {
      switch(requestCode)
      {
      case RESULT_PREFERENCES:
         switch (resultCode)
         {
         case RESULT_CANCELED:
         case RESULT_OK:
            break;

         case utils.RESULT_TEXT_SCALE:
            {
               final float scale = getTextViewTextScale();

               artistTitle.setTextSize(scale * defaultTextViewTextSize);
               albumTitle.setTextSize(scale * defaultTextViewTextSize);
               songTitle.setTextSize(scale * defaultTextViewTextSize);
            }
            break;

         case utils.RESULT_SMM_DIRECTORY:
            {
               sendBroadcast
                  (new Intent (utils.ACTION_COMMAND).putExtra(utils.EXTRA_COMMAND, utils.COMMAND_SMM_DIRECTORY));
               // value from preferences
            }
            break;

         default:
            utils.errorLog
               (this, "activity.onActivityResult: unknown preferences resultCode " + resultCode);
            break;
         }
         break;

      default:
         utils.errorLog
            (this, "activity.onActivityResult: unknown requestCode " + requestCode);
      }
   }
}
