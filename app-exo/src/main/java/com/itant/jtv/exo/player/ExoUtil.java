package com.itant.jtv.exo.player;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import com.miekir.mvvm.context.GlobalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class ExoUtil {

    public static boolean haveTrack(Tracks tracks, int type) {
        int count = 0;
        for (Tracks.Group trackGroup : tracks.getGroups()) if (trackGroup.getType() == type) count += trackGroup.length;
        return count > 0;
    }

    public static void selectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        selectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void deselectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        deselectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(GlobalContext.getContext());
        trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage(Locale.getDefault().getISO3Language()).setForceHighestSupportedBitrate(true).setTunnelingEnabled(false));
        return trackSelector;
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    public static int getRetry(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return 2;
        if (errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && errorCode <= PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && errorCode <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) return 2;
        return 1;
    }



    private static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri) {
        Bundle extras = new Bundle();
        for (Map.Entry<String, String> header : headers.entrySet()) extras.putString(header.getKey(), header.getValue());
        return new MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build();
    }

    public static MediaItem getMediaItem(Map<String, String> headers, Uri uri, String mimeType) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri));
        if (mimeType != null) builder.setMimeType(mimeType);
        builder.setMediaId(uri.toString());
        return builder.build();
    }

    private static void selectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i == track || trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void deselectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i != track && trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void setTrackParameters(ExoPlayer player, int group, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(player.getCurrentTracks().getGroups().get(group).getMediaTrackGroup(), trackIndices)).build());
    }
}
