package com.example.chatapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.example.chatapp.databinding.ActivityMainBinding
import com.example.chatapp.model.User
import com.example.chatapp.ui.ChatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private lateinit var getResult: ActivityResultLauncher<Intent>
    private val STORAGE_REQUEST_CODE = 23423
    private lateinit var uri: Uri
    private lateinit var storageRef: StorageReference
    private lateinit var db: FirebaseFirestore
    private lateinit var mUsersRef: CollectionReference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-login: if user already signed in, skip to ChatActivity
        if (FirebaseAuth.getInstance().currentUser != null) {
            sendToActivity()
            return
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        mUsersRef = db.collection("users_collection")
        storageRef = FirebaseStorage.getInstance().reference


        ViewCompat.setOnApplyWindowInsetsListener(mBinding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Sign In button
        mBinding.signInButton.setOnClickListener {
            signIn()
        }

        // Sign Up button (on sign-up form page)
        mBinding.signUpButton.setOnClickListener {
            createAccount()
        }

        // "Don't have an account? Register" → go to sign up page
        mBinding.textViewRegister.setOnClickListener {
            showFlipperPage(1)
        }

        // "Already have an account? Sign In" → go back to sign in page
        mBinding.textViewSignIn.setOnClickListener {
            showFlipperPage(0)
        }

        // "Pick a Profile Photo (Optional)" → go to profile photo page
        mBinding.textViewGoToProfile.setOnClickListener {
            showFlipperPage(2)
        }

        // "Sign Up" link on profile page → go back to sign up page
        mBinding.textViewSignUp.setOnClickListener {
            showFlipperPage(1)
        }

        // Profile image click → pick image
        mBinding.profileImage.setOnClickListener {
            val permission = getPermissionToRequest()
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
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
                mBinding.profileImage.setImageURI(it.data?.data)
                uri = it.data?.data!!
            }
        }
    }

    private fun showFlipperPage(index: Int) {
        val current = mBinding.flipper.displayedChild
        if (index > current) {
            mBinding.flipper.setInAnimation(this, android.R.anim.slide_in_left)
            mBinding.flipper.setOutAnimation(this, android.R.anim.slide_out_right)
            mBinding.flipper.displayedChild = index
        } else {
            mBinding.flipper.setInAnimation(this, R.anim.slide_in_right)
            mBinding.flipper.setOutAnimation(this, R.anim.slide_out_left)
            mBinding.flipper.displayedChild = index
        }
    }

    private fun getPermissionToRequest(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun signIn(
        email: String = mBinding.signInInputEmail.editText?.text.toString().trim(),
        password: String = mBinding.signInInputPassword.editText?.text.toString()
            .trim()
    ) {
        showProgressBar1()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter your email and password", Toast.LENGTH_LONG)
                .show()
            hideProgressBar1()
            return
        }

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    hideProgressBar1()
                    sendToActivity()
                } else {
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Sign in failed",
                        Toast.LENGTH_LONG
                    ).show()
                    hideProgressBar1()
                }
            }
    }

    private fun createAccount() {
        showProgressBar2()

        val email = mBinding.signUpInputEmail.text.toString().trim()
        val password = mBinding.signUpInputPassword.text.toString().trim()
        val confirmPassword = mBinding.signUpInputConfirmPassword.text.toString().trim()
        val username = mBinding.signUpInputUsername.text.toString().trim()


        if (username.isEmpty()) {
            Toast.makeText(this, "Please enter a username", Toast.LENGTH_LONG)
                .show()
            hideProgressBar2()
            return
        }
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_LONG)
                .show()
            hideProgressBar2()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_LONG).show()
            hideProgressBar2()
            return
        }
        if (password.length < 6) {
            Toast.makeText(
                this,
                "Password should be at least 6 characters",
                Toast.LENGTH_LONG
            ).show()
            hideProgressBar2()
            return
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    if (this::uri.isInitialized) {
                        val filePath = storageRef.child("profile_images")
                            .child("${FirebaseAuth.getInstance().currentUser!!.uid}.jpg")

                        filePath.putFile(uri).addOnSuccessListener { uploadTask ->
                            val result: Task<Uri> = uploadTask.metadata?.reference?.downloadUrl!!
                            result.addOnSuccessListener { downloadUri ->
                                val user = User(
                                    username,
                                    downloadUri.toString(),
                                    FirebaseAuth.getInstance().currentUser!!.uid
                                )
                                saveUserAndProceed(user)
                            }
                        }.addOnFailureListener {
                            // Profile image upload failed — create account without image
                            val user = User(
                                username,
                                "",
                                FirebaseAuth.getInstance().currentUser!!.uid
                            )
                            saveUserAndProceed(user)
                        }
                    } else {
                        val user =
                            User(username, "", FirebaseAuth.getInstance().currentUser!!.uid)
                        saveUserAndProceed(user)
                    }
                } else {
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Account creation failed",
                        Toast.LENGTH_LONG
                    ).show()
                    hideProgressBar2()
                }
            }

    }

    private fun saveUserAndProceed(user: User) {
        mUsersRef.document()
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(
                    this@MainActivity,
                    "Account Created Successfully!",
                    LENGTH_LONG
                ).show()
                hideProgressBar2()
                sendToActivity()
            }.addOnFailureListener {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to save user data: ${it.message}",
                    LENGTH_LONG
                )
                    .show()
                hideProgressBar2()
            }
    }

    private fun getImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        getResult.launch(intent)
    }

    private fun requestPermission() {
        val permission = getPermissionToRequest()
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this@MainActivity,
                permission
            )
        ) {
            AlertDialog.Builder(this@MainActivity)
                .setPositiveButton("Yes") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(permission),
                        STORAGE_REQUEST_CODE
                    )
                }.setNegativeButton("No") { dialog, _ ->
                    dialog.cancel()
                }.setTitle("Permission Needed")
                .setMessage("This app needs permission to access your storage to set a profile picture.")
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(permission),
                STORAGE_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getImage()
        } else {
            Toast.makeText(this@MainActivity, "Permission Denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendToActivity() {
        startActivity(Intent(this@MainActivity, ChatActivity::class.java))
        finish()
    }


    private fun showProgressBar1() {
        if (::mBinding.isInitialized) mBinding.progressBar1.visibility = View.VISIBLE
    }

    private fun hideProgressBar1() {
        if (::mBinding.isInitialized) mBinding.progressBar1.visibility = View.GONE
    }

    private fun showProgressBar2() {
        if (::mBinding.isInitialized) mBinding.progressBar2.visibility = View.VISIBLE
    }

    private fun hideProgressBar2() {
        if (::mBinding.isInitialized) mBinding.progressBar2.visibility = View.GONE
    }


}
