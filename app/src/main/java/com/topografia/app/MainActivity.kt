package com.topografia.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvCoordenadas = findViewById<TextView>(R.id.tvCoordenadas)
        val btnLerNmea = findViewById<Button>(R.id.btnLerNmea)

        btnLerNmea.setOnClickListener {
            tvCoordenadas.text = "Botão ativado!\nPronto para receber o RTK."
        }
    }
}
