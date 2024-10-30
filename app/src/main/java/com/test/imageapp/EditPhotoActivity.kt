package com.test.imageapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class EditPhotoActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var originalBitmap: Bitmap
    private var brightness: Float = 0f
    private var contrast: Float = 1f
    private var saturation: Float = 1f
    private var photoPath: String? = null
    private var photoUri: Uri? = null
    private var photoId: Long? = null
    private var rotationAngle: Float = 0f
    private var editedBitmap: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_photo)

        imageView = findViewById(R.id.ivPhoto)

        photoUri = intent.getStringExtra("photoUri")?.let { Uri.parse(it) }
        photoPath = intent.getStringExtra("photoPath")
        photoId = intent.getLongExtra("photoId", -1).takeIf { it != -1L }

        originalBitmap = when {
            photoUri != null -> MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
            photoPath != null -> BitmapFactory.decodeFile(photoPath)
            else -> throw IllegalArgumentException("No photo source provided")
        }

        imageView.setImageBitmap(originalBitmap)

        setupSeekBars()
        setupButtons()
    }

    private fun setupSeekBars() {
        findViewById<SeekBar>(R.id.sbBrightness).apply {
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    brightness = (progress - 50) / 50f
                    applyImageFilters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<SeekBar>(R.id.sbContrast).apply {
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    contrast = (progress + 50) / 50f
                    applyImageFilters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<SeekBar>(R.id.sbSaturation).apply {
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    saturation = (progress + 50) / 50f
                    applyImageFilters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnRotate).setOnClickListener {
            rotateImage()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveEditedImage()
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun rotateImage() {
        // Increase rotation angle by 90 degrees on each click
        rotationAngle = (rotationAngle + 90) % 360

        // Create a rotated bitmap using the Matrix class
        val matrix = android.graphics.Matrix().apply { postRotate(rotationAngle) }
        editedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0,
            originalBitmap.width, originalBitmap.height,
            matrix, true
        )

        // Display the rotated image
        imageView.setImageBitmap(editedBitmap)
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { _, _ ->
                deletePhoto()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePhoto() {
        try {
            var deleted = false

            photoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    deleted = file.delete()
                }
            }

            if (photoId != null) {
                val deletedRows = contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(photoId.toString())
                )
                deleted = deletedRows > 0
            } else if (photoUri != null) {
                val deletedRows = contentResolver.delete(photoUri!!, null, null)
                deleted = deletedRows > 0
            }

            if (deleted) {
                Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Cannot delete photo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error while deleting photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyImageFilters() {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        val colorMatrix = ColorMatrix()

        val contrastMatrix = ColorMatrix().apply {
            val scale = contrast
            val translate = (1f - scale) * 127.5f
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorMatrix.postConcat(contrastMatrix)

        val brightnessMatrix = ColorMatrix().apply {
            val translateValue = brightness * 255
            set(floatArrayOf(
                1f, 0f, 0f, 0f, translateValue,
                0f, 1f, 0f, 0f, translateValue,
                0f, 0f, 1f, 0f, translateValue,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        colorMatrix.postConcat(brightnessMatrix)

        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        colorMatrix.postConcat(saturationMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        val editedBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        )

        Canvas(editedBitmap).drawBitmap(originalBitmap, 0f, 0f, paint)

        imageView.setImageBitmap(editedBitmap)
    }


    private fun saveEditedImage() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    val editedBitmap = (imageView.drawable as BitmapDrawable).bitmap
                    editedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_SHORT).show()
            } ?: throw Exception("Unable to create file")

            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        originalBitmap.recycle()
        (imageView.drawable as? BitmapDrawable)?.bitmap?.recycle()
    }
}