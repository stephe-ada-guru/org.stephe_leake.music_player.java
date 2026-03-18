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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2

import kotlinx.coroutines.guava.await

import java.io.BufferedWriter
import java.io.File
import java.io.FilenameFilter
import java.io.FileWriter

import kotlinx.coroutines.launch

import org.apache.commons.io.FilenameUtils

import android.view.View.GONE
import android.view.View.VISIBLE

data class PlaylistInfo(val name: String, val limit: Int)

private class PlaylistAdapter(
    private val playlists: List<PlaylistInfo>,
    private val onClick: (PlaylistInfo) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>()
{
   class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
   {
      val nameView: TextView = itemView.findViewById(R.id.item_playlist_name)
      val limitView: TextView = itemView.findViewById(R.id.item_playlist_limit)
   }

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder
   {
      val view = LayoutInflater.from(parent.context)
         .inflate(R.layout.item_playlist, parent, false)
      return PlaylistViewHolder(view)
   }

   override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int)
   {
      val playlistInfo = playlists[position]
      holder.nameView.text = playlistInfo.name.removeSuffix(".m3u")

      holder.limitView.text =
         when (playlistInfo.limit) {
         utils.playlistNoLimit -> "no limit"
         utils.limitDontSave -> "not set"
         else -> playlistInfo.limit.toString()}
         
      holder.itemView.setOnClickListener { onClick(playlistInfo) }
   }

   override fun getItemCount() = playlists.size
}

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener
{
   private var defaultTextViewTextSize : Float = 1.0F // set in onCreate
   
   private var newPlaylistIntent = Intent()
   private var updatePlaylistIntent = Intent()

   // "by viewModels" returns a singleton (shared with
   // SearchActivity), using the factory if necessary.
   private val viewModel : MainViewModel by viewModels {(application as MusicPlayerApplication).mainViewModelFactory}
   
   private var mediaController : Player? = null

   private lateinit var slideshow: ViewPager2
   private lateinit var imageSlideshowAdapter: ImageSlideshowAdapter
   private val slideshowHandler = Handler(Looper.getMainLooper()) // Handler for slideshow transitions
   private var slideshowRunnable: Runnable? = null
   private val slideshowIntervalMs = 10000L // 10 seconds

   private fun createNotificationChannel()
   {
      val channel = NotificationChannel(
         utils.notificationChannelId, utils.notificationChannelId, NotificationManager.IMPORTANCE_LOW)
      
      channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

      (this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
         .createNotificationChannel(channel)
   }

   private fun checkPermission()
   {
      val requiredPermissions = arrayOf(
         android.Manifest.permission.POST_NOTIFICATIONS,
         android.Manifest.permission.READ_MEDIA_AUDIO,
         android.Manifest.permission.READ_MEDIA_IMAGES)

      val permissionsToRequest = mutableListOf<String>()
      
      // Request MANAGE_EXTERNAL_STORAGE to read/write playlist, logs,
      // songs, database.
      if (!Environment.isExternalStorageManager())
         {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = "package:${applicationContext.packageName}".toUri()
            startActivity(intent)
            // This does _not_ trigger MainActivity.onRequestPermissionsResult
         }

      for (permission in requiredPermissions)
         {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
               {
                  permissionsToRequest.add(permission)
               }
         }
      
      if (permissionsToRequest.isNotEmpty())
         {
            var rationaleRequired = false
            
            for (permission in requiredPermissions)
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
            
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 0)
         }
   }

   private suspend fun playlistToPlayer(category : String, play : Boolean, saveState : Boolean)
   // Start playing playlist utils.globalDirectory/<category>.m3u
   {
      utils.debugLog("playlistToPlayer '$category' play=$play saveState=$saveState")
      
      // First save current state, if valid
      if (saveState && viewModel.playlistName.value != "" && category != viewModel.playlistName.value)
         {
            utils.savePlaylistCounts(
               this@MainActivity,
               category = viewModel.playlistName.value,
               index    = mediaController!!.currentMediaItemIndex,
               pos      = mediaController!!.currentPosition,
               limit    = utils.limitDontSave)
         }
      
      // This can trigger onMediaItemTransition, which saves counts
      // for the current playlist. So call viewModel.writeName after
      // this. IMPROVEME: add flag "clearing" to disable save state?
      mediaController!!.clearMediaItems()

      viewModel.writeName(category)     
      
      val absFilename  = utils.playlistFileName(category)
      val playlistFile = File (absFilename)
      val counts = utils.readPlaylistCounts(this@MainActivity, category)
      
      if (!playlistFile.canRead())
         {
            // This is an SMM error, or failing sdcard
            utils.alertLog(this, "can't read $absFilename")
            return
         }

      playlistFile.forEachLine{
         filename ->
         // We search for the file name, not the metadata (despite
         // Android's recommendation); we sometimes edit the metadata
         // to match Spotify (and sometimes the Android media scanner
         // screws up), so this is more reliable. We use MediaStore to
         // get all the metadata from the song file.
         val cursor : Cursor? = application.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            /* projection */ arrayOf(MediaStore.Audio.Media._ID,
                                     MediaStore.MediaColumns.ALBUM_ARTIST,
                                     MediaStore.Audio.AlbumColumns.ALBUM,
                                     MediaStore.Audio.AudioColumns.TITLE,
                                     MediaStore.MediaColumns.ARTIST,
                                     MediaStore.MediaColumns.COMPOSER,
                                     MediaStore.MediaColumns.YEAR),
            /* selection  */ "${MediaStore.MediaColumns.DATA} = ?",
            /* selectionArgs */ arrayOf(utils.globalDirectory + "/" + filename),
            /* sortOrder */ null)
         
         if (cursor == null || !cursor.moveToFirst())
            {
               // not found. Also checked in DownloadUtils.getSongs, but it might get deleted.
               utils.errorLog("not found '$filename'")
            }
         else
            {
               val songId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
               val albumArtist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM_ARTIST))
               val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM))
               val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE))
               val artist =
                  cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST))
               val composer =
                  cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.COMPOSER))
               val year =
                  cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.YEAR))
               val songFileName = utils.globalDirectory + "/" + filename
               val songFile = File(songFileName)
               val extra = Bundle()

               extra.putString("Song_File", songFileName)
               extra.putString("Liner_Notes", songFile.parent!! + "/liner_notes.pdf")
               
               val metaData = androidx.media3.common.MediaMetadata.Builder()
               // Adding the artwork here makes switching playlists
               // too slow, so we do it in media item transition.
                  .setAlbumArtist(albumArtist)
                  .setAlbumTitle(album)
                  .setTitle(title)
                  .setArtist(artist)
                  .setComposer(composer)
                  .setReleaseYear(year.toInt())
                  .setExtras(extra)
                  .build()
               val item : MediaItem = MediaItem.Builder()
                  .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI , songId))
                  .setMediaId(songId.toString())
                  .setMediaMetadata(metaData)
                  .build()
               mediaController?.addMediaItem(item)
            }
         
         cursor?.close()
      } // forEachLine

      if (mediaController!!.mediaItemCount == 0)
         {
            utils.alertLog(this, "Empty playlist file.")
            return
         }
      
      mediaController?.prepare()
      mediaController?.seekTo(counts.index, counts.pos)

      if (play)
         {
            mediaController?.play()
         }

      playerListener.updateDisplay (saveState)                       
   } // playlistToPlayer

   private fun showPlaylistPickerDialog(onPlaylistSelected: (String) -> Unit)
   {
      val playlistDir = File (utils.globalDirectory)  
      val playlistFilter = FilenameFilter{ _, name -> name.endsWith(".m3u", ignoreCase = true) }
      val playlistFiles = playlistDir.list(playlistFilter)
      val builder = AlertDialog.Builder(this)

      if (playlistFiles.isNullOrEmpty())
         {
            builder.setTitle("no playlists found")
               .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
               .show()
            return
         }

      // This must happen inside a coroutine as readPlaylistLimit is a suspend function
      lifecycleScope.launch {
         val playlistInfos = playlistFiles.map { filename ->
            val category = filename.removeSuffix(".m3u")
            PlaylistInfo(filename, utils.readPlaylistLimit(this@MainActivity, category))}

         val dialogView = layoutInflater.inflate(R.layout.dialog_select_playlist, null)
         builder.setView(dialogView)
         val dialog = builder.create()

         val recyclerView = dialogView.findViewById<RecyclerView>(R.id.select_playlist_playlists)
         val changeLimit = dialogView.findViewById<CheckBox>(R.id.select_playlist_change_limit)
         val editControlsLayout = dialogView.findViewById<LinearLayout>(R.id.select_playlist_edit_controls)
         val editingPlaylist = dialogView.findViewById<TextView>(R.id.select_playlist_editing_playlist)
         val noLimit = dialogView.findViewById<CheckBox>(R.id.select_playlist_no_limit)
         val limitInput = dialogView.findViewById<EditText>(R.id.select_playlist_new_limit)
         val okButton = dialogView.findViewById<Button>(R.id.select_playlist_ok_edit)
         val cancelButton = dialogView.findViewById<Button>(R.id.select_playlist_cancel_edit)

         var selectedPlaylistForEdit: PlaylistInfo? = null

         changeLimit.setOnCheckedChangeListener { _, isChecked ->
            editControlsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE}
         
         noLimit.setOnCheckedChangeListener { _, isChecked ->
            limitInput.visibility = if (isChecked) View.GONE else View.VISIBLE}

         // --- Adapter and Click Logic ---
         recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
         recyclerView.adapter = PlaylistAdapter(playlistInfos) { selectedPlaylist ->
           if (changeLimit.isChecked) {
              // EDIT MODE: User wants to change the limit. Show the controls.
              selectedPlaylistForEdit = selectedPlaylist
              editControlsLayout.visibility = View.VISIBLE
              editingPlaylist.text = "Editing: ${selectedPlaylist.name.removeSuffix(".m3u")}"
              if (selectedPlaylist.limit == utils.playlistNoLimit) {
                 noLimit.isChecked = true
                 limitInput.visibility = View.GONE
              } else {
                 noLimit.isChecked = false
                 limitInput.visibility = View.VISIBLE
                 limitInput.setText(selectedPlaylist.limit.toString())
              }
           } else {
              // SELECT MODE: Immediately select the playlist and close the dialog.
              onPlaylistSelected(selectedPlaylist.name)
              dialog.dismiss()
           }
         }// adapter
         
         okButton.setOnClickListener {
            selectedPlaylistForEdit?.let { playlistToEdit ->
               val newLimit = if (noLimit.isChecked) {
                  utils.playlistNoLimit
               } else {
                  val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                  val defaultLimit = prefs.getString(getString(R.string.song_count_max_key), "50")?.toIntOrNull() ?: 50
                  limitInput.text.toString().toIntOrNull() ?: defaultLimit
               }
            
            val category = playlistToEdit.name.removeSuffix(".m3u")
            lifecycleScope.launch {
               utils.debugLog("showPlaylistPickerDialog edit limit '$category' $newLimit")
               val current = utils.readPlaylistCounts(this@MainActivity, category)
               utils.savePlaylistCounts(this@MainActivity, category, current.index, current.pos, newLimit)
            }
            
            onPlaylistSelected(playlistToEdit.name)
            dialog.dismiss()
            }
         } // Ok listener

         cancelButton.setOnClickListener {
            // Just hide the edit controls and go back to selection mode
            editControlsLayout.visibility = View.GONE
            selectedPlaylistForEdit = null
         }

         dialog.show()
      } // launch
   } // showPlaylistPickerDialog

   val imageExtensions = arrayOf("jpg", "jpeg", "png", "webp", "bmp")

   private fun getImages(absPath: String): List<Uri>
   {
      val prefs = PreferenceManager.getDefaultSharedPreferences(this)

      // User (or an Android bug) might have put an invalid string into preferences
      val minSizeString : String = prefs.getString (
         this.getString(R.string.min_image_size_key), this.getString(R.string.min_image_size_default))!!
      var minSize = this.getString(R.string.min_image_size_default).toLong()

      try
      {
         minSize = minSizeString.toLong()
      }
      catch (_ : java.lang.NumberFormatException)
      {
         utils.alertLog(
            this,
            "invalid value '${minSizeString}' for preference ${this.getString(R.string.min_image_size_title)}; must be an integer.")
      }

      val directory = File(absPath)
      if (!directory.exists() || !directory.isDirectory)
         {
            return emptyList() 
         }

      val files = directory.listFiles{
         file ->
            !file.isDirectory &&
         imageExtensions.any {ext -> file.name.endsWith(".$ext", ignoreCase = true)} &&
         file.length() > minSize}

      return files // files can be null if there is an IO error.
          ?.mapNotNull{ file ->
              try {Uri.fromFile(file)} catch (_ : Exception) {null}}
          ?: emptyList()
   }

   private fun startSlideshowTimer()
   {
      slideshowRunnable = Runnable {
         var currentItem = slideshow.currentItem
         currentItem++
         if (currentItem >= imageSlideshowAdapter.itemCount) {
            currentItem = 0 // Loop back to the first slide
         }
         slideshow.setCurrentItem(currentItem, true) // Use true for smooth scroll
         if (slideshowRunnable != null)
            slideshowHandler.postDelayed(slideshowRunnable!!, slideshowIntervalMs)
      }
      slideshowHandler.postDelayed(slideshowRunnable!!, slideshowIntervalMs)
   }

   private fun stopSlideshowTimer()
   {
      slideshowRunnable?.let {
         slideshowHandler.removeCallbacks(it)
         slideshowRunnable = null
      }
   }

   @Composable
   fun CategoryDisplay(viewModel: MainViewModel)
   {
      // "by" connects the viewModel state to the Composable update.
      val category by viewModel.currentCategory

      // Display the text.
      Text(
         text = category ?: "", 
         modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black), 
         textAlign = TextAlign.Center,
         color = Color.White,
         style = MaterialTheme.typography.titleLarge
      )
   }

   private val playerListener = object : Player.Listener
   {
      private fun updateText(view: TextView, content: CharSequence?)
      {
         if (content == null || content.isEmpty())
            view.visibility = GONE
         else
            {
               view.visibility = VISIBLE
               view.text = content
            }
      }

      private fun updateText(
         view: TextView,
         content: CharSequence?,
         compare1: CharSequence?,
         compare2: CharSequence?)
      {
         if (content == null || content.isEmpty()) view.visibility = GONE
            else if (content == compare1) view.visibility = GONE
            else if (content == compare2) view.visibility = GONE
            else {
               view.visibility = VISIBLE
               view.text = content
            }
      }

      @SuppressLint("SetTextI18n")
      @OptIn(UnstableApi::class)
      fun updateDisplay(saveState : Boolean)
      {
         utils.debugLog("updateDisplay saveState=$saveState")

         val count = mediaController!!.mediaItemCount
         val index = mediaController!!.currentMediaItemIndex
         val pos = mediaController!!.currentPosition

         // utils.savePlaylistCounts is "suspend" because it accesses
         // DataStore<Preferences>. updateDisplay cannot wait for it
         // to complete, because onMediaItemTransition can't wait. So
         // we need to launch a coroutine. We can't use
         // lifecycleScope, because that is _cancelled_ when
         // MainActivity is not active (which it is not, most of the
         // time! Coroutine jobs are not even started). So we use
         // viewModelScope, which can only be accessed from
         // mainViewModel.
         if (saveState)
            viewModel.savePlaylistCounts(index, pos)

         val playlistView = utils.findTextViewById(this@MainActivity, R.id.playlist)
         val yearView = utils.findTextViewById(this@MainActivity, R.id.year)
         val composerView = utils.findTextViewById(this@MainActivity, R.id.composer)
         val artistView = utils.findTextViewById(this@MainActivity, R.id.artist)
         val albumArtistView = utils.findTextViewById(this@MainActivity, R.id.albumArtist)
         val albumView = utils.findTextViewById(this@MainActivity, R.id.album)
         val titleView = utils.findTextViewById(this@MainActivity, R.id.title)
         
         val metadata = mediaController!!.currentMediaItem!!.mediaMetadata
         val yearInt = metadata.releaseYear ?: 0
         val yearText = if (yearInt == 0) "" else yearInt.toString()
         val composerText = metadata.composer ?: ""
         val titleText = metadata.title ?: ""
         val artistText = metadata.artist ?: ""
         val albumArtistText = metadata.albumArtist ?: ""
         val albumText = metadata.albumTitle ?: ""
         
         // 'index' is 0 indexed
         playlistView.text = viewModel.playlistName.value + " " + (index + 1).toString() + "/" + count

         val songFile = File(metadata.extras!!.getString("Song_File")!!)
         val images = getImages(songFile.parent!!)

         imageSlideshowAdapter.updateImages(images)

         if (images.isEmpty())
            slideshow.visibility = GONE
         else
            {
               slideshow.visibility = VISIBLE
               stopSlideshowTimer()

               if (images.size > 1)
                  startSlideshowTimer()

               // metadata is _not_ a reference, so we need to build a
               // replacement mediaItem.
               //
               // This artwork shows on the lock screen, not in the main UI.
               // Artwork embedded in the mp3 file is shown in the main UI;
               // All the others are displayed in the slideshow below.
               val newMetadata = androidx.media3.common.MediaMetadata.Builder()
                  .populate(metadata)
                  .setArtworkUri(images.first())
                  .build()
               val newItem = mediaController!!.currentMediaItem!!.buildUpon()
                  .setMediaMetadata(newMetadata)
                  .build()
               mediaController!!.replaceMediaItem(index, newItem)
            }
                    
         updateText(composerView, composerText, artistText, albumArtistText)
         updateText(artistView, artistText, albumArtistText, null)
         updateText(albumArtistView, albumArtistText, null, null)
         updateText(yearView, yearText)
         albumView.text = albumText
         titleView.text = titleText
         
         // categoryView is managed by CategoryDisplay above, triggered by this:
         viewModel.getCategory (albumArtistText.toString(), albumText.toString(), titleText.toString())

      } // updateDisplay

      override fun onIsPlayingChanged(isPlaying: Boolean)
      {
         if (isPlaying)
            {
               // User resumed play from saved state; nothing to do here
            }
         else
            {
               // User paused; save state for later resume.
               viewModel.savePlaylistCounts(
                  index = mediaController!!.currentMediaItemIndex,
                  pos = mediaController!!.currentPosition)
            }
      } // onIsPlayingChanged
      
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int)
      // This is called when media items are _added_ to the playlist,
      // in _addition_ to when it starts playing or moves to the next song.
      {
         super.onMediaItemTransition(mediaItem, reason)

         utils.debugLog("onMediaItemTransition $reason")
         
         if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            {
               // Called when a new item is added to the list, or an
               // item is replaced (ie, below); no display to update.
               // Apparently we avoid an infinite loop here.
               return
            }

         // Other reasons are:
         // Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT 
         // Player.MEDIA_ITEM_TRANSITION_REASON_AUTO>
         // Player.MEDIA_ITEM_TRANSITION_REASON_SEEK>
         // We don't support Repeat, so we need to update the display.
         
         if (mediaItem == null)
            {
               // Playlist ended. Just stop playing; leave display at
               // last song so user knows what's going on. We'd like
               // to set count.pos to end of song, but we've missed
               // that chance.
            }
         else
            {
               updateDisplay (saveState = true)                       
            }
      } // onMediaItemTransition

      override fun onPlayerError(error: PlaybackException)
      {
         val metadata = mediaController!!.currentMediaItem!!.mediaMetadata
         val songFile = File(metadata.extras!!.getString("Song_File")!!)

         utils.errorLog("cannot play '$songFile'")
      }
      
      override fun onTracksChanged(tracks: Tracks)
      {
         // WORKAROUND: the standard function for this does something wrong, which crashes the app.
         // Doing nothing here fixes it.
      }
      
   } // playerListener

   fun onClickNote(v : View)
   {
      val buttonText = (((v as Button).text as String).replace('\n', ' '))
      val controller = mediaController!!
      val index = controller.currentMediaItemIndex

      if (index > 0 && viewModel.playlistName.value != "")
         {
            val noteFileName = utils.notesFileName(viewModel.playlistName.value)
            val metaData = controller.currentMediaItem!!.mediaMetadata
            val writer = BufferedWriter(FileWriter(noteFileName, true)) // append
            val absSongFile = metaData.extras!!.getString("Song_File")!!
            val relSongFile = absSongFile.substring(utils.globalDirectory.length + 1) // no leading /
            writer.write("\"${relSongFile}\" $buttonText")
            writer.newLine()
            writer.close()
         }
   }
   
   private fun getTextViewTextScale() : Float
   {
      val prefs = PreferenceManager.getDefaultSharedPreferences(this)
      val scale : String = prefs.getString (
         this.getString(R.string.text_scale_key), this.getString(R.string.text_scale_default))!!

      try
      {
         return scale.toFloat()
      }
      catch (_ : NumberFormatException)
      {
         utils.errorLog("invalid text_scale preference: $scale")
         return 1.0f
      }
   }

   private fun scaleTextViews()
   {
      val scale = getTextViewTextScale()

      utils.findTextViewById(this, R.id.album).textSize = scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.albumArtist).textSize = scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.artist).textSize = 0.5f * scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.composer).textSize = 0.5f * scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.title).textSize = scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.title).textSize = scale * defaultTextViewTextSize
      utils.findTextViewById(this, R.id.year).textSize = 0.5f * scale * defaultTextViewTextSize
   }

   ////////// Activity lifetime methods (in lifecycle order)

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)

      PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
      PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)

      // viewModel is not destroyed when MainActivity is, for rotate,
      // memory recover, etc. So tell it mediaController is now null.
      viewModel.setMediaControllerReady(false)

      utils.appDirectory = this.getExternalFilesDir(null)!!.absolutePath

      createNotificationChannel()

      setContentView(R.layout.mainactivity)
      setSupportActionBar(findViewById(R.id.main_toolbar)) // for menu

      val categoryView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.category)
      categoryView.setContent {CategoryDisplay(viewModel = viewModel)}
      
      defaultTextViewTextSize = utils.findTextViewById(this, R.id.artist).textSize

      scaleTextViews()
      
      val playlistView = utils.findTextViewById(this, R.id.playlist)
      playlistView.setOnClickListener()
      {
         // lifecycleScope is ok here; the user has just clicked on an item in MainActivity UI.
         showPlaylistPickerDialog {
            filename ->
               lifecycleScope.launch{
                  playlistToPlayer(FilenameUtils.getBaseName(filename), play = true, saveState = true)}}
      }

      slideshow = findViewById(R.id.image_slideshow)
      imageSlideshowAdapter = ImageSlideshowAdapter(emptyList())
      slideshow.adapter = imageSlideshowAdapter

      // Wait for viewModel flows. 
      lifecycleScope.launch {
         repeatOnLifecycle(Lifecycle.State.STARTED) {

            launch {
               viewModel.errorMessage.collect {
                  message -> 
                     if (message != null)
                     {
                        AlertDialog.Builder(this@MainActivity)
                           .setTitle("Database or File Error")
                           .setMessage(message)
                           .setPositiveButton("OK") {
                              dialog, _ ->
                                 viewModel.clearError()
                              dialog.dismiss()}
                           .setCancelable(false)
                           .show()
                     }
               }
            }
            
            // Wait for mediaController to be ready, and the playlist name
            // to be read from DataStore.
            launch {
               viewModel.isPlayerReadyToInitialize.collect{
                  // called when it changes state
                  isReady ->
                     if (isReady)
                     {
                        if (mediaController == null)
                           utils.errorLog ("MainActivity.onCreate launch player: mediaController == null")
                        else
                           {
                              if (mediaController!!.isPlaying)
                                 {
                                    // UI was killed, but service is
                                    // still active, and has been
                                    // playing; update UI
                                    if (mediaController!!.currentMediaItem != null)
                                       {
                                          playerListener.updateDisplay (saveState = true)
                                       }
                                 }
                              else
                                 {
                                    // We don't know the state of the
                                    // media controller playlist;
                                    // restore it from scratch.
                                    val category : String = viewModel.playlistName.value
                                    
                                    if (category.isNotEmpty())
                                       {
                                          playlistToPlayer(category, play = false, saveState = false)
                                       }
                                 }
                           }
                     } 
               }
            }

            // Listen for ReloadPlaylist events (and others) from mainViewModel
            launch {
               AppEventBus.events.collect {
                  event: AppEvent ->
                     when (event) {
                        is AppEvent.ReloadPlaylist -> {
                           if (mediaController != null)
                              {
                                 // We only get this when the playlist
                                 // file corresponding to
                                 // viewModel.playlistName.value has
                                 // been edited, and the correct
                                 // counts saved. So don't overwrite
                                 // them with the outdated state of
                                 // the current mediaController playlist.
                                 playlistToPlayer(
                                    viewModel.playlistName.value,
                                    play = mediaController!!.isPlaying,
                                    saveState = false)
                              }
                        }
                     }
               }
            }
         }
      }

      checkPermission()
   } // onCreate

   override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?)
   {
      if (key == getString(R.string.text_scale_key))
           {
              scaleTextViews()
           }
    }
   
   // @OptIn(UnstableApi::class)
   override fun onStart()
   {
      super.onStart()

      lifecycleScope.launch {
         if (viewModel.controllerFuture == null)
            {
               val sessionToken = SessionToken(
                  this@MainActivity, ComponentName(this@MainActivity, PlayService::class.java))
               viewModel.controllerFuture = MediaController.Builder(this@MainActivity, sessionToken).buildAsync()
            }

         mediaController = viewModel.controllerFuture!!.await() // waits until the above async completes.

         mediaController!!.removeListener(playerListener)
         mediaController!!.addListener(playerListener)
         
         val playerView = findViewById<PlayerView>(R.id.player_view)
         playerView.setPlayer(mediaController)
         
         viewModel.setMediaControllerReady(true)
      }   
   } // onStart

   override fun onResume()
   {
      super.onResume()

      val playerView = findViewById<PlayerView>(R.id.player_view)
      playerView.onResume()
      if (imageSlideshowAdapter.itemCount > 1)
         startSlideshowTimer()
   }
   
   override fun onPause()
   {
      super.onPause()

      val playerView = findViewById<PlayerView>(R.id.player_view)
      playerView.onPause()
      stopSlideshowTimer()
   }
   
   override fun onStop()
   {
      super.onStop()
   }
   
   override fun onDestroy()
   {
      stopSlideshowTimer()
      mediaController?.removeListener(playerListener)
      PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
 
      super.onDestroy()
   }

   ////////// Menu

   override fun onCreateOptionsMenu(menu: Menu): Boolean
   {
      val inf: MenuInflater = menuInflater
      inf.inflate(R.menu.main_menu, menu)
      return true // display menu
   }

   override fun onPrepareOptionsMenu(menu: Menu): Boolean 
   {
      super.onPrepareOptionsMenu(menu)

      if (mediaController == null || mediaController!!.currentMediaItem == null)
         {
            // MenuItem doesn't declare isEnabled.
            @Suppress("UsePropertyAccessSyntax")
            menu.findItem(R.id.menu_liner_notes).setEnabled(false)
            return false
         }

      val metaData = mediaController!!.currentMediaItem!!.mediaMetadata
      val file = File(metaData.extras!!.getString("Liner_Notes")!!)
      @Suppress("UsePropertyAccessSyntax")
      menu.findItem(R.id.menu_liner_notes).setEnabled(file.exists())

      return true
   }

   override fun onOptionsItemSelected(item: MenuItem): Boolean
   {
      when (item.itemId)
      {
         // Alphabetical order

         R.id.menu_clean_playlist ->
            {
               // lifecycleScope is ok here; the user has just clicked
               // on an item in MainActivity UI.
               showPlaylistPickerDialog {
                  filename -> 
                     val category = FilenameUtils.getBaseName(filename)
                     lifecycleScope.launch {
                        DownloadUtils.cleanPlaylist(this@MainActivity, category)
                        utils.savePlaylistCounts(this@MainActivity, category, index = 0,
                                                 pos = utils.posDontSave, limit = utils.limitDontSave)
                        if (viewModel.playlistName.value == category)
                           // See comment at ReloadPlaylist.
                           playlistToPlayer(
                              viewModel.playlistName.value,
                              play = mediaController!!.isPlaying,
                              saveState = false)}
               }
            }
         
         R.id.menu_copy ->
            {
               val clipManage: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

               val album       : TextView = utils.findTextViewById(this, R.id.album)
               val albumArtist : TextView = utils.findTextViewById(this, R.id.albumArtist)
               val artist      : TextView = utils.findTextViewById(this, R.id.artist)
               val composer    : TextView = utils.findTextViewById(this, R.id.composer)
               val title       : TextView = utils.findTextViewById(this, R.id.title)
               
               val msg: String = albumArtist.text.toString() +
               " " + artist.text.toString() +
               " " + album.text.toString() +
               " " + title.text.toString() +
               " " + composer.text.toString()

               clipManage.setPrimaryClip (ClipData.newPlainText ("song", msg))
            }

         R.id.menu_db_sync ->
            {
               this.startService(Intent(utils.SYNC_DB_COMMAND, null, this, SyncService::class.java))
            }

         R.id.menu_db_sync_edit_state ->
            {
               startActivity(Intent(this, SyncStateActivity::class.java))
            }

         R.id.menu_db_init ->
            {
               this.startService(Intent(utils.INIT_DB_COMMAND, null, this, SyncService::class.java))
            }

         R.id.menu_db_resume_init ->
            {
               this.startService(Intent(utils.RESUME_INIT_DB_COMMAND, null, this, SyncService::class.java))
            }

         R.id.menu_liner_notes ->
            {
               val metaData = mediaController!!.currentMediaItem!!.mediaMetadata
               utils.viewPDF(this,metaData.extras!!.getString("Liner_Notes")!!)
            }

         R.id.menu_new_playlist ->
            {
               val prefs   : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
               val serverIP: String?           = prefs.getString (this.getString(R.string.server_IP_key), null)
               
               if (null == serverIP)
                  {
                     utils.alertLog(this, "set Server IP in preferences")
                  }
               else
                  {
                     // Get the playlist name, which is the song category;
                     // tell play service to download initial playlist.
                     
                     val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                     builder.setTitle("new playlist category")
                     
                     val dialog = layoutInflater.inflate(R.layout.dialog_new_playlist, null)
                     val categoryInput = dialog.findViewById<EditText>(R.id.new_playlist_category)
                     val noLimit = dialog.findViewById<CheckBox>(R.id.new_playlist_no_limit)
                     val limitInput = dialog.findViewById<EditText>(R.id.new_playlist_limit)

                     val defaultLimit = prefs.getString(getString(R.string.song_count_max_key), "50") ?: "50"
                     limitInput.setText(defaultLimit)

                     noLimit.setOnCheckedChangeListener {_, isChecked -> limitInput.visibility =
                                                            if (isChecked) View.GONE else View.VISIBLE}

                     builder.setView(dialog)
                     
                     builder.setPositiveButton ("OK")
                     {_, _ ->
                         val category = categoryInput.text.toString()

                      if (category.isNotEmpty())
                         {
                            val songLimit = if (noLimit.isChecked)
                            {
                              utils.playlistNoLimit
                            }
                            else
                               {
                                  limitInput.text.toString().toIntOrNull() ?: defaultLimit.toInt()
                               }

                            newPlaylistIntent = Intent (utils.DOWNLOAD_COMMAND, null, this, DownloadService::class.java)
                               .putExtra(utils.EXTRA_PLAYLIST_CATEGORY, category)
                               .putExtra(utils.EXTRA_PLAYLIST_LIMIT, songLimit)
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

         R.id.menu_preferences ->
            {
               // We don't need a result
               this.startActivity(Intent(this@MainActivity, PrefActivity::class.java))
            }
         
         R.id.menu_reset_playlist ->
            {
               // lifecycleScope is ok here; the user has just clicked
               // on an item in MainActivity UI.
               lifecycleScope.launch {
                  utils.savePlaylistCounts(this@MainActivity, viewModel.playlistName.value, 0, 0,
                                           limit = utils.limitDontSave)
                  // See comment at ReloadPlaylist
                  playlistToPlayer(
                     viewModel.playlistName.value,
                     play = mediaController!!.isPlaying,
                     saveState = false)}
            }

         R.id.menu_search ->
            {
               val dbFileName : String = utils.globalDirectory + "/smm.db"
               val dbFile = File(dbFileName)
               var errorMessage: String? = null
               
               if (!dbFile.exists())
                  errorMessage = "Database file '${dbFileName}' does not exist; install it."
               else if (!dbFile.isFile)
                   errorMessage = "Database file '${dbFileName}' is a directory, not a file."
               else if (!dbFile.canRead())
                   errorMessage = "Database file '${dbFileName}' is not readable (not clear why)."

                if (errorMessage == null)
                   startActivity(Intent(this, SearchActivity::class.java))
               else
                  utils.alertLog(this, "Database file '${dbFileName}' does not exist; check preference setting.")
            }

         R.id.menu_search_spotify ->
            {
               val metadata = mediaController!!.currentMediaItem!!.mediaMetadata
               val titleText = metadata.title ?: ""
               val albumArtistText = metadata.albumArtist ?: ""
               val query = "$albumArtistText $titleText"
               val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                  putExtra(SearchManager.QUERY, query)
                  setPackage("com.spotify.music")
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
               }

               try {
                  startActivity(intent)
               } catch (_: ActivityNotFoundException) {
                  utils.errorLog("Spotify is not installed.")
               }
            }
            
         R.id.menu_show_debug_log ->
            {
               startActivity(utils.showLogIntent(this@MainActivity, utils.debugLogFileName()))
            }

         R.id.menu_show_download_log ->
            {
               startActivity(utils.showLogIntent(this@MainActivity, DownloadUtils.downloadLogFileName()))
            }

         R.id.menu_show_error_log ->
            {  
               startActivity(
                  Intent(Intent.ACTION_VIEW)
                     .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                     .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                     .setDataAndType(
                        FileProvider.getUriForFile(
                           this,
                           this.applicationContext.packageName + ".provider",
                           File(utils.errorLogFileName())),
                        "text/plain"))
            }

         R.id.menu_show_playlist ->
            { 
              startActivity(
                 Intent(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setDataAndType(
                       FileProvider.getUriForFile(
                          this,
                          this@MainActivity.applicationContext.packageName + ".provider",
                          File(utils.playlistFileName(viewModel.playlistName.value))),
                       "text/plain"))
            }

         R.id.menu_show_sync_log ->
            {
               startActivity(utils.showLogIntent(this@MainActivity, syncUtils.syncLogFileName()))
            }

         R.id.menu_update_playlist ->
            {
               val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
               val serverIP: String?        = prefs.getString (this.getString(R.string.server_IP_key), null)

               if (null == serverIP)
                  utils.alertLog(this, "set Server IP in preferences")
               else
                  {
                     showPlaylistPickerDialog {
                        filename ->
                           updatePlaylistIntent = Intent (
                              utils.DOWNLOAD_COMMAND, null, this, DownloadService::class.java)
                           .putExtra(utils.EXTRA_PLAYLIST_CATEGORY, FilenameUtils.getBaseName(filename))
                        
                        this.startService(updatePlaylistIntent)
                     }
                  }
            }

         else ->
            {
               Log.e(utils.logTag, "activity.onOptionsItemSelected: unknown MenuItemId $item.itemId")
            }
      }
      return false // continue menu processing
   } // onOptionsItemSelected

}
