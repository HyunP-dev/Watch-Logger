package kr.ac.hallym.watchlogger

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import kr.ac.hallym.watchlogger.databinding.ActivityMainBinding
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity :
    FragmentActivity(),
    AmbientModeSupport.AmbientCallbackProvider,
    SensorEventListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private lateinit var sensorManager: SensorManager
    private lateinit var heartrateSensor: Sensor
    var bpm: Float = 0f

    private lateinit var saveThread: Thread
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissions = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions
                .map { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }
                .reduce { acc: Boolean, b: Boolean -> acc || b }) {
            requestPermissions(permissions, 1001)
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartrateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) as Sensor
        sensorManager.registerListener(this, heartrateSensor, SensorManager.SENSOR_DELAY_UI)

        ambientController = AmbientModeSupport.attach(this)

        binding.savebtn.setOnCheckedChangeListener { _, state ->
            Log.d("savebtn", if (state) "true" else "false")
            if (state) {
                saveThread = Thread {
                    val file = File(filesDir.path, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)+".csv")
                    Log.d("save", file.toString())
                    file.appendText("time,bpm\n")

                    val hz = 30
                    while (true) {
                        try {
                            val line = "${System.currentTimeMillis()},$bpm\n"
                            Log.d("save", line)
                            file.appendText(line)

                            Thread.sleep((1000/hz).toLong())
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
                saveThread.start()
            } else {
                saveThread.interrupt()
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = MyAmbientCallback()

    private class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {

        override fun onEnterAmbient(ambientDetails: Bundle?) {
            Log.d("BPM", "onEnterAmbient")
        }

        override fun onExitAmbient() {
            Log.d("BPM", "onExitAmbient")
        }

        override fun onUpdateAmbient() {

        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            bpm = event.values[0]
            Log.d("BPM", bpm.toString())
            runOnUiThread {
                binding.bpmView.text = bpm.toString()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) { }
}