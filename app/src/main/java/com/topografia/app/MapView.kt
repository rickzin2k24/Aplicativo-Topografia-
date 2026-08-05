package com.topografia.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot
import kotlin.math.sqrt

class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // Pincéis (Cores e formas)
    private val paintPonto = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintTexto = Paint().apply { color = Color.WHITE; textSize = 35f }
    private val paintLinhaMedicao = Paint().apply { color = Color.parseColor("#FFC107"); strokeWidth = 5f }
    private val paintRTK = Paint().apply { color = Color.parseColor("#00FF00"); style = Paint.Style.FILL }
    private val paintSelecao = Paint().apply { color = Color.parseColor("#00BFFF"); style = Paint.Style.FILL }
    
    // NOVOS PINCÉIS DE LOCAÇÃO
    private val paintCruz = Paint().apply { color = Color.RED; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintIma = Paint().apply { color = Color.parseColor("#FF9800"); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintLinhaLocacao = Paint().apply { color = Color.parseColor("#00FFFF"); strokeWidth = 6f; style = Paint.Style.STROKE } // Azul piscina

    var listaDePontos = listOf<PontoTopografico>()
    var rtkNorte = 0.0
    var rtkLeste = 0.0

    // Variáveis da Trena (COGO)
    private var pontoSelecionado1: PontoTopografico? = null
    private var pontoSelecionado2: PontoTopografico? = null
    var onMedicaoCalculada: ((Double) -> Unit)? = null

    // VARIÁVEIS DO MÓDULO LOCAÇÃO
    var modoLocacao = false
    var alvoLocacao: PontoTopografico? = null
    private var pontoFocado: PontoTopografico? = null
    var onLocacaoLock: ((PontoTopografico?) -> Unit)? = null

    private val escala = 15f 

    // Motor de Câmera
    private var mPosX = 0f
    private var mPosY = 0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mTouchDownX = 0f // Para saber se foi clique ou arrasto
    private var mTouchDownY = 0f
    private var mScaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mScaleFactor *= detector.scaleFactor
                mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 20.0f))
                invalidate()
                return true
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#191919"))

        canvas.save()
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)

        val centroX = width / 2f
        val centroY = height / 2f

        // Desenha os pontos do projeto
        for (ponto in listaDePontos) {
            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()

            canvas.drawCircle(telaX, telaY, 15f, paintPonto)
            canvas.drawText(ponto.nome, telaX + 25f, telaY + 10f, paintTexto)

            if (ponto == pontoSelecionado1 || ponto == pontoSelecionado2) {
                canvas.drawCircle(telaX, telaY, 20f, paintSelecao)
            }
        }

        // Desenha a linha da Trena Virtual
        if (pontoSelecionado1 != null && pontoSelecionado2 != null) {
            val x1 = centroX + ((pontoSelecionado1!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y1 = centroY - ((pontoSelecionado1!!.norteUtm - rtkNorte) * escala).toFloat()
            val x2 = centroX + ((pontoSelecionado2!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y2 = centroY - ((pontoSelecionado2!!.norteUtm - rtkNorte) * escala).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paintLinhaMedicao)
        }

        // --- SISTEMA DE LOCAÇÃO (O ÍMÃ E A LINHA) ---
        
        // Descobre que parte do "chão" está exatamente no centro do vidro do celular
        val invX = (centroX - mPosX) / mScaleFactor
        val invY = (centroY - mPosY) / mScaleFactor

        if (modoLocacao) {
            var min_dist = Float.MAX_VALUE
            var pontoIma: PontoTopografico? = null
            
            // Varre o projeto para ver se alguma quina está perto do centro da tela
            for (ponto in listaDePontos) {
                val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
                val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()
                
                val dist = hypot(telaX - invX, telaY - invY)
                if (dist < (80f / mScaleFactor) && dist < min_dist) { // Alcance do Ímã
                    min_dist = dist
                    pontoIma = ponto
                }
            }
            pontoFocado = pontoIma
            
            // O Xzinho magnético
            val cX = if (pontoIma != null) centroX + ((pontoIma.lesteUtm - rtkLeste) * escala).toFloat() else invX
            val cY = if (pontoIma != null) centroY - ((pontoIma.norteUtm - rtkNorte) * escala).toFloat() else invY
            
            val size = 20f / mScaleFactor
            // Desenha o X
            canvas.drawLine(cX - size, cY - size, cX + size, cY + size, paintCruz)
            canvas.drawLine(cX - size, cY + size, cX + size, cY - size, paintCruz)
            
            // Se o ímã puxou a quina, desenha o alerta visual laranja
            if (pontoIma != null) {
                canvas.drawCircle(cX, cY, 35f / mScaleFactor, paintIma)
            }
        }

        // Se o alvo estiver travado, desenha a linha guia azul ligando o RTK (Centro) ao Ponto
        if (alvoLocacao != null) {
            val alvoX = centroX + ((alvoLocacao!!.lesteUtm - rtkLeste) * escala).toFloat()
            val alvoY = centroY - ((alvoLocacao!!.norteUtm - rtkNorte) * escala).toFloat()
            canvas.drawLine(centroX, centroY, alvoX, alvoY, paintLinhaLocacao)
            
            // Um marcador extra para não perder o alvo de vista
            canvas.drawCircle(alvoX, alvoY, 25f / mScaleFactor, paintIma)
        }

        // Desenha Você (RTK) por cima de tudo
        canvas.drawCircle(centroX, centroY, 20f, paintRTK)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX = event.x
                mLastTouchY = event.y
                mTouchDownX = event.x
                mTouchDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - mLastTouchX
                    val dy = event.y - mLastTouchY
                    mPosX += dx
                    mPosY += dy
                    invalidate()
                }
                mLastTouchX = event.x
                mLastTouchY = event.y
            }
            MotionEvent.ACTION_UP -> {
                // Se mexeu muito pouco o dedo, o aplicativo considera que foi um "Toque" (Clique)
                val distTap = hypot(event.x - mTouchDownX, event.y - mTouchDownY)
                if (distTap < 15f) { 
                    
                    if (modoLocacao) {
                        // Se estiver no modo locação, o clique trava o alvo magnético!
                        alvoLocacao = pontoFocado
                        onLocacaoLock?.invoke(alvoLocacao)
                    } else {
                        // Modo Trena normal
                        val toqueX = (event.x - mPosX) / mScaleFactor
                        val toqueY = (event.y - mPosY) / mScaleFactor
                        val centroX = width / 2f
                        val centroY = height / 2f
                        var tocouEmAlgumPonto = false

                        for (ponto in listaDePontos) {
                            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
                            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()
                            if (hypot(telaX - toqueX, telaY - toqueY) < (60f / mScaleFactor)) {
                                tocouEmAlgumPonto = true
                                if (pontoSelecionado1 == null) pontoSelecionado1 = ponto
                                else if (pontoSelecionado2 == null && ponto != pontoSelecionado1) {
                                    pontoSelecionado2 = ponto
                                    val dist = hypot(ponto.lesteUtm - pontoSelecionado1!!.lesteUtm, ponto.norteUtm - pontoSelecionado1!!.norteUtm)
                                    onMedicaoCalculada?.invoke(dist)
                                } else {
                                    pontoSelecionado1 = ponto
                                    pontoSelecionado2 = null
                                }
                                break
                            }
                        }
                        if (!tocouEmAlgumPonto) {
                            pontoSelecionado1 = null
                            pontoSelecionado2 = null
                        }
                    }
                    invalidate()
                }
            }
        }
        return true
    }
}
