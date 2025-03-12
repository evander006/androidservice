package com.example.serviceapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors

class BackService: Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Log.e("create", "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("create", "onstart command")
        //запуск в фоновом потоке
        Executors.newSingleThreadExecutor().execute {
            for (i in 0..100){
                println("Progress:$i %")
            }
            stopSelf()
        }

        return START_STICKY
    }

    //после завершения вызываем onDestroy
    override fun onDestroy() {
        super.onDestroy()
        Log.e("create", "onDestroy")
    }
}