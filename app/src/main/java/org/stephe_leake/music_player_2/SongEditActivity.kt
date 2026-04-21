//  Copyright (C) 2025 Stephen Leake. All Rights Reserved.
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

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.launch

class SongEditActivity : ComponentActivity()
{
   private val viewModel: SongEditViewModel by viewModels {
      SongEditViewModel.Companion.SongEditViewModelFactory(
         (application as MusicPlayerApplication).db.songDao())}

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      val id = this@SongEditActivity.intent.getIntExtra("SONG_ID", -1)
      viewModel.loadSong(id)

      setContent {
         val isLoaded by viewModel.isSongLoaded.collectAsState(initial = false)

         if (isLoaded)
           {
            if (viewModel.isValid())
               {
                SongEditScreen(viewModel, onSaveSuccess = { finish() })
            }
            else
               {
                  LaunchedEffect(Unit) {
                    utils.alertLog(this@SongEditActivity, "Song $id not found")
                    finish()}
               }
           }
        else
           {
              // Show a loading indicator while the database works
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()}
           }
      }
   }

   suspend fun safeSave(onSaveSuccess: () -> Unit)
   {
      try
      {
         viewModel.save()
         onSaveSuccess()
      }
      catch (e: SQLiteConstraintException)
      {
         // Probably from user editing an index field to match an existing song
         utils.alertLog(this@SongEditActivity, e.message ?: "Database constraint violation")
      }
   }
}

@OptIn(ExperimentalMaterial3Api::class) 
@Composable
fun SongEditScreen(viewModel: SongEditViewModel, onSaveSuccess: () -> Unit)
{
   val song = viewModel.songState ?: return
   val scope = rememberCoroutineScope()
   val context = LocalContext.current as SongEditActivity

   Scaffold(
      topBar = {
         TopAppBar(
            title = { Text("Edit Song: ${song.ID}") },
            actions ={IconButton(onClick = {scope.launch {context.safeSave(onSaveSuccess)}})
                      {Icon(Icons.Default.Save, contentDescription = "Save")}})})
   { padding ->
        Column(
           modifier = Modifier
              .padding(padding)
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
           // Non-editable display fields
           Text("ID: ${song.ID}", style = MaterialTheme.typography.bodySmall)
           Text("Modified: ${song.Modified}", style = MaterialTheme.typography.bodySmall)
           
           HorizontalDivider()

           // Editable Fields
           SongTextField("Last_Downloaded", song.Last_Downloaded ?: "") {text ->
              viewModel.updateField { s -> s.copy(Last_Downloaded = text.ifBlank{null}) } }
           SongTextField("Prev_Downloaded", song.Prev_Downloaded ?: "") {text ->
              viewModel.updateField { s -> s.copy(Prev_Downloaded = text.ifBlank{null}) } }
           SongTextField("Title", song.Title) {text -> viewModel.updateField { s -> s.copy(Title = text) } }
           SongTextField("Artist", song.Artist ?: "") {text ->
              viewModel.updateField { s -> s.copy(Artist = text.ifBlank{null}) } }
           SongTextField("Album", song.Album ?: "") {text ->
              viewModel.updateField { s -> s.copy(Album = text.ifBlank{null}) } }
           SongTextField("File Name", song.File_Name) {text ->
              viewModel.updateField { s -> s.copy(File_Name = text) } }
           SongTextField("Category", song.Category) {text ->
              viewModel.updateField { s -> s.copy(Category = text) } }
           
           SongTextField("Track", song.Track?.toString() ?: "") {text -> 
              val value = text.toIntOrNull()
              viewModel.updateField { s -> s.copy(Track = value)}} 
           
           SongTextField("Play_Before", song.Play_Before?.toString() ?: "") {text -> 
              val value = text.toIntOrNull()
              viewModel.updateField { s -> s.copy(Play_Before = value)}} 
           
           SongTextField("Play_After", song.Play_After?.toString() ?: "") {text -> 
              val value = text.toIntOrNull()
              viewModel.updateField { s -> s.copy(Play_After = value)}}
        }
   }
}

@Composable
fun SongTextField(label: String, value: String, onValueChange: (String) -> Unit)
{
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
