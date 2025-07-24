//  Abstract :
//
//  viewPager2 adapter
//
//  Copyright (C) 2025 Stephen Leake.  All Rights Reserved.
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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ImageSlideshowAdapter(private var imageUris: List<Uri>) :
   RecyclerView.Adapter<ImageSlideshowAdapter.ImageViewHolder>()
{
    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    {
        val imageView: ImageView = itemView.findViewById(R.id.slideshow_item_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder
    {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_slideshow_image, parent, false) // Create this layout file next
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int)
    {
        val imageUri = imageUris[position]
        Glide.with(holder.itemView.context)
            .load(imageUri)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateImages(newImageUris: List<Uri>) {
        imageUris = newImageUris
        notifyDataSetChanged() 
    }
}
