package com.example.innerprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var printer: BluetoothDevice? = null
    private var bitmap: Bitmap? = null

    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var rb58: RadioButton

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        contentResolver.openInputStream(uri)?.use { bitmap = BitmapFactory.decodeStream(it) }
        preview.setImageBitmap(bitmap)
        status.text = "تم اختيار الصورة"
    }

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        findPrinter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply { text = "اضغط (بحث عن الطابعة)"; textSize = 16f }
        val btnFind = Button(this).apply { text = "بحث عن الطابعة الداخلية"; setOnClickListener { checkPermsThenFind() } }
        val btnPick = Button(this).apply { text = "اختيار صورة الإيصال"; setOnClickListener { pickImage.launch("image/*") } }
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER }
        rb58 = RadioButton(this).apply { text = "58mm"; isChecked = true; id = 1 }
        val rb80 = RadioButton(this).apply { text = "80mm"; id = 2 }
        group.addView(rb58); group.addView(rb80)
        preview = ImageView(this).apply { adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val btnPrint = Button(this).apply { text = "طباعة"; setOnClickListener { print() } }
        listOf(status, btnFind, btnPick, group, preview, btnPrint).forEach { root.addView(it) }
        setContentView(root)
        checkPermsThenFind()
    }

    private fun checkPermsThenFind() {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) { askPerms.launch(need.toTypedArray()); return }
        }
        findPrinter()
    }

    /** يبحث في الأجهزة المقترنة عن الطابعة الداخلية (الاسم عادةً InnerPrinter أو العنوان 00:11:22:33:44:55) */
    private fun findPrinter() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { status.text = "فعّل البلوتوث أولاً"; return }
        val bonded = adapter.bondedDevices.toList()
        printer = bonded.firstOrNull {
            val n = (it.name ?: "").lowercase()
            n.contains("inner") || n.contains("printer") || it.address == "00:11:22:33:44:55"
        }
        if (printer != null) {
            status.text = "تم العثور على الطابعة: ${printer!!.name} (${printer!!.address})"
        } else if (bonded.isEmpty()) {
            status.text = "لا توجد أجهزة مقترنة"
        } else {
            val names = bonded.map { "${it.name} - ${it.address}" }.toTypedArray()
            android.app.AlertDialog.Builder(this).setTitle("اختر الطابعة")
                .setItems(names) { _, i -> printer = bonded[i]; status.text = "الطابعة: ${names[i]}" }
                .show()
        }
    }

    private fun connect(dev: BluetoothDevice): BluetoothSocket {
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
        return try {
            dev.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
        } catch (e: Exception) {
            dev.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
        }
    }

    private fun print() {
        val dev = printer ?: run { status.text = "لم يتم تحديد الطابعة"; return }
        val bmp = bitmap ?: run { status.text = "اختر صورة أولاً"; return }
        val width = if (rb58.isChecked) 384 else 576
        status.text = "جاري الطباعة..."
        Thread {
            try {
                val socket = connect(dev)
                socket.use { s ->
                    val os = s.outputStream
                    os.write(EscPos.INIT)
                    for (chunk in EscPos.rasterChunks(bmp, width)) {
                        os.write(chunk); os.flush(); Thread.sleep(60)
                    }
                    os.write(EscPos.feed(4)); os.flush()
                    Thread.sleep(500)
                }
                runOnUiThread { status.text = "تمت الطباعة ✔" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "فشل: ${e.message}" }
            }
        }.start()
    }
}
