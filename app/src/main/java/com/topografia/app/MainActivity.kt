package com.topografia.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Esta é a estrutura da nossa "memória" de pontos
data class PontoTopografico(
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val cotaChao: Double,
    val statusRtk: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusRTK: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvCota: TextView
    private lateinit var etAlturaBastao: EditText
    private lateinit var etCotaProjeto: EditText
    private lateinit var tvResultadoCorteAterro: TextView
    
    private lateinit var etNomePonto: EditText
    private lateinit var btnGravarPonto: Button
    private lateinit var btnConectar: Button

    // Variáveis que seguram a coordenada atual "congelada" para salvar
    private var latAtual: Double = 0.0
    private var lonAtual: Double = 0.0
    private var cotaChaoAtual: Double = 0.0
    private var statusRtkAtual: String = "Desconectado"

    // O Banco de Dados temporário (A lista de pontos)
    private val listaDePontos = mutableListOf<PontoTopografico>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusRTK = findViewById(R.id.tvStatusRTK)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvCota = findViewById(R.id.tvCota)
        etAlturaBastao = findViewById(R.id.etAlturaBastao)
        etCotaProjeto = findViewById(R.id.etCotaProjeto)
        tvResultadoCorteAterro = findViewById(R.id.tvResultadoCorteAterro)
        
        etNomePonto = findViewById(R.id.etNomePonto)
        btnGravarPonto = findViewById(R.id.btnGravarPonto)
        btnConectar = findViewById(R.id.btnConectar)

        btnConectar.setOnClickListener {
            val nmeaTeste = "\$GPGGA,123519,0854.1234,S,03622.5678,W,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }

        // AÇÃO DO BOTÃO GRAVAR PONTO
        btnGravarPonto.setOnClickListener {
            val nomeDoPonto = etNomePonto.text.toString()

            if (nomeDoPonto.isEmpty()) {
                Toast.makeText(this, "Erro: Digite um nome para o ponto!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (latAtual == 0.0 && lonAtual == 0.0) {
                Toast.makeText(this, "Erro: Nenhuma coordenada lida ainda!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Cria o ponto e salva na lista
            val novoPonto = PontoTopografico(nomeDoPonto, latAtual, lonAtual, cotaChaoAtual, statusRtkAtual)
            listaDePontos.add(novoPonto)

            // Avisa o topógrafo na tela e limpa o nome para o próximo ponto
            Toast.makeText(this, "Ponto '$nomeDoPonto' SALVO! Total: ${listaDePontos.size}", Toast.LENGTH_LONG).show()
            etNomePonto.text.clear()
        }
    }

    private fun processarNMEA(linhaNmea: String) {
        val partes = linhaNmea.split(",")
        
        if (partes[0] == "\$GPGGA" && partes.size > 10) {
            
            val latNmea = partes[2]
            val latDir = partes[3] 
            val lonNmea = partes[4]
            val lonDir = partes[5] 
            val cotaNmeaString = partes[9]
            val qualidade = partes[6] 

            latAtual = converterNmeaParaGrausDecimais(latNmea, latDir)
            lonAtual = converterNmeaParaGrausDecimais(lonNmea, lonDir)

            tvLatitude.text = "Lat: ${String.format("%.6f", latAtual)}°"
            tvLongitude.text = "Lon: ${String.format("%.6f", lonAtual)}°"

            when (qualidade) {
                "4" -> {
                    statusRtkAtual = "FIXO"
                    tvStatusRTK.text = "STATUS: FIXO"
                    tvStatusRTK.setTextColor(Color.parseColor("#4CAF50"))
                }
                "5" -> {
                    statusRtkAtual = "FLOAT"
                    tvStatusRTK.text = "STATUS: FLOAT"
                    tvStatusRTK.setTextColor(Color.parseColor("#FFC107"))
                }
                else -> {
                    statusRtkAtual = "AUTÔNOMO"
                    tvStatusRTK.text = "STATUS: AUTÔNOMO"
                    tvStatusRTK.setTextColor(Color.parseColor("#D32F2F"))
                }
            }

            try {
                val cotaNmea = cotaNmeaString.toDouble()
                val alturaBastao = etAlturaBastao.text.toString().toDoubleOrNull() ?: 0.0
                val cotaProjeto = etCotaProjeto.text.toString().toDoubleOrNull() ?: 0.0

                cotaChaoAtual = cotaNmea - alturaBastao
                
                tvCota.text = "Cota Antena: $cotaNmeaString m\nCota do Chão: ${String.format("%.3f", cotaChaoAtual)} m"

                if (cotaProjeto > 0.0) { 
                    val diferenca = cotaProjeto - cotaChaoAtual

                    if (diferenca > 0) {
                        tvResultadoCorteAterro.text = "ATERRAR: ${String.format("%.3f", diferenca)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#1976D2"))
                    } else if (diferenca < 0) {
                        tvResultadoCorteAterro.text = "CORTAR: ${String.format("%.3f", diferenca * -1)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#D32F2F"))
                    } else {
                        tvResultadoCorteAterro.text = "NO GREIDE (0.000 m)"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#4CAF50"))
                    }
                } else {
                    tvResultadoCorteAterro.text = "Aguardando Grade..."
                    tvResultadoCorteAterro.setTextColor(Color.parseColor("#000000"))
                }

            } catch (e: Exception) {
                tvResultadoCorteAterro.text = "Erro no cálculo"
            }
        }
    }

    private fun converterNmeaParaGrausDecimais(nmeaValor: String, direcao: String): Double {
        if (nmeaValor.isEmpty()) return 0.0
        
        val pontoIndex = nmeaValor.indexOf('.')
        if (pontoIndex == -1) return 0.0

        val grausString = nmeaValor.substring(0, pontoIndex - 2)
        val minutosString = nmeaValor.substring(pontoIndex - 2)

        val graus = grausString.toDoubleOrNull() ?: 0.0
        val minutos = minutosString.toDoubleOrNull() ?: 0.0

        var grausDecimais = graus + (minutos / 60.0)

        if (direcao == "S" || direcao == "W") {
            grausDecimais *= -1
        }

        return grausDecimais
    }
}
