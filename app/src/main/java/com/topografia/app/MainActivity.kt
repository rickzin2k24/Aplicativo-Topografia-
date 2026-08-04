package com.topografia.app

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter

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
    private lateinit var btnExportarCsv: Button
    private lateinit var btnConectar: Button

    private var latAtual: Double = 0.0
    private var lonAtual: Double = 0.0
    private var cotaChaoAtual: Double = 0.0
    private var statusRtkAtual: String = "Desconectado"

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
        btnExportarCsv = findViewById(R.id.btnExportarCsv)
        btnConectar = findViewById(R.id.btnConectar)

        btnConectar.setOnClickListener {
            val nmeaTeste = "\$GPGGA,123519,0854.1234,S,03622.5678,W,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }

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

            val novoPonto = PontoTopografico(nomeDoPonto, latAtual, lonAtual, cotaChaoAtual, statusRtkAtual)
            listaDePontos.add(novoPonto)

            Toast.makeText(this, "Ponto '$nomeDoPonto' SALVO! Total: ${listaDePontos.size}", Toast.LENGTH_LONG).show()
            etNomePonto.text.clear()
        }

        // AÇÃO DO NOVO BOTÃO DE EXPORTAR
        btnExportarCsv.setOnClickListener {
            exportarParaCSV()
        }
    }

    // FUNÇÃO QUE GERA O ARQUIVO
    private fun exportarParaCSV() {
        if (listaDePontos.isEmpty()) {
            Toast.makeText(this, "Nenhum ponto para exportar!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Cria um nome de arquivo único baseado na hora
            val nomeArquivo = "Levantamento_${System.currentTimeMillis()}.csv"
            
            // Acha a pasta "Downloads" do Android
            val pastaDownloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val arquivo = File(pastaDownloads, nomeArquivo)
            val escritor = FileWriter(arquivo)

            // Escreve o cabeçalho do arquivo
            escritor.append("Nome,Latitude,Longitude,Cota,Status\n")

            // Escreve todos os pontos gravados
            for (ponto in listaDePontos) {
                escritor.append("${ponto.nome},${ponto.latitude},${ponto.longitude},${ponto.cotaChao},${ponto.statusRtk}\n")
            }

            escritor.flush()
            escritor.close()

            Toast.makeText(this, "Arquivo salvo com sucesso na pasta Downloads!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
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
