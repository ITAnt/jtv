package tv.danmaku.ijk.custom;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;

@Keep
public class TvVideoView extends VideoView<TvMediaPlayer> {
    public TvVideoView(@NonNull Context context) {
        super(context);
    }

    public TvVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TvVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        /*setPlayerFactory(new PlayerFactory<TvMediaPlayer>() {
            @Override
            public TvMediaPlayer createPlayer(Context context) {
                return new TvMediaPlayer(context);
            }
        });*/
    }

    @Override
    protected void setOptions() {
        super.setOptions();
        for (Map.Entry<String, Object> next : mPlayerOptions.entrySet()) {
            String key = next.getKey();
            Object value = next.getValue();
            if (value instanceof String) {
                mMediaPlayer.setPlayerOption(key, (String) value);
            } else if (value instanceof Long) {
                mMediaPlayer.setPlayerOption(key, (Long) value);
            }
        }
    }

    private final HashMap<String, Object> mPlayerOptions = new HashMap<>();

    /**
     * 开启硬解
     */
    public void setEnableMediaCodec(boolean isEnable) {
        int value = isEnable ? 1 : 0;
        addPlayerOption("mediacodec-all-videos", value);
        addPlayerOption("mediacodec-sync", value);
        addPlayerOption("mediacodec-auto-rotate", value);
        addPlayerOption("mediacodec-handle-resolution-change", value);
    }

    public void addPlayerOption(String name, String value) {
        mPlayerOptions.put(name, value);
    }

    public void addPlayerOption(String name, long value) {
        mPlayerOptions.put(name, value);
    }
}
