package com.example.mydrawingapp

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.nvt.color.ColorPickerDialog
import android.Manifest
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    var progressDialog : Dialog? = null

    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result -> if(result.resultCode == RESULT_OK && result.data != null) {
                val background: ImageView = findViewById(R.id.id_background_image)
                background.setImageURI(result.data?.data)
            }
        }

    private val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    val intent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(intent)
                } else {
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)

        drawingView?.setSizeForBrush(20.toFloat())

        //SELECTING BRUSH SIZE
        val brushBtn = findViewById<ImageButton>(R.id.id_brush_btn)
        brushBtn.setOnClickListener{
            showBrushSizeChooserDialog()
        }
        //SELECTING COLOR
        val paintBucketBtn = findViewById<ImageButton>(R.id.id_paintBucket_btn)
        paintBucketBtn.setOnClickListener{
            val colorPicker = ColorPickerDialog(
                this,
                Color.BLACK, // color init
                true, // true is show alpha
                object : ColorPickerDialog.OnColorPickerListener {
                    override fun onCancel(dialog: ColorPickerDialog?) {
                        // handle click button Cancel

                    }

                    override fun onOk(dialog: ColorPickerDialog?, colorPicker: Int) {
                        // handle click button OK
                        drawingView?.setColor(colorPicker)
                    }
                })
            colorPicker.show()
        }
        //UNDO
        val undoBtn = findViewById<ImageButton>(R.id.id_undo_btn)
        undoBtn.setOnClickListener {
            drawingView?.undo()
        }

        //REDO

        val redoBtn = findViewById<ImageButton>(R.id.id_redo_btn)
        redoBtn.setOnClickListener {
            drawingView?.redo()
        }

        //CLEAR

        val clearBtn = findViewById<ImageButton>(R.id.id_clear_btn)
        clearBtn.setOnClickListener {
            drawingView?.clear()
        }

        //SETTING UP THE IMAGE

        val imageButton: ImageButton = findViewById(R.id.id_addImage_btn)
        imageButton.setOnClickListener {
            requestStoragePermission()
        }

        //REMOVING BACKGROUND

        val removeBackground = findViewById<ImageButton>(R.id.id_removeBackground_btn)
        removeBackground.setOnClickListener {
            val background: ImageView = findViewById(R.id.id_background_image)
            background.setImageResource(R.drawable.white_background)
        }
        // SAVING THE IMAGE
        val saveBtn = findViewById<ImageButton>(R.id.id_save_btn)
        saveBtn.setOnClickListener {
            showProgressDialog()
            if(isReadStorageAllowed()) {
                lifecycleScope.launch{
                    val flView = findViewById<FrameLayout>(R.id.id_frame_layout)
                    saveImage(getBitmap(flView))
                }
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size")
        brushDialog.show()
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.id_small_button)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.id_medium_button)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.id_large_button)
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }


    private fun requestStoragePermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("Drawing App", "Drawing App needs to Access External Storage")
        }
        else{
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }


    private fun showRationalDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
            .setPositiveButton("Cancel") { dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmap(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDraw = view.background
        if(bgDraw != null) {
            bgDraw.draw(canvas)
        }
        else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return returnedBitmap

    }

    private suspend fun saveImage(mBitmap: Bitmap?) : String {
        var result = ""
        withContext(Dispatchers.IO) {
            if(mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(Environment.DIRECTORY_DOWNLOADS +
                        System.currentTimeMillis()/1000 + ".png")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        dismissProgressDialog()
                        if(result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File Saved Successfully $result",
                                Toast.LENGTH_LONG).show()
                            shareImage(result)
                        }
                        else {
                            Toast.makeText(this@MainActivity, "File Not Saved, Something went wrong",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog() {
        progressDialog = Dialog(this@MainActivity)

        progressDialog?.setContentView(R.layout.progress_bar_dialog)
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        if(progressDialog != null) {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }

    private fun shareImage(result : String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) {
            path, uri ->
            val shareIntent = Intent()
             shareIntent.action = ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share Image") )
        }
    }


}