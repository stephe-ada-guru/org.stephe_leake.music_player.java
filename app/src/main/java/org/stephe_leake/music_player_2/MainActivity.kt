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

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView

import com.google.common.util.concurrent.MoreExecutors

import java.io.BufferedWriter
import java.io.File
import java.io.FilenameFilter
import java.io.FileWriter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import org.apache.commons.io.FilenameUtils

import org.json.JSONObject
import org.json.JSONTokener

import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import android.view.View.GONE
import android.view.View.VISIBLE

class MainActivity : AppCompatActivity()
{
   private val CHECK_PERM_NEW_PLAYLIST    = 101
   private val CHECK_PERM_RESET_PLAYLIST  = 102
   private val CHECK_PERM_UPDATE_PLAYLIST = 103
   private val CHECK_PERM_PICK_PLAYLIST   = 104

   private var newPlaylistIntent = Intent()
   
   private val viewModel : MainViewModel by viewModels()
   
   private var mediaController : Player? = null

   private fun CreateNotificationChannel()
   {
      val channel = NotificationChannel(
         utils.notificationChannelId, utils.notificationChannelId, NotificationManager.IMPORTANCE_LOW)
      
      channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)

      (this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
         .createNotificationChannel(channel)
   }

   private fun checkPermission(code : Int) : Boolean
   // Returns true if permissions are already granted, false if
   // they are now requested.
   //
   // If false, caller must return and wait for
   // MainActivity.onRequestPermissionsResult to restart the
   // activity indicated by 'code'.
   {
      val REQUIRED_PERMISSIONS = arrayOf(
         android.Manifest.permission.POST_NOTIFICATIONS,
         android.Manifest.permission.READ_MEDIA_AUDIO,
         android.Manifest.permission.READ_MEDIA_IMAGES)

      val permissionsToRequest = mutableListOf<String>()
      
      var result = true

      // Request MANAGE_EXTERNAL_STORAGE to read/write playlist, logs, songs.
      if (!Environment.isExternalStorageManager())
         {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse("package:${applicationContext.packageName}")
            startActivity(intent)
            result = false
         }

      for (permission in REQUIRED_PERMISSIONS)
         {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
               {
                  permissionsToRequest.add(permission)
               }
         }
      
      if (permissionsToRequest.isNotEmpty())
         {
            var rationaleRequired = false
            
            for (permission in REQUIRED_PERMISSIONS)
               {
                  if (this.shouldShowRequestPermissionRationale(permission))
                     {
                        // See // https://developer.android.com/training/permissions/requesting#explain
                        rationaleRequired = true
                     }
               }

            if (rationaleRequired)
               { utils.alertLog(
                    this, "We store music files in a globally accessible place, " +
                    "so we need file read/write permission." +
                    " We show download status and player controls in notifications.")
               }
            
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), code)

