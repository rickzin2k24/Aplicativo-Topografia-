package com.topografia.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.Locale
import kotlin.math.hypot

class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paintPonto = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintTexto = Paint().apply { color = Color.WHITE; textSize = 35f }
    private val paintLinhaMedicao = Paint().apply { color = Color.parseColor("#FFC107"); strokeWidth = 5f }
    private val paintRTK = Paint().apply { color = Color.parseColor("#00E676"); style = Paint.Style.FILL }
    private val paintSelecao = Paint().apply { color = Color.parseColor("#00BFFF"); style = Paint.Style.FILL }
    private val paintCruz = Paint().apply { color = Color.RED; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintIma = Paint().apply { color = Color.parseColor("#FF9800"); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintLinhaLocacao = Paint().apply { color = Color.parseColor("#00FFFF"); strokeWidth = 6f; style = Paint.Style.STROKE }
    
    private val paintEscala = Paint().apply { 
        color = Color.parseColor("#00FFFF")
        textSize = 38f
        isFakeBoldText = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK) 
    }

    var listaDePontos = listOf<PontoTopografico>()
    var rtkNorte = 0.0
    var rtkLeste = 0.0
    
    var azimuteUsuario = 0f

    private var pontoSelecionado1: PontoTopografico? = null
    private var pontoSelecionado2: PontoTopografico? = null
    var onMedicaoCalculada: ((Double) -> Unit)? = null

    var modoLocacao = false
    var alvoLocacao: PontoTopografico? = null
    private var pontoFocado: PontoTopografico? = null
    var onLocacaoLock: ((PontoTopografico?) -> Unit)? = null

    private val escala = 15f 

    private var mPosX = 0f
    private var mPosY = 0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mTouchDownX = 0f 
    private var mTouchDownY = 0f
    private var mScaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                
                val fatorOriginal = detector.scaleFactor
                // Mantém o freio macio no zoom
                val amortecido = 1.0f + ((fatorOriginal - 1.0f) * 0.15f) 
                
                val newScaleFactor = Math.max(0.1f, Math.min(mScaleFactor * amortecido, 100.0f))
                
                // CORREÇÃO CRÍTICA DO ZOOM: Ancora o movimento no Ponto Focal (centro dos dedos)
                val scaleRatio = newScaleFactor / mScaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY
                
                mPosX = focusX - (focusX - mPosX) * scaleRatio
                mPosY = focusY - (focusY - mPosY) * scaleRatio
                
                mScaleFactor = newScaleFactor
                invalidate()
                return true
            }
        })
    }

    fun centralizarNoUsuario() {
        mPosX = 0f
        mPosY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#121212")) 

        canvas.save() 
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)

        val centroX = width / 2f
        val centroY = height / 2f

        for (ponto in listaDePontos) {
            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()

            val raioPonto = 15f / mScaleFactor
            canvas.drawCircle(telaX, telaY, raioPonto, paintPonto)
            
            val textSizeOriginal = 35f
            paintTexto.textSize = textSizeOriginal / mScaleFactor
            canvas.drawText(ponto.nome, telaX + (25f / mScaleFactor), telaY + (10f / mScaleFactor), paintTexto)

            if (ponto == pontoSelecionado1 || ponto == pontoSelecionado2) {
                canvas.drawCircle(telaX, telaY, 20f / mScaleFactor, paintSelecao)
            }
        }

        if (pontoSelecionado1 != null && pontoSelecionado2 != null) {
            val x1 = centroX + ((pontoSelecionado1!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y1 = centroY - ((pontoSelecionado1!!.norteUtm - rtkNorte) * escala).toFloat()
            val x2 = centroX + ((pontoSelecionado2!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y2 = centroY - ((pontoSelecionado2!!.norteUtm - rtkNorte) * escala).toFloat()
            
            paintLinhaMedicao.strokeWidth = 5f / mScaleFactor
            canvas.drawLine(x1, y1, x2, y2, paintLinhaMedicao)
        }

        val invX = (centroX - mPosX) / mScaleFactor
        val invY = (centroY - mPosY) / mScaleFactor

        if (modoLocacao) {
            var min_dist = Float.MAX_VALUE
            var pontoIma: PontoTopografico? = null
            
            for (ponto in listaDePontos) {
                val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
                val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()
                
                val dist = hypot(telaX - invX, telaY - invY)
                if (dist < (80f / mScaleFactor) && dist < min_dist) { 
                    min_dist = dist
                    pontoIma = ponto
                }
            }
            pontoFocado = pontoIma
            
            val cX = if (pontoIma != null) centroX + ((pontoIma.lesteUtm - rtkLeste) * escala).toFloat() else invX
            val cY = if (pontoIma != null) centroY - ((pontoIma.norteUtm - rtkNorte) * escala).toFloat() else invY
            
            val sizeX = 20f / mScaleFactor
            paintCruz.strokeWidth = 4f / mScaleFactor
            canvas.drawLine(cX - sizeX, cY - sizeX, cX + sizeX, cY + sizeX, paintCruz)
            canvas.drawLine(cX - sizeX, cY + sizeX, cX + sizeX, cY - sizeX, paintCruz)
            
            if (pontoIma != null) {
                paintIma.strokeWidth = 4f / mScaleFactor
                canvas.drawCircle(cX, cY, 35f / mScaleFactor, paintIma)
            }
        }

        if (alvoLocacao != null) {
            val alvoX = centroX + ((alvoLocacao!!.lesteUtm - rtkLeste) * escala).toFloat()
            val alvoY = centroY - ((alvoLocacao!!.norteUtm - rtkNorte) * escala).toFloat()
            paintLinhaLocacao.strokeWidth = 6f / mScaleFactor
            canvas.drawLine(centroX, centroY, alvoX, alvoY, paintLinhaLocacao)
            
            paintIma.strokeWidth = 4f / mScaleFactor
            canvas.drawCircle(alvoX, alvoY, 25f / mScaleFactor, paintIma)
        }

        val tamanhoRtk = 45f / mScaleFactor 
        val pathRtk = Path()
        
        pathRtk.moveTo(centroX, centroY) 
        pathRtk.lineTo(centroX - (tamanhoRtk * 0.6f), centroY + tamanhoRtk) 
        pathRtk.lineTo(centroX + (tamanhoRtk * 0.6f), centroY + tamanhoRtk) 
        pathRtk.close()
        
        canvas.save()
        canvas.rotate(azimuteUsuario, centroX, centroY)
        canvas.drawPath(pathRtk, paintRTK)
        canvas.restore() 

        canvas.restore() 

        val alturaEmMetros = (width / (escala * mScaleFactor)) * 0.8f 
        
        val textoEscala = if (alturaEmMetros < 1.0f) {
            "Alt Câmera: ${String.format(Locale.getDefault(), "%.0f", alturaEmMetros * 100)} cm"
        } else {
            "Alt Câmera: ${String.format(Locale.getDefault(), "%.2f", alturaEmMetros)} m"
        }
        
        canvas.drawText(textoEscala, 40f, 320f, paintEscala)
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
                val distTap = hypot(event.x - mTouchDownX, event.y - mTouchDownY)
                if (distTap < 15f) { 
                    if (modoLocacao) {
                        alvoLocacao = pontoFocado
                        onLocacaoLock?.invoke(alvoLocacao)
                    } else {
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
