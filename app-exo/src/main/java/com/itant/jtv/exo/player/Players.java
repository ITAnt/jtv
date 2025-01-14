package com.itant.jtv.exo.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.itant.jtv.storage.kv.KeyValue;
import com.miekir.mvvm.context.GlobalContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class Players implements Player.Listener {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final StringBuilder builder;
    private final Formatter formatter;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private ExoPlayer exoPlayer;
    private String format;
    private String url;

    private long position;
    private int decode = HARD;
    private int count;
    private int retry;

    public static boolean isExo(int type) {
        return type == EXO;
    }

    public static boolean isHard(int decode) {
        return decode == HARD;
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isSoft() {
        return decode == SOFT;
    }

    public boolean isExo() {
        return true;
    }

    public Players(Activity activity) {
        builder = new StringBuilder();
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(GlobalContext.getContext(), 0, new Intent(GlobalContext.getContext(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView exo) {
        releaseExo();
        initExo(exo);
    }

    private void initExo(PlayerView view) {
        exoPlayer = new ExoPlayer.Builder(GlobalContext.getContext())
                .setLoadControl(new DefaultLoadControl())
                .setTrackSelector(ExoUtil.buildTrackSelector())
                .setRenderersFactory(new NextRenderersFactory(GlobalContext.getContext(), KeyValue.INSTANCE.getHardCodec() ? HARD : SOFT))
                .setMediaSourceFactory(new MediaSourceFactory())
                .build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }


    public ExoPlayer exo() {
        return exoPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        session.setMetadata(metadata);
    }

    public void setPlayer(int player) {
        this.decode = getDecode(player);
    }

    /**
     * @param player
     * @return 1：硬解
     */
    public int getDecode(int player) {
        return 1;
    }

    public void setDecode(int player, int decode) {

    }

    public void setPosition(long position) {
        this.position = position;
    }

    public boolean canToggleDecode() {
        return isExo() && ++count <= 1;
    }

    public void reset() {
        position = C.TIME_UNSET;
        removeTimeoutCheck();
        count = 0;
        retry = 0;
    }

    public void clear() {
        headers = null;
        format = null;
        url = null;
    }

    public int addRetry() {
        return ++retry;
    }

    public String stringToTime(long time) {
        //return Util.format(builder, formatter, time);
        return "";
    }

    public int getVideoWidth() {
        return exoPlayer.getVideoSize().width;
    }

    public int getVideoHeight() {
        return exoPlayer.getVideoSize().height;
    }

    public float getSpeed() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackParameters().speed;
        return 1.0f;
    }

    public long getPosition() {
        if (isExo() && exoPlayer != null) return exoPlayer.getCurrentPosition();
        return 0;
    }

    public long getDuration() {
        if (isExo() && exoPlayer != null) return exoPlayer.getDuration();
        return -1;
    }

    public long getBuffered() {
        if (isExo() && exoPlayer != null) return exoPlayer.getBufferedPosition();
        return 0;
    }


    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public boolean isEnd() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackState() == Player.STATE_ENDED;
        return false;
    }

    public boolean isRelease() {
        return exoPlayer == null;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 5 * 60 * 1000;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null) exoPlayer.setPlaybackSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.2f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void nextPlayer() {
        setPlayer(isExo() ? IJK : EXO);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (isExo() && exoPlayer != null) exoPlayer.seekTo(time);
    }

    public void play() {
        if (isPlaying() || isEnd()) return;
        session.setActive(true);
        if (isExo()) playExo();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (isExo()) pauseExo();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        if (isExo()) stopExo();
        session.setActive(false);
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void release() {
        session.release();
        if (isExo()) releaseExo();
        removeTimeoutCheck();
    }

    private void playExo() {
        if (exoPlayer == null) return;
        exoPlayer.play();
    }

    private void pauseExo() {
        if (exoPlayer == null) return;
        exoPlayer.pause();
    }

    private void stopExo() {
        if (exoPlayer == null) return;
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }

    private void releaseExo() {
        if (exoPlayer == null) return;
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    public void setMediaSource(String url) {
        setMediaSource(new HashMap<>(), url);
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        setMediaSource(headers, url, null);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format) {
        if (isExo() && exoPlayer != null) exoPlayer.setMediaItem(
                ExoUtil.getMediaItem(headers, uri(this.url = url), this.format = format),
                position
        );
        if (isExo() && exoPlayer != null) exoPlayer.prepare();
    }

    public static Uri uri(String url) {
        return Uri.parse(url.trim().replace("\\", ""));
    }

    private void removeTimeoutCheck() {

    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    public Uri getUri() {
        return Uri.parse(getUrl());
    }

    public String[] getHeaderArray() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
        return list.toArray(new String[0]);
    }

    public Bundle getHeaderBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
        return bundle;
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        //ActionEvent.update();
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            //if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private PlayStatusListener playStatusListener;

    public void setPlayStatusListener(PlayStatusListener playStatusListener) {
        this.playStatusListener = playStatusListener;
    }

    public PlayStatusListener getPlayStatusListener() {
        return playStatusListener;
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                break;
            case Player.STATE_BUFFERING:
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
            case Player.STATE_ENDED:
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        //Logger.t(TAG).e(error.errorCode + "," + url);
        if (playStatusListener != null) {
            playStatusListener.onError();
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        //PlayerEvent.state(state);
        if (playStatusListener == null) {
            return;
        }
        if (state == Player.STATE_READY) {
            playStatusListener.onPlay();
        } else {
            playStatusListener.onError();
        }
    }
}
