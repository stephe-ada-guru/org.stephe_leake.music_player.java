//  Abstract :
//
//  Utilities for downloading from smm_server
//
//  Copyright (C) 2016, 2017 Stephen Leake. All Rights Reserved.
//
//  This program is free software; you can redistribute it and/or
//  modify it under terms of the GNU General Public License as
//  published by the Free Software Foundation; either version 3, or (at
//  your option) any later version. This program is distributed in the
//  hope that it will be useful, but WITHOUT ANY WARRANTY; without even
//  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//  PURPOSE. See the GNU General Public License for more details. You
//  should have received a copy of the GNU General Public License
//  distributed with this program; see file COPYING. If not, write to
//  the Free Software Foundation, 51 Franklin Street, Suite 500, Boston,
//  MA 02110-1335, USA.

package org.stephe_leake.android.stephes_music;

import android.media.MediaScannerConnection;
import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;

public class DownloadUtils
{
   private static final int BUFFER_SIZE = 8 * 1024;

   // used in processDirEntry
   private static String       playlistDir;
   private static List<String> mentionedFiles;

   private static OkHttpClient httpClient = null;

   final private static String logFileExt = ".txt";
   public static String logFileName()
   {
      return utils.smmDirectory + "/download_log" + logFileExt;
   }

   public static LogLevel prefLogLevel = LogLevel.Info;

   public static void log(Context context, LogLevel level, String msg)
   {
      if (level.toInt() >= prefLogLevel.toInt())
      {
         final SimpleDateFormat fmt         = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss : ", Locale.US);
         final long             time        = System.currentTimeMillis(); // local time zone
         final String           timeStamp   = fmt.format(time);
         final String           logFileName = DownloadUtils.logFileName();
         String                 levelImg    = "";

         switch (level)
         {
         case Info:
            levelImg = "";
            break;

         case Error:
            levelImg = "ERROR: ";
            break;
         }

         {
            File logFile = new File(logFileName);
            if (logFile.exists() && time - logFile.lastModified() > 4 * utils.millisPerHour)
            {
               final String oldLogFileName = utils.smmDirectory + "/download_log_1" + logFileExt;
               File oldLogFile = new File(oldLogFileName);

               if (oldLogFile.exists()) oldLogFile.delete();

               logFile.renameTo(oldLogFile);
            }
         }

         try
         {
            PrintWriter writer = new PrintWriter(new FileWriter(logFileName, true)); // append

            writer.println(timeStamp + levelImg + msg);
            writer.close();
         }
         catch (java.io.IOException e)
         {
            if (null != context)
               // null in unit tests
               utils.errorLog(context, "can't write log to " + logFileName(), e);
         }
      }
   }

   private static List<String> readPlaylist(String playlistFilename, boolean lowercase)
      throws IOException
   {
      // Read playlist file, return list of files (lowercase) in it.
      File         playlistFile = new File(playlistFilename);
      LineIterator i;

      LinkedList<String> result = new LinkedList<>();

      for (i = FileUtils.lineIterator(playlistFile); i.hasNext();)
      {
         String line = i.next();
         result.addLast(lowercase ? line.toLowerCase() : line);
      }
      i.close();
      return result;
   }

   public static int editPlaylist(Context context, String playlistFilename, String lastFilename)
      throws IOException
   // Delete lines from start of playlist file up to but not including
   // line in last file; that song is currently being played.
   //
   // Return delete count.
   //
   // throws IOException if can't read or write playlistFilename
   {
      int deleteCount = 0;

      try
      {
         List<String>     lines       = readPlaylist(playlistFilename, false);
         LineNumberReader input       = new LineNumberReader(new FileReader(lastFilename));
         String           lastPlayed  = input.readLine();
         boolean          found       = false;

         input.close();
         new File(lastFilename).delete();

         if (null == lastPlayed)
         {
            // last file is empty; nothing to delete
         }
         else
         {
            // Check if lastPlayed is in playlist
            for (String line : lines)
            {
               if (!found)
                  found = line.equals(lastPlayed);
            }

            if (found)
            {
               FileWriter output = new FileWriter(playlistFilename); // Erases file

               found = false;

               for (String line : lines)
               {
                  if (!found)
                  {
                     found = line.equals(lastPlayed);
                  }
                  if (found)
                     output.write(line + "\n");
                  else
                     deleteCount++;
               }
               output.close();
            }
         }
      }
      catch (FileNotFoundException e)
      { // from 'FileReader(lastFilename)'; file not found; same as empty; do nothing
      }

      return deleteCount;
   }

   private static String normalize(String item)
   {
      char[] temp = item.toCharArray();

      for (int i = 0; i < temp.length; i++)
         if (temp[i] == '\\')
            temp[i] = '/';

      return new String(temp);
   }

