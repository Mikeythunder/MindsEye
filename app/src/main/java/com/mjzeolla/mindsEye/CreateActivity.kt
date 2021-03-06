package com.mjzeolla.mindsEye

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.mjzeolla.mindsEye.models.BoardSize
import com.mjzeolla.mindsEye.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        private const val PICK_PHOTO_CODE = 655
        private const val TAG = "CreateActivity"
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTO_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 15
    }

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton


    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private val chosenImageUris = mutableListOf<Uri>()
    private var numImages = -1

    private val storage = Firebase.storage
    private val dataBase = Firebase.firestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker =  findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)
        tvTitle = findViewById(R.id.tvTitle)
        btnBack = findViewById(R.id.imgBtnClose)

        btnBack.setOnClickListener(this)

        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImages = boardSize.getNumPairs()
        tvTitle.text = "Choose Pictures (0 / $numImages)"


        //This is the method to save the custom game to Firebase servers
        btnSave.setOnClickListener{
            saveDataToFirebase()
        }


        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}


        })

        adapter  = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener{
            override fun onPlaceholderClicked() {
                //Need to check if the user granted permission to use photos
                if(isPermissionGranted(this@CreateActivity, READ_PHOTO_PERMISSION)){
                    launchIntentForPhotos()
                }
                else{
                    requestPermission(this@CreateActivity, READ_PHOTO_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }

        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode == READ_EXTERNAL_PHOTOS_CODE){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                launchIntentForPhotos()
            else
                Toast.makeText(this, "To create custom game you must provide access to photos", Toast.LENGTH_LONG).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != PICK_PHOTO_CODE || resultCode != RESULT_OK || data == null){
            Log.w(TAG, "User canceled from photo picker")
            return
        }
        val selectedUri = data.data
        val clipData = data.clipData
        if(clipData!=null){
            Log.i(TAG, "clipData number of images ${clipData.itemCount}: $clipData")
            for(i in 0 until clipData.itemCount){
                val clipItem = clipData.getItemAt(i)
                if(chosenImageUris.size < numImages){
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if(selectedUri != null){
            Log.i(TAG,"data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose Pictures (${chosenImageUris.size} / $numImages)"

        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun saveDataToFirebase() {
        Log.i(TAG, "saveDataToFirebase")
        btnSave.isEnabled = false
        val gameName = etGameName.text.toString()
        //Need to make sure not overriding someone else's data
        dataBase.collection("games").document(gameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                        .setTitle("Name taken")
                        .setMessage("A game already exists with the name $gameName")
                        .setPositiveButton("OK", null)
                        .show()
                btnSave.isEnabled = true
            } else {
                handleImageUploading(gameName)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Encountered an error while saving memory game", exception)
            Toast.makeText(this, "Encountered an error while saving memory game", Toast.LENGTH_LONG).show()
            btnSave.isEnabled = true
        }
    }

    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()

        Log.i(TAG, "saveDataToFirebase")
        for((index, photoUri) in chosenImageUris.withIndex()){
            //This part of the function down grades all the image resolution for storage
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray).continueWithTask {photoUploadTask ->
                Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                photoReference.downloadUrl
            }.addOnCompleteListener { downloadUrlTask ->
                if(!downloadUrlTask.isSuccessful){
                    Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    didEncounterError = true
                    return@addOnCompleteListener
                }
                if(didEncounterError){
                    pbUploading.visibility = View.GONE
                    return@addOnCompleteListener
                }
                val downloadUrl = downloadUrlTask.result.toString()
                uploadedImageUrls.add(downloadUrl)
                pbUploading.progress = uploadedImageUrls.size * 100/ chosenImageUris.size
                Log.i(TAG, "Finished uploading $photoUri, number uploaded ${uploadedImageUrls.size}")
                if(uploadedImageUrls.size == chosenImageUris.size){
                    handleAllImagesUploaded(gameName, uploadedImageUrls)
                }
            }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, uploadedImageUrls: MutableList<String>) {
        dataBase.collection("games").document(gameName)
                .set(mapOf("images" to uploadedImageUrls))
                .addOnCompleteListener { gameCreationTask ->
                    pbUploading.visibility = View.GONE
                    if(!gameCreationTask.isSuccessful){
                        Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                        Toast.makeText(this, "Failed game creation", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    Log.i(TAG, "Successfully created game $gameName")
                    AlertDialog.Builder(this)
                            .setTitle("Upload complete! Let's play your game '$gameName'")
                            .setPositiveButton("OK"){_,_ ->
                                val resultData = Intent()
                                resultData.putExtra(EXTRA_GAME_NAME, gameName)
                                setResult(Activity.RESULT_OK, resultData)
                                finish()
                            }.show()
                }
    }

    //This part of the function down grades all the image resolution for storage
    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitMap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitMap.width} and height ${originalBitMap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitMap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImageUris.size != numImages){
            return false
        }
        if(etGameName.text.isEmpty() || etGameName.text.length < MIN_GAME_NAME_LENGTH){
            return false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Choose Pictures to add"), PICK_PHOTO_CODE)
    }

    override fun onClick(view: View?) {
        if (view == btnBack) {
            finish();
        }
    }

}