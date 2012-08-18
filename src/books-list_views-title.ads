--  Abstract :
--
--  Title list display widget for Books application
--
--  Copyright (C) 2012 Stephen Leake.  All Rights Reserved.
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
--  the Free Software Foundation, 51 Franklin Street, Suite 500, Boston,
--  MA 02110-1335, USA.

pragma License (GPL);

with Books.Database;
package Books.List_Views.Title is

   type Gtk_Title_List_Record is new Gtk_List_View_Record with null record;
   type Gtk_Title_List is access all Gtk_Title_List_Record'Class;

   procedure Gtk_New (Title_View : out Gtk_List_View);

   overriding procedure Insert_List_Row
     (List_View : access Gtk_Title_List_Record;
      Table     : access Books.Database.Data_Tables.Table'Class;
      ID        : in     Books.Database.ID_Type);

end Books.List_Views.Title;
