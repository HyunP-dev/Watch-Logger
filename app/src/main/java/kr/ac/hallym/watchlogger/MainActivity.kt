package kr.ac.hallym.watchlogger

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import it.sauronsoftware.ftp4j.FTPClient
import it.sauronsoftware.ftp4j.FTPDataTransferListener
import kr.ac.hallym.watchlogger.databinding.ActivityMainBinding
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random


// TODO: UI 개선
class MainActivity :
    FragmentActivity(),
    AmbientModeSupport.AmbientCallbackProvider,
    SensorEventListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private lateinit var sensorManager: SensorManager
    private lateinit var heartrateSensor: Sensor
    private lateinit var accSensor: Sensor
    private lateinit var gyroSensor: Sensor

    private lateinit var file: File

    var accValues: FloatArray = FloatArray(3)
    var gyroValues: FloatArray = FloatArray(3)

    var bpm: Float = 0f

    private lateinit var saveThread: Thread

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissions = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
        if (permissions
                .map {
                    ContextCompat.checkSelfPermission(
                        this,
                        it
                    ) == PackageManager.PERMISSION_DENIED
                }
                .reduce { acc: Boolean, b: Boolean -> acc || b }
        ) {
            requestPermissions(permissions, 1001)
        }

        val idFile = File(filesDir.path, "id.txt")
        var idCode = ""
        if (idFile.exists()) {
            idCode = idFile.readText()
        } else {
            idCode = Random.nextInt().toString()
            idFile.appendText(idCode)
        }
        binding.idView.text = "id: $idCode"

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartrateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) as Sensor
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) as Sensor
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) as Sensor

        arrayOf(heartrateSensor, accSensor, gyroSensor).forEach {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }


        ambientController = AmbientModeSupport.attach(this)

        binding.savebtn.setOnCheckedChangeListener { _, state ->
            if (state) {
                saveThread = Thread {
                    val logTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    file = File(
                        filesDir.path,
                         "${idCode}_${logTime}.csv"
                    )
                    Log.d("save", file.toString())
                    file.appendText("id,time,bpm,ax,ay,az,gx,gy,gz\n")

                    val hz = 20
                    while (true) {
                        try {
                            val acc = accValues.joinToString(",")
                            val gyro = gyroValues.joinToString(",")
                            val line = "$idCode,${System.currentTimeMillis()},$bpm,$acc,$gyro\n"
                            Log.d("save", line)
                            file.appendText(line)

                            Thread.sleep((1000 / hz).toLong())
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
                saveThread.start()
            } else {
                saveThread.interrupt()
                Thread {
                    val ftpClient = FTPClient()
                    Thread {
                        ftpClient.connect("senunas.ipdisk.co.kr", 2348)
                        ftpClient.login("pakhyun", "parkhyun")
                        ftpClient.type = FTPClient.TYPE_BINARY
                        ftpClient.changeDirectory("/HDD1/pak_hyun/")
                    }.start()
                    ftpClient.upload(file, object : FTPDataTransferListener {
                        override fun started() {}

                        override fun transferred(length: Int) {}

                        override fun completed() {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Send completed", Toast.LENGTH_SHORT)
                                    .show()
                                binding.sendbtn.isEnabled = false
                            }

                        }

                        override fun aborted() {}

                        override fun failed() {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Send failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    })
                }.start()

//                binding.sendbtn.isEnabled = true
            }
        }

//        binding.sendbtn.isEnabled = false
//        binding.sendbtn.setOnClickListener {
//
//        }
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

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                bpm = event.values[0]
                Log.d("BPM", bpm.toString())
                runOnUiThread {
                    binding.bpmView.text = "${bpm}bpm"
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(accValues)
            }
            Sensor.TYPE_GYROSCOPE -> {
                event.values.copyInto(gyroValues)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}