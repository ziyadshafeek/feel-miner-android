package io.github.j3r0nim0.feelminer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.concurrent.thread

/**
 * Feelcoin-only foreground mining service.
 *
 * Runs the bundled official feelcoin-miner binary (renamed libfeelminer.so,
 * extracted to filesDir so it can be executed) with a JSON config:
 * coin=feelcoin, algo=rx/0, pool pool.feelcoin.org:4244 TLS (or 4242
 * plaintext if the user picks it), user = the user's own FEEL public
 * address, pass=x, donate-level=0, huge-pages=false.
 *
 * Safety, defaults ON: charging-only, CPU pause 65 C / resume 55 C,
 * battery pause 40 C / resume 37 C. Pause here is a clean stop and
 * restart, not a hitless pause — stated plainly, so no dataset is
 * claimed to survive a pause.
 */
class MiningService : Service() {
    companion object {
        const val EXTRA_WALLET = "wallet"
        const val EXTRA_WORKER = "worker"
        const val EXTRA_THREADS = "threads"
        const val EXTRA_TLS = "tls"
        const val EXTRA_CHARGING_ONLY = "charging_only"

        @Volatile var running = false
        @Volatile var pausedReason: String? = null
        @Volatile var hashrate60 = 0.0
        @Volatile var accepted = 0
        @Volatile var rejected = 0
        @Volatile var statusText = "stopped"
        @Volatile var logText = ""
    }

