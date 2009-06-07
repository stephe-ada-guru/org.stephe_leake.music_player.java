--  Abstract :
--
--  See spec
--
--  Copyright (C) 2009 Stephen Leake.  All Rights Reserved.
--
--  This program is free software; you can redistribute it and/or
--  modify it under terms of the GNU General Public License as
--  published by the Free Software Foundation; either version 3, or (at
--  your option) any later version. This program is distributed in the
--  hope that it will be useful, but WITHOUT ANY WARRANTY; without even
--  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
--  PURPOSE. See the GNU General Public License for more details. You
--  should have received a copy of the GNU General Public License
--  distributed with this program; see file COPYING. If not, write to
--  the Free Software Foundation, 59 Temple Place - Suite 330, Boston,
--  MA 02111-1307, USA.

with Ada.Text_IO;
with Import_Books.Collection_Table;
with Import_Books.Title_Table;
procedure Import_Books.Import_CollectionTitle (Root_File_Name : in String)
is
   use GNU.DB.SQLCLI;
   use MySQL_ID_Binding;
   use SAL.CSV;

   Collection : ID_Indicator_Type;
   Title      : ID_Indicator_Type;

   MySQL_Statement : SQLHANDLE;

   File_Name : constant String := Root_File_Name & "_collectiontitle.csv";

   File : File_Type;

begin
   Ada.Text_IO.Put_Line ("Importing CollectionTitle table from " & File_Name);

   Open (File, File_Name, Max_Row_Size => 3 * (Name_Field_Length + 3) + Title_Field_Length + 5);

   if Columns (File) /= 6 then
      raise SAL.Initialization_Error with "expected 6 columns; found" & Integer'Image (Columns (File));
   end if;

   SQLAllocHandle (SQL_HANDLE_STMT, MySQL_Connection, MySQL_Statement);
   SQLPrepare (MySQL_Statement, String'("INSERT INTO CollectionTitle (Collection, Title) VALUES (?, ?)"));
   SQLBindParameter (MySQL_Statement, 1, Collection.ID'Access, Collection.Indicator'Access);
   SQLBindParameter (MySQL_Statement, 2, Title.ID'Access, Title.Indicator'Access);

   Warm_Fuzzy_Count := 0;
   loop
      Collection_Table.Read (File, 1, Collection);
      Title_Table.Read (File, 5, Title);

      SQLExecute (MySQL_Statement);

      Warm_Fuzzy;

      exit when End_Of_File (File);

      Next_Row (File);
   end loop;

   SQLCommit (MySQL_Connection);

   Ada.Text_IO.New_Line;
   Ada.Text_IO.Put_Line (Integer'Image (Warm_Fuzzy_Count) & " CollectionTitles");
end Import_Books.Import_CollectionTitle;
