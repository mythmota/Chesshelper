package com.chesshelper.myapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // Lista de jogadas de teste para simular análises reais
    private val movesDemo = listOf("e4", "Nf3", "Bc4", "Qh5", "O-O", "Bxf7+")
    private var currentMoveIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        // Torna a janela arrastável pelo ecrã
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
            txtSuggestion.text = "A analisar tabuleiro..."
            btnAnalyze.isEnabled = false
            
            // Simula o cálculo da análise
            Handler(Looper.getMainLooper()).postDelayed({
                val moveNotation = movesDemo[currentMoveIndex]
                currentMoveIndex = (currentMoveIndex + 1) % movesDemo.size
                
                // Converte a notação para texto em português
                txtSuggestion.text = parseChessMove(moveNotation)
                btnAnalyze.isEnabled = true
            }, 1500)
        }
    }

    // Função que traduz a jogada de xadrez em instruções claras
    private fun parseChessMove(move: String): String {
        if (move == "O-O") return "Jogada: Roque Curto"
        if (move == "O-O-O") return "Jogada: Roque Longo"

        val isCapture = move.contains("x")
        val cleanMove = move.replace("x", "").replace("+", "").replace("#", "")

        val pieceName = when {
            cleanMove.startsWith("N") -> "Cavalo"
            cleanMove.startsWith("B") -> "Bispo"
            cleanMove.startsWith("R") -> "Torre"
            cleanMove.startsWith("Q") -> "Dama"
            cleanMove.startsWith("K") -> "Rei"
            else -> "Peão"
        }

        val targetSquare = if (pieceName == "Peão") cleanMove else cleanMove.drop(1)
        val actionText = if (isCapture) "captura em" else "para"

        return "Mover $pieceName $actionText $targetSquare"
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
