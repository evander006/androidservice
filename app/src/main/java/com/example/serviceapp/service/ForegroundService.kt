package com.example.serviceapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import com.example.serviceapp.CHANNEL_ID
import com.example.serviceapp.MainActivity
import com.example.serviceapp.MusicData
import com.example.serviceapp.R
import androidx.media.app.NotificationCompat.MediaStyle
import android.Manifest.permission.POST_NOTIFICATIONS
import android.media.MediaSession2
import android.net.Uri
import android.support.v4.media.session.MediaSessionCompat
import com.example.serviceapp.CHANNEL_NAME
import com.example.serviceapp.songsList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PREV="prev"
const val NEXT="next"
const val PLAY_PAUSE="play_pause"

class ForegroundService : Service() {
    private var musicList= mutableListOf<MusicData>()

    val musicBinder=MusicBinder()

    private var mediaPlayer: MediaPlayer? = null

    private val currTrack= MutableStateFlow<MusicData>(MusicData())
    private val maxDuration= MutableStateFlow(0f)
    private val currDuration= MutableStateFlow(0f)
    private val isPlaying = MutableStateFlow(false)

    private var job:Job?=null
    //inner class для чего
    inner class MusicBinder : Binder(){
        fun getService() = this@ForegroundService
        fun setMutableList(list: List<MusicData>){
            this@ForegroundService.musicList = list.toMutableList()
        }
        fun currTrack() = this@ForegroundService.currTrack
        fun maxDuration() = this@ForegroundService.maxDuration
        fun currDuration() = this@ForegroundService.currDuration
        fun isPlaying() = this@ForegroundService.isPlaying
    }

    override fun onBind(intent: Intent?): IBinder?{
        return musicBinder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaPlayer=MediaPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (songsList.isEmpty()) {
            Log.e("MusicService", "Songs list is empty")
            return START_NOT_STICKY
        }
        intent?.let {
            when(intent.action){
                PREV->{
                    prev()
                }
                NEXT->{
                    next()
                }
                PLAY_PAUSE->{
                    playPause()
                }
                else->{
                    currTrack.update { songsList[0] }
                    playTrack(currTrack.value)
                }
            }
        }

        return START_STICKY
    }

    fun seekTo(pos:Int){
        mediaPlayer?.seekTo(pos)
    }

    private fun prev(){
        if (musicList.isEmpty()) return
        job?.cancel()
        mediaPlayer?.reset()
        mediaPlayer=MediaPlayer()
        val index=musicList.indexOf(currTrack.value)
        val prevIndex = if (index > 0) index - 1 else musicList.lastIndex
        val prevItem=musicList.get(prevIndex)
        currTrack.update { prevItem }
        playTrack(prevItem)
    }

    private fun next(){
        if (musicList.isEmpty()) return
        job?.cancel()
        mediaPlayer?.reset()
        mediaPlayer=MediaPlayer()
        val index=musicList.indexOf(currTrack.value)
        val nextIndex = if (index < musicList.lastIndex) index + 1 else 0
        val nextItem=musicList.get(nextIndex)
        currTrack.update {nextItem}
        playTrack(nextItem)

    }

    private fun playPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
            showNotification(currTrack.value)
        } ?: Log.e("music", "MediaPlayer is null in playPause()")
    }

    private fun playTrack(track: MusicData){
        mediaPlayer?.reset()
        mediaPlayer=MediaPlayer()
        mediaPlayer?.setDataSource(this, convertToUri(track.songId))
        mediaPlayer?.prepareAsync()
        mediaPlayer?.setOnPreparedListener {
            mediaPlayer?.start()
            showNotification(track)
            updateDuration()
        }
    }

    private fun updateDuration(){
        //The Main dispatcher is used for executing coroutines on the main/UI thread in Android.
        //This is useful when you need to perform operations that update the UI or interact with UI components.
        job = CoroutineScope(Dispatchers.Main).launch {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) return@launch

                val duration = player.duration.toFloat()
                if (duration == 0f) {
                    Log.e("MusicService", "Invalid duration: 0")
                    return@launch
                }

                maxDuration.update { duration }
                while (true) {
                    currDuration.update { player.currentPosition.toFloat() }
                    delay(1000)
                }
            } ?: Log.e("music", "MediaPlayer is null in updateDuration()")
        }
    }

    private fun convertToUri(id: Int): Uri {
        return Uri.parse("android.resource://${packageName}/raw/${id}")
    }


    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME,NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="Music Player Contols"
            }
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

    }

    private fun showNotification(track:MusicData) {
        val session=MediaSessionCompat(this,"music")
        isPlaying.update { mediaPlayer?.isPlaying!! }
        val style=androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0,1,2)
            .setMediaSession(session.sessionToken)
        val intentPrev=Intent(this, ForegroundService::class.java).apply {
            action=PREV
        }
        val pendingIntentPrev = PendingIntent.getService(this,0,intentPrev, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val intentNext=Intent(this, ForegroundService::class.java).apply {
            action=NEXT
        }
        val pendingIntentNext = PendingIntent.getService(this,0,intentNext, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val intentPlayPause=Intent(this, ForegroundService::class.java).apply {
            action= PLAY_PAUSE
        }
        val pendingIntentPlayPause = PendingIntent.getService(this,0,intentPlayPause, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(style)
            .setContentTitle(track.name)
            .setContentText(track.author)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .addAction(R.drawable.prev,"prev",pendingIntentPrev)
            .addAction(R.drawable.next,"next",pendingIntentNext)
            .setLargeIcon(BitmapFactory.decodeResource(resources, track.coverage))
            .addAction(
                if (mediaPlayer?.isPlaying == true) {
                    R.drawable.pause
                }
                else {
                    R.drawable.play
                }, "play_pause",pendingIntentPlayPause
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ForegroundService", "No permission to post notifications!")
            return
        }
        startForeground(1, notification)

    }

}