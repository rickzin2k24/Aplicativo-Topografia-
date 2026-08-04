package com.topografia.app

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

data class PontoTopografico(
    val nome: String,
    val norteUtm: Double,
    val lesteUtm: Double,
    val cotaChao: Double,
    val zonaUtm: String,
    val statusRtk: String
)

class MainActivity : AppCompatActivity() {

    // Ferramentas do Topo
    private lateinit var btnCad: Button
    private lateinit var btnLocacao: Button
    private lateinit var btnCogo: Button
    private lateinit var btnConectar: Button

    // Displays
    private lateinit var tvStatusRTK: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvNorteUTM: TextView
    private lateinit var tvLesteUTM: TextView
    private lateinit var tvCota: TextView
    private lateinit var tvResultadoCorteAterro: TextView
    
    // Motor Gráfico CAD
    private lateinit var mapaTopografico: MapView
    
    // Inputs e Controles Base
    private lateinit var etAlturaBastao: EditText
    private lateinit var etCotaProjeto: EditText
    private lateinit var etNomePonto: EditText
    private lateinit var btnGravarPonto: ImageButton
    private lateinit var btnExportarCsv: Button

    // Variáveis Globais
    private var latAtual: Double = 0.0
    private var lonAtual: Double = 0.0
    private var norteUtmAtual: Double = 0.0
    private var lesteUtmAtual: Double = 0.0
    private var cotaChaoAtual: Double = 0.0
    private var zonaUtmAtual: String = "---"
    private var statusRtkAtual: String = "Desconectado"

    private val listaDePontos = mutableListOf<PontoTopografico>()

    // Motor Proj4j
    private val crsFactory = CRSFactory()
    private val ctFactory = CoordinateTransformFactory()
    private val wgs84Src = crsFactory.createFromName("EPSG:4326")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ligando Ferramentas
        btnCad = findViewById(R.id.btnCad)
        btnLocacao = findViewById(R.id.btnLocacao)
        btnCogo = findViewById(R.id.btnCogo)
        btnConectar = findViewById(R.id.btnConectar)

        // Ligando Textos e Inputs
        tvStatusRTK = findViewById(R.id.tvStatusRTK)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvNorteUTM = findViewById(R.id.tvNorteUTM)
        tvLesteUTM = findViewById(R.id.tvLesteUTM)
        tvCota = findViewById(R.id.tvCota)
        tvResultadoCorteAterro = findViewById(R.id.tvResultadoCorteAterro)
        etAlturaBastao = findViewById(R.id.etAlturaBastao)
        etCotaProjeto = findViewById(R.id.etCotaProjeto)
        etNomePonto = findViewById(R.id.etNomePonto)
        btnGravarPonto = findViewById(R.id.btnGravarPonto)
        btnExportarCsv = findViewById(R.id.btnExportarCsv)
        
        // Ligando o Mapa
        mapaTopografico = findViewById(R.id.mapaTopografico)
        
        // O que fazer quando a trena virtual do mapa terminar a conta
        mapaTopografico.onMedicaoCalculada = { distancia ->
            Toast.makeText(this, "Distância Trena: ${String.format("%.3f", distancia)} m", Toast.LENGTH_LONG).show()
        }

        // --- AÇÕES DAS FERRAMENTAS DO TOPO ---
        btnCad.setOnClickListener {
            Toast.makeText(this, "Ferramenta CAD (DXF / LandXML) será construída aqui!", Toast.LENGTH_SHORT).show()
        }

        btnLocacao.setOnClickListener {
            Toast.makeText(this, "Locação Ativada: Interpolação de linha a cada 10m!", Toast.LENGTH_SHORT).show()
        }

        btnCogo.setOnClickListener {
            Toast.makeText(this, "Calculadora COGO (Graus e Taludes) selecionada!", Toast.LENGTH_SHORT).show()
        }

        btnConectar.setOnClickListener {
            val nmeaTeste = "\$GPGGA,123519,0854.1234,S,03622.5678,W,4,08,0.9,764.123,M,46.9,M,,*47"
            processarNMEA(nmeaTeste)
        }

