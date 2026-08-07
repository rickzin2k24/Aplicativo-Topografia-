package com.topografia.app

// Commit forçando a renderização perfeita da UI e Zoom de Pontos
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.math.*

data class PontoTopografico(
    val nome: String,
    val norteUtm: Double,
    val lesteUtm: Double,
    val cotaChao: Double,
    val zonaUtm: String,
    val statusRtk: String,
    val nomeProjeto: String
)

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var btnCad: Button
    private lateinit var btnLocacao: Button
    private lateinit var btnCogo: Button
    private lateinit var btnConectar: Button
    private lateinit var btnCentralizar: ImageButton
    private lateinit var btnZoomProjeto: ImageButton
    private lateinit var btnImportar: Button

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
    
    private var areaAtual: String = "CAMPO"
    private var nomeObraAtual: String = "Padrao"
    private var projetoAtual: String = "[CAMPO] Padrao" 

    private lateinit var locationManager: LocationManager
    private val PERMISSION_REQUEST_GPS = 100

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var magnetometro: Sensor? = null
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val exportarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    val escritor = OutputStreamWriter(outputStream)
                    escritor.append("Ponto,Norte(m),Leste(m),Elevacao(m),Zona,Projeto\n")
                    for (ponto in listaDePontos) {
                        escritor.append(
                            "${ponto.nome}," +
                            "${String.format(Locale.US, "%.3f", ponto.norteUtm)}," +
                            "${String.format(Locale.US, "%.3f", ponto.lesteUtm)}," +
                            "${String.format(Locale.US, "%.3f", ponto.cotaChao)}," +
                            "${ponto.zonaUtm}," +
                            "${ponto.nomeProjeto}\n"
                        )
                    }
                    escritor.flush()
                    escritor.close()
                    Toast.makeText(this, "Arquivo CSV salvo com sucesso!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao salvar CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val importarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processarArquivoImportado(uri)
        } else {
            Toast.makeText(this, "Importação cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        btnCad = findViewById(R.id.btnCad)
        btnLocacao = findViewById(R.id.btnLocacao)
        btnCogo = findViewById(R.id.btnCogo)
        btnConectar = findViewById(R.id.btnConectar)
        btnCentralizar = findViewById(R.id.btnCentralizar)
        btnZoomProjeto = findViewById(R.id.btnZoomProjeto)
        btnImportar = findViewById(R.id.btnImportar)

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

        mostrarDialogoDeProjeto()
        checarPermissoesGps() 

        mapaTopografico.onMedicaoCalculada = { distancia ->
            Toast.makeText(this, "Distância Trena: ${String.format(Locale.getDefault(), "%.3f", distancia)} m", Toast.LENGTH_LONG).show()
        }

        btnCentralizar.setOnClickListener {
            mapaTopografico.centralizarNoUsuario()
            Toast.makeText(this, "Foco: RTK Atual", Toast.LENGTH_SHORT).show()
        }
        
        btnZoomProjeto.setOnClickListener {
            mapaTopografico.zoomParaProjeto()
            Toast.makeText(this, "Foco: Malha do Projeto", Toast.LENGTH_SHORT).show()
        }

        btnImportar.setOnClickListener {
            importarLauncher.launch("*/*")
        }

        btnLocacao.setOnClickListener {
            mapaTopografico.modoLocacao = !mapaTopografico.modoLocacao
            if (mapaTopografico.modoLocacao) {
                btnLocacao.setBackgroundColor(Color.parseColor("#D84315"))
                Toast.makeText(this, "MODO LOCAÇÃO ATIVADO", Toast.LENGTH_SHORT).show()
                tvResultadoCorteAterro.text = "SELECIONE ALVO..."
                tvResultadoCorteAterro.setTextColor(Color.parseColor("#AAAAAA"))
            } else {
                btnLocacao.setBackgroundColor(Color.parseColor("#4D3319"))
                mapaTopografico.alvoLocacao = null
                tvResultadoCorteAterro.text = "---"
            }
            mapaTopografico.invalidate()
        }

        mapaTopografico.onLocacaoLock = { ponto ->
            if (ponto != null) {
                Toast.makeText(this, "ALVO TRAVADO: ${ponto.nome}", Toast.LENGTH_SHORT).show()
                val dist = hypot(ponto.lesteUtm - lesteUtmAtual, ponto.norteUtm - norteUtmAtual)
                tvResultadoCorteAterro.text = "ALVO: ${ponto.nome}\nDIST: ${String.format(Locale.getDefault(), "%.3f", dist)}m"
                tvResultadoCorteAterro.setTextColor(Color.parseColor("#00FFFF"))
            } else {
                tvResultadoCorteAterro.text = "SELECIONE ALVO..."
                tvResultadoCorteAterro.setTextColor(Color.parseColor("#AAAAAA"))
            }
        }

        btnCad.setOnClickListener { mostrarDialogoDeProjeto() }
        btnCogo.setOnClickListener { Toast.makeText(this, "Calculadora COGO", Toast.LENGTH_SHORT).show() }

        btnConectar.setOnClickListener {
            Toast.makeText(this, "Aguardando implementação Bluetooth SPP...", Toast.LENGTH_SHORT).show()
        }

        btnGravarPonto.setOnClickListener {
            val nomeDoPonto = etNomePonto.text.toString()

            if (nomeDoPonto.isEmpty()) {
                Toast.makeText(this, "Erro: Digite um nome para o ponto!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (norteUtmAtual == 0.0) {
                Toast.makeText(this, "Erro: Sem coordenada de GPS!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val novoPonto = PontoTopografico(nomeDoPonto, norteUtmAtual, lesteUtmAtual, cotaChaoAtual, zonaUtmAtual, statusRtkAtual, projetoAtual)
            listaDePontos.add(novoPonto)
            dbHelper.inserirPonto(novoPonto)
            mapaTopografico.listaDePontos = listaDePontos // Dispara o motor TIN
            
            Toast.makeText(this, "Ponto salvo na área $areaAtual", Toast.LENGTH_SHORT).show()

            val match = Regex("(\\d+)$").find(nomeDoPonto)
            if (match != null) {
                val numStr = match.value
                val nextNum = numStr.toInt() + 1
                val newName = nomeDoPonto.dropLast(numStr.length) + String.format(Locale.US, "%0${numStr.length}d", nextNum)
                etNomePonto.setText(newName)
            } else {
                etNomePonto.setText("${nomeDoPonto}1")
            }
            etNomePonto.setSelection(etNomePonto.text.length)
        }

        btnExportarCsv.setOnClickListener {
            if (listaDePontos.isEmpty()) {
                Toast.makeText(this, "A área $areaAtual está vazia!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "${projetoAtual}.csv")
            }
            exportarLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        acelerometro?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometro?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this) 
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            val azimuteEmRadianos = orientation[0]
            val azimuteEmGraus = Math.toDegrees(azimuteEmRadianos.toDouble()).toFloat()
            
            mapaTopografico.azimuteUsuario = azimuteEmGraus
            mapaTopografico.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checarPermissoesGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_GPS)
        } else {
            iniciarLeituraGpsCelular()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_GPS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarLeituraGpsCelular()
        } else {
            Toast.makeText(this, "Permissão de GPS negada. O mapa não irá atualizar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarLeituraGpsCelular() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
        } catch (ex: SecurityException) {
            Log.e("MainActivity", "Erro de segurança ao acessar GPS", ex)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            
            latAtual = location.latitude
            lonAtual = location.longitude
            cotaChaoAtual = location.altitude 

            val utmCoords = converterGrausParaUTM(latAtual, lonAtual)
            lesteUtmAtual = utmCoords[0]
            norteUtmAtual = utmCoords[1]

            val zonaUtmNumerica = ((lonAtual + 180) / 6).toInt() + 1
            val hemisferio = if (latAtual >= 0) "N" else "S"
            zonaUtmAtual = "${zonaUtmNumerica}${hemisferio}"

            statusRtkAtual = "GPS INTERNO"
            tvStatusRTK.text = "GPS INTERNO (±${location.accuracy.toInt()}m)"
            tvStatusRTK.setTextColor(Color.parseColor("#FFC107"))

            mapaTopografico.rtkNorte = norteUtmAtual
            mapaTopografico.rtkLeste = lesteUtmAtual
            mapaTopografico.invalidate()

            tvLatitude.text = "Lat: ${String.format(Locale.getDefault(), "%.6f", latAtual)}°"
            tvLongitude.text = "Lon: ${String.format(Locale.getDefault(), "%.6f", lonAtual)}°"
            tvNorteUTM.text = "N: ${String.format(Locale.getDefault(), "%.3f", norteUtmAtual)}"
            tvLesteUTM.text = "E: ${String.format(Locale.getDefault(), "%.3f", lesteUtmAtual)}"
            tvCota.text = String.format(Locale.getDefault(), "%.3f", cotaChaoAtual)

            if (mapaTopografico.modoLocacao && mapaTopografico.alvoLocacao != null) {
                val alvo = mapaTopografico.alvoLocacao!!
                val dist = hypot(alvo.lesteUtm - lesteUtmAtual, alvo.norteUtm - norteUtmAtual)
                
                // Cálculo de Greide na Locação com Bastão
                val alturaBastao = etAlturaBastao.text.toString().toDoubleOrNull() ?: 0.0
                val cotaProjeto = etCotaProjeto.text.toString().toDoubleOrNull() ?: alvo.cotaChao
                val cotaPontaBastao = cotaChaoAtual - alturaBastao
                val diferencaCota = cotaProjeto - cotaPontaBastao
                
                val txtCorteAterro = if (diferencaCota > 0) "ATERRO: ${String.format(Locale.getDefault(), "%.3f", diferencaCota)}m"
                                     else if (diferencaCota < 0) "CORTE: ${String.format(Locale.getDefault(), "%.3f", diferencaCota * -1)}m"
                                     else "NO GREIDE"
                                     
                tvResultadoCorteAterro.text = "ALVO: ${alvo.nome}\nDIST: ${String.format(Locale.getDefault(), "%.3f", dist)}m\n$txtCorteAterro"
                tvResultadoCorteAterro.setTextColor(Color.parseColor("#00FFFF"))
            } else {
                tvResultadoCorteAterro.text = "---"
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun mostrarDialogoDeProjeto() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val labelArea = TextView(this).apply { 
            text = "Área de Trabalho (Origem):"
            setTextColor(Color.LTGRAY) 
            textSize = 12f
        }
        val spinnerArea = Spinner(this)
        val areas = arrayOf("CAMPO", "XML", "CSV", "TXT")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, areas)
        spinnerArea.adapter = adapter
        spinnerArea.setSelection(areas.indexOf(areaAtual))

        val labelNome = TextView(this).apply { 
            text = "Nome do Projeto:"
            setTextColor(Color.LTGRAY) 
            setPadding(0, 30, 0, 0) 
            textSize = 12f
        }
        val inputProjeto = EditText(this).apply {
            hint = "Ex: Loteamento_A"
            setText(nomeObraAtual)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.DKGRAY)
        }

        layout.addView(labelArea)
        layout.addView(spinnerArea)
        layout.addView(labelNome)
        layout.addView(inputProjeto)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Gerenciador de Projetos")
            .setView(layout)
            .setPositiveButton("ABRIR / CRIAR") { _, _ ->
                val areaSel = spinnerArea.selectedItem.toString()
                val nomeDig = inputProjeto.text.toString().trim().replace(" ", "_")
                
                if (nomeDig.isNotEmpty()) {
                    areaAtual = areaSel
                    nomeObraAtual = nomeDig
                    projetoAtual = "[$areaAtual] $nomeObraAtual"
                    
                    btnCad.text = areaAtual
                    
                    listaDePontos.clear()
                    listaDePontos.addAll(dbHelper.buscarPontosPorProjeto(projetoAtual))
                    mapaTopografico.listaDePontos = listaDePontos
                    mapaTopografico.zoomParaProjeto()
                    
                    Toast.makeText(this, "Área ativa: $projetoAtual", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("LIMPAR ÁREA") { _, _ ->
                Toast.makeText(this, "Para excluir do banco, insira o código em DatabaseHelper.kt", Toast.LENGTH_LONG).show()
                listaDePontos.clear()
                mapaTopografico.listaDePontos = listaDePontos
            }
            .setCancelable(false)
            .show()
    }

    private fun processarArquivoImportado(uri: android.net.Uri) {
        val contentResolver = applicationContext.contentResolver
        val tipoMime = contentResolver.getType(uri) ?: ""
        val nomeArquivo = uri.path ?: ""

        Toast.makeText(this, "Lendo arquivo... aguarde.", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val extensao = nomeArquivo.substringAfterLast('.', "").uppercase()
                val novaArea = when {
                    extensao == "XML" || tipoMime.contains("xml") -> "XML"
                    extensao == "CSV" || tipoMime.contains("csv") -> "CSV"
                    extensao == "TXT" || tipoMime.contains("text") -> "TXT"
                    else -> areaAtual
                }

                areaAtual = novaArea
                projetoAtual = "[$areaAtual] $nomeObraAtual"

                val inputStream = contentResolver.openInputStream(uri)
                val reader = inputStream?.bufferedReader()
                var pontosImportados = 0

                reader?.useLines { linhas ->
                    if (areaAtual == "XML") {
                        var lendoPonto = false
                        var nomePontoXML = ""
                        
                        for (linha in linhas) {
                            val l = linha.trim()
                            if (l.startsWith("<CgPoint")) {
                                val matchNome = Regex("name=\"([^\"]+)\"").find(l)
                                nomePontoXML = matchNome?.groupValues?.get(1) ?: "PT_${pontosImportados}"
                                lendoPonto = true
                                
                                val matchCoords = Regex(">([^<]+)</CgPoint>").find(l)
                                if (matchCoords != null) {
                                    val coords = matchCoords.groupValues[1].trim().split(Regex("\\s+"))
                                    if (coords.size >= 3) {
                                        salvarPontoImportado(nomePontoXML, coords[0], coords[1], coords[2])
                                        pontosImportados++
                                    }
                                    lendoPonto = false
                                }
                            } else if (lendoPonto && !l.contains("<")) {
                                val coords = l.split(Regex("\\s+"))
                                if (coords.size >= 3) {
                                    salvarPontoImportado(nomePontoXML, coords[0], coords[1], coords[2])
                                    pontosImportados++
                                }
                                lendoPonto = false
                            }
                        }
                    } 
                    else {
                        for ((index, linha) in linhas.withIndex()) {
                            if (index == 0 && linha.contains(Regex("[A-Za-z]"))) continue 
                            
                            val colunas = linha.split(Regex("[,;\\t]"))
                            if (colunas.size >= 4) {
                                val nome = colunas[0].trim()
                                val norte = colunas[1].trim()
                                val leste = colunas[2].trim()
                                val cota = colunas[3].trim()
                                
                                salvarPontoImportado(nome, norte, leste, cota)
                                pontosImportados++
                            }
                        }
                    }
                }

                runOnUiThread {
                    btnCad.text = areaAtual
                    listaDePontos.clear()
                    listaDePontos.addAll(dbHelper.buscarPontosPorProjeto(projetoAtual))
                    mapaTopografico.listaDePontos = listaDePontos
                    mapaTopografico.zoomParaProjeto() // Agora a câmera VAI direto pros pontos importados!
                    
                    Toast.makeText(this, "$pontosImportados pontos salvos em $projetoAtual!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Erro ao importar: O formato está correto?", Toast.LENGTH_LONG).show()
                    Log.e("Importacao", "Falha fatal no parser", e)
                }
            }
        }.start() 
    }

    private fun salvarPontoImportado(nome: String, norteStr: String, lesteStr: String, cotaStr: String) {
        val norte = norteStr.toDoubleOrNull() ?: 0.0
        val leste = lesteStr.toDoubleOrNull() ?: 0.0
        val cota = cotaStr.toDoubleOrNull() ?: 0.0
        
        if (norte != 0.0 && leste != 0.0) {
            val novoPonto = PontoTopografico(nome, norte, leste, cota, "IMPORT", "IMPORTADO", projetoAtual)
            dbHelper.inserirPonto(novoPonto)
        }
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
