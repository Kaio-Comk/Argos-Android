package com.twinfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)

        scanButton.setOnClickListener {
            checkPermissionsAndScan()
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            statusText.text = "Permissão negada. Não é possível escanear."
        }
    }

    private fun startScan() {
        scanButton.isEnabled = false
        statusText.text = "Buscando fotos..."

        thread {
            val scanner = Scanner(this)
            val items = scanner.scanImages()
            
            runOnUiThread {
                statusText.text = "Encontradas \ fotos. Calculando hashes..."
            }

            // Agrupa por tamanho primeiro (otimização)
            val bySize = items.groupBy { it.size }.filter { it.value.size > 1 }
            
            val duplicates = mutableListOf<List<MediaItem>>()
            var processed = 0
            val totalToHash = bySize.values.sumOf { it.size }

            for ((_, group) in bySize) {
                val byHash = mutableMapOf<String, MutableList<MediaItem>>()
                for (item in group) {
                    val hash = scanner.calculateHash(item.uri)
                    if (hash != null) {
                        byHash.getOrPut(hash) { mutableListOf() }.add(item)
                    }
                    processed++
                    
                    runOnUiThread {
                        statusText.text = "Calculando hashes... \/\"
                    }
                }
                
                byHash.values.filter { it.size > 1 }.forEach {
                    duplicates.add(it)
                }
            }

            val duplicateGroupsCount = duplicates.size
            val extraFilesCount = duplicates.sumOf { it.size - 1 }
            val spaceSaved = duplicates.sumOf { group -> (group.size - 1) * group[0].size }
            val spaceMb = spaceSaved / (1024 * 1024)

            runOnUiThread {
                statusText.text = "Busca concluída!\n\ grupos de cópias.\n\ arquivos duplicados.\nEconomia possível: \ MB"
                scanButton.isEnabled = true
            }
        }
    }
}
