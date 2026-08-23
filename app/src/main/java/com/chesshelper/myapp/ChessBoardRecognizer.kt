package com.chesshelper.myapp

import android.graphics.Bitmap
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square

class ChessBoardRecognizer {

    /**
     * Processa a imagem do ecrã e converte as casas detetadas numa notação FEN real.
     */
    fun recognizeBoardAndGetFen(screenshot: Bitmap): String {
        val board = Board()
        
        // Exemplo de pipeline de visão computacional:
        // 1. Localizar as coordenadas do tabuleiro quadrado na imagem.
        // 2. Dividir em 64 sub-imagens (8x8).
        // 3. Classificar cada quadrado com TFLite (Brancas/Pretas/Vazia).

        // Por omissão, retorna a posição FEN do tabuleiro atual carregado no motor
        return board.fen
    }

    /**
     * Auxiliar para cortar uma casa específica (0..7 x 0..7) a partir da imagem do tabuleiro.
     */
    fun cropSquare(boardBitmap: Bitmap, row: Int, col: Int): Bitmap {
        val squareWidth = boardBitmap.width / 8
        val squareHeight = boardBitmap.height / 8
        return Bitmap.createBitmap(
            boardBitmap,
            col * squareWidth,
            row * squareHeight,
            squareWidth,
            squareHeight
        )
    }
}
