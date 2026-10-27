package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CameraFormat
import com.example.sensor.CameraMetering
import com.example.sensor.DepthOfFieldCalculator
import com.example.sensor.HapticManager
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private class HeldCameraFrame : ViewModel() {
  var bitmap by mutableStateOf<Bitmap?>(null)
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Suppress("UNUSED_PARAMETER")
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
  viewportHeightDp: Int = 220,
  onCameraStatusChanged: (String?, Boolean) -> Unit = { _, _ -> },
  onMeteringAreaChanged: () -> Unit = {},
  isBlackAndWhite: Boolean = false,
  onHeldFrameChanged: (Bitmap?) -> Unit = {}
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
  val previewView = remember(context) {
    PreviewView(context).apply {
      keepScreenOn = true
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
      importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
      clipToOutline = true
      outlineProvider = ViewOutlineProvider.BOUNDS
    }
  }
  val heldFrame: HeldCameraFrame = viewModel { HeldCameraFrame() }
  val monochromePaint = remember { monochromePreviewPaint() }
  val displayedHeldFrame = remember(isLocked, heldFrame.bitmap, isBlackAndWhite) {
    heldFrame.bitmap?.takeIf { isLocked }?.let { renderHeldCameraFrame(it, isBlackAndWhite) }
  }
  val locked by rememberUpdatedState(isLocked)
  val onReading by rememberUpdatedState(onMeasuredEvChanged)
  val onStatus by rememberUpdatedState(onCameraStatusChanged)
  val onAreaChanged by rememberUpdatedState(onMeteringAreaChanged)
  val onHeldFrame by rememberUpdatedState(onHeldFrameChanged)
  var hasPermission by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var permissionRequested by rememberSaveable { mutableStateOf(false) }
  var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
  var retry by remember { mutableIntStateOf(0) }
  var previewSize by remember { mutableStateOf(IntSize.Zero) }
  var cameraStatus by remember { mutableStateOf<String?>("Waiting for camera") }
  var measuring by remember { mutableStateOf(true) }
  var canRetry by remember { mutableStateOf(false) }
  var framingLabel by remember { mutableStateOf("Phone framing") }
  var selectedX by rememberSaveable(format, focalLengthMm) { mutableStateOf<Float?>(null) }
  var selectedY by rememberSaveable(format, focalLengthMm) { mutableStateOf(0.5f) }
  var previousFrameWidth by rememberSaveable(format, focalLengthMm) { mutableIntStateOf(0) }
  var previousFrameHeight by rememberSaveable(format, focalLengthMm) { mutableIntStateOf(0) }
  var selectionVersion by remember { mutableIntStateOf(0) }
  var canSelectMeteringArea by remember { mutableStateOf(false) }
  val selectedArea = selectedX?.let { CameraMetering.selectedAreaAt(it, selectedY) }

  fun selectArea(x: Float?, y: Float = 0.5f) {
    if (locked || !hasPermission || !resumed || !canSelectMeteringArea) return
    if (x != null && CameraMetering.selectedAreaAt(x, y) == null) return
    selectedX = x
    selectedY = y
    selectionVersion++
    cameraStatus = if (x == null) "Measuring frame" else "Measuring selected area"
    measuring = true
    canRetry = false
    onAreaChanged()
    hapticManager.performDialClick(previewView)
  }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    hasPermission = granted
    permissionRequested = true
  }

  LaunchedEffect(cameraStatus, measuring) { onStatus(cameraStatus, measuring) }
  LaunchedEffect(displayedHeldFrame) { onHeldFrame(displayedHeldFrame) }
  DisposableEffect(previewView) {
    onDispose { onHeldFrame(null) }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        resumed = true
      } else if (event == Lifecycle.Event.ON_PAUSE) {
        resumed = false
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(isLocked, previewView) {
    if (!isLocked) {
      heldFrame.bitmap = null
    } else if (heldFrame.bitmap == null) {
      repeat(4) {
        val snapshot = try { previewView.bitmap } catch (_: IllegalStateException) { null }
        snapshot?.let {
          heldFrame.bitmap = it
          return@LaunchedEffect
        }
        delay(40)
      }
    }
  }

  DisposableEffect(hasPermission, resumed, previewSize, format, focalLengthMm, retry) {
    canSelectMeteringArea = false
    val disposed = AtomicBoolean(false)
    var provider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var camera: Camera? = null
    var lastCaptureMs = SystemClock.elapsedRealtime()
    var lastProcessedMs = 0L
    var requestedRegion: Rect? = null
    var requestedSelectionVersion = -1
    var regionRequestedMs = 0L
    var lastValidatedReadingMs = 0L
    var zoomReady = false
    var supportsFrameMetering = false
    var activeArray: Rect? = null
    var watchdog: Runnable? = null

    fun unavailable(message: String) {
      if (!disposed.get()) {
        cameraStatus = message
        measuring = false
        canRetry = true
      }
    }

    if (!hasPermission) {
      cameraStatus = "Camera permission needed"
      measuring = false
      canRetry = false
    } else if (!resumed) {
      if (!locked) {
        cameraStatus = "Camera paused"
        measuring = false
      }
    } else if (previewSize.width > 0 && previewSize.height > 0) {
      cameraStatus = "Waiting for camera"
      measuring = true
      canRetry = false
      val providerFuture = ProcessCameraProvider.getInstance(context)
      previewView.doOnLayout {
        providerFuture.addListener({
          if (!disposed.get()) {
            try {
              val cameraProvider = providerFuture.get()
              provider = cameraProvider
              val selector = CameraSelector.DEFAULT_BACK_CAMERA
              if (!cameraProvider.hasCamera(selector)) {
                unavailable("No rear camera available")
              } else {
                val cameraInfo = Camera2CameraInfo.from(cameraProvider.getCameraInfo(selector))
                activeArray = cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                supportsFrameMetering = (cameraInfo.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0
                val builder = Preview.Builder().setDynamicRange(DynamicRange.SDR).setPreviewStabilizationEnabled(false)
                val interop = Camera2Interop.Extender(builder)
                  .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                  .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                  .setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
                  .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                  .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                if (cameraInfo.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                    ?.contains(CaptureRequest.CONTROL_SCENE_MODE_DISABLED) == true) {
                  interop.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
                }
                if (cameraInfo.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                    ?.contains(CaptureRequest.CONTROL_EFFECT_MODE_OFF) == true) {
                  interop.setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
                }
                if (cameraInfo.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                    ?.contains(CaptureRequest.EDGE_MODE_OFF) == true) {
                  interop.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                }
                if (cameraInfo.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                    ?.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) == true) {
                  interop.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                }
                if (Build.VERSION.SDK_INT >= 28) {
                  val distortionModes = cameraInfo.getCameraCharacteristic(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                  val mode = distortionModes?.firstOrNull { it == CaptureRequest.DISTORTION_CORRECTION_MODE_FAST }
                    ?: distortionModes?.firstOrNull { it == CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY }
                  if (mode != null) {
                    interop.setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE, mode)
                  } else if (distortionModes != null && distortionModes.isNotEmpty()) {
                    val uncorrectedArray = cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
                    if (uncorrectedArray != null && uncorrectedArray != activeArray) supportsFrameMetering = false
                  }
                }
                interop.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                  override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    mainExecutor.execute {
                      if (!disposed.get()) {
                        val now = SystemClock.elapsedRealtime()
                        lastCaptureMs = now
                        if (!locked && zoomReady && supportsFrameMetering && now - lastProcessedMs >= 180L) {
                          lastProcessedMs = now
                          val sensor = activeArray
                          val transform = previewView.sensorToViewTransform
                          val crop = result.get(CaptureResult.SCALER_CROP_REGION)
                          val area = selectedX?.let { CameraMetering.selectedAreaAt(it, selectedY) }
                          val measuringLabel = if (area == null) "Measuring frame" else "Measuring selected area"
                          val region = if (sensor != null && transform != null && crop != null) {
                            CameraMetering.frameRegion(previewView.width, previewView.height, transform, sensor, crop, area)
                          } else null
                          val originalRegion = requestedRegion
                          if (region == null) {
                            unavailable("Camera framing unavailable")
                          } else if (requestedSelectionVersion != selectionVersion || CameraMetering.needsRegionUpdate(originalRegion, region)) {
                            requestedRegion = region
                            requestedSelectionVersion = selectionVersion
                            val submittedSelectionVersion = selectionVersion
                            regionRequestedMs = now
                            cameraStatus = measuringLabel
                            measuring = true
                            canRetry = false
                            val options = CaptureRequestOptions.Builder()
                              .setCaptureRequestOption(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(region, MeteringRectangle.METERING_WEIGHT_MAX)))
                              .build()
                            camera?.let { activeCamera ->
                              val future = Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
                              future.addListener({
                                if (!disposed.get() && selectionVersion == submittedSelectionVersion && requestedRegion == region) {
                                  try { future.get() } catch (_: Exception) {
                                    unavailable(if (area == null) "Frame metering unavailable" else "Selected-area metering unavailable. Use Whole frame.")
                                  }
                                }
                              }, mainExecutor)
                            }
                          } else {
                            val reportedRegions = result.get(CaptureResult.CONTROL_AE_REGIONS)
                            val regionMatches = originalRegion != null && reportedRegions?.size == 1 && reportedRegions[0].meteringWeight > 0 &&
                              CameraMetering.regionsMatch(originalRegion, reportedRegions[0].rect)
                            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                            val unsettledMs = CameraMetering.unsettledDurationMs(now, regionRequestedMs, lastValidatedReadingMs)
                            when {
                              !regionMatches -> {
                                if (unsettledMs > 3000L) {
                                  unavailable(if (area == null) "This camera cannot meter the outlined frame"
                                    else "This camera cannot meter the selected area. Use Whole frame.")
                                } else {
                                  cameraStatus = measuringLabel
                                  measuring = true
                                  canRetry = false
                                }
                              }
                              aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> unavailable("Too dark for a reliable camera reading")
                              aeState != CaptureResult.CONTROL_AE_STATE_CONVERGED -> {
                                cameraStatus = measuringLabel
                                measuring = true
                                canRetry = unsettledMs > 5000L
                              }
                              else -> {
                                val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
                                val ev = CameraMetering.ev100(
                                  result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                                  result.get(CaptureResult.SENSOR_SENSITIVITY),
                                  result.get(CaptureResult.LENS_APERTURE),
                                  boost
                                )
                                if (ev == null) {
                                  unavailable("Camera exposure data unavailable")
                                } else {
                                  // Device AE is an estimate; compare with a reference meter before relying on it.
                                  lastValidatedReadingMs = now
                                  onReading(ev)
                                  cameraStatus = null
                                  measuring = false
                                  canRetry = false
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                })
                val useCase = builder.build()
                preview = useCase
                useCase.surfaceProvider = previewView.surfaceProvider
                val viewport = previewView.viewPort
                if (viewport == null) {
                  unavailable("Camera preview unavailable")
                } else {
                  val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(useCase).build()
                  val activeCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
                  camera = activeCamera
                  val physicalSize = cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                  val phoneFocals = cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                  val sensorRotation = cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION)
                  val displayRotation = (previewView.display?.rotation ?: Surface.ROTATION_0) * 90
                  val desiredZoom = if (physicalSize != null && sensorRotation != null) {
                    CameraMetering.framingZoom(
                      focalLengthMm * DepthOfFieldCalculator.getCropFactor(format), phoneFocals,
                      physicalSize.width, physicalSize.height, format.aspectRatioFloat,
                      (sensorRotation - displayRotation + 360) % 360
                    )
                  } else null
                  val zoomState = activeCamera.cameraInfo.zoomState.value
                  val zoom = (desiredZoom ?: 1f).coerceIn(zoomState?.minZoomRatio ?: 1f, zoomState?.maxZoomRatio ?: 1f)
                  framingLabel = when {
                    desiredZoom == null -> "Phone framing"
                    kotlin.math.abs(zoom - desiredZoom) > 0.02f -> "Phone lens limit · approximate framing"
                    else -> "Approximate ${focalLengthMm}mm framing"
                  }
                  val zoomFuture = activeCamera.cameraControl.setZoomRatio(zoom)
                  zoomFuture.addListener({
                    if (!disposed.get()) {
                      try {
                        zoomFuture.get()
                        zoomReady = true
                        canSelectMeteringArea = supportsFrameMetering && activeArray != null
                      } catch (_: Exception) {
                        unavailable("Camera zoom unavailable")
                      }
                    }
                  }, mainExecutor)
                  if (!supportsFrameMetering || activeArray == null) unavailable("This phone cannot meter the outlined frame")
                  val checkCamera = object : Runnable {
                    override fun run() {
                      if (!disposed.get()) {
                        if (!locked && SystemClock.elapsedRealtime() - lastCaptureMs > 5000L) unavailable("Camera unavailable · try again")
                        previewView.postDelayed(this, 2000L)
                      }
                    }
                  }
                  watchdog = checkCamera
                  previewView.postDelayed(checkCamera, 5000L)
                }
              }
            } catch (_: Exception) {
              unavailable("Camera unavailable · try again")
            }
          }
        }, mainExecutor)
      }
    }
    onDispose {
      disposed.set(true)
      watchdog?.let { previewView.removeCallbacks(it) }
      camera?.let { Camera2CameraControl.from(it.cameraControl).clearCaptureRequestOptions() }
      preview?.let { provider?.unbind(it) }
    }
  }

  Column(modifier = modifier) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth().height(viewportHeightDp.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
      contentAlignment = Alignment.Center
    ) {
      val availableWidth = (maxWidth - 32.dp).coerceAtLeast(1.dp)
      val availableHeight = (maxHeight - 48.dp).coerceAtLeast(1.dp)
      val frameWidth = minOf(availableWidth, availableHeight * format.aspectRatioFloat)
      val frameHeight = frameWidth / format.aspectRatioFloat
      Box(
        modifier = Modifier.size(frameWidth, frameHeight).clipToBounds().border(1.dp, Color.White.copy(alpha = 0.85f))
      ) {
        AndroidView(
          factory = { previewView },
          // Native camera pixels must not remain live around the held bitmap.
          update = {
            it.alpha = if (isLocked) 0f else 1f
            it.setLayerType(if (isBlackAndWhite) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
              if (isBlackAndWhite) monochromePaint else null)
          },
          modifier = Modifier.fillMaxSize().onSizeChanged {
            val changed = previousFrameWidth > 0 && previousFrameHeight > 0 &&
              (previousFrameWidth != it.width || previousFrameHeight != it.height)
            if (changed && !locked && selectedX != null) {
              selectedX = null
              selectionVersion++
              onAreaChanged()
            }
            previousFrameWidth = it.width
            previousFrameHeight = it.height
            previewSize = it
          }
        )
        if (isLocked) {
          val bitmap = displayedHeldFrame
          if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Held camera frame", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
          } else {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
              Text("Reading held\nPreview unavailable", color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
        Canvas(
          Modifier.fillMaxSize().pointerInput(isLocked, format, focalLengthMm) {
            detectTapGestures { point ->
              if (!canRetry && size.width > 0 && size.height > 0) {
                selectArea(point.x / size.width, point.y / size.height)
              }
            }
          }
        ) {
          selectedArea?.let { area ->
            val position = Offset(area.left * size.width, area.top * size.height)
            val areaSize = Size(area.width() * size.width, area.height() * size.height)
            drawRect(Color.Black, position, areaSize, style = Stroke(5.dp.toPx()))
            drawRect(Color.White, position, areaSize, style = Stroke(2.dp.toPx()))
          }
        }
      }
      if (!hasPermission || (canRetry && !isLocked)) {
        Column(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).verticalScroll(rememberScrollState()).padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
          Text(cameraStatus ?: "Camera unavailable", color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
          if (!hasPermission) {
            Text("Use the rear camera to meter the scene inside the frame.", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Enable camera") }
              if (permissionRequested) {
                TextButton(onClick = {
                  context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Settings", color = Color.White) }
              }
            }
          } else {
            Button(onClick = { retry++ }) { Text("Try again") }
          }
        }
      } else {
        Text(
          text = if (isLocked) {
            if (selectedArea == null) "Frame held" else "Selected area held"
          } else cameraStatus ?: if (selectedArea == null) framingLabel else "Selected area",
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      TextButton(
        onClick = { selectArea(0.5f, 0.5f) },
        modifier = Modifier.weight(1f),
        enabled = !isLocked && hasPermission && resumed && canSelectMeteringArea
      ) { Text("Meter centre") }
      TextButton(
        onClick = { selectArea(null) },
        modifier = Modifier.weight(1f),
        enabled = selectedArea != null && !isLocked && hasPermission && resumed && canSelectMeteringArea
      ) { Text("Whole frame") }
    }
  }
}
