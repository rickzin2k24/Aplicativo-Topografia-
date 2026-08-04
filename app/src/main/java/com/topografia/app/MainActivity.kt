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

        // Ligando o código aos elementos da tela
        tvStatusRTK = findViewById(R.id.tvStatusRTK)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvCota = findViewById(R.id.tvCota)
        etAlturaBastao = findViewById(R.id.etAlturaBastao)
        etCotaProjeto = findViewById(R.id.etCotaProjeto)
        tvResultadoCorteAterro = findViewById(R.id.tvResultadoCorteAterro)
        btnConectar = findViewById(R.id.btnConectar)

        btnConectar.setOnClickListener {
            // Nossa linha NMEA de teste simulando a antena
            val nmeaTeste = "\$GPGGA,123519,4807.038,N,01131.000,E,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }
    }

    private fun processarNMEA(linhaNmea: String) {
        val partes = linhaNmea.split(",")
        
        // Verifica se é a linha que contém as coordenadas 3D (GPGGA)
        if (partes[0] == "\$GPGGA" && partes.size > 10) {
            
            val lat = partes[2]
            val lon = partes[4]
            val cotaNmeaString = partes[9]
            val qualidade = partes[6] 

            tvLatitude.text = "Lat: $lat"
            tvLongitude.text = "Lon: $lon"

            // Verifica o status do RTK
            when (qualidade) {
                "4" -> {
                    tvStatusRTK.text = "STATUS: FIXO"
                    tvStatusRTK.setTextColor(Color.parseColor("#4CAF50")) // Verde
                }
                "5" -> {
                    tvStatusRTK.text = "STATUS: FLOAT"
                    tvStatusRTK.setTextColor(Color.parseColor("#FFC107")) // Amarelo
                }
                else -> {
                    tvStatusRTK.text = "STATUS: AUTÔNOMO"
                    tvStatusRTK.setTextColor(Color.parseColor("#D32F2F")) // Vermelho
                }
            }

            // --- INÍCIO DO CÁLCULO TOPOGRÁFICO ---
            try {
                // Transforma os textos em números decimais
                val cotaNmea = cotaNmeaString.toDouble()
                val alturaBastao = etAlturaBastao.text.toString().toDoubleOrNull() ?: 0.0
                val cotaProjeto = etCotaProjeto.text.toString().toDoubleOrNull() ?: 0.0

                // 1. Calcula a cota real do chão (subtraindo o bastão)
                val cotaTerreno = cotaNmea - alturaBastao
                
                // Atualiza a tela mostrando a cota da Antena e a do Chão juntas
                tvCota.text = "Cota Antena: $cotaNmeaString m\nCota do Chão: ${String.format("%.3f", cotaTerreno)} m"

                // 2. Calcula a diferença para o projeto (Corte ou Aterro)
                if (cotaProjeto > 0.0) { 
                    val diferenca = cotaProjeto - cotaTerreno

                    if (diferenca > 0) {
                        tvResultadoCorteAterro.text = "ATERRAR: ${String.format("%.3f", diferenca)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#1976D2")) // Azul
                    } else if (diferenca < 0) {
                        tvResultadoCorteAterro.text = "CORTAR: ${String.format("%.3f", diferenca * -1)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#D32F2F")) // Vermelho
                    } else {
                        tvResultadoCorteAterro.text = "NO GREIDE (0.000 m)"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#4CAF50")) // Verde
                    }
                } else {
                    tvResultadoCorteAterro.text = "Aguardando Grade..."
                    tvResultadoCorteAterro.setTextColor(Color.parseColor("#000000")) // Preto
                }

            } catch (e: Exception) {
                tvResultadoCorteAterro.text = "Erro no cálculo"
            }
        }
    }
}
