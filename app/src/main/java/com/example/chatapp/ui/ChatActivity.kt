package com.example.chatapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.adaptors.EmojiAdapter
import com.example.chatapp.adaptors.MessagesAdaptor
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.User
import com.example.chatapp.utils.AvatarUtils
import com.example.chatapp.utils.EmojiData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.mikhaellopez.circularimageview.CircularImageView
import com.squareup.picasso.Picasso

class ChatActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var usersRef: CollectionReference
    private lateinit var messageRef: CollectionReference
    private lateinit var sendButton: ImageButton
    private lateinit var attachImageButton: ImageButton
    private lateinit var emojiToggleButton: ImageButton
    private lateinit var editTextMessage: EditText
    private lateinit var messagesAdaptor: MessagesAdaptor
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messages: MutableList<ChatMessage>
    private lateinit var currentUser: User
    private lateinit var storageReference: StorageReference
    private var selectedImageUri: Uri? = null
    private lateinit var getResult: ActivityResultLauncher<Intent>
    private lateinit var progressBar: ProgressBar
    private val storageRequestCode = 23423
    private var messageListener: ListenerRegistration? = null

    // Emoji Section Views
    private lateinit var emojiPickerContainer: View
    private lateinit var emojiRecyclerView: RecyclerView
    private lateinit var emojiAdapter: EmojiAdapter
    private lateinit var emojiBackspaceButton: ImageButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        db = FirebaseFirestore.getInstance()
        usersRef = db.collection("users_collection")
        messageRef = db.collection("messages_collection")

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sendButton = findViewById(R.id.send_message_button)
        attachImageButton = findViewById(R.id.attach_image_button)
        emojiToggleButton = findViewById(R.id.emoji_toggle_button)
        editTextMessage = findViewById(R.id.input_message)
        messageRecyclerView = findViewById(R.id.message_recycler_view)
        storageReference = FirebaseStorage.getInstance().reference
        progressBar = findViewById(R.id.progressbarChatBot)

        initRecyclerView()
        initEmojiSection()
        getCurrentUser()

        sendButton.setOnClickListener { insertMessage() }

        attachImageButton.setOnClickListener {
            hideEmojiPicker()
            val permission = getPermissionToRequest()
            if (ActivityCompat.checkSelfPermission(
                    this@ChatActivity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermission()
            } else {
                getImage()
            }
        }

        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                selectedImageUri = result.data?.data
                uploadImage()
            }
        }
    }

    private fun initEmojiSection() {
        emojiPickerContainer = findViewById(R.id.emoji_picker_container)
        emojiRecyclerView = findViewById(R.id.emoji_recycler_view)
        emojiBackspaceButton = findViewById(R.id.emoji_backspace_button)

        emojiAdapter = EmojiAdapter(EmojiData.SMILEYS) { emoji ->
            insertEmojiIntoInput(emoji)
        }

        emojiRecyclerView.layoutManager = GridLayoutManager(this, 7)
        emojiRecyclerView.adapter = emojiAdapter

        emojiToggleButton.setOnClickListener {
            toggleEmojiPicker()
        }

        editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                hideEmojiPicker()
            }
        }

        editTextMessage.setOnClickListener {
            hideEmojiPicker()
        }

        emojiBackspaceButton.setOnClickListener {
            editTextMessage.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }

        // Category Tabs
        findViewById<TextView>(R.id.tab_smileys).setOnClickListener {
            emojiAdapter.updateEmojis(EmojiData.SMILEYS)
        }
        findViewById<TextView>(R.id.tab_hearts).setOnClickListener {
            emojiAdapter.updateEmojis(EmojiData.REACTIONS)
        }
        findViewById<TextView>(R.id.tab_hands).setOnClickListener {
            emojiAdapter.updateEmojis(EmojiData.GESTURES)
        }
        findViewById<TextView>(R.id.tab_fire).setOnClickListener {
            emojiAdapter.updateEmojis(EmojiData.POPULAR)
        }
    }

    private fun toggleEmojiPicker() {
        if (emojiPickerContainer.visibility == View.VISIBLE) {
            hideEmojiPicker()
            showKeyboard()
        } else {
            hideKeyboard()
            emojiPickerContainer.visibility = View.VISIBLE
        }
    }

    private fun hideEmojiPicker() {
        if (emojiPickerContainer.visibility == View.VISIBLE) {
            emojiPickerContainer.visibility = View.GONE
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editTextMessage.windowToken, 0)
    }

    private fun showKeyboard() {
        editTextMessage.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editTextMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun insertEmojiIntoInput(emoji: String) {
        val start = editTextMessage.selectionStart.coerceAtLeast(0)
        val end = editTextMessage.selectionEnd.coerceAtLeast(0)
        val minPos = Math.min(start, end)
        val maxPos = Math.max(start, end)
        editTextMessage.text?.replace(minPos, maxPos, emoji)
        editTextMessage.setSelection(minPos + emoji.length)
    }

    private fun getPermissionToRequest(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    override fun onStart() {
        super.onStart()
        attachMessageListener()
    }

    override fun onStop() {
        super.onStop()
        messageListener?.remove()
        messageListener = null
    }

    private fun attachMessageListener() {
        messageListener?.remove()
        messageListener = messageRef.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener(this) { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                var hasNewItems = false
                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        try {
                            val message = dc.document.toObject(ChatMessage::class.java)
                            messages.add(message)
                            messagesAdaptor.notifyItemInserted(messages.size - 1)
                            hasNewItems = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (hasNewItems && messages.isNotEmpty()) {
                    messageRecyclerView.post {
                        messageRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
    }

    private fun initRecyclerView() {
        messages = mutableListOf()
        messagesAdaptor = MessagesAdaptor(this, messages)
        messageRecyclerView.adapter = messagesAdaptor
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        messageRecyclerView.layoutManager = layoutManager
        messageRecyclerView.setHasFixedSize(false)
    }

    private fun getCurrentUser() {
        val authUser = FirebaseAuth.getInstance().currentUser ?: return
        val currentUid = authUser.uid

        val fallbackName = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "User"
        currentUser = User(name = fallbackName, profileImage = "", id = currentUid)
        updateToolbarAvatar(currentUser)

        usersRef.document(currentUid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                snapshot.toObject(User::class.java)?.let { u ->
                    currentUser = u
                    updateToolbarAvatar(currentUser)
                }
            } else {
                usersRef.whereEqualTo("id", currentUid).get().addOnSuccessListener { querySnap ->
                    for (doc in querySnap) {
                        val u = doc.toObject(User::class.java)
                        currentUser = u
                        updateToolbarAvatar(currentUser)
                    }
                }
            }
        }
    }

    private fun updateToolbarAvatar(user: User) {
        val toolbarAvatar = findViewById<CircularImageView>(R.id.toolbar_avatar) ?: return
        val initialDrawable = AvatarUtils.getAvatarDrawable(this, user, 40)
        toolbarAvatar.borderColor = AvatarUtils.getColorForUser(user.id.ifEmpty { user.name })

        if (user.profileImage.isNotEmpty()) {
            Picasso.get()
                .load(user.profileImage)
                .placeholder(initialDrawable)
                .error(initialDrawable)
                .into(toolbarAvatar)
        } else {
            toolbarAvatar.setImageDrawable(initialDrawable)
        }
    }

    private fun insertMessage() {
        val messageText = editTextMessage.text.toString().trim()

        if (messageText.isNotEmpty()) {
            if (::currentUser.isInitialized) {
                messageRef.document()
                    .set(ChatMessage(currentUser, messageText))
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            editTextMessage.setText("")
                        }
                    }
            } else {
                Toast.makeText(this, "Loading user profile...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_sign_out -> {
                messageListener?.remove()
                messageListener = null
                FirebaseAuth.getInstance().signOut()
                Intent(this@ChatActivity, MainActivity::class.java).also {
                    startActivity(it)
                }
                finish()
                return true
            }
        }
        return false
    }

    private fun getImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        getResult.launch(intent)
    }

    private fun requestPermission() {
        val permission = getPermissionToRequest()
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this@ChatActivity,
                permission
            )
        ) {
            AlertDialog.Builder(this@ChatActivity)
                .setPositiveButton(R.string.dialog_button_yes) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this@ChatActivity,
                        arrayOf(permission),
                        storageRequestCode
                    )
                }
                .setNegativeButton(R.string.dialog_button_no) { dialog, _ ->
                    dialog.cancel()
                }
                .setTitle("Permission needed")
                .setMessage("This permission is needed for accessing internal storage to attach photos.")
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this@ChatActivity,
                arrayOf(permission),
                storageRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == storageRequestCode && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getImage()
        } else {
            Toast.makeText(this@ChatActivity, "Permission Denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadImage() {
        val localUri = selectedImageUri ?: return
        showProgressBar()

        val filePath = storageReference.child("chat_images")
            .child("${System.currentTimeMillis()}.jpg")

        filePath.putFile(localUri).addOnSuccessListener { task ->
            task.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                val downloadUrlString = downloadUri.toString()

                if (::currentUser.isInitialized) {
                    val message = ChatMessage(currentUser, "", downloadUrlString)
                    messageRef.document()
                        .set(message)
                        .addOnCompleteListener { firestoreTask ->
                            if (firestoreTask.isSuccessful) {
                                Toast.makeText(this, "Image sent!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Image send failed", Toast.LENGTH_SHORT).show()
                            }
                            hideProgressBar()
                        }
                } else {
                    hideProgressBar()
                    Toast.makeText(this, "User info not available", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                hideProgressBar()
                Toast.makeText(this, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            hideProgressBar()
            Toast.makeText(this, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProgressBar() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        progressBar.visibility = View.GONE
    }
}
