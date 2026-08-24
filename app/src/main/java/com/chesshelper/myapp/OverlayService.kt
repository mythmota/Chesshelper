package com.chesshelper.myapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.github.bhlangonijr.chesslib.Board

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var mediaProjection: MediaProjection? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val chessBoard = Board()
    private val boardRecognizer = ChessBoardRecognizer()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager.addView(overlayView, params)

        val txtSuggestion = overlayView.findViewById<TextView>(R.id.txtSuggestion)
        val btnAnalyze = overlayView.findViewById<Button>(R.id.btnAnalyze)

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        btnAnalyze.setOnClickListener {
            txtSuggestion.text = "IA a analisar..."
            btnAnalyze.isEnabled = false

            // Esconde temporariamente o botão para não sair no printscreen
            overlayView.visibility = View.INVISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                takeSingleScreenshot { screenshot ->
                    overlayView.visibility = View.VISIBLE

                    if (screenshot != null) {
                        val fenPosition = boardRecognizer.recognizeBoardAndGetFen(screenshot)
                        chessBoard.loadFromFen(fenPosition)

                        val legalMoves = chessBoard.legalMoves()

                        if (legalMoves.isNotEmpty()) {
                            val bestMove = legalMoves[0]
                            txtSuggestion.text = parseChessMove(bestMove.toString())
                        } else {
                            txtSuggestion.text = "Sem jogadas válidas detetadas"
                        }
                    } else {
                        txtSuggestion.text = "Erro ao capturar ecrã"
                    }

                    btnAnalyze.isEnabled = true
                }
            }, 150)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()

        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA_INTENT", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA_INTENT")
        }

        if (resultCode != -1 && dataIntent != null) {
            try {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return START_STICKY
    }

    private fun takeSingleScreenshot(callback: (Bitmap?) -> Unit) {
        val proj = mediaProjection
        if (proj == null) {
            callback(null)
            return
        }

        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        var vDisplay: VirtualDisplay? = null

        reader.setOnImageAvailableListener({ imageReader ->
            var image: Image? = null
            var bitmap: Bitmap? = null
            try {
                image = imageReader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bmpTemp = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bmpTemp.copyPixelsFromBuffer(buffer)
                    bitmap = Bitmap.createBitmap(bmpTemp, 0, 0, screenWidth, screenHeight)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image?.close()
                reader.close()
                vDisplay?.release()
                callback(bitmap)
            }
        }, Handler(Looper.getMainLooper()))

        vDisplay = proj.createVirtualDisplay(
            "ChessCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    private fun parseChessMove(moveStr: String): String {
        if (moveStr.length < 4) return "Jogada: $moveStr"
        val from = moveStr.substring(0, 2)
        val to = moveStr.substring(2, 4)
        return "Mover de $from para $to"
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chess_overlay_channel"
            val channel = NotificationChannel(channelId, "Chess Overlay", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("ChessHelper Ativo")
                .setContentText("Pronto para analisar ecrã...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, notification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (e: Exception) { }
        }
    }
}
