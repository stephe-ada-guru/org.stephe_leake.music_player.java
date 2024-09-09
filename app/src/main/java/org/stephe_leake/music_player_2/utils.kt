//  Abstract :
//
//  misc stuff
//
//  Copyright (C) 2011 - 2013, 2015 - 2018, 2021, 2024 Stephen Leake.  All Rights Reserved.
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

import android.content.Context
import android.view.View;
import android.widget.TextView;
import androidx.activity.ComponentActivity

class utils
{
   companion object {

      // download service commands
      val COMMAND_CANCEL_DOWNLOAD : Int = 2;
      val COMMAND_DOWNLOAD        : Int = 3;

      // objects

      var mainActivity: ComponentActivity? = null;

      // methods

      fun findTextViewById (a: ComponentActivity, id: Int) : TextView
      {
         val v : View? = a.findViewById(id);
         
         if (v == null) throw RuntimeException("no such id " + id);
            
            if (v is TextView)
            {
               return v
            }
         else
            {
               throw RuntimeException(id.toString() + " is not a TextView; it is a " + v.toString());
            }
      }
   }
}
