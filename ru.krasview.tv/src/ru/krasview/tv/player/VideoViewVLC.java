package ru.krasview.tv.player;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by anya on 25.06.16.
 */
public class VideoViewVLC extends SurfaceView implements IVLCVout.Callback, VideoInterface {

    public final static String TAG = "Krasview/VideoViewVLC";

    private SurfaceView mSurface;
    private SurfaceHolder holder;

    private LibVLC libvlc;
    private org.videolan.libvlc.MediaPlayer mMediaPlayer = null;
    private int mVideoWidth = 100;
    private int mVideoHeight = 100;

    TVController mTVController;
    VideoController mVideoController;
    Map<String, Object> mMap;

    int dw = 1;
    int dh = 1;

    boolean stopped = false;

    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_SIZE = 3;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private static final int SURFACE_FROM_SETTINGS = 7;
    private int mCurrentSize = SURFACE_FROM_SETTINGS;

    String pref_aspect_ratio = "default";
    String pref_aspect_ratio_video = "default";


    public VideoViewVLC(Context context) {
        super(context);
       init();
    }

    private void init() {
        mSurface = this;
        holder = mSurface.getHolder();

        this.setFocusable(false);
        this.setClickable(false);
    }

    @Override
    public void setVideoAndStart(String address) {
        this.createPlayer(address);
    }

    @Override
    public void stop() {
        if(mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.stop();
        stopped = true;
    }

    @Override
    public void pause() {
        if(mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.pause();
    }

    @Override
    public void play() {
        if(mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.play();
    }

    @Override
    public void setTVController(TVController tc) {
        mTVController = tc;
        mTVController.setVideo(this);
    }

    @Override
    public void setVideoController(VideoController vc) {
        mVideoController = vc;
        mVideoController.setVideo(this);
    }

    @Override
    public void setMap(Map<String, Object> map) {
        mMap = map;
        if(mTVController != null) {
            mTVController.setMap(mMap);
        }
        if(mVideoController != null) {
            mVideoController.setMap(mMap);
        }
        getPrefs();
        stopped = false;
    }

    @Override
    public boolean isPlaying() {

        return mMediaPlayer.isPlaying();
    }

    @Override
    public boolean showOverlay() {
        mHandler.removeMessages(SHOW_PROGRESS);
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        return true;
    }

    @Override
    public boolean hideOverlay() {
        return false;
    }

    @Override
    public int getProgress() {
        if(mMediaPlayer == null) {
            return 0;
        }
        return (int) mMediaPlayer.getTime();
    }

    @Override
    public int getLeight() {
        if(mMediaPlayer == null) {
            return 0;
        }
        return (int) mMediaPlayer.getLength();
    }

    @Override
    public int getTime() {
        if(mMediaPlayer == null) {
            return 0;
        }
        return (int) mMediaPlayer.getTime();
    }

    @Override
    public void setTime(int time) {
        if(mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.setTime(time);
    }

    @Override
    public int changeSizeMode() {
        return 0;
    }

    @Override
    public String changeAudio() {
        return null;
    }

    @Override
    public String changeSubtitle() {
        return null;
    }

    @Override
    public int getAudioTracksCount() {
        return 0;
    }

    @Override
    public int getSpuTracksCount() {
        return 0;
    }

    @Override
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {

    }

    @Override
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {

    }

    @Override
    public int changeOrientation() {
        setSize(mVideoWidth, mVideoHeight);
        return 0;
    }

    @Override
    public void end() {
        // TODO Auto-generated method stub
        if(mTVController != null) {
            mTVController.end();
        }
        if(mVideoController != null) {
            mVideoController.end();
        }

        mHandler.removeMessages(SHOW_PROGRESS);
    }
    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        Log.d("MyVLC", "CreatePlayer " + media);
        releasePlayer();
        try {
            if (media.toString().length() > 0) {
                Toast toast = Toast.makeText(getContext(), media.toString(), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create LibVLC
            // TODO: make this more robust, and sync with audio demo
            ArrayList<String> options = new ArrayList<String>();
            //options.add("--subsdec-encoding <encoding>");
            options.add("--aout=opensles");
            options.add("--audio-time-stretch"); // time stretching
            options.add("-vvv"); // verbosity
            libvlc = new LibVLC(options);
            //libvlc.setOnHardwareAccelerationError(this);
            holder.setKeepScreenOn(true);

            // Create media player
            mMediaPlayer = new org.videolan.libvlc.MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);

            // Set up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.setVideoView(mSurface);
            //vout.setSubtitlesView(mSurfaceSubtitles);
            vout.addCallback(this);
            vout.attachViews();


            URL url = new URL(media);
            Uri uri = Uri.parse(media);

            Log.d("MyVLC", "uri scheme " + uri.getScheme());

            Media m = new Media(libvlc, uri);
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    // TODO: handle this cleaner
    public void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.removeCallback(this);
        vout.detachViews();
        holder = null;
        libvlc.release();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    public void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        if(holder == null || mSurface == null)
            return;

        // get screen size
        int w = ((Activity)this.getContext()).getWindow().getDecorView().getWidth();
        int h = ((Activity)this.getContext()).getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        holder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        ViewGroup.LayoutParams lp = mSurface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
    }

    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height == 0)
            return;

        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {

    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vlcVout) {

    }

    private org.videolan.libvlc.MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);

    private static class MyPlayerListener implements org.videolan.libvlc.MediaPlayer.EventListener {
        private WeakReference<VideoViewVLC> mOwner;

        public MyPlayerListener(VideoViewVLC owner) {
            mOwner = new WeakReference<VideoViewVLC>(owner);
        }

        @Override
        public void onEvent(org.videolan.libvlc.MediaPlayer.Event event) {
            VideoViewVLC player = mOwner.get();

            switch(event.type) {
                case org.videolan.libvlc.MediaPlayer.Event.EndReached:
                    Log.d(TAG, "MediaPlayerEndReached");
                    player.releasePlayer();
                    break;
                case org.videolan.libvlc.MediaPlayer.Event.Playing:
                case org.videolan.libvlc.MediaPlayer.Event.Paused:
                case org.videolan.libvlc.MediaPlayer.Event.Stopped:
                default:
                    break;
            }
        }
    }

    private void getPrefs() {
        SharedPreferences prefs;
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        pref_aspect_ratio = prefs.getString("aspect_ratio", "default");
        if(mMap.get("type").equals("video")) {

            pref_aspect_ratio_video = prefs.getString("aspect_ratio_video", "default");
        } else {
            pref_aspect_ratio_video = prefs.getString("aspect_ratio_tv", "default");

        }
    }

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SURFACE_SIZE:
                    //changeSurfaceSize();
                    break;
                case SHOW_PROGRESS:
                    setOverlayProgress();
                    break;
            }
        }
    };

    private void setOverlayProgress() {
        if(mVideoController != null) {
            mVideoController.showProgress();
        }
        mHandler.removeMessages(SHOW_PROGRESS);
        Message msg = mHandler.obtainMessage(SHOW_PROGRESS);
        mHandler.sendMessageDelayed(msg, 1000);
        return;
    }

    @Override
    public boolean dispatchKeyEvent (KeyEvent event) {
        if(mTVController!=null) {
            return mTVController.dispatchKeyEvent(event);
        }
        if(mVideoController!=null) {
            return mVideoController.dispatchKeyEvent(event);
        }
        return true;
    }
}