            result = false
         }
      return result
   }

   private suspend fun playlistToPlayer(category : String, play : Boolean)
   {
      // Start playing playlist utils.globalDirectory/<category>.m3u

      mediaController?.clearMediaItems()
      
      val absFilename  = utils.playlistFileName(category)
      val playlistFile = File (absFilename)
      val counts = utils.readPlaylistCounts(category)
      
      if (!playlistFile.canRead())
         {
            // This is an SMM error, or failing sdcard
            utils.alertLog(this, "can't read " + absFilename)
            return
         }

      viewModel.writeCategory(category)
      
      playlistFile.forEachLine{
         line ->
            val data : JSONObject = JSONTokener(line).nextValue() as JSONObject
         val Album_Artist = data.getString("Album_Artist") // FIXME: Album_Artist may be empty- don't match?
         val Album = data.getString("Album")
         val Title = data.getString("Title")
         val Filename = data.getString("File_Name")
         
         var cursor : Cursor? = utils.mainActivity!!.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            /* projection */ arrayOf(MediaStore.Audio.Media._ID,
                                     MediaStore.MediaColumns.ARTIST,
                                     MediaStore.MediaColumns.COMPOSER,
                                     MediaStore.MediaColumns.YEAR),
            /* selection  */ "${MediaStore.MediaColumns.ALBUM_ARTIST} = ? AND " +
            "${MediaStore.Audio.AlbumColumns.ALBUM} = ? AND " +
            "${MediaStore.Audio.AudioColumns.TITLE} = ?",
            /* selectionArgs */ arrayOf(Album_Artist.removeSurrounding("\""),
                                        Album.removeSurrounding("\""),
                                        Title.removeSurrounding("\"")
            ),
            /* sortOrder */ null)
         
         // The syntax that gemini gives for .use is _not_ correct!
         if (cursor == null || !cursor.moveToFirst())
            {
               // not found. Also checked in DownloadUtils.getSongs, but it might get deleted.
               utils.alertLog(this, "not found '" + Filename + "'")
               utils.errorLog("not found '" + Filename + "'")
            }
         else
            {
               val songId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
               val artist =
                  cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST))
               val composer =
                  cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.COMPOSER))
               val year =
                  cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.YEAR))
               val file = File(utils.globalDirectory + "/" + Filename)
               val extra = Bundle()

               extra.putString("Liner_Notes", file.getParent()!! + "/" + "liner_notes.pdf")
               
               val metaData = androidx.media3.common.MediaMetadata.Builder()
                  .setAlbumArtist(Album_Artist)
                  .setAlbumTitle(Album)
                  .setTitle(Title)
                  .setArtist(artist)
                  .setComposer(composer)
                  .setReleaseYear(year.toInt())
                  .setExtras(extra)
                  .build()
               val item : MediaItem = MediaItem.Builder()
                  .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI , songId))
                  .setMediaMetadata(metaData)
                  .build()
               mediaController?.addMediaItem(item)
            }
         
         cursor?.close()
      } // forEachLine

      mediaController?.prepare()
      mediaController?.seekTo(counts.index, counts.pos)

      if (play)
         {
            mediaController?.play()
         }
      else
         {
            // onMediaItemTransition is not triggered.
            playerListener.updateDisplay ()                       
         }
   } // playlistToPlayer

   val commandReceiver = object : BroadcastReceiver()
   {
      override fun onReceive(context: Context?, intent: Intent?)
      {
         if (intent?.action == utils.RESTART_PLAYLIST_COMMAND)
            {
               if (viewModel.playlistState.value.baseName.isNotEmpty())
                  {
                     lifecycleScope.launch {
                        playlistToPlayer(viewModel.playlistState.value.baseName,
                                         play = mediaController!!.isPlaying())}
                  }
            }
      }
   }
   
   private fun showPlaylistPickerDialog(onPlaylistSelected: (String) -> Unit)
   {
      val playlistDir = File (utils.globalDirectory)
      
      val playlistFilter = FilenameFilter{ _, name -> name.endsWith(".m3u", ignoreCase = true) }
      val playlists = playlistDir.list(playlistFilter)
      val builder = AlertDialog.Builder(this)

      if (playlists == null || playlists.isEmpty())
         {
            builder.setTitle("no playlists found")
               .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
         }
      else
         {
            builder.setTitle("Select a Playlist")
               .setItems(playlists) {dialog, which -> onPlaylistSelected(playlists[which])
                                     dialog.dismiss()}
               .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
         }
      
      val dialog = builder.create()
      dialog.show()
   }

   private val playerListener = object : Player.Listener
   {
      private fun updateText(view: TextView, content: CharSequence?)
      {
         if (content == null || content.length == 0)
            view.setVisibility(GONE)
         else
            {
               view.setVisibility(VISIBLE)
               view.setText(content)
            }
      }

      private fun updateText(
         view: TextView,
         content: CharSequence?,
         compare1: CharSequence?,
         compare2: CharSequence?)
      {
         if (content == null || content.length == 0) view.setVisibility(GONE)
            else if (content == compare1) view.setVisibility(GONE)
            else if (content == compare2) view.setVisibility(GONE)
            else {
               view.setVisibility(VISIBLE)
               view.setText(content)
            }
      }

      fun updateDisplay()
      {
         // FIXME: display album art.

         // FIXME: add year, composer to metadata
         
         val count = mediaController!!.mediaItemCount
         val index = mediaController!!.currentMediaItemIndex
         val pos = mediaController!!.currentPosition
         lifecycleScope.launch {viewModel.writeState(count, index, pos)}

         val playlistView = utils.findTextViewById(utils.mainActivity!!, R.id.playlist)
         val yearView = utils.findTextViewById(utils.mainActivity!!, R.id.year)
         val composerView = utils.findTextViewById(utils.mainActivity!!, R.id.composer)
         val artistView = utils.findTextViewById(utils.mainActivity!!, R.id.artist)
         val albumArtistView = utils.findTextViewById(utils.mainActivity!!, R.id.albumArtist)
         val albumView = utils.findTextViewById(utils.mainActivity!!, R.id.album)
         val titleView = utils.findTextViewById(utils.mainActivity!!, R.id.title)
         // val totalTime = utils.findTextViewById(utils.mainActivity!!, R.id.totalTime)
         
         val metadata = mediaController!!.currentMediaItem!!.mediaMetadata
         val yearInt = metadata.releaseYear ?: 0
         val yearText = if (yearInt == 0) "" else yearInt.toString()
         val composerText = metadata.composer ?: ""
         val titleText = metadata.title ?: ""
         val artistText = metadata.artist ?: ""
         val albumArtistText = metadata.albumArtist ?: ""
         val albumText = metadata.albumTitle ?: ""

         // 'index' is 0 indexed
         playlistView.setText(viewModel.playlistState.value.baseName + " " + (index + 1).toString() + "/" + count)
         
         updateText(composerView, composerText, artistText, albumArtistText)
         updateText(artistView, artistText, albumArtistText, null)
         updateText(albumArtistView, albumArtistText, null, null)
         updateText(yearView, yearText)
         albumView.setText(albumText)
         titleView.setText(titleText)
      } // updateDisplay

      override fun onIsPlayingChanged(isPlaying: Boolean)
      {
         if (isPlaying)
            {
               // nothing to do here
            }
         else
            {
               // Can't call mediaController methods from another thread
               val count = mediaController!!.mediaItemCount
               val index = mediaController!!.currentMediaItemIndex
               val pos = mediaController!!.currentPosition
               lifecycleScope.launch {viewModel.writeState(count, index, pos)}
            }
      } // onIsPlayingChanged
      
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int)
      {
         super.onMediaItemTransition(mediaItem, reason)

         if (mediaItem == null)
            {
               // Playlist ended.
               lifecycleScope.launch {viewModel.clearSavedState()}
               
               val composerView = utils.findTextViewById(utils.mainActivity!!, R.id.composer)
               val artistView = utils.findTextViewById(utils.mainActivity!!, R.id.artist)
               val albumArtistView = utils.findTextViewById(utils.mainActivity!!, R.id.albumArtist)
               val yearView = utils.findTextViewById(utils.mainActivity!!, R.id.year)
               val albumView = utils.findTextViewById(utils.mainActivity!!, R.id.album)
               val titleView = utils.findTextViewById(utils.mainActivity!!, R.id.title)
               // val totalTime = utils.findTextViewById(utils.mainActivity!!, R.id.totalTime)

               composerView.setText("")
               artistView.setText("")
               albumArtistView.setText("")
               yearView.setText("")
               albumView.setText("")
               titleView.setText("")
            }
         else
            {
               // Apparently 'reason' is not reliable, so we always update
               // Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT 
               // Player.MEDIA_ITEM_TRANSITION_REASON_AUTO>
               // Player.MEDIA_ITEM_TRANSITION_REASON_SEEK>
               // Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED

               updateDisplay ()                       
            }
      } // onMediaItemTransition

   } // playerListener

   public fun onClickNote(v : View)
   {
      val buttonText = (((v as Button).getText() as String).replace('\n', ' '));
      val controller = mediaController!!
      val index = controller.currentMediaItemIndex

      if (index > 0 && viewModel.playlistState.value.baseName != "")
         {
            val noteFileName = utils.appDirectory + "/" + viewModel.playlistState.value.baseName + ".note"
            val metaData = controller.currentMediaItem!!.mediaMetadata
            val data = JSONObject()
               .put("Album_Artist", metaData.albumArtist)
               .put("Album", metaData.albumTitle)
               .put("Title", metaData.title)
            
            val writer = BufferedWriter(FileWriter(noteFileName, true)); // append
            
            writer.write(data.toString() + ' ' + buttonText);
            writer.newLine();
            writer.close();
         }
   }
   
   ////////// Activity lifetime methods (in lifecycle order)

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      enableEdgeToEdge()

      utils.mainActivity = this

      utils.appDirectory = this.getExternalFilesDir(null)!!.getAbsolutePath()

      utils.showDownloadLogIntent = Intent(Intent.ACTION_VIEW)
         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
         .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
         .setDataAndType(
            FileProvider.getUriForFile(
               this,
               applicationContext.packageName + ".provider",
               File(DownloadUtils.downloadLogFileName())),
            "text/plain")

      utils.showErrorLogIntent = Intent(Intent.ACTION_VIEW)
         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
         .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
         .setDataAndType(
            FileProvider.getUriForFile(
               this,
               applicationContext.packageName + ".provider",
               File(utils.errorLogFileName())),
            "text/plain")

      CreateNotificationChannel()
      
      setContentView(R.layout.mainactivity)
      setSupportActionBar(findViewById(R.id.main_toolbar))

      // Set up displays, top to bottom left to right
      
      val playlistView = utils.findTextViewById(this, R.id.playlist)
      playlistView.setOnClickListener()
      {
         if (checkPermission(CHECK_PERM_PICK_PLAYLIST))
            {
               showPlaylistPickerDialog() {
                  filename ->
                     lifecycleScope.launch{playlistToPlayer(FilenameUtils.getBaseName(filename), play = true)}}
            }
      }

      lifecycleScope.launch {
         viewModel.isPlayerReadyToInitialize.collect{
            // called when it changes state
            isReady ->
               if (isReady)
               {
                  if (mediaController!!.isPlaying())
                     {
                        // UI was killed, but service still active; update UI
                        if (mediaController!!.currentMediaItem != null)
                           {
                              playerListener.updateDisplay ()
                           }
                     }
                  else
                     {
                        val category : String = viewModel.playlistState.value.baseName
                        
                        if (category.isNotEmpty())
                           {
                              playlistToPlayer(category, play = false)
                           }
                     }
               } 
         }
      }
   } // onCreate

   override fun onRequestPermissionsResult(requestCode : Int,
                                           permissions : Array<String>,
                                           grantResults: IntArray)
   {
      var someRefused = false
      var refusedMessage = ""
      
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)

      if (grantResults.isEmpty())
         return

      for (i in 0 .. permissions.size - 1)
         {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
               {
                  someRefused = true
                  refusedMessage += permissions[i] 
               }
         }

      if (!someRefused)
         {
            // All permissions were granted.

            when (requestCode)
            {
               CHECK_PERM_NEW_PLAYLIST ->
                  {
                     this.startService(newPlaylistIntent)
                  }

               CHECK_PERM_PICK_PLAYLIST ->
                  {
                     showPlaylistPickerDialog() {
                        filename -> lifecycleScope.launch {
                           playlistToPlayer(FilenameUtils.getBaseName(filename), play = true)}
                     }
                  }
               
               CHECK_PERM_RESET_PLAYLIST ->
                  {
                     // FIXME: copy from R.id.menu_reset_playlist below
                  }
               
               else -> 
                  {
                     utils.errorLog("programmer error.")
                  }
            }
         }
      else
         {
            // Some permission denied`
            utils.alertLog(this, "You denied a required permission; " + refusedMessage)
         }
   } // onRequestPermissionsResult

   // @OptIn(UnstableApi::class)
   override fun onStart()
   {
      super.onStart()
      val sessionToken = SessionToken(this, ComponentName(this, PlayService::class.java))

      val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
      controllerFuture.addListener(
         {
            mediaController = controllerFuture.get()
            mediaController!!.addListener(playerListener)

            val playerView = findViewById<PlayerView>(R.id.player_view)
            playerView.setPlayer(mediaController)

            viewModel.setMediaControllerReady(true)
         }, MoreExecutors.directExecutor())
   } // onStart

   override fun onResume()
   {
      super.onResume()

      val playerView = findViewById<PlayerView>(R.id.player_view)
      playerView.onResume()
   }
   
   override fun onPause()
   {
      super.onPause()

      val playerView = findViewById<PlayerView>(R.id.player_view)
      playerView.onPause()
   }
   
   override fun onStop()
   {
      super.onStop()
   }
   
   override fun onDestroy()
   {
      mediaController?.removeListener(playerListener)
      
      super.onDestroy()
   }

   ////////// Menu

   override fun onCreateOptionsMenu(menu: Menu): Boolean
   {
      val Inf: MenuInflater = getMenuInflater()
      Inf.inflate(R.menu.main_menu, menu)
      return true // display menu
   }

   override fun onPrepareOptionsMenu(menu: Menu): Boolean 
   {
      super.onPrepareOptionsMenu(menu)

      if (mediaController == null) {return false}
      if (mediaController!!.currentMediaItem == null) {return false}

      val metaData = mediaController!!.currentMediaItem!!.mediaMetadata
      val file = File(metaData.extras!!.getString("Liner_Notes")!!)
      menu.findItem(R.id.menu_liner_notes).setEnabled(file.exists())

      return true
   }

   override fun onOptionsItemSelected(item: MenuItem): Boolean
   {
      when (item.getItemId())
      {
         // Alphabetical order

         R.id.menu_copy ->
            {
               var clipManage: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

               val album       : TextView = utils.findTextViewById(this, R.id.album)
               val albumArtist : TextView = utils.findTextViewById(this, R.id.albumArtist)
               val artist      : TextView = utils.findTextViewById(this, R.id.artist)
               val composer    : TextView = utils.findTextViewById(this, R.id.composer)
               val title       : TextView = utils.findTextViewById(this, R.id.title)
               
               val Msg: String = albumArtist.getText().toString() +
               " " + artist.getText().toString() +
               " " + album.getText().toString() +
               " " + title.getText().toString() +
               " " + composer.getText().toString()

               clipManage.setPrimaryClip (ClipData.newPlainText ("song", Msg))
            }

         R.id.menu_new_playlist ->
            {
               var res     : Resources         = getResources()
               var prefs   : SharedPreferences = this.getPreferences(MODE_PRIVATE)
               var serverIP: String            =
                  // Default in preferences.xml doesn't seem to be used.
               prefs.getString (res.getString(R.string.server_IP_key), res.getString(R.string.server_IP_default))!!
               
               if (null == serverIP)
                  {
                     // can't get here with current default for
                     // serverIP, but keep it in case we get
                     // preferences working properly.
                     utils.alertLog(this, "set Server IP in preferences")
                  }
               else
                  {
                     // Get the playlist name, which is the song category;
                     // tell play service to download initial playlist.
                     
                     var builder: AlertDialog.Builder = AlertDialog.Builder(this)
                     builder.setTitle("new playlist category")
                     
                     val input = EditText(this)
                     builder.setView(input)
                     
                     builder.setPositiveButton ("OK")
                     {_, _ ->

                         newPlaylistIntent = Intent (utils.DOWNLOAD_COMMAND, null, this, DownloadService::class.java)
                         .putExtra(utils.EXTRA_PLAYLIST_CATEGORY, input.getText().toString())
                      
                      if (checkPermission(CHECK_PERM_NEW_PLAYLIST))
                         {
                            this.startService(newPlaylistIntent)
                         }
                     } 

                     builder.setNegativeButton ("Cancel")
                     {dialog, _ ->
                         dialog.cancel()
                     }
                     
                     builder.show()
                  }
            }

         R.id.menu_liner_notes ->
            {
               val metaData = mediaController!!.currentMediaItem!!.mediaMetadata
               val file = File(metaData.extras!!.getString("Liner_Notes")!!)
               val contentUri: Uri? =  FileProvider.getUriForFile(
                  this,
                  "${this.applicationContext.packageName}.provider",
                  file)

               var intent: Intent = Intent(Intent.ACTION_VIEW)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  .setDataAndType(contentUri, "application/pdf")
               
               startActivity(intent)
            }

         R.id.menu_preferences ->
            {
               // We don't need a result
               this.startActivity(Intent(utils.mainActivity, PrefActivity::class.java))
            }
         
         R.id.menu_reset_playlist ->
            {
               if (checkPermission(CHECK_PERM_RESET_PLAYLIST))
                  {
                     //FIXME: don't have play service yet
                     // sendBroadcast(Intent(utils.ACTION_PLAY_COMMAND)
                     //                 .putExtra(utils.EXTRA_COMMAND, utils.COMMAND_RESET_PLAYLIST))
                  }
            }

         R.id.menu_search ->
            {
               //FIXME: start search activity, search local database
            }

         R.id.menu_share ->
            {
               //FIXME: 
                  // utils.verboseLog("sharing " + utils.retriever.musicUri.toString())

               //  intent: Intent = Intent()
               // .setAction(Intent.ACTION_SEND)
               // .putExtra(Intent.EXTRA_STREAM, utils.retriever.musicUri)
               // .setType("audio/mp3")

               // startActivity(Intent.createChooser(intent, "Share song via ..."))
            }

         R.id.menu_show_download_log ->
            { 
              startActivity(utils.showDownloadLogIntent)
            }

         R.id.menu_show_error_log ->
            {  
               startActivity(utils.showErrorLogIntent)
            }

         R.id.menu_update_playlist ->
            {
               if (checkPermission(CHECK_PERM_UPDATE_PLAYLIST))
                  {
                     //FIXME: don't have this fragment yet
                     // var res: Resources           = getResources()
                     // var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                     // var serverIP: String         = prefs.getString (res.getString(R.string.server_IP_key),
                     //                                                 res.getString(R.string.server_IP_default))

                     // if (null == serverIP)
                     //    //FIXME: don't have utils yet
                     //    // utils.alertLog(this, "set Server IP in preferences")
                     // else
                     // {
                        //    //  var diag: PickPlaylistDialogFragment = PickPlaylistDialogFragment()
                        //    //  var args: Bundle = Bundle()
                        //    // args.putInt("command", utils.COMMAND_DOWNLOAD)
                        //    // diag.setArguments(args)
                        //    // diag.show(getFragmentManager(), "pick update playlist")
                        // }
                  }
            }

         else ->
            {
               Log.e(utils.logTag, "activity.onOptionsItemSelected: unknown MenuItemId " + item.getItemId())
            }
      }
      return false // continue menu processing
   } // onOptionsItemSelected

}
