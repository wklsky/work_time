/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraScreen.kt
 * @Description: 拍照识别入口界面，CameraX 预览与拍照，取景框引导用户对齐考勤机屏幕
 */
package com.worktime.pro.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // 相册选择：用户可上传已有考勤照片，与拍照走同一识别链路
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            decodeUriToBitmap(context, uri)?.let(onCaptured)
                ?: Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予相机权限")
            }
            androidx.compose.material3.TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                Text("从相册选择")
            }
        }
        return
    }

    CameraPreview(
        context = context,
        onCaptured = onCaptured,
        onClose = onClose,
        onPickFromGallery = { galleryLauncher.launch("image/*") }
    )
}

/** 兼容 Android 各版本的 Uri 解码，失败返回 null 由调用方提示用户 */
private fun decodeUriToBitmap(context: Context, uri: Uri): android.graphics.Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    null
}

@Composable
private fun CameraPreview(
    context: Context,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onClose: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(context, "相机启动失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))

        // 解绑并关闭执行器，防止内存泄漏
        onDispose {
            camera = null
            torchOn = false
            cameraExecutor.shutdown()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = androidx.compose.ui.graphics.Color.White)
        }

        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = {
                    val next = !torchOn
                    camera?.cameraControl?.enableTorch(next)
                    torchOn = next
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        Text(
            "请将考勤机屏幕完整放入取景框",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "避免反光 · 保持稳定 · 确保文字清晰",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )

        // 取景框：引导用户把考勤机屏幕放入中央区域
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.52f)
                .border(
                    width = 1.dp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { capture(imageCapture, cameraExecutor, context, onCaptured) }) {
                Text("拍照")
            }
            androidx.compose.material3.TextButton(
                onClick = onPickFromGallery,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text("从相册选择")
            }
        }
    }
}

/** ImageCapture 回调必须在主线程切回，Bitmap 生成后交给 ViewModel 识别 */
private fun capture(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    context: Context,
    onCaptured: (android.graphics.Bitmap) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                val bitmap = image.toBitmap()
                image.close()
                android.os.Handler(context.mainLooper).post { onCaptured(bitmap) }
            }

            override fun onError(exception: ImageCaptureException) {
                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(context, "拍照失败：${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}
