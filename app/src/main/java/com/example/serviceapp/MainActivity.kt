package com.example.serviceapp

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.serviceapp.databinding.ActivityMainBinding
import com.example.serviceapp.service.BackService
import com.example.serviceapp.service.ForegroundService
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.example.serviceapp.service.NEXT
import com.example.serviceapp.service.PLAY_PAUSE
import com.example.serviceapp.service.PREV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.sql.Connection

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var intent:Intent
    private var currTrack= MutableStateFlow<MusicData>(MusicData())
    private var maxDuration= MutableStateFlow(0f)
    private var currDuration= MutableStateFlow(0f)
    private var isPlaying = MutableStateFlow(false)
    private lateinit var service:ForegroundService
    private var isBound=false
    val connection = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as ForegroundService.MusicBinder).getService()
            service.musicBinder.setMutableList(songsList)
            startService(intent)
            lifecycleScope.launch {
                service.musicBinder.maxDuration().collectLatest {
                    maxDuration.value = it
                    binding.seekBar.max = it.toInt()
                }
            }
            lifecycleScope.launch {
                service.musicBinder.currDuration().collectLatest {
                    currDuration.value = it
                    binding.seekBar.progress = it.toInt()
                }
            }
            lifecycleScope.launch {
                service.musicBinder.currTrack().collectLatest {
                    currTrack.value = it
                    updateUI(it)
                }
            }
            lifecycleScope.launch {
                service.musicBinder.isPlaying().collectLatest {
                    isPlaying.value = it
                    updatePlayPauseBtn(it)
                }
            }
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound=false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //начиная с версии андроид TIRAMISU нужно запрашивать разрешение на показ уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(POST_NOTIFICATIONS), 101)
            }
        }
        intent=Intent(this, ForegroundService::class.java)
        setUpListeners()


    }

    override fun onStart() {
        super.onStart()

        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        isBound=false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(intent)
        unbindService(connection)
        isBound=false
    }

    private fun setUpListeners(){
        binding.btnPlayPause.setOnClickListener {
            if (foregroundServiceRunning(ForegroundService::class.java)){
                intent.apply {
                    action= PLAY_PAUSE
                }
                startService(intent)
            }else{
                Toast.makeText(this, "Service not running", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnNext.setOnClickListener {
            if (foregroundServiceRunning(ForegroundService::class.java)) {
                intent.apply {
                    action = NEXT
                }
                startService(intent)
            }else{
                Toast.makeText(this, "Service not running", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (foregroundServiceRunning(ForegroundService::class.java)) {
                intent.apply {
                    action = PREV
                }
                startService(intent)
            }else{
                Toast.makeText(this, "Service not running", Toast.LENGTH_SHORT).show()
            }
        }
        binding.seekBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser){
                    service.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }

    private fun updateUI(track:MusicData){
        binding.trackTitle.text=track.name
        binding.trackAuthor.text=track.author
        binding.imageView.setImageResource(track.coverage)

    }
    private fun updatePlayPauseBtn(isPlaying:Boolean){
        var icon = if (isPlaying) R.drawable.pause else R.drawable.play
        binding.btnPlayPause.setImageResource(icon)
    }

    private fun foregroundServiceRunning(sClass:Class<ForegroundService>):Boolean{
        //ActivityManager - класс для отслеживания информации об Activity, с чем взаимодейтсвует, серсисы и тд
        val manager:ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        //проходимся по всем запущеным сервисам по циклу и если нашли ForegroundService, возвращаем true (запущен) иначе false
        for (service:ActivityManager.RunningServiceInfo in manager.getRunningServices(Integer.MAX_VALUE)){
            if (sClass.name.equals(service.service.className)){
                return true
            }
        }
        return false
    }

}