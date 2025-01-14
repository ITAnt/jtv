//package com.itant.jtv.vlc.widget.vlc
//
//import android.content.Context
//import android.net.Uri
//import android.util.AttributeSet
//import android.util.Log
//import com.blankj.utilcode.util.RegexUtils
//import com.blankj.utilcode.util.ScreenUtils
//import com.itant.jtv.manager.ThreadManager
//import com.itant.jtv.storage.kv.KeyValue
//import com.itant.jtv.vlc.widget.vlc.VlcManager.mLibVLC
//import com.itant.jtv.vlc.widget.vlc.VlcManager.mediaPlayer
//import org.videolan.libvlc.Media
//import org.videolan.libvlc.MediaPlayer
//import org.videolan.libvlc.interfaces.IVLCVout
//import org.videolan.libvlc.interfaces.IVLCVout.OnNewVideoLayoutListener
//import org.videolan.libvlc.util.VLCVideoLayout
//
//class VlcVideoViewBackup @JvmOverloads constructor(
//    context: Context,
//    attrs: AttributeSet? = null,
//    defStyleAttr: Int = 0,
//    defStyleRes: Int = 0
//) : VLCVideoLayout(context, attrs, defStyleAttr, defStyleRes), IView, MediaPlayer.EventListener, OnNewVideoLayoutListener {
//    companion object {
//        private const val TAG = "VLCPlayer"
//        const val USE_TEXTURE_VIEW: Boolean = false
//        const val ENABLE_SUBTITLES: Boolean = false
//    }
//
//    init {
//        //mediaPlayer.setAudioOutput("opensles_android")
//        mediaPlayer.setEventListener(this)
//    }
//
//    private val voutCallback = object : IVLCVout.Callback {
//        override fun onSurfacesCreated(vlcVout: IVLCVout?) {
//            play(lastPlayUrl)
//        }
//
//        override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {}
//    }
//
//    private var lastPlayUrl: String? = null
//    override fun play(url: String?) {
//        if (!RegexUtils.isURL(url) && !RegexUtils.isURL(lastPlayUrl)) {
//            return
//        }
//        lastPlayUrl = if (!RegexUtils.isURL(url)) lastPlayUrl else url
//
//        mediaPlayer.vlcVout.addCallback(voutCallback)
//
//        if (mediaPlayer.vlcVout.areViewsAttached()) {
//            ThreadManager.actionPool.submit {
//                try {
//                    val media = Media(mLibVLC, Uri.parse(url))
//                    // 硬解码
//                    val hardCodec = KeyValue.hardCodec
//                    media.setHWDecoderEnabled(hardCodec, hardCodec)
//                    //media.addOption(":network-caching=10000") // 默认1500
//                    //media.addOption(":clock-jitter=0")
//                    //media.addOption(":clock-synchro=0")
//                    if (hardCodec) {
//                        //media.addOption(":avcodec-hw=any")
//                        //media.addOption(":avcodec-threads=1")
//                        //media.addOption(":codec=mediacodec_ndk")
//                        //media.addOption(":codec=avcodec")
//                        //media.addOption(":codec=mediacodec_ndk,iomx,all")
//                        // 解决音频问题
//                        //media.addOption(":no-ts-trust-pcr")
//
//                        //media.addOption(":sout-chromecast-audio-passthrough=true")
//                        //media.addOption(":sout-chromecast-conversion-quality=2")
//                    }
//                    mediaPlayer.media = media
//                    media.release()
//                    mediaPlayer.play()
//                } catch (e: Exception) {
//                    Log.e(TAG, e.message ?: "unknown exception")
//                }
//            }
//        } else {
//            mediaPlayer.attachViews(this@VlcVideoViewBackup, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
//        }
//    }
//
//    fun onStart() {
//        play()
//    }
//
//    override fun onStop() {
//        mediaPlayer.vlcVout.removeCallback(voutCallback)
//        mediaPlayer.vlcVout.detachViews()
//        ThreadManager.actionPool.submit {
//            mediaPlayer.stop()
//        }
//    }
//
//    override fun onDestroy() {
//        playerEventCallback = null
//        mediaPlayer.setEventListener(null)
//        /*ThreadManager.actionPool.submit {
//            mediaPlayer.release()
//            mLibVLC.release()
//        }*/
//    }
//
//    var playerEventCallback: VlcPlayerCallback? = null
//    override fun onEvent(event: MediaPlayer.Event) {
//        when (event.type) {
//            MediaPlayer.Event.Buffering -> {
//                // 处理缓冲事件
//                Log.e(TAG, "Buffering...")
//                playerEventCallback?.onBuffering(event.buffering)
//            }
//
//            MediaPlayer.Event.EndReached -> {
//                Log.e(TAG, "EndReached...")
//                // 处理播放结束事件
//                playerEventCallback?.onEndReached()
//            }
//
//            MediaPlayer.Event.EncounteredError -> {
//                Log.e(TAG, "EncounteredError...")
//                // 处理播放错误事件
//                playerEventCallback?.onError()
//            }
//
//            MediaPlayer.Event.TimeChanged -> {
//                Log.e(TAG, "TimeChanged...")
//                // 处理播放进度变化事件
//                playerEventCallback?.onTimeChanged(event.timeChanged)
//            }
//
//            MediaPlayer.Event.PositionChanged -> {
//                // 处理播放位置变化事件
//                playerEventCallback?.onPositionChanged(event.positionChanged)
//            }
//
//            MediaPlayer.Event.Vout -> {
//                // 在视频开始播放之前，视频的宽度和高度可能还没有被确定，
//                // 因此我们需要在MediaPlayer.Event.Vout事件发生后才能获取到正确的宽度和高度
//                //(vlcPlayer.mediaPlayer?.getSelectedTrack(IMedia.Track.Type.Video) as? IMedia.VideoTrack?)?.let {
//                mediaPlayer.currentVideoTrack?.let {
//                    Log.e(TAG, "currentVideoTrack width:${it.width} height:${it.height}")
//                    playerEventCallback?.onVideoSizeChanged(it.width, it.height)
//                }
//            }
//        }
//    }
//
//    override fun onNewVideoLayout(
//        vlcVout: IVLCVout?,
//        width: Int,
//        height: Int,
//        visibleWidth: Int,
//        visibleHeight: Int,
//        sarNum: Int,
//        sarDen: Int
//    ) {
//        Log.e(TAG, "onNewVideoLayout width:${width} height:${height}")
//        playerEventCallback?.onVideoSizeChanged(width, height)
//    }
//
//    /**
//     * 改变尺寸
//     */
//    override fun scaleViewSize(currentVideoWidth: Int, currentVideoHeight: Int) {
//        var scaledHeight = ScreenUtils.getScreenHeight()
//        val calWidth = (scaledHeight * currentVideoWidth * 1.0f / currentVideoHeight).toInt()
//        val scaledWidth = if (calWidth > ScreenUtils.getScreenWidth()) {
//            scaledHeight = (ScreenUtils.getScreenWidth() * currentVideoHeight * 1.0f / ScreenUtils.getScreenWidth()).toInt()
//            ScreenUtils.getScreenWidth()
//        } else {
//            calWidth
//        }
//
//        mediaPlayer.let { mediaPlayer ->
//            mediaPlayer.scale = 0f
//            mediaPlayer.aspectRatio = "${scaledWidth}:${scaledHeight}"
//            post {
//                layoutParams = layoutParams.apply {
//                    width = scaledWidth
//                    height = scaledHeight
//                }
//                invalidate()
//            }
//        }
//    }
//}
