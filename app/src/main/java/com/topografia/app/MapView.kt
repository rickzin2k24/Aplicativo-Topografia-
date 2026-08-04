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

    private val paintPonto = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintTexto = Paint().apply { color = Color.WHITE; textSize = 35f }
    private val paintLinhaMedicao = Paint().apply { color = Color.parseColor("#FFC107"); strokeWidth = 5f }
    private val paintRTK = Paint().apply { color = Color.parseColor("#00FF00"); style = Paint.Style.FILL }
    private val paintSelecao = Paint().apply { color = Color.parseColor("#00BFFF"); style = Paint.Style.FILL }

    var listaDePontos = listOf<PontoTopografico>()
    var rtkNorte = 0.0
    var rtkLeste = 0.0

    private var pontoSelecionado1: PontoTopografico? = null
    private var pontoSelecionado2: PontoTopografico? = null
    var onMedicaoCalculada: ((Double) -> Unit)? = null

    private val escala = 15f 

    // Variáveis do Motor de Câmera (Zoom e Pan)
    private var mPosX = 0f
    private var mPosY = 0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mScaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector

    init {
        // Inicializa o detector de movimento de pinça (Zoom)
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mScaleFactor *= detector.scaleFactor
                mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 20.0f)) // Limite do Zoom
                invalidate()
                return true
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#191919"))

        // Aplica o Zoom e o Arraste (Pan) na câmera
        canvas.save()
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)

        val centroX = width / 2f
        val centroY = height / 2f

        for (ponto in listaDePontos) {
            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()

            canvas.drawCircle(telaX, telaY, 15f, paintPonto)
            canvas.drawText(ponto.nome, telaX + 25f, telaY + 10f, paintTexto)

            if (ponto == pontoSelecionado1 || ponto == pontoSelecionado2) {
                canvas.drawCircle(telaX, telaY, 20f, paintSelecao)
            }
        }

        if (pontoSelecionado1 != null && pontoSelecionado2 != null) {
            val x1 = centroX + ((pontoSelecionado1!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y1 = centroY - ((pontoSelecionado1!!.norteUtm - rtkNorte) * escala).toFloat()
            val x2 = centroX + ((pontoSelecionado2!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y2 = centroY - ((pontoSelecionado2!!.norteUtm - rtkNorte) * escala).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paintLinhaMedicao)
        }

        canvas.drawCircle(centroX, centroY, 20f, paintRTK)
        canvas.restore() // Fim da área afetada pela câmera
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Avisa o detector de Zoom sobre o toque
        scaleDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX = event.x
                mLastTouchY = event.y

                // Desfaz a conta da câmera para saber onde o dedo tocou no mundo real
                val toqueX = (event.x - mPosX) / mScaleFactor
                val toqueY = (event.y - mPosY) / mScaleFactor
                val centroX = width / 2f
                val centroY = height / 2f

                var tocouEmAlgumPonto = false

                for (ponto in listaDePontos) {
                    val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
                    val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()
                    
                    val distanciaDoDedo = hypot(telaX - toqueX, telaY - toqueY)
                    
                    if (distanciaDoDedo < 60f) { 
                        tocouEmAlgumPonto = true
                        if (pontoSelecionado1 == null) {
                            pontoSelecionado1 = ponto
                        } else if (pontoSelecionado2 == null && ponto != pontoSelecionado1) {
                            pontoSelecionado2 = ponto
                            val dx = pontoSelecionado2!!.lesteUtm - pontoSelecionado1!!.lesteUtm
                            val dy = pontoSelecionado2!!.norteUtm - pontoSelecionado1!!.norteUtm
                            val distanciaReal = sqrt(dx * dx + dy * dy)
                            onMedicaoCalculada?.invoke(distanciaReal) 
                        } else {
                            pontoSelecionado1 = ponto
                            pontoSelecionado2 = null
                        }
                        invalidate()
                        break
                    }
                }

                if (!tocouEmAlgumPonto) {
                    pontoSelecionado1 = null
                    pontoSelecionado2 = null
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Se não estiver dando zoom (usando dois dedos), faz o arraste do mapa
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
        }
        return true
    }
}
