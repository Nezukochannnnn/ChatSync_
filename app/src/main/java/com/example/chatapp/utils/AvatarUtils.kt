package com.example.chatapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.model.User

object AvatarUtils {

    // Cache generated avatar drawables to avoid main-thread bitmap allocation during scrolling
    private val avatarCache = LruCache<String, Drawable>(100)

    // Curated palette of 16 distinct vibrant colors
    private val COLOR_PALETTE = intArrayOf(
        Color.parseColor("#FF6B6B"), // Coral Pink
        Color.parseColor("#4ECDC4"), // Mint Teal
        Color.parseColor("#45B7D1"), // Sky Blue
        Color.parseColor("#96CEB4"), // Pastel Green
        Color.parseColor("#9B59B6"), // Amethyst Purple
        Color.parseColor("#3498DB"), // Royal Blue
        Color.parseColor("#1ABC9C"), // Emerald Green
        Color.parseColor("#E67E22"), // Warm Orange
        Color.parseColor("#E91E63"), // Deep Pink
        Color.parseColor("#00BCD4"), // Cyan
        Color.parseColor("#8E44AD"), // Purple Accent
        Color.parseColor("#2ECC71"), // Jade Green
        Color.parseColor("#F39C12"), // Amber Gold
        Color.parseColor("#D35400"), // Rust Orange
        Color.parseColor("#C0392B"), // Crimson Red
        Color.parseColor("#16A085")  // Dark Teal
    )

    fun getColorForUser(identifier: String): Int {
        if (identifier.isEmpty()) return COLOR_PALETTE[0]
        val hash = kotlin.math.abs(identifier.hashCode())
        return COLOR_PALETTE[hash % COLOR_PALETTE.size]
    }

    fun generateInitialDrawable(context: Context, name: String, identifier: String, sizeDp: Int = 40): Drawable {
        val cacheKey = "$identifier-$name-$sizeDp"
        avatarCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = getColorForUser(if (identifier.isNotEmpty()) identifier else name)

        // Draw solid circular background
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paintBg)

        // Draw uppercase initial letter
        val displayName = name.trim()
        val initial = if (displayName.isNotEmpty()) displayName.first().uppercase() else "?"

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.45f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val textBounds = Rect()
        paintText.getTextBounds(initial, 0, initial.length, textBounds)
        val yOffset = (sizePx / 2f) + (textBounds.height() / 2f) - textBounds.bottom

        canvas.drawText(initial, sizePx / 2f, yOffset, paintText)

        val drawable = BitmapDrawable(context.resources, bitmap)
        avatarCache.put(cacheKey, drawable)
        return drawable
    }

    fun getAvatarDrawable(context: Context, user: User, sizeDp: Int = 40): Drawable {
        val displayName = user.name.ifEmpty { "User" }
        val id = user.id.ifEmpty { displayName }
        return generateInitialDrawable(context, displayName, id, sizeDp)
    }

    fun getGroupAvatarDrawable(context: Context, sizeDp: Int = 40): Drawable {
        val cacheKey = "group-avatar-$sizeDp"
        avatarCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Solid circular background with primary brand color
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6C63FF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paintBg)

        // Draw group vector icon centered inside circle
        val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_group)
        if (vectorDrawable != null) {
            val iconSize = (sizePx * 0.55f).toInt()
            val left = (sizePx - iconSize) / 2
            val top = (sizePx - iconSize) / 2
            vectorDrawable.setBounds(left, top, left + iconSize, top + iconSize)
            vectorDrawable.draw(canvas)
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        avatarCache.put(cacheKey, drawable)
        return drawable
    }
}
