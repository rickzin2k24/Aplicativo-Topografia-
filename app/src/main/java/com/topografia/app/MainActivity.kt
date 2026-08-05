package com.topografia.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStreamWriter

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.*

// 1. ESTRUTURA ATUALIZADA DO PONTO
data class PontoTopografico(
    val nome: String,
    val norteUtm: Double,
    val lesteUtm: Double,
    val cotaChao: Double,
    val zonaUtm: String,
    val statusRtk: String,
    val nomeProjeto: String // O ponto agora sabe onde ele mora
)

class MainActivity : AppCompatActivity() {

    private lateinit var btnCad: Button
    private lateinit var btnLocacao: Button
    private lateinit var btnCogo: Button
    private lateinit var btnConectar: Button

    private lateinit var tvStatusRTK: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvNorteUTM: TextView
    private lateinit var tvLesteUTM: TextView
    private lateinit var tvCota: TextView
    private lateinit var tvResultadoCorteAterro: TextView
    
    private lateinit var mapaTopografico: MapView
    
    private lateinit var etAlturaBastao: EditText
    private lateinit var etCotaProjeto: EditText
    private lateinit var etNomePonto: EditText
    private lateinit var btnGravarPonto: ImageButton
    private lateinit var btnExportarCsv: Button

    private var latAtual: Double = 0.0
    private var lonAtual: Double = 0.0
    private var norteUtmAtual: Double = 0.0
    private var lesteUtmAtual: Double = 0.0
    private var cotaChaoAtual: Double = 0.0
    private var zonaUtmAtual: String = "---"
    private var statusRtkAtual: String = "Desconectado"

    private val listaDePontos = mutableListOf<PontoTopografico>()
    private lateinit var dbHelper: DatabaseHelper
    
    // VARIÁVEL DE GESTÃO DO PROJETO ATUAL
    private var projetoAtual: String = "Projeto_Padrao"

    // 2. MOTOR DE EXPORTAÇÃO NATIVO (Escolher Pasta)
    private val exportarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    val escritor = OutputStreamWriter(outputStream)
                    
