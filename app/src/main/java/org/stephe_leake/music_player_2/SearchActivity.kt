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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SearchActivity : ComponentActivity()
{
   private val db by lazy { SongDatabase.getDatabase(this) }
   private val viewModel: SearchViewModel by viewModels {
      object : androidx.lifecycle.ViewModelProvider.Factory {
         @Suppress("UNCHECKED_CAST")
         // This is always safe here because this is a locally
         // declared anonymous factory. But the compiler doesn't know it.
         override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(db.songDao()) as T
         }
      }
   }

   @kotlinx.coroutines.ExperimentalCoroutinesApi
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContent {SearchScreen(viewModel)}
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
@Composable
fun SearchScreen(viewModel: SearchViewModel)
{
   var selectedTabIndex by remember { mutableStateOf(0) }
   val tabs = listOf("General Search", "Detailed Search")

   val generalQuery by viewModel.generalQuery.collectAsState<String>()
   val generalResults by viewModel.generalSearchResults.collectAsState<List<Song>>()

   val detailedTitle by viewModel.detailedTitle.collectAsState<String>()
   val detailedArtist by viewModel.detailedArtist.collectAsState<String>()
   val detailedAlbum by viewModel.detailedAlbum.collectAsState<String>()
   val detailedAlbumArtist by viewModel.detailedAlbumArtist.collectAsState<String>()
   val detailedComposer by viewModel.detailedComposer.collectAsState<String>()
   val detailedCategory by viewModel.detailedCategory.collectAsState<String>()
   val detailedResults by viewModel.detailedSearchResults.collectAsState<List<Song>>()
   
   Scaffold { padding ->
                 Column(modifier = Modifier.padding(padding)) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                       tabs.forEachIndexed { index, title ->
                                                Tab(
                                                   selected = selectedTabIndex == index,
                                                   onClick = { selectedTabIndex = index },
                                                   text = { Text(title) }
                                                )
                       }
                    }
                    
                    when (selectedTabIndex) {
                       0 -> Column {
                          SearchField(
                             value = generalQuery,
                             onValueChange = viewModel::onGeneralQueryChange,
                             label = "Search..."
                          )
                          HorizontalDivider()
                          SearchResults(results = generalResults)
                       }
                       1 -> Column {
                          DetailedSearchFields(
                             title = detailedTitle, onTitleChange = { viewModel.detailedTitle.value = it },
                             artist = detailedArtist, onArtistChange = { viewModel.detailedArtist.value = it },
                             album = detailedAlbum, onAlbumChange = { viewModel.detailedAlbum.value = it },
                             albumArtist = detailedAlbumArtist, onAlbumArtistChange = {
                                viewModel.detailedAlbumArtist.value = it },
                             composer = detailedComposer, onComposerChange = { viewModel.detailedComposer.value = it },
                             category = detailedCategory, onCategoryChange = { viewModel.detailedCategory.value = it }
                          )
                          HorizontalDivider()
                          SearchResults(results = detailedResults)
                       }
                    }
                 }
   }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, label: String)
{
    OutlinedTextField(
       value = value,
       onValueChange = onValueChange,
       label = { Text(label) },
       modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
       singleLine = true
    )
}

@Composable
fun DetailedSearchFields(
   title: String, onTitleChange: (String) -> Unit,
   artist: String, onArtistChange: (String) -> Unit,
   album: String, onAlbumChange: (String) -> Unit,
   albumArtist: String, onAlbumArtistChange: (String) -> Unit,
   composer: String, onComposerChange: (String) -> Unit,
   category: String, onCategoryChange: (String) -> Unit
)
{
   LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
      item { SearchField(value = title, onValueChange = onTitleChange, label = "Title") }
      item { SearchField(value = artist, onValueChange = onArtistChange, label = "Artist") }
      item { SearchField(value = album, onValueChange = onAlbumChange, label = "Album") }
      item { SearchField(value = albumArtist, onValueChange = onAlbumArtistChange, label = "Album Artist") }
      item { SearchField(value = composer, onValueChange = onComposerChange, label = "Composer") }
      item { SearchField(value = category, onValueChange = onCategoryChange, label = "Category") }
   }
}

@Composable
fun SearchResults(results: List<Song>)
{
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    )
    {
       if (results.isEmpty())
          {
          item {
             Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No results found.")
             }
          }
        } else {
             items(results) { song ->
                                 SongItem(song)
                              Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SongItem(song: Song)
{
   Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp)) {
         if (song.Title != null)
            Text(song.Title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
         
         if (song.Album_Artist != null)
            Text("by ${song.Album_Artist}", style = MaterialTheme.typography.bodyMedium)

         if (song.Album != null)
            Text("from ${song.Album}", style = MaterialTheme.typography.bodySmall)

         if (song.Category != null)
            Text("Category: ${song.Category}", style = MaterialTheme.typography.bodySmall)
      }
   }
}
