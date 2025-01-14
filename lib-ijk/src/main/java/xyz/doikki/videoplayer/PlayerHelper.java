package xyz.doikki.videoplayer;

import tv.danmaku.ijk.media.player.IjkLibLoader;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class PlayerHelper {
    public static void init() {
        try {
            IjkMediaPlayer.loadLibrariesOnce(new IjkLibLoader() {
                @Override
                public void loadLibrary(String s) throws UnsatisfiedLinkError, SecurityException {
                    try {
                        System.loadLibrary(s);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
