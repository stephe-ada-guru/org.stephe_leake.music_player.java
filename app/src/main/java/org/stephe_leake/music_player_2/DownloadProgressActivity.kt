//  Abstract :
//
//  Show update-playlist progress.
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

class DownloadProgressActivity : AppCompatActivity()
{
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.download_progress_activity)

      val statusView    = findViewById<TextView>(R.id.download_status_text)
      val cancelButton  = findViewById<Button>(R.id.download_cancel_button)
      val showLogButton = findViewById<Button>(R.id.download_show_log_button)
      val dismissButton = findViewById<Button>(R.id.download_dismiss_button)
      val syncDbButton  = findViewById<Button>(R.id.download_sync_db_button)

      cancelButton.setOnClickListener {
         sendBroadcast(Intent(this, MPBroadcastReceiver::class.java).apply {
            action = utils.COMMAND_CANCEL_DOWNLOAD
         })
      }

      showLogButton.setOnClickListener {
         startActivity(utils.showLogIntent(this, DownloadUtils.downloadLogFileName()))
      }

      dismissButton.setOnClickListener { finish() }

      syncDbButton.setOnClickListener {
         startService(Intent(utils.SYNC_DB_COMMAND, null, this, SyncService::class.java))
         startActivity(Intent(this, SyncProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
         })
      }

      lifecycleScope.launch {
         DownloadStatusBus.status.collectLatest { status ->
            when (status)
            {
               is DownloadStatus.Idle ->
                  {
                     statusView.text = "Starting..."
                     cancelButton.visibility = View.VISIBLE
                     dismissButton.visibility = View.GONE
                     syncDbButton.visibility  = View.GONE
                  }
               is DownloadStatus.Progress ->
                  {
                     statusView.text = status.label
                     cancelButton.visibility = View.VISIBLE
                     dismissButton.visibility = View.GONE
                     syncDbButton.visibility  = View.GONE
                  }
               is DownloadStatus.Done ->
                  {
                     statusView.text = if (status.msg.isEmpty()) "Done" else "Done: ${status.msg}"
                     cancelButton.visibility = View.GONE
                     dismissButton.visibility = View.VISIBLE
                     syncDbButton.visibility  = View.VISIBLE
                  }
               is DownloadStatus.Error ->
                  {
                     statusView.text = status.msg
                     cancelButton.visibility = View.GONE
                     dismissButton.visibility = View.VISIBLE
                     syncDbButton.visibility  = View.GONE
                  }
            }
         }
      }
   }
}
