package io.github.j3r0nim0.feelminer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Feelcoin only, first screen. No Monero steps, no Monero address field,
 * no other coin, no solo daemon. If the address does not validate as a
 * Feelcoin address (prefixes 84/85/86, Base58 + Keccak checksum), mining
 * does not start.
 */
class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("feelminer", MODE_PRIVATE) }
    private val ui = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            statusText.text = "Status: ${MiningService.statusText}"
            hashrateText.text = "Hashrate (60s): ${MiningService.hashrate60} H/s"
            sharesText.text = "Accepted: ${MiningService.accepted}  Rejected: ${MiningService.rejected}"
            logText.text = MiningService.logText.ifEmpty { "Log will appear here." }
            startButton.text = if (MiningService.running) "Mining…" else "Start mining"
            ui.postDelayed(this, 2000)
        }
    }

    private lateinit var walletEdit: EditText
    private lateinit var walletError: TextView
    private lateinit var workerEdit: EditText
    private lateinit var threadsSpinner: Spinner
    private lateinit var poolSpinner: Spinner
    private lateinit var chargingOnlySwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var hashrateText: TextView
    private lateinit var sharesText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        walletEdit = findViewById(R.id.walletEdit)
        walletError = findViewById(R.id.walletError)
        workerEdit = findViewById(R.id.workerEdit)
        threadsSpinner = findViewById(R.id.threadsSpinner)
        poolSpinner = findViewById(R.id.poolSpinner)
        chargingOnlySwitch = findViewById(R.id.chargingOnlySwitch)
        saveButton = findViewById(R.id.saveButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        hashrateText = findViewById(R.id.hashrateText)
        sharesText = findViewById(R.id.sharesText)
        logText = findViewById(R.id.logText)

        threadsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("1", "2", "4", "8"))
        poolSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("pool.feelcoin.org:4244 TLS (official default)", "pool.feelcoin.org:4242 plaintext (alternative)")
        )

        walletEdit.setText(prefs.getString("wallet", "") ?: "")
        workerEdit.setText(prefs.getString("worker", "phone") ?: "phone")
        threadsSpinner.setSelection(listOf("1", "2", "4", "8").indexOf(prefs.getString("threads", "2") ?: "2").coerceAtLeast(0))
        poolSpinner.setSelection(if (prefs.getBoolean("tls", true)) 0 else 1)
        chargingOnlySwitch.isChecked = prefs.getBoolean("charging_only", true)

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        saveButton.setOnClickListener {
            val wallet = walletEdit.text.toString().trim()
            if (!FeelWallet.isValid(wallet)) {
                walletError.text = "This does not look like a Feelcoin public address. Copy only the Receive address from your Feelcoin wallet. Do not paste seed words, a private key, a password, or a Monero address."
                return@setOnClickListener
            }
            walletError.text = ""
            prefs.edit()
                .putString("wallet", wallet)
                .putString("worker", workerEdit.text.toString().trim().ifEmpty { "phone" })
                .putString("threads", threadsSpinner.selectedItem.toString())
                .putBoolean("tls", poolSpinner.selectedItemPosition == 0)
                .putBoolean("charging_only", chargingOnlySwitch.isChecked)
                .apply()
            Toast.makeText(this, "Saved: ${FeelWallet.masked(wallet)}", Toast.LENGTH_SHORT).show()
        }

        startButton.setOnClickListener {
            val wallet = prefs.getString("wallet", "") ?: ""
            if (!FeelWallet.isValid(wallet)) {
                walletError.text = "Save a valid Feelcoin address first. See the error above."
                return@setOnClickListener
            }
            val intent = Intent(this, MiningService::class.java).apply {
                putExtra(MiningService.EXTRA_WALLET, wallet)
                putExtra(MiningService.EXTRA_WORKER, prefs.getString("worker", "phone") ?: "phone")
                putExtra(MiningService.EXTRA_THREADS, (prefs.getString("threads", "2") ?: "2").toIntOrNull() ?: 2)
                putExtra(MiningService.EXTRA_TLS, prefs.getBoolean("tls", true))
                putExtra(MiningService.EXTRA_CHARGING_ONLY, prefs.getBoolean("charging_only", true))
            }
            ContextCompat.startForegroundService(this, intent)
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, MiningService::class.java).apply { action = "STOP" }
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(poll)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(poll)
    }
}
