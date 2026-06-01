//  Abstract :
//
//  Show sync-db progress.
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

package org.stephe_leake.music_player_2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun truncate(msg: String): String
{
   // Don't let the status message overflow the screen. In an error
   // message, the first part indicates what the JSON message is, the
   // last part gives the actual error.
   if (msg.length <= 200) return msg
   return msg.take(100) + "\n...\n" + msg.takeLast(100)
}

class SyncProgressActivity : AppCompatActivity()
{
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.sync_progress_activity)

      val statusView    = findViewById<TextView>(R.id.sync_status_text)
      val cancelButton  = findViewById<Button>(R.id.sync_cancel_button)
      val showLogButton = findViewById<Button>(R.id.sync_show_log_button)
      val dismissButton = findViewById<Button>(R.id.sync_dismiss_button)

      cancelButton.setOnClickListener {
         sendBroadcast(Intent(this, MPBroadcastReceiver::class.java).apply {
            action = utils.COMMAND_CANCEL_SYNC_DB
         })
      }

      showLogButton.setOnClickListener {
         startActivity(utils.showLogIntent(this, syncUtils.syncLogFileName()))
      }

      dismissButton.setOnClickListener { finish() }

      lifecycleScope.launch {
         SyncStatusBus.status.collectLatest { status ->
            when (status)
            {
               is SyncStatus.Idle ->
                  {
                     statusView.text = "Starting..."
                     cancelButton.visibility = View.VISIBLE
                     dismissButton.visibility = View.GONE
                  }
               is SyncStatus.Progress ->
                  {
                     statusView.text = truncate(status.label)
                     cancelButton.visibility = View.VISIBLE
                     dismissButton.visibility = View.GONE
                  }
               is SyncStatus.Done ->
                  {
                     statusView.text = if (status.msg.isEmpty()) "Done" else truncate("Done: ${status.msg}")
                     cancelButton.visibility = View.GONE
                     dismissButton.visibility = View.VISIBLE
                  }
               is SyncStatus.Error ->
                  {
                     statusView.text = truncate(status.msg)
                     cancelButton.visibility = View.GONE
                     dismissButton.visibility = View.VISIBLE
                  }
            }
         }
      }
   }
}