   private static String relativeName(String root, String full)
   {
      if (full.startsWith(root))
         return normalize(full.substring(root.length() + 1, full.length()));
      else
         return "";
   }

   private static int processDirEntry(Context context, File entry)
   // Returns count of files deleted
   {
      int entryCount  = 0;
      int deleteCount = 0;

      if (entry.isDirectory())
      {
         // listFiles returns "." as the dir name; does not return ".."; cannot filter it!

         // Delete music files not in playlist
         for (File subDir : FileUtils.listFiles(entry, new SuffixFileFilter(".mp3"), FalseFileFilter.FALSE))
            deleteCount += processDirEntry(context, subDir);

         // Recurse into directories
        for (File subDir : FileUtils.listFilesAndDirs(entry, FalseFileFilter.FALSE, TrueFileFilter.TRUE))
           if (subDir != entry)
              deleteCount += processDirEntry(context, subDir);

         // Delete dir if there's no music or subdirectories left
         //
         // Count directories
         for (File subDir : FileUtils.listFilesAndDirs(entry, FalseFileFilter.FALSE, TrueFileFilter.TRUE))
            if (subDir != entry)
               entryCount++;

         if (0 == entryCount)
         {
            // Count music files
            for (File subDir : FileUtils.listFiles(entry, new SuffixFileFilter(".mp3"), FalseFileFilter.FALSE))
               entryCount++;
         }

         if (0 == entryCount)
            try
            {
               FileUtils.deleteDirectory(entry);
            }
            catch (IOException e)
            {
               log(context, LogLevel.Error, "cannot delete directory '" + entry.getAbsolutePath() + "'");
            }
      }
      else if (entry.isFile())
      {
         final String name = relativeName(playlistDir, entry.getAbsolutePath()).toLowerCase();

         if (!mentionedFiles.contains(name))
         {
            entry.delete();
            deleteCount++;
         }
      }
      // else special file; ignored

      return deleteCount;
   }

   private static void ensureHttpClient()
   {
      if (null == httpClient)
      {
         httpClient = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(5, TimeUnit.MINUTES) // don't have to handle retry at higher level
            .build();
      }
   }

   public static void cleanPlaylist(Context context, String category, String playlistDir, String smmDir)
   {
      //  Delete lines in category.m3u that are before song in
      //  SMM_Dir/category.last.
      //
      //  Directory names end in '/'

      // We can't declare a File object for lastFile; that prevents
      // delete in editPlaylist.
      final String playlistFilename = FilenameUtils.concat(playlistDir, category + ".m3u");
      final String lastFilename     = FilenameUtils.concat(smmDir, category + ".last");

      try
      {
         // getPath() returns empty string if file does not exist
         if ("" != FilenameUtils.getPath(playlistFilename))
            if ("" != FilenameUtils.getPath(lastFilename))
            {
               int deleteCount = editPlaylist(context, playlistFilename, lastFilename);
               log(context, LogLevel.Info, category + " playlist cleaned: " + deleteCount + " songs deleted");
            }
      }
      catch (IOException e)
      {
         // from editPlaylist (which calls readPlaylist)
         log(context, LogLevel.Error, "cannot read/write playlist '" + playlistFilename + "'");
      }
   }

   public static void cleanSongs(Context context, String category, String playlistDir, String smmDir)
   {
      //  Delete files from Playlist_Dir/Category that are not
      //  mentioned in playlist file category.m3u.
      //
      //  Directory names end in '/'

      // We can't declare a File object for playlistFile or lastFile;
      // that prevents rename, delete in editPlaylist.
      final String playlistFilename = FilenameUtils.concat(playlistDir, category + ".m3u");
      final String lastFilename     = FilenameUtils.concat(smmDir, category + ".last");

      DownloadUtils.playlistDir = playlistDir;

      try
      {
         mentionedFiles = readPlaylist(playlistFilename, true);

         //  Search playlist directory, delete files not in playlist
         {
            File targetDir   = new File(playlistDir, category);
            int  deleteCount = 0;

            for (File subDir : FileUtils.listFilesAndDirs(targetDir, FalseFileFilter.FALSE, TrueFileFilter.TRUE))
               if (subDir != targetDir)
                  deleteCount += processDirEntry(context, subDir);

            log(context, LogLevel.Info, category + " song storage cleaned: " + deleteCount + " files deleted");
         }
      }
      catch (IOException e)
      {
         // from editPlaylist (which calls readPlaylist)
         log(context, LogLevel.Error, "cannot read/write playlist '" + playlistFilename + "'");
      }
   }

