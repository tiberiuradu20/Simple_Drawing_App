package com.example.simpledrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private var drawingView:DrawingView?=null
    private var mImageButtonCurrentPaint:ImageButton?=null
    var customProgressDialog:Dialog?=null



    val openGalleryLauncher:ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode== RESULT_OK && result.data!=null){
               val imageBackGround:ImageView=findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
         permissions ->
            permissions.entries.forEach{
              val permissionName=it.key
              val isGranted=it.value
               if(isGranted){
                        Toast.makeText(
                            this, "Permission granted, now you can read the storage files",
                            Toast.LENGTH_LONG
                        ).show()
                   val pickIntent=Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                   openGalleryLauncher.launch(pickIntent)
                }
                else{
               if(permissionName==Manifest.permission.READ_MEDIA_IMAGES){
                       Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show()
                   }
                }
            }

        }


    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView=findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors=findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint=linearLayoutPaintColors[0] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )
       val ib_brush:ImageButton=findViewById(R.id.id_brush)
        ib_brush.setOnClickListener{
            showBrushSizeChooserDialog()
        }
       val ib_undo:ImageButton=findViewById(R.id.id_undo)
       ib_undo.setOnClickListener {view->
           drawingView?.onClickUndo()
       }
        val ib_redo:ImageButton=findViewById(R.id.id_redo)
        ib_redo.setOnClickListener {view->
            drawingView?.onClickRedo()
        }


        val ibGallery:ImageButton=findViewById(R.id.ib_gallery)

        ibGallery.setOnClickListener {
            requestStoragePermission()
        }
      val ibSave:ImageButton=findViewById(R.id.id_save)


        ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }


    }
    private fun isReadStorageAllowed():Boolean{

        val result=ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_IMAGES)
        return result == PackageManager.PERMISSION_GRANTED
    }
     private fun requestStoragePermission() {
       if (ActivityCompat.shouldShowRequestPermissionRationale(
                 this,
                 Manifest.permission.READ_MEDIA_IMAGES
             )
         ) {
             showRationaleDialog("Drawing App", "Drawing App" + " needs to Access Your" +
                     " external storage")
         } else {
             requestPermission.launch(
                 arrayOf(
                     Manifest.permission.READ_MEDIA_IMAGES,
                     Manifest.permission.WRITE_EXTERNAL_STORAGE

                 )
             )

         }
     }


    private fun showRationaleDialog(title: String, message:String){
        val builder: AlertDialog.Builder= AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message)
            .setPositiveButton("OK"){dialog,_->
                dialog.dismiss()
            }
        builder.create().show()
    }///Aceasta este o metoda foarte importanta!!!!!!!

    @SuppressLint("SuspiciousIndentation")///Abureala!!!!!!!!!!
    private fun showBrushSizeChooserDialog(){
       val brushDialog=Dialog(this)
          brushDialog.setContentView(R.layout.dialog_brush_size)
          brushDialog.setTitle("Brush size: ")
        val smallBtn= brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn= brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val largeBtn= brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)



      smallBtn.setOnClickListener{
          drawingView?.setSizeForBrush(10.toFloat())
          brushDialog.dismiss()
      }
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()




    }
   fun paintClicked(view: View){
       if(view!==mImageButtonCurrentPaint){
        val imageButton=view as ImageButton
        val colorTag=imageButton.tag.toString()
        drawingView?.setColor(colorTag)

           imageButton!!.setImageDrawable(
               ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
           )
           mImageButtonCurrentPaint!!.setImageDrawable(
               ContextCompat.getDrawable(this,R.drawable.pallet_normal)
           )
           mImageButtonCurrentPaint=view
       }
   }
    private fun getBitmapFromView(view: View):Bitmap{
      val returnedBitmap=Bitmap.createBitmap(view.width,view.height,
           Bitmap.Config.ARGB_8888)
       val canvas=Canvas(returnedBitmap)
        val bgDrawable=view.background
        if(bgDrawable!=null){
            bgDrawable.draw(canvas)
        }
        else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }
  /*private suspend fun saveBitmapFile(mBitmap: Bitmap):String{
        var result=""
      val bytes=ByteArrayOutputStream()
        withContext(Dispatchers.IO){
            if(mBitmap!=null){
                try{

                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                val file= File(externalCacheDir?.absoluteFile.toString() +
                File.separator+"SimpleDrawingApp"+System.currentTimeMillis() /1000 + ".png")
               val fo=FileOutputStream(file)
                    fo.write(bytes.toByteArray())
                    fo.close()
                   result=file.absolutePath
                   runOnUiThread{
                       if(result.isNotEmpty()){
                           Toast.makeText(this@MainActivity,"File Saved",
                               Toast.LENGTH_LONG).show()
                       }else{
                           Toast.makeText(this@MainActivity,"Error",
                               Toast.LENGTH_LONG).show()
                       }
                   }
                }catch(e:Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result
    }*/
  /*private suspend fun saveBitmapFile(mBitmap: Bitmap): Uri? { //VARIANTA CORECTA
      return withContext(Dispatchers.IO) {
          var uri: Uri? = null
          try {
              val values = ContentValues().apply {
                  put(MediaStore.Images.Media.DISPLAY_NAME, "SimpleDrawingApp_${System.currentTimeMillis() / 1000}.png")
                  put(MediaStore.Images.Media.MIME_TYPE, "image/png")
              }

              val contentResolver = contentResolver
              val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

              val item = contentResolver.insert(collection, values)

              contentResolver.openOutputStream(item!!)?.use { outputStream ->
                  mBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
              }

              uri = item
              runOnUiThread {
                  cancelProgressDialog()
                  if (uri != null) {
                      Toast.makeText(this@MainActivity, "File saved", Toast.LENGTH_LONG).show()
                  shareImage(uri)
                  } else {
                      Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_LONG).show()
                  }
              }
          } catch (e: Exception) {
              e.printStackTrace()
          }
          uri
      }
  }*/
  /*private suspend fun saveBitmapFile(mBitmap: Bitmap): Uri? {
      //VARIANTA CARE FUNCTIONEAZA SI PENTRU API-URI MAI MICI DE 34
      return withContext(Dispatchers.IO) {
          var uri: Uri? = null
          try {
              val values = ContentValues().apply {
                  put(MediaStore.Images.Media.DISPLAY_NAME, "SimpleDrawingApp_${System.currentTimeMillis() / 1000}.png")
                  put(MediaStore.Images.Media.MIME_TYPE, "image/png")
              }

              val contentResolver = contentResolver

              // Use the appropriate API based on the device's version
              val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
              } else {
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI
              }

              val item = contentResolver.insert(collection, values)

              contentResolver.openOutputStream(item!!)?.use { outputStream ->
                  mBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
              }

              uri = item
              launch(Dispatchers.Main) {

                  if (uri != null) {
                      Toast.makeText(this@MainActivity, "File saved", Toast.LENGTH_LONG).show()
                  } else {
                      Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_LONG).show()
                  }
              }
          } catch (e: Exception) {
              e.printStackTrace()
          }
          uri
      }
  }*/
    private fun showProgressDialog(){
        customProgressDialog=Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if(customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog=null
        }
    }
   /*private fun shareImage(result:String){
       MediaScannerConnection.scanFile(this,arrayOf(result),null){
           path,uri->
           val shareIntent=Intent()
           shareIntent.action=Intent.ACTION_SEND
           shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
           shareIntent.type="image/png"
           startActivity(Intent.createChooser(shareIntent,"Share"))
       }
   }*/
   private fun shareImage(uri: Uri) {
       try {
           val shareIntent = Intent(Intent.ACTION_SEND)
           shareIntent.type = "image/png"
           shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
           startActivity(Intent.createChooser(shareIntent, "Share Image"))
       } catch (e: Exception) {
           e.printStackTrace()
           Toast.makeText(this@MainActivity, "Error sharing image", Toast.LENGTH_SHORT).show()
       }
   }
    private suspend fun saveBitmapFile(mBitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            var uri: Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "SimpleDrawingApp_${System.currentTimeMillis() / 1000}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }

                val contentResolver = contentResolver
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val item = contentResolver.insert(collection, values)

                contentResolver.openOutputStream(item!!)?.use { outputStream ->
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                }

                uri = item
                runOnUiThread {
                    cancelProgressDialog()
                    if (uri != null) {
                        Toast.makeText(this@MainActivity, "File saved", Toast.LENGTH_LONG).show()
                        shareImage(uri)
                    } else {
                        Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uri
        }
    }




}