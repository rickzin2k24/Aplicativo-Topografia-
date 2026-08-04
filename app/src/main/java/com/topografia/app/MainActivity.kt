package com.topografia.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusRTK: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvCota: TextView
    private lateinit var btnConectar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ligando o código à tela
        tvStatusRTK = findViewById(R.id.tvStatusRTK)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvCota = findViewById(R.id.tvCota)
        btnConectar = findViewById(R.id.btnConectar)

        btnConectar.setOnClickListener {
            // No futuro, isso vai ligar o Bluetooth. 
            // Por enquanto, vamos mandar uma linha NMEA de teste para ver a mágica acontecer!
            val nmeaTeste = "\$GPGGA,123519,4807.038,N,01131.000,E,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }
    }

    // O Fatiador NMEA
    private fun processarNMEA(linhaNmea: String) {
        val partes = linhaNmea.split(",")
        
        // Verifica se é a linha que contém as coordenadas 3D (GPGGA)
        if (partes[0] == "\$GPGGA" && partes.size > 10) {
            
            val lat = partes[2]
            val lon = partes[4]
            val cota = partes[9]
            val qualidade = partes[6] 

            // Atualiza os textos na tela
            tvLatitude.text = "Lat: $lat"
            tvLongitude.text = "Lon: $lon"
            tvCota.text = "Cota (Z): $cota m"

            // Verifica se o RTK está Fixo (4), Float (5) ou Autônomo (1)
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
        }
    }
}
