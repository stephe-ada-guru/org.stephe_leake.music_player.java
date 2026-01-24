//  Abstract :
//
//  Operations enumeration type for Books Sync protocol.
//  Matches Ada Books.Database_Remote.Operations.
//
//  Copyright (C) 2016 - 2017, 2020 Stephen Leake. All Rights Reserved.
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

package org.stephe_leake.music_player_2

enum Operations
{
   // Must be uppercase for valueOf() to match Ada 'Image.
   // Must match books-database_remote.ads Operations
   QUIT(0),
   GET_DATA_DATA(1),
   GET_LINK_DATA(2),
   GET_DATA_LAST_ID(3),
   GET_LINK_LAST_ID(4),
   GET_DATA_MODIFIED(5),
   GET_DATA_NEW(6),
   GET_LINK_MODIFIED(7),
   GET_LINK_NEW(8),
   CONFLICT_DATA(9),
   CONFLICT_LINK(10),
   PROGRESS(11),
   INSERT_DATA(12),
   INSERT_LINK(13),
   UPDATE_DATA(14),
   UPDATE_LINK(15),
   RENUMBER_DATA(16),
   RENUMBER_LINK(17);

   private int index;
   public Operations(int i) {index = i;}

   static Operations toOperation(int i)
   {
      switch (i)
      {
      case 0:
         return QUIT;
      case 1:
         return GET_DATA_DATA;
      case 2:
         return GET_LINK_DATA;
      case 3:
         return GET_DATA_LAST_ID;
      case 4:
         return GET_LINK_LAST_ID;
      case 5:
         return GET_DATA_MODIFIED;
      case 6:
         return GET_DATA_NEW;
      case 7:
         return GET_LINK_MODIFIED;
      case 8:
         return GET_LINK_NEW;
      case 9:
         return CONFLICT_DATA;
      case 10:
         return CONFLICT_LINK;
      case 11:
         return PROGRESS;
      case 12:
         return INSERT_DATA;
      case 13:
         return INSERT_LINK;
      case 14:
         return UPDATE_DATA;
      case 15:
         return UPDATE_LINK;
      case 16:
         return RENUMBER_DATA;
      case 17:
         return RENUMBER_LINK;

      default:
         throw new IllegalArgumentException("invalid operation index " + i);
      }
   }

   // public static Operations valueOf(String i); implicit, same case as declaration

   // public static Operations[] values(); implicit, for iteration
}
