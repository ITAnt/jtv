package com.itant.jtv.vlc.widget.vlc

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.getSystemService
import com.blankj.utilcode.util.RegexUtils
import com.itant.jtv.manager.ThreadManager
import com.itant.jtv.storage.kv.KeyValue
import com.miekir.mvvm.context.GlobalContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.interfaces.IVLCVout.OnNewVideoLayoutListener
import org.videolan.libvlc.util.VLCVideoLayout

class VlcVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : VLCVideoLayout(context, attrs, defStyleAttr, defStyleRes), IView, MediaPlayer.EventListener, OnNewVideoLayoutListener {
    companion object {
        private const val TAG = "VLCPlayer"
        const val USE_TEXTURE_VIEW: Boolean = false
        const val ENABLE_SUBTITLES: Boolean = false
    }

    private val options = ArrayList<String>().apply {
        // 选择任意可用硬件解码器
        /*if (KeyValue.hardCodec) {
            add("--avcodec-hw=any")
        }*/
        // 强制rtsp-tcp，加快加载视频速度
        if (KeyValue.cacheEnable) {
            add("--rtsp-tcp")
            add("--network-caching=15_000")
            // 缓冲时长
            add("--live-caching=15_000")
        }

        //add("--ipv4-timeout=15000")
        // 加了可以在onNewVideoLayout获取视频宽高
        //add("--vout=android-display")
        //add("--verbose=2") // 日志级别
        //add("--vvv") // 日志级别，加了这个会非常卡UI
        //add("--codec=mediacodec_ndk")
        //add("--vout=gles2,none")
        //new LibVLC("--codec=mediacodec_ndk")

        // 以下这堆参数解决了当贝无法识别硬解视频播放的问题，那么，ijk是否也可以用这种方式解决？
        val audioManager = context.getSystemService<AudioManager>()!!
        val audioTrackSessionId = audioManager.generateAudioSessionId()
        // 对于 opensles 音频输出，尝试
        add("--aout=opensles")
        add("--audio-time-stretch")
        add("--vout=android_display,none")
        add("--audio-resampler")
        add("soxr")
        add("--audiotrack-session-id=${audioTrackSessionId}")
        add("--sout-chromecast-audio-passthrough")
        add("--no-sout-chromecast-audio-passthrough")
        add("--sout-keep")
    }
    private var mLibVLC = LibVLC(GlobalContext.getContext(), options)
    var mediaPlayer = MediaPlayer(mLibVLC)

    init {
        mediaPlayer.setEventListener(this)
    }

    private val voutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout?) {
            play(lastPlayUrl)
        }

        override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {}
    }

    private var lastPlayUrl: String? = null
    override fun play(url: String?) {
        if (!RegexUtils.isURL(url) && !RegexUtils.isURL(lastPlayUrl)) {
            return
        }
        lastPlayUrl = if (!RegexUtils.isURL(url)) lastPlayUrl else url

        mediaPlayer.vlcVout.addCallback(voutCallback)

        if (mediaPlayer.vlcVout.areViewsAttached()) {
            ThreadManager.actionPool.submit {
                try {
                    val media = Media(mLibVLC, Uri.parse(url))
                    // 硬解码
                    val hardCodec = KeyValue.hardCodec
                    media.setHWDecoderEnabled(hardCodec, hardCodec)
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                } catch (e: Exception) {
                    Log.e(TAG, e.message ?: "unknown exception")
                    onStop()
                    recycle()
                    mLibVLC = LibVLC(GlobalContext.getContext(), options)
                    mediaPlayer = MediaPlayer(mLibVLC)
                }
            }
        } else {
            mediaPlayer.attachViews(this@VlcVideoView, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
        }
    }

    fun onStart() {
        play()
    }

    override fun onStop() {
        val playerCopy = mediaPlayer
        ThreadManager.actionPool.submit {
            playerCopy.pause()
        }
        mediaPlayer.vlcVout.detachViews()
        mediaPlayer.vlcVout.removeCallback(voutCallback)
        mediaPlayer.detachViews()
    }

    override fun onDestroy() {
        mediaPlayer.setEventListener(null)
        playerEventCallback = null
        recycle()
    }

    private fun recycle() {
        val playerCopy = mediaPlayer
        ThreadManager.actionPool.submit {
            playerCopy.release()
        }
        val vlcCopy = mLibVLC
        ThreadManager.actionPool.submit {
            vlcCopy.release()
        }
    }

    var playerEventCallback: VlcPlayerCallback? = null
    override fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Buffering -> {
                // 处理缓冲事件
                Log.e(TAG, "Buffering...")
                playerEventCallback?.onBuffering(event.buffering)
            }

            MediaPlayer.Event.EndReached -> {
                Log.e(TAG, "EndReached...")
                // 处理播放结束事件
                playerEventCallback?.onEndReached()
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError...")
                // 处理播放错误事件
                playerEventCallback?.onError()
            }

            MediaPlayer.Event.TimeChanged -> {
                Log.e(TAG, "TimeChanged...")
                // 处理播放进度变化事件
                playerEventCallback?.onTimeChanged(event.timeChanged)
            }

            MediaPlayer.Event.PositionChanged -> {
                // 处理播放位置变化事件
                playerEventCallback?.onPositionChanged(event.positionChanged)
            }

            MediaPlayer.Event.Vout -> {
                // 在视频开始播放之前，视频的宽度和高度可能还没有被确定，因此我们需要在MediaPlayer.Event.Vout事件发生后才能获取到正确的宽度和高度
                //(vlcPlayer.mediaPlayer?.getSelectedTrack(IMedia.Track.Type.Video) as? IMedia.VideoTrack?)?.let {
                mediaPlayer.currentVideoTrack?.let {
                    Log.e(TAG, "currentVideoTrack width:${it.width} height:${it.height}")
                    playerEventCallback?.onVideoSizeChanged(it.width, it.height)
                }
            }
        }
    }

    override fun onNewVideoLayout(vlcVout: IVLCVout?, width: Int, height: Int, visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int) {
        Log.e(TAG, "onNewVideoLayout width:${width} height:${height}")
        playerEventCallback?.onVideoSizeChanged(width, height)
    }
}
