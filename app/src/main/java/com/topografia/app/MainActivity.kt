package com.topografia.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusRTK: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvCota: TextView
    private lateinit var etAlturaBastao: EditText
    private lateinit var etCotaProjeto: EditText
    private lateinit var tvResultadoCorteAterro: TextView
    private lateinit var btnConectar: Button

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
        btnConectar = findViewById(R.id.btnConectar)

        btnConectar.setOnClickListener {
            // Nova string NMEA de teste com coordenadas Sul (S) e Oeste (W)
            val nmeaTeste = "\$GPGGA,123519,0854.1234,S,03622.5678,W,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }
    }

    private fun processarNMEA(linhaNmea: String) {
        val partes = linhaNmea.split(",")
        
        if (partes[0] == "\$GPGGA" && partes.size > 10) {
            
            // Agora pegamos o número e também a letra da direção (N/S, E/W)
            val latNmea = partes[2]
            val latDir = partes[3] 
            val lonNmea = partes[4]
            val lonDir = partes[5] 
            val cotaNmeaString = partes[9]
            val qualidade = partes[6] 

            // Convertendo a string bruta do NMEA para Graus Decimais exatos
            val latGraus = converterNmeaParaGrausDecimais(latNmea, latDir)
            val lonGraus = converterNmeaParaGrausDecimais(lonNmea, lonDir)

            // Atualizamos a tela mostrando a coordenada com 6 casas decimais de precisão
            tvLatitude.text = "Lat: ${String.format("%.6f", latGraus)}°"
            tvLongitude.text = "Lon: ${String.format("%.6f", lonGraus)}°"

            when (qualidade) {
                "4" -> {
                    tvStatusRTK.text = "STATUS: FIXO"
                    tvStatusRTK.setTextColor(Color.parseColor("#4CAF50"))
                }
                "5" -> {
                    tvStatusRTK.text = "STATUS: FLOAT"
                    tvStatusRTK.setTextColor(Color.parseColor("#FFC107"))
                }
                else -> {
                    tvStatusRTK.text = "STATUS: AUTÔNOMO"
                    tvStatusRTK.setTextColor(Color.parseColor("#D32F2F"))
                }
            }

            try {
                val cotaNmea = cotaNmeaString.toDouble()
                val alturaBastao = etAlturaBastao.text.toString().toDoubleOrNull() ?: 0.0
                val cotaProjeto = etCotaProjeto.text.toString().toDoubleOrNull() ?: 0.0

                val cotaTerreno = cotaNmea - alturaBastao
                
                tvCota.text = "Cota Antena: $cotaNmeaString m\nCota do Chão: ${String.format("%.3f", cotaTerreno)} m"

                if (cotaProjeto > 0.0) { 
                    val diferenca = cotaProjeto - cotaTerreno

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

    // A MÁGICA DA CONVERSÃO GEOGRÁFICA
    private fun converterNmeaParaGrausDecimais(nmeaValor: String, direcao: String): Double {
        if (nmeaValor.isEmpty()) return 0.0
        
        // A lógica do NMEA é fundir graus e minutos (ex: 0854.1234)
        // O ponto decimal nos ajuda a separar as casas
        val pontoIndex = nmeaValor.indexOf('.')
        if (pontoIndex == -1) return 0.0

        // Os minutos são sempre os 2 dígitos colados antes do ponto + os decimais (ex: 54.1234)
        // Os graus é tudo o que sobrar antes disso (ex: 08)
        val grausString = nmeaValor.substring(0, pontoIndex - 2)
        val minutosString = nmeaValor.substring(pontoIndex - 2)

        val graus = grausString.toDoubleOrNull() ?: 0.0
        val minutos = minutosString.toDoubleOrNull() ?: 0.0

        // Transforma os minutos em graus dividindo por 60 e soma
        var grausDecimais = graus + (minutos / 60.0)

        // Aplica a regra do hemisfério (Sul ou Oeste ficam negativos)
        if (direcao == "S" || direcao == "W") {
            grausDecimais *= -1
        }

        return grausDecimais
    }
}
