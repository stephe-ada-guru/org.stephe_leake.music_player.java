//  Abstract :
//
//  misc stuff
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
import android.app.PendingIntent;
import android.content.Context;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.PrintWriter;
import java.lang.RuntimeException;

public class utils
{

   public static final String preferencesName = "stephes_music";

   // Must be shorter than 23 chars
   public static final String logTag =
      "stephes_music";
   //  1        10        20 |

   //  Notification ids; all with null tag
   public static final int notif_play_id     = 1;
   public static final int notif_download_id = 2;


   //  Messages to user views, sent via Intent. Alphabetical order
   public static final String META_CHANGED = "org.stephe_leake.android.stephes_music.metachanged";
   //  get artist, album, title, albumArt from utils.retriever.
   //  extras:
   //  duration string (milliseconds)
   //  playlist string (name pos/count)

   public static final String PLAYSTATE_CHANGED = "org.stephe_leake.android.stephes_music.playstatechanged";
   //  playing  boolean
   //  position int (milliseconds)

   //  Commands to server via Intent actions sent via broadcast. Alphabetical order
   //  Only one action, so we can add commands without adding to the reciever filter.
   public static final String ACTION_COMMAND = "org.stephe_leake.android.stephes_music.action.command";
   // according to android docs, extra field names must inlude the package prefix (no explanation of why)
   public static final String EXTRA_COMMAND = "org.stephe_leake.android.stephes_music.extra.command";
   public static final String EXTRA_COMMAND_POSITION =
      "org.stephe_leake.android.stephes_music.action.command_position";
   public static final String EXTRA_COMMAND_PLAYLIST =
      "org.stephe_leake.android.stephes_music.action.command_playlist";

   // values for extras; alphabetical. We'd like to use an enum here,
   // but we can't make that parcelable for intent extras.
   public static final int COMMAND_RESERVED = 1; // something sends this to PlayService!

   public static final int COMMAND_DOWNLOAD           = 2;
   public static final int COMMAND_DUMP_LOG           = 3;
   public static final int COMMAND_JUMP               = 4;
   public static final int COMMAND_NEXT               = 5;
   public static final int COMMAND_NOTE               = 6;
   public static final int COMMAND_PAUSE              = 7;
   public static final int COMMAND_PLAY               = 8;
   public static final int COMMAND_PLAYLIST           = 9; // playlist  string (abs file name)
   public static final int COMMAND_PREVIOUS           = 10;
   public static final int COMMAND_QUIT               = 11;
   public static final int COMMAND_RESET_PLAYLIST     = 12;
   public static final int COMMAND_SAVE_STATE         = 13;
   public static final int COMMAND_SEEK               = 14; // position  int (milliseconds)
   public static final int COMMAND_PLAYLIST_DIRECTORY = 15;
   public static final int COMMAND_SMM_DIRECTORY      = 17;
   public static final int COMMAND_TOGGLEPAUSE        = 18;
   public static final int COMMAND_UPDATE_DISPLAY     = 19;

   // sub-activity result codes
   public static final int RESULT_TEXT_SCALE         = Activity.RESULT_FIRST_USER + 1;
   public static final int RESULT_PLAYLIST_DIRECTORY = Activity.RESULT_FIRST_USER + 2;
   public static final int RESULT_SMM_DIRECTORY      = Activity.RESULT_FIRST_USER + 3;

   ////////// Shared objects

   public static MetaData retriever;

   public static String playlistDirectory;
   // Absolute path to directory where playlist files reside. The
   // list of available playlists consists of all .m3u files in
   // this directory.
   //
   // Set from playlist file passed to playList, or by preferences

   public static String smmDirectory;
   // Absolute path to directory containing files used to interface
   // with Stephe's Music manager (smm); contains .last files, notes
   // files.
   //
   // Set by preferences.

   public static PendingIntent activityIntent;
   // For notifications.

   ////////// methods

   public static TextView findTextViewById (Activity a, int id)
   {
      final View v = a.findViewById(id);

      if (v == null) throw new RuntimeException("no such id " + id);

      if (v instanceof TextView)
      {
         return (TextView)v;
      }
      else
      {
         throw new RuntimeException(id + " is not a TextView; it is a " + v.toString());
      }
   }

   public static String makeTimeString(Context context, long millisecs)
   {
      final Time time     = new Time();
      final long oneHour  = 3600 * 1000; // milliseconds
      final String format = context.getString
         (millisecs < oneHour ? R.string.durationformatshort : R.string.durationformatlong);

      time.set(millisecs);
      return time.format(format);
   }

   static class LogEntry
   {
      Object item;

      LogEntry(Object o) {item = o;}

      void dump(PrintWriter out)
      {
         if (item instanceof Exception)
         {
            out.println(item);
            ((Exception)item).printStackTrace(out);
         }
         else
         {
            out.println(item);
         }
      }
   }

   private static LogEntry[] log = new LogEntry[100];

   private static int logNext = 0;

   public static void debugClear()
   {
      for (int i = 0; i < log.length; i++)
      {
         log[i] = null;
      }
      logNext = 0;
   }

   public static void debugLog(Object o)
   {
      // Cache error messages to be dumped by debugDump, which is
      // called by 'adb shell dumpsys activity service ...service' and
      // activity menu "dump log".
      //
      // However, this log disappears if the service dies. FIXME: need
      // 'dump log on service die' option.
      //
      // If 'o' is an Exception, the dump will include a stack trace.

      log[logNext++] = new LogEntry(o);
      if (logNext >= log.length)
      {
         logNext = 0;
      }
   }

   public static void debugDump(PrintWriter out)
   {
      // No point in catching exceptions here, we can't report them
      // (no Context, so no toasts).
      for (int i = 0; i < log.length; i++)
      {
         int idx = (logNext + i);
         if (idx >= log.length)
         {
            idx -= log.length;
         }
         LogEntry entry = log[idx];
         if (entry != null)
         {
            entry.dump(out);
         }
      }
   }

   public static void errorLog(Context context, String msg, Throwable e)
   {
      // programmer errors (possibly due to Android bugs :)
      Log.e(logTag, msg, e);
      Toast.makeText(context, msg + e.toString(), Toast.LENGTH_LONG).show();
   }

   public static void errorLog(Context context, String msg)
   {
      // programmer errors (possibly due to Android bugs :)
      Log.e(logTag, msg);
      Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
   }

   static void infoLog(Context context, String msg)
   {
      // helpful user messages, ie "could not play"; displayed for a short time.
      Log.i(logTag, msg);
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
   }

   public static void alertLog(Context context, String msg)
   {
      // Messages containing info user needs time to read; requires explicit dismissal.
      //
      // Cannot be called from a service
      Log.i(logTag, msg);
      new AlertDialog.Builder(context).setMessage(msg).setPositiveButton(R.string.Ok, null).show();
   }

   public static void alertLog(Context context, String msg, Throwable e)
   {
      // Messages containing info user needs time to read; requires explicit dismissal.
      //
      // Cannot be called from a service
      Log.e(logTag, msg);
      new AlertDialog.Builder(context).setMessage(msg + e.toString()).setPositiveButton(R.string.Ok, null).show();
   }

   static void verboseLog(String msg)
   {
      if (BuildConfig.DEBUG) Log.v(logTag, msg);
   }

}
