/**
 * Operations enumeration type for the Music Player Sync protocol.
 * Matches the remote Ada server's operations.
 *
 * Copyright (C) 2016 - 2024 Stephen Leake. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3, or (at
 * your option) any later version. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License
 * distributed with this program; see file COPYING. If not, write to
 * the Free Software Foundation, 51 Franklin Street, Suite 500, Boston,
 * MA 02110-1335, USA.
 */

package org.stephe_leake.music_player_2

enum class Operations(val code: Int)
{
    // Must be uppercase to match Ada 'Image for valueOf() to work implicitly.
    // Must match smm-database_remote.ads Operations
    QUIT(0),
    GET(1),
    GET_LAST_ID(2),
    GET_MODIFIED(3),
    GET_NEW(4),
    CONFLICT(5),
    PROGRESS(6),
    INSERT(7),
    UPDATE(8),
    RENUMBER(9);

    companion object {
        // Create a map for fast, safe lookups by integer code.
        // This is more efficient and safer than a switch statement.
        private val map = entries.associateBy(Operations::code)

        /**
         * Converts an integer code to its corresponding Operations enum constant.
         * @param code The integer code to look up.
         * @return The matching Operations enum.
         * @throws IllegalArgumentException if the code is invalid.
         */
        fun toOperations(code: Int): Operations =
            map[code] ?: throw IllegalArgumentException("Invalid operation code: $code")

         // public static Operations valueOf(String i); implicit, same case as declaration

         // public static Operations[] values(); implicit, for iteration
    }
}