   public static String[] getNewSongsList(Context context, String serverIP, String category, int count, int randomSeed)
   // randomSeed = -1 means randomize; other values used in unit tests.
   {
      final String url = "http://" + serverIP + ":8080/download?category=" + category +
         "&count=" + Integer.toString(count) + (-1 == randomSeed ? "" : "&seed=" + Integer.toString(randomSeed));

      Request request = new Request.Builder().url(url).build();
      String[] result = {};

      ensureHttpClient();

      try (Response response = httpClient.newCall(request).execute())
      {
         try
         {
            result = response.body().string().split("\r\n");
         }
         catch (IOException e)
         {
            // From response.body()
            log(context, LogLevel.Error, "getNewSongsList request has no body: " + e.toString());
         }
      }
      catch (IOException e)
      {
         // From httpClient.newCall; connection failed after retry
         log(context, LogLevel.Error, "getNewSongsList '" + url + "': http request failed: " + e.toString());
      }

      log(context, LogLevel.Info, "getNewSongsList: " + Integer.toString(result.length) + " songs");
      return result;
   }

   private static Boolean getFile(Context context, String serverIP, String resource, File fileName)
   {
      // Get 'resource' from 'serverIP', store in 'fileName'.
      // 'resource' may have only path and file name.
      //
      // Return true if successful, false for any errors (error messages in log).

      HttpUrl url = new HttpUrl.Builder()
         .scheme("http")
         .host(serverIP)
         .port(8080)
         .addPathSegments(resource)
         .build();

      try
      {
         fileName.createNewFile();
      }
      catch (IOException e)
      {
         log(context, LogLevel.Error, "cannot create file " + fileName.getAbsolutePath());
         return false;
      }

      try (Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute())
      {
         if (!response.isSuccessful())
         {
            log(context, LogLevel.Error, "getFile '" + url.toString() +"' request failed: " +
                response.code() + " " + response.message());
            return false;
         }

         BufferedInputStream in            = new BufferedInputStream(response.body().byteStream());
         FileOutputStream    out           = new FileOutputStream(fileName);
         byte[] buffer                     = new byte[BUFFER_SIZE];
         final String        contentLen    = response.header("Content-Length");
         int                 contentLength = Integer.parseInt(contentLen);
         int                 downloaded    = 0;
         int                 count         = 0;

         while ((count = in.read(buffer)) != -1)
         {
            downloaded += count;
            out.write(buffer, 0, count);
         }
         out.close();
         in.close();

         if (downloaded != contentLength)
            log(context, LogLevel.Error, "downloading '" + resource + "'; got " +
                downloaded + "bytes, expecting " + contentLength);
         else
            log(context, LogLevel.Verbose, "downloaded '" + resource + "'");
      }
      catch (IOException e)
      {
         // From httpClient.newCall; connection failed after retry
         log(context, LogLevel.Error, "http request failed: getFile '" + resource + "': " + e.toString());
         return false;
      }
      catch (NumberFormatException e)
      {
         // from parseInt; assume it can't fail
         log(context, LogLevel.Error, "Programmer Error: parseInt failed");
         return false;
      }

      return true;
   }

   public static String[] getMetaList(Context context,
                                      String  serverIP,
                                      String  resource)
      throws IOException
   {
      // If throw exception, error is already logged; client should retry later.

      HttpUrl url = new HttpUrl.Builder()
         .scheme("http")
         .host(serverIP)
         .port(8080)
         .addPathSegments(resource)
         .addPathSegment("meta")
         .build();

      final Request request = new Request.Builder().url(url).build();
      String[] result       = new String("").split("\r\n");

      ensureHttpClient();

      try (Response response = httpClient.newCall(request).execute())
      {
         try
         {
            result = response.body().string().split("\r\n");
         }
         catch (IOException e)
         {
            // From response.body()
            log(context, LogLevel.Error, "getMetaList request has no body: " + e.toString());
            throw e;
         }
      }
      catch (IOException e)
      {
         // From httpClient.newCall; connection failed after retry
         log(context, LogLevel.Error, "http request failed: getMetaList '" + resource + "': " + e.toString());
         throw e;
      }

      return result;
   }

   private static Boolean getMeta(Context                context,
                                  String                 serverIP,
                                  String                 resourcePath,
                                  File                   destDir,
                                  MediaScannerConnection mediaScanner)
   {
      // Return false for any errors
      String[] files;

      File objFile;
      String ext;
      String mime = "";

      try
      {
         files = getMetaList(context, serverIP, resourcePath);
      }
      catch (IOException e)
      {
         return false;
      }

      if (files.length == 1 && files[0].length() == 0)
         // no meta files for this directory
         return true;

      for (String file : files)
      {
         objFile = new File(destDir, FilenameUtils.getName(file));

         if (!objFile.exists())
            // might exist if more songs in playlist than in db; meditation.
         {
            if (!getFile(context, serverIP, file, objFile))
               //  Assume network connection died; subsequent files will
               //  fail as well
               return false;
         }

         ext = FilenameUtils.getExtension(objFile.toString());

         if (ext.equals(".mp3"))
            mime = "audio/mpeg";
         else if (ext.equals(".pdf"))
            mime = "application/pdf";

         if (null != mediaScanner)
            mediaScanner.scanFile(objFile.getAbsolutePath(), mime);
      }
      return true;
   }

