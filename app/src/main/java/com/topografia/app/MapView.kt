package com.topografia.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.sqrt

// Esta classe é a tela preta central que desenha o CAD e entende o toque do dedo
class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // Ferramentas de pintura (cores e espessuras)
    private val paintPonto = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintTexto = Paint().apply { color = Color.WHITE; textSize = 35f }
    private val paintLinhaMedicao = Paint().apply { color = Color.parseColor("#FFC107"); strokeWidth = 5f }
    private val paintRTK = Paint().apply { color = Color.parseColor("#00FF00"); style = Paint.Style.FILL } // Verde limão

    // Dados que a tela vai receber
    var listaDePontos = listOf<PontoTopografico>()
    var rtkNorte = 0.0
    var rtkLeste = 0.0

    // Variáveis para a Trena Virtual
    private var pontoSelecionado1: PontoTopografico? = null
    private var pontoSelecionado2: PontoTopografico? = null
    var onMedicaoCalculada: ((Double) -> Unit)? = null // Função para avisar a tela principal

    // O ESCALÍMETRO (1 metro no real = 15 pixels na tela)
    private val escala = 15f 

    // Função que desenha as coisas na tela dezenas de vezes por segundo
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#191919")) // Fundo escuro do terreno

        val centroX = width / 2f
        val centroY = height / 2f

        // 1. Desenha todos os pontos gravados e os vetores
        for (ponto in listaDePontos) {
            // Calcula a posição do ponto na tela em relação a onde a antena está
            val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
            val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat() // Y é invertido na tela

            canvas.drawCircle(telaX, telaY, 15f, paintPonto)
            canvas.drawText(ponto.nome, telaX + 25f, telaY + 10f, paintTexto)

            // Se o ponto foi tocado pelo dedo, pinta ele de azul para dar destaque
            if (ponto == pontoSelecionado1 || ponto == pontoSelecionado2) {
                val paintSelecao = Paint().apply { color = Color.parseColor("#00BFFF"); style = Paint.Style.FILL }
                canvas.drawCircle(telaX, telaY, 20f, paintSelecao)
            }
        }

        // 2. Trena Virtual: Desenha a linha de medição se 2 pontos estiverem selecionados
        if (pontoSelecionado1 != null && pontoSelecionado2 != null) {
            val x1 = centroX + ((pontoSelecionado1!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y1 = centroY - ((pontoSelecionado1!!.norteUtm - rtkNorte) * escala).toFloat()
            val x2 = centroX + ((pontoSelecionado2!!.lesteUtm - rtkLeste) * escala).toFloat()
            val y2 = centroY - ((pontoSelecionado2!!.norteUtm - rtkNorte) * escala).toFloat()
            
            canvas.drawLine(x1, y1, x2, y2, paintLinhaMedicao)
        }

        // 3. Desenha VOCÊ (Antena RTK) sempre exatamente no centro da tela
        canvas.drawCircle(centroX, centroY, 20f, paintRTK)
    }

    // Função que "sente" onde o seu dedo tocou na tela
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val toqueX = event.x
            val toqueY = event.y
            val centroX = width / 2f
            val centroY = height / 2f

            var tocouEmAlgumPonto = false

            // Varre a lista pra ver se o dedo encostou perto de algum ponto
            for (ponto in listaDePontos) {
                val telaX = centroX + ((ponto.lesteUtm - rtkLeste) * escala).toFloat()
                val telaY = centroY - ((ponto.norteUtm - rtkNorte) * escala).toFloat()

                // Pitágoras para saber a distância do toque até o ponto (margem de erro do dedo grosso)
                val distanciaDoDedo = hypot(telaX - toqueX, telaY - toqueY)
                
                if (distanciaDoDedo < 60f) { // Se tocou a menos de 60 pixels do ponto
                    tocouEmAlgumPonto = true
                    
                    if (pontoSelecionado1 == null) {
                        pontoSelecionado1 = ponto // Seleciona o primeiro
                    } else if (pontoSelecionado2 == null && ponto != pontoSelecionado1) {
                        pontoSelecionado2 = ponto // Seleciona o segundo e MEDE!
                        
                        // Cálculo matemático da distância horizontal (DX/DY)
                        val dx = pontoSelecionado2!!.lesteUtm - pontoSelecionado1!!.lesteUtm
                        val dy = pontoSelecionado2!!.norteUtm - pontoSelecionado1!!.norteUtm
                        val distanciaReal = sqrt(dx * dx + dy * dy)
                        
                        // Envia a resposta pra tela principal
                        onMedicaoCalculada?.invoke(distanciaReal) 
                    } else {
                        // Se tocar num terceiro ponto, zera tudo e começa de novo
                        pontoSelecionado1 = ponto
                        pontoSelecionado2 = null
                    }
                    
                    invalidate() // Manda o Android redesenhar a tela com as cores novas
                    break
                }
            }

            // Se tocou no vazio, limpa as seleções
            if (!tocouEmAlgumPonto) {
                pontoSelecionado1 = null
                pontoSelecionado2 = null
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
