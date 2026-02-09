/**
 * Unit test for the stream serialization logic in syncUtils.
 */

package org.stephe_leake.music_player_2

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun ByteArray.toHexString(): String = joinToString(separator = " ") {String.format("%02x", it)}

class SyncUtilsTest
{

    @Test
    fun sendString_and_readString_areCompatible()
    {
        // 1. Setup
        val originalString = "{\"Operation\":\"INSERT\",\"Value\":{\"ID\":1,\"Modified\":\"2000-01-02 00:00:00\",\"Data\":{\"File_Name\":\"Arthur/C./Clarke.mp3\",\"Category\":\"vocal\",\"Album_Artist\":\"Arthur\",\"Album\":\"C.\",\"Title\":\"Clarke\"}}}"
 
        val outputStream = ByteArrayOutputStream()

        // 2. Act: Write the string using sendString
        syncUtils.sendString(outputStream, originalString)

        val writtenBytes = outputStream.toByteArray()
        println("--- KOTLIN GENERATED HEX ---")
        println("HEX: ${writtenBytes.toHexString()}")
        println("Byte Count: ${writtenBytes.size}")
        println("--------------------------")
        
        // 3. Prepare for reading
        val inputStream = ByteArrayInputStream(writtenBytes)

        // 4. Act: Read the string back using readString
        val resultString = syncUtils.readString(inputStream)

        // 5. Assert: The result must match the original
        assertEquals("The string read back should be identical to the original", originalString, resultString)
    }

    @Test
    fun sendString_withEmptyString() {
        val originalString = ""
        val outputStream = ByteArrayOutputStream()

        syncUtils.sendString(outputStream, originalString)

        val writtenBytes = outputStream.toByteArray()
        val inputStream = ByteArrayInputStream(writtenBytes)

        // In your current implementation, this will throw a ProtocolException
        // because the length is 0. This test verifies that behavior.
        assertThrows(java.net.ProtocolException::class.java) {
            syncUtils.readString(inputStream)
        }
    }
}
