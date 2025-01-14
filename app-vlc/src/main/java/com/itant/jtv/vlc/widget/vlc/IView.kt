package com.itant.jtv.vlc.widget.vlc

interface IView {
    fun onDestroy()
    fun play(url: String? = null)
    fun onStop()
    //fun scaleViewSize(currentVideoWidth: Int, currentVideoHeight: Int)
}