    private var process: Process? = null
    private var workerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shouldRun = false
    private var wallet = ""
    private var worker = "phone"
    private var threads = 2
    private var tls = true
    private var chargingOnly = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempTenths / 10.0
            if (chargingOnly && !plugged) {
                pause("Paused — not charging")
            } else if (tempC >= 40.0) {
                pause("Paused — battery $tempC C")
            } else if (tempC <= 37.0 && pausedReason?.startsWith("Paused — battery") == true) {
                resume()
            } else if (plugged && pausedReason == "Paused — not charging") {
                resume()
            }
            checkCpuTemp()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("feelminer", "Feel Miner", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopMining()
            stopSelf()
            return START_NOT_STICKY
        }
        wallet = intent?.getStringExtra(EXTRA_WALLET) ?: wallet
        worker = intent?.getStringExtra(EXTRA_WORKER) ?: worker
        threads = intent?.getIntExtra(EXTRA_THREADS, 2) ?: 2
        tls = intent?.getBooleanExtra(EXTRA_TLS, true) ?: true
        chargingOnly = intent?.getBooleanExtra(EXTRA_CHARGING_ONLY, true) ?: true
        startForeground(1, buildNotification("Starting Feelcoin miner…"))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FeelMiner::mining")
        wakeLock?.acquire()
        shouldRun = true
        running = true
        statusText = "running"
        startMiner()
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "feelminer")
            .setContentTitle("Feel Miner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun extractBinary(): File {
        // Kept only as a fallback name; the normal path runs the binary
        // directly from nativeLibraryDir, as the old Anvil miner did,
        // because filesDir is noexec on API 29+.
        return File(applicationInfo.nativeLibraryDir, "libfeelminer.so")
    }

    private fun writeConfig(): File {
        val port = if (tls) 4244 else 4242
        val config = """
        {
          "api": {"id": null, "worker-id": null},
          "http": {"enabled": false, "host": "127.0.0.1", "port": 0, "access-token": null, "restricted": true},
          "autosave": false,
          "background": false,
          "colors": false,
          "randomx": {"init": -1, "mode": "auto", "1gb-pages": false, "rdmsr": false, "wrmsr": false, "numa": false},
          "cpu": {"enabled": true, "huge-pages": false, "huge-pages-jit": false, "hw-aes": null, "priority": null, "asm": true, "max-threads-hint": 100},
          "opencl": {"enabled": false},
          "cuda": {"enabled": false},
          "donate-level": 0,
          "donate-over-proxy": 0,
          "pools": [{"algo": "rx/0", "coin": "feelcoin", "url": "pool.feelcoin.org:$port", "user": "$wallet", "pass": "x", "rig-id": null, "nicehash": false, "keepalive": false, "enabled": true, "tls": $tls, "tls-fingerprint": null, "daemon": false}],
          "print-time": 60,
          "health-print-time": 60,
          "retries": 5,
          "retry-pause": 5,
          "syslog": false,
          "verbose": 0,
          "watch": true,
          "pause-on-battery": false,
          "pause-on-active": false
        }
        """.trimIndent()
        val f = File(filesDir, "feelminer-config.json")
        f.writeText(config)
        return f
    }

    private fun startMiner() {
        workerThread?.interrupt()
        workerThread = thread(start = true) {
            try {
                // The CI job places the binary in jniLibs as libfeelminer.so.
                // At runtime, Android extracts jniLibs to nativeLibraryDir,
                // which is executable — run it from there, do not copy it
                // to filesDir (noexec on API 29+).
                val binary = File(applicationInfo.nativeLibraryDir, "libfeelminer.so")
                check(binary.exists()) { "Feelcoin miner binary not found at ${binary.absolutePath}." }
                val config = writeConfig()
                val pb = ProcessBuilder(binary.absolutePath, "--config=${config.absolutePath}", "--no-color", "--threads=$threads")
                pb.redirectErrorStream(true)
                pb.directory(filesDir)
                val p = pb.start()
                process = p
                appendLog("Started feelcoin-miner, threads=$threads, pool pool.feelcoin.org:${if (tls) 4244 else 4242} ${if (tls) "TLS" else "plaintext"}")
                val reader = p.inputStream.bufferedReader()
                var line: String?
                while (shouldRun && reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    appendLog(l)
                    parseLine(l)
                    if (pausedReason != null) break
                }
                try { p.destroy() } catch (_: Exception) {}
                process = null
            } catch (e: Exception) {
                Log.e("FeelMiner", "miner failed", e)
                appendLog("Miner error: ${e.message}")
                statusText = "error: ${e.message}"
            }
        }
    }

    private fun parseLine(line: String) {
        // XMRig speed line: "speed 10s/60s/15m  123.4 120.1 118.9 H/s"
        val speed = Regex("""10s/60s/15m[:\s]+([\d.]+|n/a)\s+([\d.]+|n/a)\s+([\d.]+|n/a)\s+[KMG]?H/s""", RegexOption.IGNORE_CASE).find(line)
        if (speed != null) {
            hashrate60 = speed.groupValues[1].toDoubleOrNull() ?: hashrate60
        }
        if (line.contains("accepted", ignoreCase = true)) {
            val m = Regex("""accepted\s*\((\d+)/(\d+)\)""").find(line)
            if (m != null) {
                accepted = m.groupValues[1].toIntOrNull() ?: accepted
                rejected = m.groupValues[2].toIntOrNull() ?: rejected
            }
        }
        if (line.contains("new job", ignoreCase = true)) {
            statusText = "connected — new job received"
        }
    }

    private fun appendLog(line: String) {
        val lines = (logText + "\n" + line).lines().takeLast(50)
        logText = lines.joinToString("\n")
    }

    private fun checkCpuTemp() {
        try {
            var maxC = 0.0
            val dir = File("/sys/class/thermal")
            dir.listFiles()?.forEach { zone ->
                try {
                    val t = File(zone, "temp").readText().trim().toDoubleOrNull()
                    if (t != null) {
                        val c = if (t > 1000) t / 1000.0 else t
                        if (c > maxC) maxC = c
                    }
                } catch (_: Exception) {}
            }
            if (maxC >= 65.0) pause("Paused — CPU $maxC C")
            else if (maxC <= 55.0 && pausedReason?.startsWith("Paused — CPU") == true) resume()
        } catch (_: Exception) {}
    }

    private fun pause(reason: String) {
        if (pausedReason != null) return
        pausedReason = reason
        statusText = reason
        appendLog(reason)
        try { process?.destroy() } catch (_: Exception) {}
        process = null
    }

    private fun resume() {
        if (pausedReason == null) return
        pausedReason = null
        statusText = "running"
        appendLog("Resumed mining")
        startMiner()
    }

    private fun stopMining() {
        shouldRun = false
        running = false
        pausedReason = null
        statusText = "stopped"
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        workerThread?.interrupt()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        stopMining()
        super.onDestroy()
    }
}
