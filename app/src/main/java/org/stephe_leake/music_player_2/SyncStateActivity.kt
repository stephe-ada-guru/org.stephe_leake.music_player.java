package org.stephe_leake.music_player_2

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncStateActivity : AppCompatActivity()
{
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.sync_state_activity)

      val syncIdInput = findViewById<EditText>(R.id.edit_sync_id)
      val syncTimeInput = findViewById<EditText>(R.id.edit_sync_time)
      val saveButton = findViewById<Button>(R.id.edit_sync_save)

      // Load the current values and populate the EditText fields
      lifecycleScope.launch {
         val currentPrefs = playlistPrefsState.data.first()
         val currentSyncId = currentPrefs[PlaylistCountsPreferenceKeys.syncIdKey]
         val currentSyncTime = currentPrefs[PlaylistCountsPreferenceKeys.syncTimeKey]
         
         syncIdInput.setText(currentSyncId?.toString() ?: "")
         syncTimeInput.setText(currentSyncTime ?: "")
      }

      saveButton.setOnClickListener {
         saveSyncState(
            syncIdInput.text.toString(),
            syncTimeInput.text.toString())
        }
    }

    private fun saveSyncState(newId: String, newTime: String)
    {
        lifecycleScope.launch {
            playlistPrefsState.edit { prefs ->
               if (newId.isBlank()) {
                  prefs.remove(PlaylistCountsPreferenceKeys.syncIdKey)
               } else {
                  prefs[PlaylistCountsPreferenceKeys.syncIdKey] = Integer.decode (newId)
               }

            if (newTime.isBlank()) {
               prefs.remove(PlaylistCountsPreferenceKeys.syncTimeKey)
            } else {
               prefs[PlaylistCountsPreferenceKeys.syncTimeKey] = newTime
            }
            }
            finish() // Close the activity after saving
        }
    }
}