   public static Boolean getSongs(Context                context,
                                  String                 serverIP,
                                  String[]               songs,
                                  String                 category,
                                  String                 root,
                                  MediaScannerConnection mediaScanner)
   {
      // Get 'songs' from 'serverIP', store in 'root/<category>', add
      // to playlist 'root/<category>.m3u'. Also get album art, liner
      // notes for new directories. Add files to mediaScanner, if not
      // null.
      //
      // Returns 'true' for success or non-recoverable error; 'false'
      // for recoverable error; client should retry later.

      final File songRoot     = new File(new File(root), category);
      File       playlistFile = new File(new File(root), category + ".m3u");
      FileWriter playlistWriter;
      int        count        = 0;
      Boolean    success      = true;

      try
      {
         playlistWriter = new FileWriter(playlistFile, true); // append
      }
      catch (IOException e)
      {
         log(context, LogLevel.Error, "cannot open '" + playlistFile.getAbsolutePath() + "' for append.");
         return true; // non-recoverable
      }

      try
      {
         for (String song : songs)
         {
            File destDir     = new File(songRoot, FilenameUtils.getPath(song));
            File destination = new File(songRoot, song);
            File songFile;

            if (!destDir.exists())
            {
               destDir.mkdirs();
               if (!getMeta(context, serverIP, FilenameUtils.getPath(song), destDir, mediaScanner))
               {
                  // Delete dir so meta will be downloaded on retry
                  destDir.delete();
                  success = false;
               }
            }

            if (success)
            {
               songFile = new File(destDir, FilenameUtils.getName(song));
               if (getFile(context, serverIP, song, songFile))
               {
                  if (null != mediaScanner)
                     mediaScanner.scanFile(songFile.getAbsolutePath(), "audio/mpeg");

                  playlistWriter.write(category + "/" + song + "\n");

                  count++;
               }
               else
                  success = false;
            }
         }
      }
      catch (IOException e)
      {
         // From playlistWriter.write
         log(context, LogLevel.Error, "cannot append to '" + playlistFile.getAbsolutePath() + "'; disk full?");
         success = true; // non-recoverable
      }
      finally
      {
         try
         {
            playlistWriter.close();
         }
         catch (IOException e)
         {
            // probably from flush cache
            log(context, LogLevel.Error, "cannot close '" + playlistFile.getAbsolutePath() + "'; disk full?");
            success = true; // non-recoverable
         }
      }

      log(context, LogLevel.Info, count + " " + category + " songs downloaded");

      return success;

      // File objects hold the corresponding disk file locked; later
      // unit test cannot delete them.
   }

   public static Boolean sendNotes(Context context,
                                   String  serverIP,
                                   String  category,
                                   String  smmDir)
   {
      // Returns 'true' for success or non-recoverable error; 'false'
      // for recoverable error; client should retry later.

      File    noteFile = new File(smmDir, category + ".note");
      Boolean success  = true;

      if (noteFile.exists())
      {
         final String url  = "http://" + serverIP + ":8080/remote_cache/" + category + ".note";
         String       data = "";

         try
         {
            LineNumberReader noteReader = new LineNumberReader(new FileReader(noteFile));
            String           line       = noteReader.readLine();

            while (line != null)
            {
               // Doc for LineNumberReader says 'line' includes line terminators, but it doesn't.
               data = data + line + "\r\n";
               line = noteReader.readLine();
            };

         }
         catch (FileNotFoundException e){} // from noteReader constructor; can't get here
         catch (IOException e){} // from noteReader.readLine; can't get here

         {
            final TextBody body    = new TextBody(data);
            final Request  request = new Request.Builder()
               .url(url)
               .put(body)
               .build();

            ensureHttpClient();

            try (Response response = httpClient.newCall(request).execute())
            {
               if (200 != response.code())
                  log(context, LogLevel.Error, "put notes failed " + response.message());
               else
                  log(context, LogLevel.Info, category + " sendNotes");
            }
            catch (IOException e)
            {
               // From httpClient.newCall; connection failed after retry
               log(context, LogLevel.Error, category + " sendNotes http request failed: " + e.toString());
               success = false;
            }
         }

         noteFile.delete();
      }

      return success;
   }
}
