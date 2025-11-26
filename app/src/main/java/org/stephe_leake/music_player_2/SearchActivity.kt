//  Abstract :
//
//  Interface to the sqlite database
//
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

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity()
{
   // "by viewModels" returns a singleton (shared with MainActivity),
   // using the factory if necessary.
   private val viewModel: SearchViewModel by viewModels {
      SearchViewModel.Companion.SearchViewModelFactory(
         (application as MusicPlayerApplication).db.songDao())}

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContent {SearchScreen(viewModel)}
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel)
{
   val groupedResults by viewModel.groupedResults.collectAsState()
   var selectedTab by remember { mutableStateOf(0) }
   val tabs = listOf("General Search", "Detailed Search")

   Column() {
      TabRow(selectedTabIndex = selectedTab) {
         tabs.forEachIndexed { index, title ->
                                  Tab(
                                     selected = selectedTab == index,
                                     onClick = { selectedTab = index },
                                     text = { Text(title) }
                                  )
         }
      }
      
      when (selectedTab) {
         0 -> GeneralSearchTab(onSearch = { query -> viewModel.performGeneralSearch(query) })
         1 -> DetailedSearchTab(onSearch = { info -> viewModel.performDetailedSearch(info) })
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      SearchResults(viewModel)
   }
}

@Composable
fun GeneralSearchTab(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Row(modifier = Modifier.padding(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search...") },
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onSearch(query) }, modifier = Modifier.padding(start = 8.dp)) {
            Text("Go")
        }
    }
}

@Composable
fun DetailedSearchTab(onSearch: (DetailedInfo) -> Unit)
{
   var query by remember { mutableStateOf(DetailedInfo()) }
   Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
   ) {
      SearchField(value = query.title, onValueChange = { query = query.copy(title = it) }, label = "Title")
      SearchField(value = query.artist, onValueChange = { query = query.copy(artist = it) }, label = "Artist")
      SearchField(value = query.album, onValueChange = { query = query.copy(album = it) }, label = "Album")
      SearchField(value = query.albumArtist, onValueChange = { query = query.copy(albumArtist = it) }, label = "Album Artist")
      SearchField(value = query.composer, onValueChange = { query = query.copy(composer = it) }, label = "Composer")
      SearchField(value = query.category, onValueChange = { query = query.copy(category = it) }, label = "Category")

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      Button(onClick = { onSearch(query) }, modifier = Modifier.padding(start = 8.dp)) {
         Text("Go")
      }
   }
}

@Composable
private fun SearchField(label: String, value: String, onValueChange: (String) -> Unit)
{
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
           .fillMaxWidth()
        // There's a lot of wasted space because of Material Design 3.
        // Setting height smaller than the default (56) hides the
        // user's text. Apparently there is no way to say "use less
        // padding". Sigh.
           .height(56.dp),
        singleLine = true
    )
}

@Composable
fun SearchResults(viewModel: SearchViewModel) {
    // Collect the grouped results from the ViewModel
    val groupedResults by viewModel.groupedResults.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Iterate through each album group in the map
        items(groupedResults.entries.toList()) { (albumInfo, songs) ->
            AlbumGroup(albumInfo, songs, viewModel)
        }
    }
} // end SearchResults

@Composable
fun AlbumGroup(albumInfo: AlbumInfo, songs: List<Song>, viewModel: SearchViewModel) {
   var expandedSong by remember {mutableStateOf<Int?>(null)}
   
   Card(
      modifier = Modifier
         .fillMaxWidth()
         .padding(horizontal = 8.dp, vertical = 4.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
   ) {
      Column(modifier = Modifier.padding(8.dp)) {
         AlbumHeader(albumInfo)
         Spacer(modifier = Modifier.height(8.dp))
         HorizontalDivider()
         Spacer(modifier = Modifier.height(8.dp))

         songs.forEach {
            song ->
               SongRow(
                  song,
                  viewModel,
                  isExpanded = song.ID == expandedSong,
                  onRowClick = {
                     // Toggle expansion: if it's already expanded, collapse it. Otherwise, expand it.
                     expandedSong = if (song.ID == expandedSong) null else song.ID
               })
            Spacer(modifier = Modifier.height(4.dp))
         }
      }
   }
} // end albumGroup

@Composable
fun AlbumHeader(albumInfo: AlbumInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = albumInfo.name ?: "<unknown album>",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${albumInfo.artist ?: ""} ${albumInfo.year ?: ""}".trim(),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        // Not displayling album art images here; no room on phone screen.
    }
} // end AlbumHeader

@Composable
fun SongRow(
   song: Song,
   viewModel: SearchViewModel,
   isExpanded: Boolean,
   onRowClick: () -> Unit) {

   // These names are actually reversed. Sigh.
   val darkBackground = MaterialTheme.colorScheme.surface 
   val lightBackground = MaterialTheme.colorScheme.surfaceVariant // A different color

   val maxLines = if (isExpanded) Int.MAX_VALUE else 1

   val coroutineScope = rememberCoroutineScope()
   Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
         .clickable {onRowClick()} ) {
      
      Icon(
         imageVector = Icons.Default.PlayArrow,
         contentDescription = "Play ${song.Title}",
         modifier = Modifier
            .padding(horizontal = 8.dp).background(lightBackground)
            .clickable {coroutineScope.launch {AppEventBus.emitEvent(AppEvent.PlaySong(song))}}
            ) 

      // Using weights to create table-like columns. Include padding
      // in text fields so it can be squashed on the phone.
      Text(
         text = song.Artist ?: "",
         modifier = Modifier.weight(1.5f).background(darkBackground).padding(horizontal = 4.dp),
         maxLines = maxLines,
         overflow = TextOverflow.Ellipsis
      )
      
      Text(
         text = song.Composer ?: "",
         modifier = Modifier.weight(1.5f).background(lightBackground).padding(horizontal = 4.dp),
         maxLines = maxLines,
         overflow = TextOverflow.Ellipsis
      )
      
      Text(
         text = song.Title ?: "",
         modifier = Modifier.weight(2f).background(darkBackground).padding(horizontal = 4.dp),
         maxLines = maxLines,
         overflow = TextOverflow.Ellipsis
      )

      EditableText(
         initialValue = song.Category ?: "",
         onSave = {category -> viewModel.updateSong(song.copy(Category = category))},  
         modifier = Modifier.weight(1.5f).background(lightBackground))
      
      // FIXME: add play before/after, with edit
   } // end Row 1

   if (isExpanded)
      Row {Text(text = song.File_Name ?: "", maxLines = maxLines)}

} // end SongRow

@Composable
fun EditableText(
    initialValue: String,
    onSave: (String) -> Unit, 
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(initialValue) }

    if (isEditing) {
        // --- Edit Mode ---
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input field
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            // Save button
            IconButton(onClick = {
                onSave(text)
                isEditing = false
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
            // Cancel button
            IconButton(onClick = {
                // Reset text to its original value and exit edit mode
                text = initialValue
                isEditing = false
            }) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    } else {
        // --- Display Mode ---
        Text(
            text = initialValue.ifEmpty { "[empty]" }, // Show placeholder for empty text
            modifier = modifier
                .fillMaxWidth()
                .clickable { isEditing = true } // Click to enter edit mode
                .padding(vertical = 16.dp) // Add padding to make it easier to click
        )
    }
} // end EditableText

// end of file
