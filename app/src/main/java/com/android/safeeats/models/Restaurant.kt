package com.android.safeeats.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Restaurant(
    val id: String,
    val name: String,
    val category: String,
    val allergenInfo: String,
    val rating: Double,
    val safetyScore: Int,
    val dietaryMatchScore: Int,
    val imageUrl: String?
) : Parcelable


