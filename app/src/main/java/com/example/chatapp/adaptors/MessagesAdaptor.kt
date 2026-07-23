package com.example.chatapp.adaptors

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.User
import com.example.chatapp.utils.AvatarUtils
import com.google.firebase.auth.FirebaseAuth
import com.mikhaellopez.circularimageview.CircularImageView
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Locale

class MessagesAdaptor(
    private val context: Context,
    private val messages: MutableList<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val RECEIVER_TYPE_HOLDER = 1   // "me" text bubble
    private val SENDER_TYPE_HOLDER = 2     // "sender" text bubble
    private val IMAGE_TYPE_HOLDER_ME = 3   // "me" image
    private val IMAGE_TYPE_HOLDER_SENDER = 4 // "sender" image

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            IMAGE_TYPE_HOLDER_ME -> {
                ImageHolderMe(
                    LayoutInflater.from(context).inflate(R.layout.me_image, parent, false)
                )
            }
            IMAGE_TYPE_HOLDER_SENDER -> {
                ImageHolderSender(
                    LayoutInflater.from(context).inflate(R.layout.sender_image, parent, false)
                )
            }
            RECEIVER_TYPE_HOLDER -> {
                MeViewHolder(
                    LayoutInflater.from(context).inflate(R.layout.me, parent, false)
                )
            }
            else -> {
                SenderViewHolder(
                    LayoutInflater.from(context).inflate(R.layout.sender, parent, false)
                )
            }
        }
    }

    private fun loadUserProfile(user: User, imageView: CircularImageView) {
        val initialDrawable = AvatarUtils.getAvatarDrawable(context, user, 36)
        val userColor = AvatarUtils.getColorForUser(user.id.ifEmpty { user.name })
        imageView.borderColor = userColor

        if (user.profileImage.isNotEmpty()) {
            Picasso.get()
                .load(user.profileImage)
                .placeholder(initialDrawable)
                .error(initialDrawable)
                .into(imageView)
        } else {
            imageView.setImageDrawable(initialDrawable)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timestampStr = message.timestamp?.let { timeFormat.format(it) } ?: ""

        when (holder) {
            is MeViewHolder -> {
                holder.textViewMessage.text = message.message
                holder.textViewTimestamp.text = timestampStr
                loadUserProfile(message.sender, holder.meProfileImage)
            }
            is SenderViewHolder -> {
                holder.textViewSender.text = message.message
                holder.textViewSenderName.text = message.sender.name.ifEmpty { "User" }
                holder.textViewSenderName.setTextColor(
                    AvatarUtils.getColorForUser(message.sender.id.ifEmpty { message.sender.name })
                )
                holder.textViewTimestamp.text = timestampStr
                loadUserProfile(message.sender, holder.senderProfileImage)
            }
            is ImageHolderMe -> {
                if (message.image.isNotEmpty()) {
                    Picasso.get()
                        .load(message.image)
                        .placeholder(R.drawable.chat_app)
                        .into(holder.meImage)
                }
                loadUserProfile(message.sender, holder.meProfileImage)
            }
            is ImageHolderSender -> {
                if (message.image.isNotEmpty()) {
                    Picasso.get()
                        .load(message.image)
                        .placeholder(R.drawable.chat_app)
                        .into(holder.senderImage)
                }
                loadUserProfile(message.sender, holder.senderProfileImage)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        return if (currentUserId == message.sender.id) {
            if (message.image.isNotEmpty()) IMAGE_TYPE_HOLDER_ME else RECEIVER_TYPE_HOLDER
        } else {
            if (message.image.isNotEmpty()) IMAGE_TYPE_HOLDER_SENDER else SENDER_TYPE_HOLDER
        }
    }

    class MeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewMessage: TextView = itemView.findViewById(R.id.text_message_me)
        val textViewTimestamp: TextView = itemView.findViewById(R.id.text_timestamp_me)
        val meProfileImage: CircularImageView = itemView.findViewById(R.id.image_profile_me)
    }

    class SenderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewSender: TextView = itemView.findViewById(R.id.text_message_sender)
        val textViewSenderName: TextView = itemView.findViewById(R.id.text_name_sender)
        val textViewTimestamp: TextView = itemView.findViewById(R.id.text_timestamp_sender)
        val senderProfileImage: CircularImageView = itemView.findViewById(R.id.image_profile_sender)
    }

    class ImageHolderMe(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val meImage: ImageView = itemView.findViewById(R.id.me_image)
        val meProfileImage: CircularImageView = itemView.findViewById(R.id.image_profile_me)
    }

    class ImageHolderSender(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderImage: ImageView = itemView.findViewById(R.id.sender_image)
        val senderProfileImage: CircularImageView = itemView.findViewById(R.id.image_profile_sender)
    }
}
