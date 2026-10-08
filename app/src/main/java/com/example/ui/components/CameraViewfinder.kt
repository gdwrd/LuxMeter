package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CameraFormat
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.HapticManager
import com.example.ui.theme.VintageBrassBezel
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface
import com.example.ui.theme.VintageWitnessAmber
import com.example.ui.theme.VintageWitnessRed
import java.util.concurrent.Executors
import kotlin.math.log2
import kotlin.math.roundToInt

@Composable
fun CameraViewfinder(
  format: CameraFormat,
  focalLengthMm: Int,
  onFocalLengthChanged: (Int) -> Unit,
  hapticManager: HapticManager,
  isLocked: Boolean,
  onToggleLock: () -> Unit,
  onMeasuredEvChanged: (Double) -> Unit,
  modifier: Modifier = Modifier,
  currentEv: Double = 12.0,
  viewportHeightDp: Int = 220
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val view = LocalView.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
    if (granted) {
      hapticManager.performDialClick(view)
    }
  }

  // Spot tap coordinates normalized (0f..1f), default center
  var spotPoint by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
  var isSpotMode by remember { mutableStateOf(true) }
  var spotLuminance by remember { mutableFloatStateOf(128f) }
  var showLensPicker by remember { mutableStateOf(false) }

  // Rate-limiting state for smooth and fast rendering
  var lastReportedEv by remember { androidx.compose.runtime.mutableDoubleStateOf(currentEv) }
  var lastReportTimeMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

  // CameraControl for optical framing zoom
  var cameraControlInstance by remember { mutableStateOf<CameraControl?>(null) }

  // Crop factor calculation
  val cropFactor = DepthOfFieldCalculator.getCropFactor(format)
  val eq35mmFocal = focalLengthMm * cropFactor

  // Standard smartphone primary lens base focal length (~26mm)
  val basePhoneFocal = 26.0
  val targetZoomRatio = (eq35mmFocal / basePhoneFocal).toFloat().coerceIn(1.0f, 8.0f)

  // Lens choices for quick selection
  val commonFocals = listOf(24, 28, 35, 50, 75, 80, 90, 105, 135, 200)

  // Apply optical zoom when focal length or format changes
  LaunchedEffect(targetZoomRatio, cameraControlInstance) {
    try {
      cameraControlInstance?.setZoomRatio(targetZoomRatio)
    } catch (_: Exception) {
      // Ignored if device doesn't support hardware level
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(viewportHeightDp.dp)
      .shadow(8.dp, RoundedCornerShape(14.dp))
      .clip(RoundedCornerShape(14.dp))
      .background(VintageLeatherDark)
      .border(2.dp, VintageBrassBezel, RoundedCornerShape(14.dp))
  ) {
    if (!hasCameraPermission) {
      // Clean Permission Request State
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(VintageLeatherSurface)
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = "Camera viewfinder",
          tint = VintageBrassPrimary,
          modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "OPTICAL VIEWFINDER",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Serif,
          color = VintageBrassBright
        )
        Text(
          text = "Frame ${format.displayName} & spot-meter exposure with ${focalLengthMm}mm framing.",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          color = VintageIvoryMuted,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = {
            hapticManager.performDialClick(view)
            permissionLauncher.launch(Manifest.permission.CAMERA)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = VintageBrassPrimary,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = "ENABLE CAMERA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    } else {
      val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

      DisposableEffect(Unit) {
        onDispose {
          cameraExecutor.shutdown()
        }
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            detectTapGestures { offset ->
              hapticManager.performDialClick(view)
              val normX = (offset.x / size.width).coerceIn(0.1f, 0.9f)
              val normY = (offset.y / size.height).coerceIn(0.1f, 0.9f)
              spotPoint = Offset(normX, normY)
            }
          }
      ) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
              scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
              }

              val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

              imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!isLocked) {
                  val yPlane = imageProxy.planes.firstOrNull()?.buffer
                  if (yPlane != null) {
                    val width = imageProxy.width
                    val height = imageProxy.height

                    var sum = 0L
                    var count = 0

                    if (isSpotMode) {
                      val centerX = (spotPoint.x * width).toInt().coerceIn(0, width - 1)
                      val centerY = (spotPoint.y * height).toInt().coerceIn(0, height - 1)
                      val radius = (width * 0.08f).toInt().coerceAtLeast(8)

                      val minX = (centerX - radius).coerceAtLeast(0)
                      val maxX = (centerX + radius).coerceAtMost(width - 1)
                      val minY = (centerY - radius).coerceAtLeast(0)
                      val maxY = (centerY + radius).coerceAtMost(height - 1)

                      for (y in minY..maxY step 2) {
                        for (x in minX..maxX step 2) {
                          val idx = y * width + x
                          if (idx in 0 until yPlane.capacity()) {
                            sum += (yPlane.get(idx).toInt() and 0xFF)
                            count++
                          }
                        }
                      }
                    } else {
                      val stride = 14
                      for (y in 0 until height step stride) {
                        for (x in 0 until width step stride) {
                          val idx = y * width + x
                          if (idx in 0 until yPlane.capacity()) {
                            sum += (yPlane.get(idx).toInt() and 0xFF)
                            count++
                          }
                        }
                      }
                    }

                    if (count > 0) {
                      val avgY = (sum.toFloat() / count).coerceIn(1f, 254f)
                      spotLuminance = avgY
                      val evDelta = log2(avgY / 128.0)
                      val dynamicEv = (currentEv + evDelta * 0.9).coerceIn(1.0, 18.0)
                      val now = System.currentTimeMillis()
                      if (kotlin.math.abs(dynamicEv - lastReportedEv) > 0.08 || (now - lastReportTimeMs > 220)) {
                        lastReportedEv = dynamicEv
                        lastReportTimeMs = now
                        onMeasuredEvChanged(dynamicEv)
                      }
                    }
                  }
                }
                imageProxy.close()
              }

              val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

              try {
                cameraProvider.unbindAll()
                val camera: Camera = cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  cameraSelector,
                  preview,
                  imageAnalysis
                )
                cameraControlInstance = camera.cameraControl
                camera.cameraControl.setZoomRatio(targetZoomRatio)
              } catch (_: Exception) {
                // Handled gracefully
              }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
          }
        )

        // Ground Glass & Rangefinder Framing Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height

          // Format crop mask computation
          val targetAspect = format.aspectRatioFloat
          val (maskW, maskH) = if (w / h > targetAspect) {
            val calcW = h * targetAspect
            calcW to h
          } else {
            val calcH = w / targetAspect
            w to calcH
          }
          val maskX = (w - maskW) / 2f
          val maskY = (h - maskH) / 2f

          // Shaded Letterboxing outside camera gate
          drawRect(color = Color(0x66000000), topLeft = Offset.Zero, size = Size(w, maskY))
          drawRect(color = Color(0x66000000), topLeft = Offset(0f, maskY + maskH), size = Size(w, h - (maskY + maskH)))
          drawRect(color = Color(0x66000000), topLeft = Offset(0f, maskY), size = Size(maskX, maskH))
          drawRect(color = Color(0x66000000), topLeft = Offset(maskX + maskW, maskY), size = Size(w - (maskX + maskW), maskH))

          // Rangefinder Frame Corner Brackets
          val bracketLen = (maskW * 0.12f).coerceAtLeast(18f)
          val strokeW = 2.5f
          val frameColor = VintageBrassBright.copy(alpha = 0.85f)

          // Corners
          drawLine(frameColor, Offset(maskX, maskY), Offset(maskX + bracketLen, maskY), strokeW)
          drawLine(frameColor, Offset(maskX, maskY), Offset(maskX, maskY + bracketLen), strokeW)
          drawLine(frameColor, Offset(maskX + maskW, maskY), Offset(maskX + maskW - bracketLen, maskY), strokeW)
          drawLine(frameColor, Offset(maskX + maskW, maskY), Offset(maskX + maskW, maskY + bracketLen), strokeW)
          drawLine(frameColor, Offset(maskX, maskY + maskH), Offset(maskX + bracketLen, maskY + maskH), strokeW)
          drawLine(frameColor, Offset(maskX, maskY + maskH), Offset(maskX, maskY + maskH - bracketLen), strokeW)
          drawLine(frameColor, Offset(maskX + maskW, maskY + maskH), Offset(maskX + maskW - bracketLen, maskY + maskH), strokeW)
          drawLine(frameColor, Offset(maskX + maskW, maskY + maskH), Offset(maskX + maskW, maskY + maskH - bracketLen), strokeW)

          // 6x6 Ground Glass center circle
          if (format == CameraFormat.MEDIUM_66) {
            drawCircle(
              color = Color(0x44EAE3D2),
              radius = maskH * 0.22f,
              center = Offset(w / 2f, h / 2f),
              style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )
          }

          // Spot Meter Reticle at user's tapped point
          val spotPixelX = spotPoint.x * w
          val spotPixelY = spotPoint.y * h
          val spotRadius = (maskW * 0.07f).coerceIn(14f, 28f)
          val reticleColor = if (isLocked) VintageWitnessRed else VintageBrassBright

          drawCircle(color = reticleColor, radius = spotRadius, center = Offset(spotPixelX, spotPixelY), style = Stroke(width = 2f))
          drawCircle(color = reticleColor, radius = 3f, center = Offset(spotPixelX, spotPixelY))
          // Cross ticks
          drawLine(reticleColor, Offset(spotPixelX - spotRadius - 5f, spotPixelY), Offset(spotPixelX - spotRadius + 3f, spotPixelY), 2f)
          drawLine(reticleColor, Offset(spotPixelX + spotRadius - 3f, spotPixelY), Offset(spotPixelX + spotRadius + 5f, spotPixelY), 2f)
          drawLine(reticleColor, Offset(spotPixelX, spotPixelY - spotRadius - 5f), Offset(spotPixelX, spotPixelY - spotRadius + 3f), 2f)
          drawLine(reticleColor, Offset(spotPixelX, spotPixelY + spotRadius - 3f), Offset(spotPixelX, spotPixelY + spotRadius + 5f), 2f)
        }

        // Top HUD Overlay: Integrated Lens Selector + Live Lock/EV
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Integrated Lens Pill with Visible Text Steppers
          Row(
            modifier = Modifier
              .background(Color(0xCC1A130D), RoundedCornerShape(8.dp))
              .border(1.dp, VintageBrassDark, RoundedCornerShape(8.dp))
              .padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF261D15))
                .border(0.6.dp, VintageBrassDark, RoundedCornerShape(5.dp))
                .clickable {
                  val currentIdx = commonFocals.indexOf(focalLengthMm)
                  val prev = if (currentIdx > 0) commonFocals[currentIdx - 1] else commonFocals.first()
                  hapticManager.performDialClick(view)
                  onFocalLengthChanged(prev)
                }
                .padding(horizontal = 5.dp, vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "◀ WIDE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageBrassPrimary
              )
            }

            Column(
              modifier = Modifier
                .clickable {
                  hapticManager.performDialClick(view)
                  showLensPicker = !showLensPicker
                }
                .padding(horizontal = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "${focalLengthMm}mm",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageBrassBright
              )
              Text(
                text = if (format != CameraFormat.FORMAT_35MM) {
                  "≈ ${eq35mmFocal.roundToInt()}mm 35Eq"
                } else {
                  format.displayName
                },
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageWitnessAmber
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF261D15))
                .border(0.6.dp, VintageBrassDark, RoundedCornerShape(5.dp))
                .clickable {
                  val currentIdx = commonFocals.indexOf(focalLengthMm)
                  val next = if (currentIdx in 0 until commonFocals.lastIndex) commonFocals[currentIdx + 1] else commonFocals.last()
                  hapticManager.performDialClick(view)
                  onFocalLengthChanged(next)
                }
                .padding(horizontal = 5.dp, vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "TELE ▶",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = VintageBrassPrimary
              )
            }
          }

          // Top Right: Spot/Matrix & Lock Button with explicit text
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Spot / Matrix Mode
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSpotMode) VintageBrassPrimary else Color(0xCC1A130D))
                .border(0.8.dp, VintageBrassDark, RoundedCornerShape(6.dp))
                .clickable {
                  hapticManager.performDialClick(view)
                  isSpotMode = !isSpotMode
                }
                .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
              Text(
                text = if (isSpotMode) "⊙ SPOT" else "⊞ MATRIX",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isSpotMode) Color.Black else VintageIvoryMuted
              )
            }

            // Lock / Live Button
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLocked) VintageWitnessRed else Color(0xCC1A130D))
                .border(0.8.dp, if (isLocked) VintageWitnessRed else VintageBrassDark, RoundedCornerShape(6.dp))
                .clickable {
                  hapticManager.performDialClick(view)
                  onToggleLock()
                }
                .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Icon(
                  imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                  contentDescription = null,
                  tint = if (isLocked) Color.White else VintageBrassPrimary,
                  modifier = Modifier.size(11.dp)
                )
                Text(
                  text = if (isLocked) "HOLD EV" else "LIVE EV",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = if (isLocked) Color.White else VintageBrassBright
                )
              }
            }
          }
        }

        // Quick Lens Preset Bar (pops open smoothly when lens pill is clicked)
        if (showLensPicker) {
          Box(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 42.dp, start = 8.dp, end = 8.dp)
              .fillMaxWidth()
              .background(Color(0xF017110C), RoundedCornerShape(8.dp))
              .border(1.dp, VintageBrassPrimary, RoundedCornerShape(8.dp))
              .padding(4.dp)
          ) {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(commonFocals) { fMm ->
                val isSelected = fMm == focalLengthMm
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) VintageBrassPrimary else Color(0xFF261D15))
                    .border(0.6.dp, if (isSelected) VintageBrassBright else VintageBrassDark, RoundedCornerShape(5.dp))
                    .clickable {
                      hapticManager.performDialClick(view)
                      onFocalLengthChanged(fMm)
                      showLensPicker = false
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                  Text(
                    text = "${fMm}mm",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) Color.Black else VintageIvoryText
                  )
                }
              }
            }
          }
        }

        // Bottom HUD Overlay: Zone System & Live EV Readout
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xDD140F0C))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val zoneNum = ((spotLuminance / 255f) * 10f).roundToInt().coerceIn(0, 10)
            val zoneRoman = listOf("0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")[zoneNum]

            Text(
              text = "ZONE $zoneRoman • ${if (zoneNum == 5) "MIDTONE" else if (zoneNum < 4) "SHADOW" else "HIGHLIGHT"}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = VintageWitnessAmber
            )

            Text(
              text = if (isLocked) "HOLD: EV ${String.format(java.util.Locale.US, "%.1f", currentEv)}"
                     else "EV₁₀₀: ${String.format(java.util.Locale.US, "%.1f", currentEv)}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isLocked) VintageWitnessRed else VintageBrassBright
            )
          }
        }
      }
    }
  }
}
