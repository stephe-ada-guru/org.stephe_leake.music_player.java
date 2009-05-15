--  Abstract :
--
--  Test Books.Database.Link_Tables.AuthorTitle.
--
--  Copyright (C) 2004 Stephen Leake.  All Rights Reserved.
--
--  This program is free software; you can redistribute it and/or
--  modify it under terms of the GNU General Public License as
--  published by the Free Software Foundation; either version 2, or (at
--  your option) any later version. This program is distributed in the
--  hope that it will be useful, but WITHOUT ANY WARRANTY; without even
--  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
--  PURPOSE. See the GNU General Public License for more details. You
--  should have received a copy of the GNU General Public License
--  distributed with this program; see file COPYING. If not, write to
--  the Free Software Foundation, 59 Temple Place - Suite 330, Boston,
--  MA 02111-1307, USA.

with AUnit.Test_Cases; use AUnit.Test_Cases;
with Ada.Strings.Unbounded;
with GNAT.OS_Lib;
package Test_Books.Database.AuthorTitle is

   type Test_Case
     (Root_Directory : GNAT.OS_Lib.String_Access;
      Debug_Level    : Integer)
      is new AUnit.Test_Cases.Test_Case with null record;

   procedure Register_Tests (T : in out Test_Case);

   function Name (T : Test_Case) return Ada.Strings.Unbounded.String_Access;

   procedure Set_Up_Case (T : in out Test_Case);

   procedure Tear_Down_Case (T : in out Test_Case);

end Test_Books.Database.AuthorTitle;