                    escritor.append("Ponto,Norte(m),Leste(m),Elevacao(m),Codigo,Projeto\n")
                    for (ponto in listaDePontos) {
                        escritor.append("${ponto.nome},${String.format("%.3f", ponto.norteUtm)},${String.format("%.3f", ponto.lesteUtm)},${String.format("%.3f", ponto.cotaChao)},${ponto.zonaUtm},${ponto.nomeProjeto}\n")
                    }
                    escritor.flush()
                    escritor.close()
                    Toast.makeText(this, "Arquivo CSV salvo na pasta escolhida!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao salvar arquivo.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        btnCad = findViewById(R.id.btnCad)
        btnLocacao = findViewById(R.id.btnLocacao)
        btnCogo = findViewById(R.id.btnCogo)
        btnConectar = findViewById(R.id.btnConectar)

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
        
        mapaTopografico = findViewById(R.id.mapaTopografico)
        
        // Pede para o usuário escolher ou criar um projeto ao abrir o App
        mostrarDialogoDeProjeto()

        mapaTopografico.onMedicaoCalculada = { distancia ->
            Toast.makeText(this, "Distância Trena: ${String.format("%.3f", distancia)} m", Toast.LENGTH_LONG).show()
        }

        // O Botão CAD agora pode servir para trocar de projeto no futuro
        btnCad.setOnClickListener { mostrarDialogoDeProjeto() }
        
        btnLocacao.setOnClickListener { Toast.makeText(this, "Locação Ativada", Toast.LENGTH_SHORT).show() }
        btnCogo.setOnClickListener { Toast.makeText(this, "Calculadora COGO", Toast.LENGTH_SHORT).show() }

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
            if (norteUtmAtual == 0.0) {
                Toast.makeText(this, "Erro: Sem coordenada RTK!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Grava o ponto já carimbado com o nome do projeto atual
            val novoPonto = PontoTopografico(nomeDoPonto, norteUtmAtual, lesteUtmAtual, cotaChaoAtual, zonaUtmAtual, statusRtkAtual, projetoAtual)
            
            listaDePontos.add(novoPonto)
            dbHelper.inserirPonto(novoPonto)
            
            mapaTopografico.listaDePontos = listaDePontos
            mapaTopografico.invalidate()

            Toast.makeText(this, "Ponto salvo no projeto: $projetoAtual", Toast.LENGTH_SHORT).show()

            val match = Regex("(\\d+)$").find(nomeDoPonto)
            if (match != null) {
                val numStr = match.value
                val nextNum = numStr.toInt() + 1
                val newName = nomeDoPonto.dropLast(numStr.length) + String.format("%0${numStr.length}d", nextNum)
                etNomePonto.setText(newName)
            } else {
                etNomePonto.setText("${nomeDoPonto}1")
            }
            etNomePonto.setSelection(etNomePonto.text.length)
        }

        btnExportarCsv.setOnClickListener {
            if (listaDePontos.isEmpty()) {
                Toast.makeText(this, "O projeto $projetoAtual está vazio!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Abre a tela nativa do Android para o topógrafo escolher a pasta e confirmar o nome do arquivo
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "${projetoAtual}_AsBuilt.csv") // Nome sugerido do arquivo
            }
            exportarLauncher.launch(intent)
        }
    }

    // 3. CAIXA DE DIÁLOGO PARA CRIAR/SELECIONAR PROJETO
    private fun mostrarDialogoDeProjeto() {
        val input = EditText(this)
        input.hint = "Ex: Loteamento_A"
        input.setText(projetoAtual)

        AlertDialog.Builder(this)
            .setTitle("Gerenciador de Projetos")
            .setMessage("Digite o nome da obra/projeto atual:")
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                val nomeDigitado = input.text.toString().trim()
                if (nomeDigitado.isNotEmpty()) {
                    projetoAtual = nomeDigitado
                    btnCad.text = "🗺️ $projetoAtual" // Atualiza o botão lá em cima
                    
                    // Limpa a tela e busca só os pontos dessa obra no Banco de Dados
                    listaDePontos.clear()
                    listaDePontos.addAll(dbHelper.buscarPontosPorProjeto(projetoAtual))
                    mapaTopografico.listaDePontos = listaDePontos
                    mapaTopografico.invalidate()
                    
                    Toast.makeText(this, "Projeto $projetoAtual carregado!", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun processarNMEA(linhaNmea: String) {
        if (!linhaNmea.startsWith("$") || !linhaNmea.contains("*")) return

        try {
            val partes = linhaNmea.split(",")
            if (partes[0] == "\$GPGGA" && partes.size > 10) {
                val latNmea = partes[2]
                val latDir = partes[3] 
                val lonNmea = partes[4]
                val lonDir = partes[5] 
                val cotaNmeaString = partes[9]
                val qualidade = partes[6] 

                if (latNmea.isEmpty() || lonNmea.isEmpty() || cotaNmeaString.isEmpty()) return

                latAtual = converterNmeaParaGrausDecimais(latNmea, latDir)
                lonAtual = converterNmeaParaGrausDecimais(lonNmea, lonDir)

                val utmCoords = converterGrausParaUTM(latAtual, lonAtual)
                lesteUtmAtual = utmCoords[0]
                norteUtmAtual = utmCoords[1]
                
                val zonaUtmNumerica = ((lonAtual + 180) / 6).toInt() + 1
                val hemisferio = if (latAtual >= 0) "N" else "S"
                zonaUtmAtual = "${zonaUtmNumerica}${hemisferio}"
                
                mapaTopografico.rtkNorte = norteUtmAtual
                mapaTopografico.rtkLeste = lesteUtmAtual
                mapaTopografico.invalidate()

                tvLatitude.text = "Lat: ${String.format("%.6f", latAtual)}°"
                tvLongitude.text = "Lon: ${String.format("%.6f", lonAtual)}°"
                tvNorteUTM.text = "N: ${String.format("%.3f", norteUtmAtual)}"
                tvLesteUTM.text = "E: ${String.format("%.3f", lesteUtmAtual)}"

                when (qualidade) {
                    "4" -> {
                        statusRtkAtual = "FIXO"
                        tvStatusRTK.text = "STATUS: FIXO"
                        tvStatusRTK.setTextColor(Color.parseColor("#00FF00"))
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
            }
        } catch (e: Exception) {}
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

    private fun converterGrausParaUTM(lat: Double, lon: Double): DoubleArray {
        val a = 6378137.0
        val eccSquared = 0.00669438
        val k0 = 0.9996

        val zoneNumber = ((lon + 180) / 6).toInt() + 1
        val lonOrigin = (zoneNumber - 1) * 6 - 180 + 3
        val lonOriginRad = Math.toRadians(lonOrigin.toDouble())
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val eccPrimeSquared = eccSquared / (1 - eccSquared)
        val N = a / sqrt(1 - eccSquared * sin(latRad) * sin(latRad))
        val T = tan(latRad) * tan(latRad)
        val C = eccPrimeSquared * cos(latRad) * cos(latRad)
        val A = cos(latRad) * (lonRad - lonOriginRad)

        val M = a * ((1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256) * latRad
                - (3 * eccSquared / 8 + 3 * eccSquared * eccSquared / 32 + 45 * eccSquared * eccSquared * eccSquared / 1024) * sin(2 * latRad)
                + (15 * eccSquared * eccSquared / 256 + 45 * eccSquared * eccSquared * eccSquared / 1024) * sin(4 * latRad)
                - (35 * eccSquared * eccSquared * eccSquared / 3072) * sin(6 * latRad))

        val utmEasting = (k0 * N * (A + (1 - T + C) * A * A * A / 6
                + (5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120)
                + 500000.0)

        var utmNorthing = (k0 * (M + N * tan(latRad) * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720)))
        
        if (lat < 0) {
            utmNorthing += 10000000.0
        }
        
        return doubleArrayOf(utmEasting, utmNorthing)
    }
}
