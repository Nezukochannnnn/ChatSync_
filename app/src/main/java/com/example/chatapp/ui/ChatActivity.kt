package com.example.chatapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.adaptors.MessagesAdaptor
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class ChatActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var usersRef: CollectionReference
    private lateinit var messageRef: CollectionReference
    private lateinit var sendButton: ImageButton
    private lateinit var attachImageButton: ImageButton
    private lateinit var editTextMessage: EditText
    private lateinit var messagesAdaptor: MessagesAdaptor
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messages: MutableList<ChatMessage>
    private lateinit var currentUser: User
    private lateinit var storageReference: StorageReference
    private lateinit var uri: Uri
    private lateinit var getResult: ActivityResultLauncher<Intent>
    private lateinit var progressBar: ProgressBar
    private val storageRequestCode = 23423


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
        editTextMessage = findViewById(R.id.input_message)
        messageRecyclerView = findViewById(R.id.message_recycler_view)
        storageReference = FirebaseStorage.getInstance().reference
        progressBar = findViewById(R.id.progressbarChatBot)

        initRecyclerView()
        getCurrentUser()

        sendButton.setOnClickListener { insertMessage() }

        attachImageButton.setOnClickListener {
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

        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.data?.let { dataUri ->
                    uri = dataUri
                    uploadImage()
                }
            }
        }
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

        messages.clear()
        messageRef.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener(this) { snapshots, error ->
                error?.let {
                    return@addSnapshotListener
                }

                snapshots?.let {
                    for (dc in it.documentChanges) {
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                val message = dc.document.toObject(ChatMessage::class.java)
                                messages.add(message)
                                messagesAdaptor.notifyItemInserted(messages.size - 1)
                                messageRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }
                            DocumentChange.Type.REMOVED -> {
                                // Optional: Handle removed messages
                            }
                            DocumentChange.Type.MODIFIED -> {
                                // Optional: Handle modified messages
                            }
                        }
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
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null) {
            usersRef.whereEqualTo("id", currentUid)
                .get()
                .addOnSuccessListener {
                    for (snapshot in it) {
                        currentUser = snapshot.toObject(User::class.java)
                        val toolbarAvatar = findViewById<com.mikhaellopez.circularimageview.CircularImageView>(R.id.toolbar_avatar)
                        if (toolbarAvatar != null) {
                            val initialDrawable = com.example.chatapp.utils.AvatarUtils.getAvatarDrawable(this, currentUser, 40)
                            toolbarAvatar.borderColor = com.example.chatapp.utils.AvatarUtils.getColorForUser(currentUser.id.ifEmpty { currentUser.name })
                            if (currentUser.profileImage.isNotEmpty()) {
                                com.squareup.picasso.Picasso.get()
                                    .load(currentUser.profileImage)
                                    .placeholder(initialDrawable)
                                    .error(initialDrawable)
                                    .into(toolbarAvatar)
                            } else {
                                toolbarAvatar.setImageDrawable(initialDrawable)
                            }
                        }
                    }
                }
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
                Toast.makeText(this, "Wait for user data to load", Toast.LENGTH_SHORT).show()
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
                .setMessage("This permission is needed for accessing the internal storage")
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
        if (this::uri.isInitialized) {
            showProgressBar()

            val filePath = storageReference.child("chat_images")
                .child("${System.currentTimeMillis()}.image")

            filePath.putFile(uri).addOnSuccessListener { task ->
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
                                    Toast.makeText(this, "Image wasn't sent!", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                hideProgressBar()
                            }
                    } else {
                        hideProgressBar()
                        Toast.makeText(this, "User info not available", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener {
                hideProgressBar()
                Toast.makeText(this, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showProgressBar() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        progressBar.visibility = View.GONE
    }

}