        // --- AÇÕES DA BASE ---
        btnGravarPonto.setOnClickListener {
            val nomeDoPonto = etNomePonto.text.toString()

            if (nomeDoPonto.isEmpty()) {
                Toast.makeText(this, "Erro: Digite um nome para o ponto!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (norteUtmAtual == 0.0) {
                Toast.makeText(this, "Erro: Sem coordenada RTK!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val novoPonto = PontoTopografico(nomeDoPonto, norteUtmAtual, lesteUtmAtual, cotaChaoAtual, zonaUtmAtual, statusRtkAtual)
            listaDePontos.add(novoPonto)
            
            // Envia a lista atualizada para o mapa desenhar
            mapaTopografico.listaDePontos = listaDePontos
            mapaTopografico.invalidate()

            Toast.makeText(this, "Ponto '$nomeDoPonto' SALVO! Total: ${listaDePontos.size}", Toast.LENGTH_SHORT).show()
            etNomePonto.text.clear()
        }

        btnExportarCsv.setOnClickListener {
            exportarParaCSV()
        }
    }

    private fun exportarParaCSV() {
        if (listaDePontos.isEmpty()) {
            Toast.makeText(this, "Nenhum ponto para exportar!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val nomeArquivo = "Levantamento_UTM_${System.currentTimeMillis()}.csv"
            val pastaDownloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val arquivo = File(pastaDownloads, nomeArquivo)
            val escritor = FileWriter(arquivo)

            escritor.append("Ponto,Norte(m),Leste(m),Elevacao(m),Codigo\n")

            for (ponto in listaDePontos) {
                escritor.append("${ponto.nome},${String.format("%.3f", ponto.norteUtm)},${String.format("%.3f", ponto.lesteUtm)},${String.format("%.3f", ponto.cotaChao)},${ponto.zonaUtm}\n")
            }
            escritor.flush()
            escritor.close()

            Toast.makeText(this, "Arquivo P,N,E,Z,D salvo com sucesso!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
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

            try {
                val lat = latAtual
                val lon = lonAtual
                val zonaUtmNumerica = ((lon + 180) / 6).toInt() + 1
                val hemisferio = if (lat >= 0) "n" else "s"
                zonaUtmAtual = "${zonaUtmNumerica}${hemisferio.toUpperCase()}"

                val epsgCode = if (lat >= 0) (32600 + zonaUtmNumerica) else (32700 + zonaUtmNumerica)
                val utmDst = crsFactory.createFromName("EPSG:${epsgCode}")

                val trans = ctFactory.createTransform(wgs84Src, utmDst)
                val latLonCoordinate = ProjCoordinate(lon, lat)
                val utmCoordinate = ProjCoordinate()
                trans.transform(latLonCoordinate, utmCoordinate)

                norteUtmAtual = utmCoordinate.y
                lesteUtmAtual = utmCoordinate.x
                
                // Envia a posição da antena para o mapa centralizar a tela
                mapaTopografico.rtkNorte = norteUtmAtual
                mapaTopografico.rtkLeste = lesteUtmAtual
                mapaTopografico.invalidate()

                tvLatitude.text = "Lat: ${String.format("%.6f", latAtual)}°"
                tvLongitude.text = "Lon: ${String.format("%.6f", lonAtual)}°"
                tvNorteUTM.text = "N: ${String.format("%.3f", norteUtmAtual)}"
                tvLesteUTM.text = "E: ${String.format("%.3f", lesteUtmAtual)}"

            } catch (e: Exception) {
                tvNorteUTM.text = "Erro UTM"
            }

            when (qualidade) {
                "4" -> {
                    statusRtkAtual = "FIXO"
                    tvStatusRTK.text = "STATUS: FIXO"
                    tvStatusRTK.setTextColor(Color.parseColor("#00FF00")) // Verde limão
                }
                "5" -> {
                    statusRtkAtual = "FLOAT"
                    tvStatusRTK.text = "STATUS: FLOAT"
                    tvStatusRTK.setTextColor(Color.parseColor("#FFC107"))
                }
                else -> {
                    statusRtkAtual = "AUTÔNOMO"
                    tvStatusRTK.text = "STATUS: AUTÔNOMO"
                    tvStatusRTK.setTextColor(Color.parseColor("#FF5252"))
                }
            }

            try {
                val cotaNmea = cotaNmeaString.toDouble()
                val alturaBastao = etAlturaBastao.text.toString().toDoubleOrNull() ?: 0.0
                val cotaProjeto = etCotaProjeto.text.toString().toDoubleOrNull() ?: 0.0

                cotaChaoAtual = cotaNmea - alturaBastao
                
                tvCota.text = String.format("%.3f", cotaChaoAtual)

                if (cotaProjeto > 0.0) { 
                    val diferenca = cotaProjeto - cotaChaoAtual
                    if (diferenca > 0) {
                        tvResultadoCorteAterro.text = "ATERRAR: ${String.format("%.3f", diferenca)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#00BFFF"))
                    } else if (diferenca < 0) {
                        tvResultadoCorteAterro.text = "CORTAR: ${String.format("%.3f", diferenca * -1)} m"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#FF5252"))
                    } else {
                        tvResultadoCorteAterro.text = "NO GREIDE"
                        tvResultadoCorteAterro.setTextColor(Color.parseColor("#00FF00"))
                    }
                } else {
                    tvResultadoCorteAterro.text = "---"
                }
            } catch (e: Exception) {
                tvResultadoCorteAterro.text = "Erro"
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

        if (direcao == "S" || direcao == "W") grausDecimais *= -1
        return grausDecimais
    }
}
