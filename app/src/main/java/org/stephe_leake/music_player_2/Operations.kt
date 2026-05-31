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

enum class Operations
{
    // Must be uppercase to match Ada 'Image for valueOf() to work.
    // Names must match smm-database_remote.ads Operations.
    QUIT,
    GET,
    GET_LAST_ID,
    GET_MODIFIED,
    GET_MODIFIED_WITH_DATA,
    GET_NEW,
    CONFLICT,
    PROGRESS,
    INSERT,
    INSERT_BATCH,
    UPDATE,
    UPDATE_BATCH,
    RENUMBER
}
