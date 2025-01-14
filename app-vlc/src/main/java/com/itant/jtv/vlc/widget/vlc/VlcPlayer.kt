//package com.itant.jtv.vlc.widget.vlc
//
//import android.content.Context
//import android.net.Uri
//import android.os.Handler
//import android.os.Looper
//import android.os.Message
//import android.text.TextUtils
//import android.view.SurfaceView
//import android.view.TextureView
//import android.view.View
//import com.itant.jtv.manager.ThreadManager
//import com.itant.jtv.storage.kv.KeyValue
//import org.videolan.libvlc.LibVLC
//import org.videolan.libvlc.Media
//import org.videolan.libvlc.MediaPlayer
//import org.videolan.libvlc.interfaces.IVLCVout.OnNewVideoLayoutListener
//
//class VlcPlayer {
//    private var libVLC: LibVLC? = null
//    var mediaPlayer: MediaPlayer? = null
//
//    private var playUrlBeforeReady = ""
//
//    private val layoutChangeListener = View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
//        val newWidth = right - left
//        val newHeight = bottom - top
//        // 设置VLC播放器的宽高参数
//        mediaPlayer?.vlcVout?.setWindowSize(newWidth, newHeight)
//    }
//
//    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            executePlay(playUrlBeforeReady)
//        }
//    }
//
//    fun executeInitPlayer(
//        context: Context,
//        videoView: IView,
//        hardCodec: Boolean,
//        eventListener: MediaPlayer.EventListener,
//        videoLayoutListener: OnNewVideoLayoutListener
//    ) {
//        (videoView as? View)?.addOnLayoutChangeListener(layoutChangeListener)
//        val options = ArrayList<String>()
//        // 防止掉帧
//        //options.add("--no-drop-late-frames")
//        // 防止掉帧
//        //options.add("--no-skip-frames")
//        // 强制rtsp-tcp，加快加载视频速度
//        options.add("--rtsp-tcp")
//        // 尝试使用硬件加速
//        if (hardCodec) {
//            options.add("--avcodec-hw=any")
//        }
//        options.add("--ipv4-timeout=15000")
//        // 缓冲时长
//        options.add("--live-caching=20000")
//        // 加了可以在onNewVideoLayout获取视频宽高，但无法通过postInvalidate动态拉伸了，需要recreate
//        options.add("--vout=android-display")
//
//        ThreadManager.actionPool.submit {
//            libVLC = LibVLC(context, options)
//            mediaPlayer = MediaPlayer(libVLC)
//            mediaPlayer?.setEventListener(eventListener)
//            handler.post {
//                mediaPlayer?.vlcVout?.run {
//                    if (videoView is TextureView) {
//                        //setVideoSurface(videoView.surfaceTexture)
//                        setVideoView(videoView)
//                        setWindowSize(videoView.width, videoView.height)
//                    } else if (videoView is SurfaceView) {
//                        //setVideoSurface(videoView.holder.surface, videoView.holder)
//                        setVideoView(videoView)
//                        setWindowSize(videoView.width, videoView.height)
//                    }
//                    attachViews(videoLayoutListener)
//                }
//                handler.sendEmptyMessage(0)
//            }
//        }
//    }
//
//    fun executeStop() {
//        ThreadManager.actionPool.submit {
//            mediaPlayer?.stop()
//        }
//    }
//
//    fun executePlay(url: String) {
//        if (mediaPlayer == null) {
//            playUrlBeforeReady = url
//            return
//        } else {
//            playUrlBeforeReady = ""
//        }
//        if (TextUtils.isEmpty(url)) {
//            return
//        }
//        ThreadManager.actionPool.submit {
//            try {
//                val media = Media(libVLC, Uri.parse(url))
//                // 硬解码
//                val hardCodec = KeyValue.hardCodec
//                media.setHWDecoderEnabled(hardCodec, hardCodec)
//                if (hardCodec) {
//                    media.addOption(":no-mediacodec-dr")
//                    media.addOption(":no-omxil-dr")
//                }
//                mediaPlayer?.media = media
//                mediaPlayer?.play()
//                media.release()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//    fun executeDestroy() {
//        val player = mediaPlayer
//        val vlc = libVLC
//        mediaPlayer = null
//        libVLC = null
//        player?.vlcVout?.detachViews()
//        player?.setEventListener(null)
//        player?.media?.release()
//        //player?.detachViews()
//        ThreadManager.actionPool.submit {
//            player?.stop()
//            player?.release()
//            vlc?.release()
//        }
//    }
//}