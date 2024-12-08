//  Abstract :
//
//  Preference User Interface to Stephe's Music Player.
//
//  Copyright (C) 2024 Stephen Leake.  All Rights Reserved.
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

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class PrefActivity : FragmentActivity()
{
   override fun onCreate(savedInstanceState: Bundle?)
   {
       super.onCreate(savedInstanceState)

       setContentView(R.layout.prefactivity)
   }
}
