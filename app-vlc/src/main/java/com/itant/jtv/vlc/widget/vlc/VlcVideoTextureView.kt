//package com.itant.jtv.vlc.widget.vlc
//
//import android.content.Context
//import android.graphics.SurfaceTexture
//import android.text.TextUtils
//import android.util.AttributeSet
//import android.util.Log
//import android.view.TextureView
//import com.blankj.utilcode.util.ScreenUtils
//import com.itant.jtv.manager.ThreadManager
//import org.videolan.libvlc.MediaPlayer
//import org.videolan.libvlc.interfaces.IVLCVout
//import org.videolan.libvlc.interfaces.IVLCVout.OnNewVideoLayoutListener
//
//class VlcVideoTextureView @JvmOverloads constructor(
//    context: Context,
//    attrs: AttributeSet? = null,
//    defStyleAttr: Int = 0,
//    defStyleRes: Int = 0
//) : TextureView(context, attrs, defStyleAttr, defStyleRes), MediaPlayer.EventListener, OnNewVideoLayoutListener, IView {
//
//    companion object {
//        private const val TAG = "VLCPlayer"
//    }
//
//    private var lastPlayUrl: String? = null
//    private val vlcPlayer: VlcPlayer by lazy { VlcPlayer() }
//
//    init {
//        surfaceTextureListener = object : SurfaceTextureListener {
//            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
//                vlcPlayer.executeInitPlayer(context, this@VlcVideoTextureView, playerEventCallback?.hardwareMediaCodec() == true, this@VlcVideoTextureView,  this@VlcVideoTextureView)
//            }
//
//            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
//                vlcPlayer.executeDestroy()
//                return false
//            }
//
//            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
//            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
//        }
//    }
//
//    /**
//     * 播放
//     */
//    override fun play(url: String?) {
//        if (TextUtils.isEmpty(url) && TextUtils.isEmpty(lastPlayUrl)) {
//            return
//        }
//
//        lastPlayUrl = if (TextUtils.isEmpty(url)) lastPlayUrl else url
//        vlcPlayer.executePlay(lastPlayUrl!!)
//    }
//
//    /**
//     * 停止播放
//     */
//    override fun onStop() {
//        vlcPlayer.executeStop()
//    }
//
//    /**
//     * 改变尺寸
//     */
//    override fun scaleViewSize(currentVideoWidth: Int, currentVideoHeight: Int) {
//        var scaledHeight = ScreenUtils.getScreenHeight()
//        val calWidth = (scaledHeight*currentVideoWidth*1.0f/currentVideoHeight).toInt()
//        val scaledWidth = if (calWidth > ScreenUtils.getScreenWidth()) {
//            scaledHeight = (ScreenUtils.getScreenWidth()*currentVideoHeight*1.0f/ ScreenUtils.getScreenWidth()).toInt()
//            ScreenUtils.getScreenWidth()
//        } else {
//            calWidth
//        }
//
//        vlcPlayer.mediaPlayer?.let { mediaPlayer ->
//            ThreadManager.actionPool.submit {
//                mediaPlayer.scale = 0f
//                mediaPlayer.aspectRatio = "${scaledWidth}:${scaledHeight}"
//                this@VlcVideoTextureView.post {
//                    this@VlcVideoTextureView.layoutParams = this@VlcVideoTextureView.layoutParams.apply {
//                        width = scaledWidth
//                        height = scaledHeight
//                    }
//                    invalidate()
//                }
//            }
//        }
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
//                vlcPlayer.mediaPlayer?.currentVideoTrack?.let {
//                    playerEventCallback?.onVideoSizeChanged(it.width, it.height)
//                }
//            }
//        }
//    }
//
//    override fun onNewVideoLayout(vlcVout: IVLCVout?, width: Int, height: Int, visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int) {
//        Log.e(TAG, "width:${width} height:${height}")
//        playerEventCallback?.onVideoSizeChanged(width, height)
//    }
//
//    override fun onDestroy() {
//        surfaceTextureListener = null
//        this@VlcVideoTextureView.playerEventCallback = null
//    }
//}
