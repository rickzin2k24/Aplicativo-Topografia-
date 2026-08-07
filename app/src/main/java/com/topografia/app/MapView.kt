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
import kotlin.math.max
import kotlin.math.min

class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paintPonto = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintTexto = Paint().apply { color = Color.WHITE; textSize = 35f }
    private val paintLinhaMedicao = Paint().apply { color = Color.parseColor("#FFC107"); strokeWidth = 5f }
    private val paintRTK = Paint().apply { color = Color.parseColor("#00E676"); style = Paint.Style.FILL }
    private val paintSelecao = Paint().apply { color = Color.parseColor("#00BFFF"); style = Paint.Style.FILL }
    private val paintCruz = Paint().apply { color = Color.RED; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintIma = Paint().apply { color = Color.parseColor("#FF9800"); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val paintLinhaLocacao = Paint().apply { color = Color.parseColor("#00FFFF"); strokeWidth = 6f; style = Paint.Style.STROKE }
    
    // Pincel da Malha de Superfície (TIN)
    private val paintMalha = Paint().apply { color = Color.parseColor("#3300FF00"); strokeWidth = 2f; style = Paint.Style.STROKE }
    
    private val paintEscala = Paint().apply { 
        color = Color.parseColor("#00FFFF")
        textSize = 38f
        isFakeBoldText = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK) 
    }

    var listaDePontos = listOf<PontoTopografico>()
        set(value) {
            field = value
            gerarMalhaTIN() // Refaz a malha sempre que a lista de pontos mudar
            invalidate()
        }

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
    
    private var pathMalhaTIN = Path()

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val amortecido = 1.0f + ((detector.scaleFactor - 1.0f) * 0.15f) 
                val newScaleFactor = max(0.01f, min(mScaleFactor * amortecido, 500.0f)) // Mais limite de zoom
                
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

    // BOTÃO 1: Focar no GPS do Celular
    fun centralizarNoUsuario() {
        mPosX = 0f
        mPosY = 0f
        mScaleFactor = 1.0f
        invalidate()
    }

    // BOTÃO 2: Focar no Projeto (Acha os pontos importados onde quer que eles estejam)
    fun zoomParaProjeto() {
        if (listaDePontos.isEmpty()) return
        
        var minE = Double.MAX_VALUE
        var maxE = -Double.MAX_VALUE
        var minN = Double.MAX_VALUE
        var maxN = -Double.MAX_VALUE
        
        for(p in listaDePontos) {
            if (p.lesteUtm < minE) minE = p.lesteUtm
            if (p.lesteUtm > maxE) maxE = p.lesteUtm
            if (p.norteUtm < minN) minN = p.norteUtm
            if (p.norteUtm > maxN) maxN = p.norteUtm
        }
        
        val centroProjE = (minE + maxE) / 2.0
        val centroProjN = (minN + maxN) / 2.0
        
        val diffE = maxE - minE
        val diffN = maxN - minN
        val maxDiff = max(diffE, diffN)
        
        val telaMin = min(width, height).toFloat() * 0.6f 
        
        if (maxDiff > 0.5) {
            mScaleFactor = (telaMin / (maxDiff * escala)).toFloat()
        } else {
            mScaleFactor = 5.0f // Se for um ponto só
        }
        
        val offsetE = (centroProjE - rtkLeste) * escala
        val offsetN = (centroProjN - rtkNorte) * escala
        
        val centroTelaX = width / 2f
        val centroTelaY = height / 2f
        
        mPosX = centroTelaX - (centroTelaX + offsetE.toFloat()) * mScaleFactor
        mPosY = centroTelaY - (centroTelaY - offsetN.toFloat()) * mScaleFactor
        
        invalidate()
    }

    // MOTOR DE SUPERFÍCIE (TIN Grego - Conecta vizinhos próximos para visualizar a malha)
    private fun gerarMalhaTIN() {
        pathMalhaTIN.reset()
        if (listaDePontos.size < 3) return
        
        val centroX = width / 2f
        val centroY = height / 2f
        
        // Algoritmo robusto O(N^2) para evitar travamento da UI em arquivos gigantes
        for (i in listaDePontos.indices) {
            val p1 = listaDePontos[i]
            val x1 = centroX + ((p1.lesteUtm - rtkLeste) * escala).toFloat()
            val y1 = centroY - ((p1.norteUtm - rtkNorte) * escala).toFloat()
            
            // Conecta com os 3 vizinhos mais próximos
            val distancias = mutableListOf<Pair<Int, Double>>()
            for (j in listaDePontos.indices) {
                if (i != j) {
                    val d = hypot(p1.lesteUtm - listaDePontos[j].lesteUtm, p1.norteUtm - listaDePontos[j].norteUtm)
                    distancias.add(Pair(j, d))
                }
            }
            distancias.sortBy { it.second }
            val limites = min(3, distancias.size)
            
            for (k in 0 until limites) {
                val vizinho = listaDePontos[distancias[k].first]
                val x2 = centroX + ((vizinho.lesteUtm - rtkLeste) * escala).toFloat()
                val y2 = centroY - ((vizinho.norteUtm - rtkNorte) * escala).toFloat()
                
                pathMalhaTIN.moveTo(x1, y1)
                pathMalhaTIN.lineTo(x2, y2)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#121212")) 

        canvas.save() 
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)

        val centroX = width / 2f
        val centroY = height / 2f
        
        // 1. Desenha a Malha TIN por baixo de tudo
        paintMalha.strokeWidth = 2f / mScaleFactor
        canvas.drawPath(pathMalhaTIN, paintMalha)

        // 2. Desenha os Pontos
        for (ponto in listaDePontos) {
            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()

            val raioPonto = 12f / mScaleFactor
            canvas.drawCircle(telaX, telaY, raioPonto, paintPonto)
            
            val textSizeOriginal = 30f
            paintTexto.textSize = textSizeOriginal / mScaleFactor
            canvas.drawText(ponto.nome, telaX + (15f / mScaleFactor), telaY + (10f / mScaleFactor), paintTexto)

            if (ponto == pontoSelecionado1 || ponto == pontoSelecionado2) {
                canvas.drawCircle(telaX, telaY, 18f / mScaleFactor, paintSelecao)
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
        
        canvas.drawText(textoEscala, 30f, height - 280f, paintEscala)
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
