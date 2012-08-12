--  Abstract :
--
--  Operations on the Author table
--
--  Copyright (C) 2002, 2004, 2009, 2012 Stephen Leake.  All Rights Reserved.
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

pragma License (GPL);

package Books.Database.Data_Tables.Author is

   use type GNATCOLL.SQL.Exec.Field_Index;

   type Table (DB : access Database'Class) is new Data_Tables.Table with private;
   type Table_Access is access all Table;

   ----------
   --  Override parent operations.

   overriding procedure Initialize (T : in out Table);

   ----------
   --  New operations

   First_Name_Index  : constant GNATCOLL.SQL.Exec.Field_Index := ID_Index + 1;
   Middle_Name_Index : constant GNATCOLL.SQL.Exec.Field_Index := ID_Index + 2;
   Last_Name_Index   : constant GNATCOLL.SQL.Exec.Field_Index := ID_Index + 3;
   --  Retrieve data from current record via Field

   procedure Insert
     (T           : in out Table;
      First_Name  : in     String;
      Middle_Name : in     String;
      Last_Name   : in     String);
   --  Insert a new record, fetch it using Find.

   procedure Update
     (T           : in out Table;
      First_Name  : in     String;
      Middle_Name : in     String;
      Last_Name   : in     String);
   --  Update the data in the current record.

private

   Name_Field_Length : constant := 20;

   type Table (DB : access Database'Class) is new Data_Tables.Table (DB => DB) with record
      null;
      --  FIXME: don't store the data, just fetch it from the cursor!
      --  Data
      --  First         : String_Access;
      --  First_Length  : aliased GNU.DB.SQLCLI.SQLINTEGER := 0;
      --  Middle        : String_Access;
      --  Middle_Length : aliased GNU.DB.SQLCLI.SQLINTEGER := 0;
      --  Last          : String_Access;
      --  Last_Length   : aliased GNU.DB.SQLCLI.SQLINTEGER := 0;

   end record;

end Books.Database.Data_Tables.Author;
