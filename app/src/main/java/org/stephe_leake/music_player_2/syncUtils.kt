//  Abstract :
//
//  Utils for Sync service
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

import android.util.Log

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class syncUtils
 {
    companion object
    {
       fun readInt(inputStream: InputStream): Int
       {
          val bytes     = ByteArray(4)
          val bytesRead = inputStream.read(bytes)
          if (bytesRead < 4)
             throw java.net.SocketException("expected 4 byte integer, got $bytesRead bytes")

          val byteBuffer = ByteBuffer.wrap(bytes)
          byteBuffer.order(ByteOrder.BIG_ENDIAN) // network order
          
          return byteBuffer.getInt()   
       }

       fun readString(inputStream: InputStream): String
       // Read a string from inputStream, in Ada stream format; [first, last, <chars>]
       // first, last are 4 byte integers little endian
       // chars are UTF-8
       {
          val first  = readInt(inputStream)
          val last   = readInt(inputStream)
          val length = last - first + 1

          if (length < 1)
             throw java.net.ProtocolException("expected positive string length, got $length")
          
          val result = ByteArray(length)
          val bytesRead = inputStream.read(result)
          if (bytesRead < length)
             throw java.net.SocketException("expected $length byte string, got $bytesRead bytes")

          return String(result)
       }

       fun checkAck(inputStream: InputStream)
       {
          val response = JSONObject(readString(inputStream))
          if (0 != response.getString("Status").compareTo("ACK"))
             throw java.net.ProtocolException("expecting ACK, got $response")
       }

       fun sendString(outputStream: OutputStream, item: String)
       {
          // Send a string to outputStream, in Ada stream format; [first, last, <chars>]
          // first, last are 4 byte little-endian integers
          // chars are UTF-8
          val bytes = item.toByteArray(Charsets.UTF_8)

          val headerBuffer = ByteBuffer.allocate(8) // 4 bytes for each integer
          headerBuffer.order(ByteOrder.BIG_ENDIAN) // The server expects network order
          headerBuffer.putInt(1)
          headerBuffer.putInt(bytes.size)

          outputStream.write(headerBuffer.array())
          outputStream.write(bytes)
          //Log.d(utils.logTag, "sync sent '${headerBuffer.array()}' '$item'") // FIXME: delete; debugging
       }

       fun sendAck(outputStream: OutputStream)
       {
          val response = JSONObject()
          
          response.put("Status", "ACK")
          sendString(outputStream, response.toString())
       }

       fun sendData(outputStream: OutputStream, data: JSONObject)
       {
          val response = JSONObject()
          
          response.put("Status", "ACK")
          response.put("Data", data)
          sendString(outputStream, response.toString())
       }

       fun sendError(outputStream: OutputStream, msg: String)
       // Ignores send errors, since we are already in error-handling
       // mode.
       {
          try
          {
             val response = JSONObject()
             
             response.put("Status", "NACK")
             response.put("Message", msg)
             sendString(outputStream, response.toString())
          }
          catch (_: JSONException) {}
          catch (_: IOException) {}
       }

       fun toJSON(song: Song) : JSONObject
       // Same format as smm-database.adb Get_JSON
       {
          val result = JSONObject()
          if (song.Deleted != "")
             {
                result.put("ID", song.ID)
                result.put("Deleted", song.Deleted)
             }
          else
             {
                val dataf = JSONObject()
                dataf.put("File_Name", song.File_Name)
                dataf.put("Category", song.Category)
                if (song.Artist != "") {dataf.put("Artist", song.Artist)}
                dataf.put("Album_Artist", song.Album_Artist)
                if (song.Composer != "") {dataf.put("Composer", song.Composer)}
                if (song.Album != "") {dataf.put("Album", song.Album)}
                if (song.Year != Song.No_Year) {dataf.put("Year", song.Year)}
                dataf.put("Title", song.Title)
                if (song.Track != Song.No_Track) {dataf.put("Track", song.Track)}
                if (song.Last_Downloaded != Song.Default_Time_String) 
                   {dataf.put("Last_Downloaded", song.Last_Downloaded)}
                if (song.Prev_Downloaded != Song.Default_Time_String)
                   {dataf.put("Prev_Downloaded", song.Prev_Downloaded)}
                if (song.Play_Before != Song.Null_ID) {dataf.put("Play_Before", song.Play_Before)}
                if (song.Play_After != Song.Null_ID) {dataf.put("Play_After", song.Play_After)}
                
                result.put("ID", song.ID)
                if (song.Modified != Song.Default_Time_String) {result.put("Modified", song.Modified)}
                result.put("Data", dataf)
             }
          return result
       }

       fun toJSON(list: List<Int>): JSONArray
       // Same format as smm.ads To_JSON
       {
          val result = JSONArray()
          for (id in list) {result.put(id)}
          return result
       }
    }
}
