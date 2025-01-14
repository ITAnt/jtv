package com.itant.jtv.exo.player;

import android.support.v4.media.session.MediaSessionCompat;


public class SessionCallback extends MediaSessionCompat.Callback {

    private final Players player;

    public static SessionCallback create(Players player) {
        return new SessionCallback(player);
    }

    private SessionCallback(Players player) {
        this.player = player;
    }

    @Override
    public void onSeekTo(long pos) {
        player.seekTo(pos);
    }

    @Override
    public void onPlay() {
        //ActionEvent.send(ActionEvent.PLAY);
        PlayStatusListener listener = player.getPlayStatusListener();
        if (listener != null) {
            listener.onPlay();
        }
    }

    @Override
    public void onPause() {
        //ActionEvent.send(ActionEvent.PAUSE);
        PlayStatusListener listener = player.getPlayStatusListener();
        if (listener != null) {
            listener.onError();
        }
    }

    @Override
    public void onSkipToPrevious() {
        //ActionEvent.send(ActionEvent.PREV);
    }

    @Override
    public void onSkipToNext() {
        //ActionEvent.send(ActionEvent.NEXT);
    }

    @Override
    public void onStop() {
        //ActionEvent.send(ActionEvent.STOP);
        PlayStatusListener listener = player.getPlayStatusListener();
        if (listener != null) {
            listener.onError();
        }
    }
}
