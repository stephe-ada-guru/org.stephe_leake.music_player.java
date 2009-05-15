--  Abstract :
--
--  See spec.
--
--  Copyright (C) 2002, 2004 Stephen Leake.  All Rights Reserved.
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
--

with Ada.Exceptions;
package body Books.Database.Data_Tables is

   --  Subprogram bodies (alphabetical order)

   procedure Delete (T : in out Table'Class)
   is begin
      Checked_Execute (T.Delete_Statement);
      Clear_Data (T);
      T.ID_Indicator := SQL_NULL_DATA;
      Next (T);
   end Delete;

   procedure Fetch (T : in out Table'Class; ID : in ID_Type)
   is begin
      T.ID           := ID;
      T.ID_Indicator := ID_Type'Size / 8;
      Checked_Execute (T.By_ID_Statement);
      SQLFetch (T.By_ID_Statement);
      SQLCloseCursor (T.By_ID_Statement);
   exception
   when GNU.DB.SQLCLI.No_Data =>
      SQLCloseCursor (T.By_ID_Statement);
      raise Books.Database.No_Data;
   end Fetch;

   procedure Finalize (T : in out Table)
   is begin
      Books.Database.Finalize (Books.Database.Table (T));

      if T.By_ID_Statement /= SQL_NULL_HANDLE then
         SQLFreeHandle (SQL_HANDLE_STMT, T.By_ID_Statement);
      end if;
      if T.By_Name_Statement /= SQL_NULL_HANDLE then
         SQLFreeHandle (SQL_HANDLE_STMT, T.By_Name_Statement);
      end if;
   end Finalize;

   procedure Find (T : in out Table'Class; Item : in String)
   is begin
      T.Find_Pattern (1 .. Item'Length) := Item;
      T.Find_Pattern (Item'Length + 1) := '%';
      T.Find_Pattern_Length := Item'Length + 1;
      SQLCloseCursor (T.Find_Statement);
      Checked_Execute (T.Find_Statement);
      SQLFetch (T.Find_Statement);
   exception
   when Constraint_Error =>
      Ada.Exceptions.Raise_Exception
        (Entry_Error'Identity,
         "Entry too long for database field; max" & Integer'Image (T.Find_Pattern'Length - 1));

   when GNU.DB.SQLCLI.No_Data =>
      SQLCloseCursor (T.Find_Statement);
      --  Just keep current data.
   end Find;

   function ID (T : in Table'Class) return ID_Type is
   begin
      if T.ID_Indicator = SQL_NULL_DATA then
         return 0;
      else
         return T.ID;
      end if;
   end ID;

end Books.Database.Data_Tables;
