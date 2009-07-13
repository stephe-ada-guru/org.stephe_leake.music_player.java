--  Abstract :
--
--  See spec.
--
--  Copyright (C) 2002, 2004, 2009 Stephen Leake.  All Rights Reserved.
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
--

with Ada.Strings.Fixed;
with GNU.DB.SQLCLI.Statement_Attribute;
package body Books.Database.Data_Tables.Series is

   --  Local declarations

   procedure Copy
     (T            : in out Table;
      Title        : in     String;
      Author       : in     ID_Type;
      Author_Valid : in     Boolean);
   --  Copy Data to Table fields.

   ----------
   --  Subprogram bodies (alphabetical order)

   procedure Copy
     (T            : in out Table;
      Title        : in     String;
      Author       : in     ID_Type;
      Author_Valid : in     Boolean)
   is
      use Ada.Strings;
      use Ada.Strings.Fixed;
      use type GNU.DB.SQLCLI.SQLINTEGER;
   begin
      Move (Source => Title, Target => T.Title.all, Drop => Right);
      T.Title_Length := GNU.DB.SQLCLI.SQLINTEGER'Min (T.Title.all'Length, Title'Length);

      if Author_Valid then
         T.Author           := Author;
         T.Author_Indicator := ID_Type'Size / 8;
      else
         T.Author_Indicator := GNU.DB.SQLCLI.SQL_NULL_DATA;
      end if;

   end Copy;

   function Author (T : in Table) return ID_Type
   is
      use type GNU.DB.SQLCLI.SQLINTEGER;
   begin
      if T.Author_Indicator = GNU.DB.SQLCLI.SQL_NULL_DATA then
         raise No_Data;
      else
         return ID_Type (T.Author);
      end if;
   end Author;

   function Author (T : in Data_Tables.Table_Access) return ID_Type
   is begin
      return Author (Table (T.all));
   end Author;

   function Author_Valid (T : in Data_Tables.Table_Access) return Boolean
   is
      use type GNU.DB.SQLCLI.SQLINTEGER;
   begin
      return Table (T.all).Author_Indicator /= GNU.DB.SQLCLI.SQL_NULL_DATA;
   end Author_Valid;

   overriding procedure Finalize (T : in out Table)
   is
      use type GNU.DB.SQLCLI.SQLHANDLE;
   begin
      Books.Database.Data_Tables.Finalize (Books.Database.Data_Tables.Table (T));

      if T.By_Author_Statement /= GNU.DB.SQLCLI.SQL_NULL_HANDLE then
         GNU.DB.SQLCLI.SQLFreeHandle (GNU.DB.SQLCLI.SQL_HANDLE_STMT, T.By_Author_Statement);
      end if;
   end Finalize;

   procedure Find_Author (T : in out Table; Author : in ID_Type)
   is
      use type GNU.DB.SQLCLI.SQLINTEGER;
   begin
      T.Author           := Author;
      T.Author_Indicator := ID_Type'Size / 8;

      Find (T, T.By_Author_Statement);
   end Find_Author;

   procedure Find_Author (T : in Data_Tables.Table_Access; Author : in ID_Type)
   is begin
      Find_Author (Table (T.all), Author);
   end Find_Author;

   overriding procedure Initialize (T : in out Table)
   is
      use GNU.DB.SQLCLI;
      use GNU.DB.SQLCLI.Statement_Attribute;
   begin
      if T.Title = null then
         T.Title        := new String'(1 .. Field_Length => ' ');
         T.Find_Pattern := new String'(1 .. Field_Length + 1  => ' '); -- for '%'
      end if;

      --  All_By_ID_Statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.All_By_ID_Statement);
      SQLPrepare
        (T.All_By_ID_Statement,
         String'("SELECT Title, Author FROM Series ORDER BY ID"));

      SQLBindCol            (T.All_By_ID_Statement, 1, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindCol (T.All_By_ID_Statement, 2, T.Author'Access, T.Author_Indicator'Access);

      --  By_ID_Statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.By_ID_Statement);
      SQLPrepare
        (T.By_ID_Statement,
         String'("SELECT ID, Title, Author FROM Series WHERE ID = ?"));

      ID_Binding.SQLBindParameter (T.By_ID_Statement, 1, T.ID'Access, T.ID_Indicator'Access);

      ID_Binding.SQLBindCol (T.By_ID_Statement, 1, T.ID'Access, T.ID_Indicator'Access);
      SQLBindCol            (T.By_ID_Statement, 2, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindCol (T.By_ID_Statement, 3, T.Author'Access, T.Author_Indicator'Access);

      --  By_Name_Statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.By_Name_Statement);
      SQLPrepare
        (T.By_Name_Statement,
         String'("SELECT ID, Title, Author FROM Series WHERE Title LIKE ? ORDER BY Title"));

      SQLSetStmtAttr (T.By_Name_Statement, SQL_BIND_BY_COLUMN);
      SQLSetStmtAttr (T.By_Name_Statement, Statement_Attribute_Unsigned'(SQL_ROWSET_SIZE, 1));

      SQLBindParameter (T.By_Name_Statement, 1, T.Find_Pattern, T.Find_Pattern_Length'Access);

      ID_Binding.SQLBindCol (T.By_Name_Statement, 1, T.ID'Access, T.ID_Indicator'Access);
      SQLBindCol            (T.By_Name_Statement, 2, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindCol (T.By_Name_Statement, 3, T.Author'Access, T.Author_Indicator'Access);

      T.Find_Pattern (1)    := '%';
      T.Find_Pattern_Length := 1;
      T.Find_Statement      := T.By_Name_Statement;

      Checked_Execute (T.Find_Statement); --  So Next is valid.

      --  Update statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.Update_Statement);
      SQLPrepare
        (T.Update_Statement,
         String'("UPDATE Series SET Title = ?, Author = ? WHERE ID = ?"));

      SQLBindParameter            (T.Update_Statement, 1, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindParameter (T.Update_Statement, 2, T.Author'Access, T.Author_Indicator'Access);
      ID_Binding.SQLBindParameter (T.Update_Statement, 3, T.ID'Access, T.ID_Indicator'Access);

      --  Insert statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.Insert_Statement);
      SQLPrepare
        (T.Insert_Statement,
         String'("INSERT INTO Series (Title, Author) VALUES (?, ?)"));

      SQLBindParameter            (T.Insert_Statement, 1, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindParameter (T.Insert_Statement, 2, T.Author'Access, T.Author_Indicator'Access);

      --  Delete statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.Delete_Statement);
      SQLPrepare
        (T.Delete_Statement,
         String'("DELETE FROM Series WHERE ID = ?"));

      ID_Binding.SQLBindParameter (T.Delete_Statement, 1, T.ID'Access, T.ID_Indicator'Access);

      --  By_Author_Statement
      SQLAllocHandle (SQL_HANDLE_STMT, T.DB.Connection, T.By_Author_Statement);
      SQLPrepare
        (T.By_Author_Statement,
         String'("SELECT ID, Title, Author FROM Series WHERE Author = ?"));

      ID_Binding.SQLBindParameter (T.By_Author_Statement, 1, T.Author'Access, T.Author_Indicator'Access);

      ID_Binding.SQLBindCol          (T.By_Author_Statement, 1, T.ID'Access, T.ID_Indicator'Access);
      SQLBindCol                     (T.By_Author_Statement, 2, T.Title, T.Title_Length'Access);
      ID_Binding.SQLBindCol          (T.By_Author_Statement, 3, T.Author'Access, T.Author_Indicator'Access);

   end Initialize;

   procedure Insert
     (T            : in out Table;
      Title        : in     String;
      Author       : in     ID_Type;
      Author_Valid : in     Boolean)
   is begin
      Copy (T, Title, Author, Author_Valid);
      Checked_Execute (T.Insert_Statement);
      Find (T, Title);
   end Insert;

   function Title (T : in Table) return String
   is begin
      return T.Title (1 .. Integer (T.Title_Length));
   end Title;

   function Title (T : in Data_Tables.Table_Access) return String
   is begin
      return Title (Table (T.all));
   end Title;

   procedure Update
     (T            : in out Table;
      Title        : in     String;
      Author       : in     ID_Type;
      Author_Valid : in     Boolean)
   is begin
      Copy (T, Title, Author, Author_Valid);
      Checked_Execute (T.Update_Statement);
   end Update;

end Books.Database.Data_Tables.Series;
