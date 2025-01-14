package com.itant.jtv.vlc.widget.vlc

interface VlcPlayerCallback {
        fun onBuffering(bufferPercent: Float) {}
        fun onEndReached() {}
        fun onTimeChanged(currentTime: Long) {}
        fun onPositionChanged(position: Float) {}

        /**
         * 是否硬件解码
         */
        fun hardwareMediaCodec(): Boolean

        fun onError()

        fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int)
    }