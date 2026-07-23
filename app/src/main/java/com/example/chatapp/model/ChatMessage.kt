package com.example.chatapp.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

class ChatMessage(
    val sender: User,
    val message: String,
    val image: String = "",
    @ServerTimestamp val timestamp: Date? = null
) {
    constructor() : this(User(), "", "", null)
}