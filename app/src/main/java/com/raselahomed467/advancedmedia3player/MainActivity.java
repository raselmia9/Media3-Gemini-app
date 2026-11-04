package com.raselahomed467.advancedmedia3player;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.VideoSize;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import androidx.media3.ui.AspectRatioFrameLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_URL =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8";

    private static final String GITHUB_HTML_URL =
            "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/EventCode.html";

    private static final UUID CLEARKEY_UUID =
            new UUID(0x1077EFECC0B24D02L, 0xACE33C1E52E2FB4BL);

    private static final UUID WIDEVINE_UUID =
            new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    private FrameLayout rootContainer;
    private ScrollView pageScrollView;
    private LinearLayout pageContent;

    private FrameLayout playerContainer;
    private PlayerView playerView;
    private ExoPlayer player;
    private ProgressBar bufferingProgress;

    private FrameLayout controllerOverlay;
    private LinearLayout topControlBar;
    private LinearLayout centerControlBar;
    private LinearLayout bottomControlBar;

    private Button pipButton;
    private Button settingsButton;
    private Button fullscreenButton;
    private Button playPauseButton;
    private Button rewindButton;
    private Button forwardButton;

    private SeekBar timelineSeekBar;
    private TextView timeCurrentText;
    private TextView timeTotalText;

    private EditText urlInput;
    private Button playButton;
    private HorizontalScrollView episodesScroll;
    private LinearLayout episodesContainer;
    private final List<Button> episodeButtons = new ArrayList<>();
    private int selectedEpisodeIndex = -1;

    private WebView webView;

    private boolean isUserSeeking = false;
    private boolean fullscreen = false;
    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Handler controlsHideHandler = new Handler(Looper.getMainLooper());
    private static final long CONTROLS_TIMEOUT_MS = 4000;

    private static class EpisodeItem {
        final String title;
        final String url;
        EpisodeItem(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeline();
            progressHandler.postDelayed(this, 500);
        }
    };

    private final Runnable hideControlsRunnable = this::hideControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        buildUi();
        initPlayer();
        loadGitHubHtml();

        playStream(DEFAULT_URL, null, null, null, null, null, false);
    }

    // ==========================================
    // UI BUILDER (100% PROGRAMMATIC)
    // ==========================================

    @SuppressLint("SetJavaScriptEnabled")
    private void buildUi() {
        rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootContainer.setBackgroundColor(Color.parseColor("#0F141C"));

        pageScrollView = new ScrollView(this);
        pageScrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageScrollView.setFillViewport(true);

        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView appTitle = new TextView(this);
        appTitle.setText("ADVANCED MEDIA3 PLAYER");
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        appTitle.setTextColor(Color.parseColor("#DCE5F2"));
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setPadding(dp(16), dp(14), dp(16), dp(10));
        pageContent.addView(appTitle);

        playerContainer = new FrameLayout(this);
        int playerHeight = (int) (getResources().getDisplayMetrics().widthPixels * (9f / 16f));
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                playerHeight
        );
        playerContainer.setLayoutParams(playerLp);
        playerContainer.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerContainer.addView(playerView);

        bufferingProgress = new ProgressBar(this);
        FrameLayout.LayoutParams bufLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        bufferingProgress.setLayoutParams(bufLp);
        bufferingProgress.setVisibility(View.GONE);
        playerContainer.addView(bufferingProgress);

        buildCustomControllerOverlay();
        playerContainer.addView(controllerOverlay);

        playerContainer.setOnClickListener(v -> toggleControls());

        pageContent.addView(playerContainer);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputRowLp.setMargins(dp(12), dp(12), dp(12), dp(6));
        inputRow.setLayoutParams(inputRowLp);

        urlInput = new EditText(this);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        urlLp.setMarginEnd(dp(8));
        urlInput.setLayoutParams(urlLp);
        urlInput.setHint("Enter stream URL or click list item below");
        urlInput.setHintTextColor(Color.parseColor("#6A788E"));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        urlInput.setSingleLine(true);
        urlInput.setPadding(dp(12), 0, dp(12), 0);
        urlInput.setBackground(createRoundedDrawable(Color.parseColor("#1C2431"), dp(8), Color.parseColor("#2E3B4E"), dp(1)));
        inputRow.addView(urlInput);

        playButton = new Button(this);
        LinearLayout.LayoutParams playBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        playButton.setLayoutParams(playBtnLp);
        playButton.setText("PLAY");
        playButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        playButton.setTextColor(Color.WHITE);
        playButton.setTypeface(Typeface.DEFAULT_BOLD);
        playButton.setPadding(dp(18), 0, dp(18), 0);
        playButton.setBackground(createRoundedDrawable(Color.parseColor("#2563EB"), dp(8), 0, 0));
        playButton.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                playStream(url, null, null, null, null, null, false);
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show();
            }
        });
        inputRow.addView(playButton);

        pageContent.addView(inputRow);

        episodesScroll = new HorizontalScrollView(this);
        episodesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams epsScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        epsScrollLp.setMargins(dp(12), dp(2), dp(12), dp(8));
        episodesScroll.setLayoutParams(epsScrollLp);
        episodesScroll.setVisibility(View.GONE);

        episodesContainer = new LinearLayout(this);
        episodesContainer.setOrientation(LinearLayout.HORIZONTAL);
        episodesContainer.setGravity(Gravity.CENTER_VERTICAL);
        episodesContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        episodesScroll.addView(episodesContainer);

        pageContent.addView(episodesScroll);

        webView = new WebView(this);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        webView.setLayoutParams(webLp);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.addJavascriptInterface(new WebAppInterface(), "CustomWebView");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectBridgeShim();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        pageContent.addView(webView);
        pageScrollView.addView(pageContent);
        rootContainer.addView(pageScrollView);

        setContentView(rootContainer);
    }

    private void buildCustomControllerOverlay() {
        controllerOverlay = new FrameLayout(this);
        controllerOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        controllerOverlay.setBackgroundColor(Color.parseColor("#44000000"));

        topControlBar = new LinearLayout(this);
        topControlBar.setOrientation(LinearLayout.HORIZONTAL);
        topControlBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
                Gravity.TOP
        );
        topControlBar.setLayoutParams(topLp);
        topControlBar.setPadding(dp(8), dp(4), dp(8), 0);

        pipButton = createIconButton("⧉", "Picture in picture");
        pipButton.setOnClickListener(v -> enterPipMode());
        topControlBar.addView(pipButton);

        settingsButton = createIconButton("⚙", "Settings");
        settingsButton.setOnClickListener(v -> showPlayerSettings());
        topControlBar.addView(settingsButton);

        fullscreenButton = createIconButton("⤢", "Fullscreen");
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        topControlBar.addView(fullscreenButton);

        controllerOverlay.addView(topControlBar);

        centerControlBar = new LinearLayout(this);
        centerControlBar.setOrientation(LinearLayout.HORIZONTAL);
        centerControlBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        centerControlBar.setLayoutParams(centerLp);

        rewindButton = createCircleButton("↺10", dp(48));
        rewindButton.setOnClickListener(v -> seekRelative(-10000));
        centerControlBar.addView(rewindButton);

        View space1 = new View(this);
        space1.setLayoutParams(new LinearLayout.LayoutParams(dp(20), 1));
        centerControlBar.addView(space1);

        playPauseButton = createCircleButton("▶", dp(58));
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        centerControlBar.addView(playPauseButton);

        View space2 = new View(this);
        space2.setLayoutParams(new LinearLayout.LayoutParams(dp(20), 1));
        centerControlBar.addView(space2);

        forwardButton = createCircleButton("10↻", dp(48));
        forwardButton.setOnClickListener(v -> seekRelative(10000));
        centerControlBar.addView(forwardButton);

        controllerOverlay.addView(centerControlBar);

        bottomControlBar = new LinearLayout(this);
        bottomControlBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomControlBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams botLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44),
                Gravity.BOTTOM
        );
        bottomControlBar.setLayoutParams(botLp);
        bottomControlBar.setPadding(dp(12), 0, dp(12), dp(4));

        timeCurrentText = new TextView(this);
        timeCurrentText.setText("00:00");
        timeCurrentText.setTextColor(Color.WHITE);
        timeCurrentText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bottomControlBar.addView(timeCurrentText);

        timelineSeekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.setMargins(dp(8), 0, dp(8), 0);
        timelineSeekBar.setLayoutParams(seekLp);
        timelineSeekBar.setMax(1000);
        timelineSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                        long target = (duration * progress) / 1000L;
                        timeCurrentText.setText(formatTime(target));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                controlsHideHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                        long target = (duration * seekBar.getProgress()) / 1000L;
                        player.seekTo(target);
                    }
                }
                isUserSeeking = false;
                scheduleControlsHide();
            }
        });
        bottomControlBar.addView(timelineSeekBar);

        timeTotalText = new TextView(this);
        timeTotalText.setText("00:00");
        timeTotalText.setTextColor(Color.parseColor("#A0ABC0"));
        timeTotalText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bottomControlBar.addView(timeTotalText);

        controllerOverlay.addView(bottomControlBar);
    }

    private Button createIconButton(String iconText, String contentDesc) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);
        btn.setText(iconText);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(createRoundedDrawable(Color.parseColor("#33000000"), dp(20), Color.parseColor("#44FFFFFF"), dp(1)));
        btn.setContentDescription(contentDesc);
        btn.setPadding(0, 0, 0, 0);
        return btn;
    }

    private Button createCircleButton(String text, int sizeDp) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizeDp, sizeDp);
        btn.setLayoutParams(lp);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp >= dp(54) ? 22 : 12);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackground(createRoundedDrawable(Color.parseColor("#66000000"), sizeDp / 2, Color.parseColor("#55FFFFFF"), dp(1)));
        btn.setPadding(0, 0, 0, 0);
        return btn;
    }

    private GradientDrawable createRoundedDrawable(int solidColor, int radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(solidColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    // ==========================================
    // CONTROLS INTERACTION
    // ==========================================

    private void toggleControls() {
        if (controllerOverlay.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controllerOverlay.setVisibility(View.VISIBLE);
        scheduleControlsHide();
    }

    private void hideControls() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        controllerOverlay.setVisibility(View.GONE);
    }

    private void scheduleControlsHide() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        controlsHideHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS);
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        updatePlayPauseButton();
        scheduleControlsHide();
    }

    private void seekRelative(long deltaMs) {
        if (player == null) return;
        long target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + deltaMs));
        player.seekTo(target);
        updateTimeline();
        scheduleControlsHide();
    }

    private void updatePlayPauseButton() {
        if (player == null || playPauseButton == null) return;
        boolean playing = player.isPlaying();
        playPauseButton.setText(playing ? "❚❚" : "▶");
    }

    private void updateTimeline() {
        if (player == null || isUserSeeking) return;

        long current = player.getCurrentPosition();
        long duration = player.getDuration();

        if (duration > 0) {
            timelineSeekBar.setEnabled(true);
            int progress = (int) ((current * 1000L) / duration);
            timelineSeekBar.setProgress(progress);
            timeCurrentText.setText(formatTime(current));
            timeTotalText.setText(formatTime(duration));
        } else {
            timelineSeekBar.setEnabled(false);
            timelineSeekBar.setProgress(0);
            timeCurrentText.setText(formatTime(current));
            timeTotalText.setText("LIVE");
        }
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hour = totalSec / 3600;

        if (hour > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hour, min, sec);
        } else {
            return String.format(Locale.US, "%02d:%02d", min, sec);
        }
    }

    // ==========================================
    // EXOPLAYER CORE & DRM SETUP
    // ==========================================

    private void initPlayer() {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        AudioProcessor downmixProcessor = createSurroundDownmixProcessor();

        renderersFactory.buildAudioSink(this, true, false) ;
        AudioSink customAudioSink = new DefaultAudioSink.Builder(this)
                .setAudioProcessors(new AudioProcessor[]{downmixProcessor})
                .build();

        renderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(
                    Context context,
                    boolean enableFloatOutput,
                    boolean enableAudioTrackPlaybackParams
            ) {
                return customAudioSink;
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        OkHttpDataSource.Factory dataSourceFactory =
                new OkHttpDataSource.Factory(okHttpClient);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingProgress.setVisibility(View.VISIBLE);
                } else {
                    bufferingProgress.setVisibility(View.GONE);
                }
                updatePlayPauseButton();
                updateTimeline();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
                if (isPlaying) {
                    scheduleControlsHide();
                } else {
                    showControls();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                bufferingProgress.setVisibility(View.GONE);
                Toast.makeText(
                        MainActivity.this,
                        "Playback Error: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                updatePipParams();
            }
        });

        progressHandler.post(progressRunnable);
    }

    private AudioProcessor createSurroundDownmixProcessor() {
        ChannelMixingMatrix stereo51 = new ChannelMixingMatrix(
                6,
                2,
                new float[]{
                        1.0f, 0.0f,
                        0.0f, 1.0f,
                        0.707f, 0.707f,
                        0.5f, 0.5f,
                        0.707f, 0.0f,
                        0.0f, 0.707f
                }
        );

        ChannelMixingMatrix stereo71 = new ChannelMixingMatrix(
                8,
                2,
                new float[]{
                        1.0f, 0.0f,
                        0.0f, 1.0f,
                        0.707f, 0.707f,
                        0.5f, 0.5f,
                        0.707f, 0.0f,
                        0.0f, 0.707f,
                        0.5f, 0.0f,
                        0.0f, 0.5f
                }
        );

        ChannelMixingAudioProcessor processor = new ChannelMixingAudioProcessor();
        processor.putChannelMixingMatrix(stereo51);
        processor.putChannelMixingMatrix(stereo71);
        return processor;
    }

    public void playStream(
            String streamUrl,
            String drmType,
            String licenseUrl,
            String keyIdHex,
            String keyHex,
            Map<String, String> headers,
            boolean isLive
    ) {

        if (player == null || streamUrl == null || streamUrl.trim().isEmpty()) return;

        urlInput.setText(streamUrl);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        OkHttpDataSource.Factory dataSourceFactory =
                new OkHttpDataSource.Factory(clientBuilder.build());

        if (headers != null) {
            dataSourceFactory.setDefaultRequestProperties(headers);
        }

        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(Uri.parse(streamUrl));

        if (drmType != null) {
            setupDrm(mediaItemBuilder, drmType, licenseUrl, keyIdHex, keyHex, headers);
        }

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(dataSourceFactory);

        player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItemBuilder.build()));
        player.prepare();
        player.play();

        updatePlayPauseButton();
        showControls();
    }

    private void setupDrm(
            MediaItem.Builder mediaItemBuilder,
            String drmType,
            String licenseUrl,
            String keyIdHex,
            String keyHex,
            Map<String, String> headers
    ) {

        if ("clearkey".equalsIgnoreCase(drmType)) {

            if (keyIdHex != null && keyHex != null) {

                String response = "{\"keys\":[{"
                        + "\"kty\":\"oct\","
                        + "\"k\":\"" + base64Url(hexToBytes(keyHex)) + "\","
                        + "\"kid\":\"" + base64Url(hexToBytes(keyIdHex)) + "\""
                        + "}],\"type\":\"temporary\"}";

                LocalMediaDrmCallback drmCallback =
                        new LocalMediaDrmCallback(response.getBytes());

                DrmSessionManager drmSessionManager =
                        new DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                        CLEARKEY_UUID,
                                        FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(drmCallback);

                DefaultDrmSessionManagerProvider provider =
                        new DefaultDrmSessionManagerProvider();

                provider.setDrmSessionManager(drmSessionManager);

                mediaItemBuilder.setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(CLEARKEY_UUID).build()
                );

            } else if (licenseUrl != null) {

                MediaItem.DrmConfiguration.Builder drmBuilder =
                        new MediaItem.DrmConfiguration.Builder(CLEARKEY_UUID)
                                .setLicenseUri(licenseUrl);

                if (headers != null) {
                    drmBuilder.setLicenseRequestHeaders(headers);
                }

                mediaItemBuilder.setDrmConfiguration(drmBuilder.build());
            }

        } else if ("widevine".equalsIgnoreCase(drmType) && licenseUrl != null) {

            MediaItem.DrmConfiguration.Builder drmBuilder =
                    new MediaItem.DrmConfiguration.Builder(WIDEVINE_UUID)
                            .setLicenseUri(licenseUrl);

            if (headers != null) {
                drmBuilder.setLicenseRequestHeaders(headers);
            }

            mediaItemBuilder.setDrmConfiguration(drmBuilder.build());
        }
    }

    private String base64Url(byte[] data) {
        return android.util.Base64.encodeToString(
                data,
                android.util.Base64.URL_SAFE
                        | android.util.Base64.NO_PADDING
                        | android.util.Base64.NO_WRAP
        );
    }

    private byte[] hexToBytes(String hex) {

        String clean = hex.replaceAll("[^0-9a-fA-F]", "");

        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal key");
        }

        byte[] result = new byte[clean.length() / 2];

        for (int i = 0; i < result.length; i++) {

            int value = Integer.parseInt(
                    clean.substring(i * 2, i * 2 + 2),
                    16
            );

            result[i] = (byte) value;
        }

        return result;
    }

    // ==========================================
    // AUDIO / SUBTITLE / VIDEO TRACK SELECTION
    // ==========================================

    private void showTrackSelection(
            int trackType,
            String title
    ) {

        if (player == null) return;

        new TrackSelectionDialogBuilder(
                this,
                title,
                player,
                trackType
        )
                .setShowDisableOption(true)
                .setTrackNameProvider(
                        this::getTrackName
                )
                .build()
                .show();
    }

    private String getTrackName(Format format) {
        if (format == null) return "Unknown";

        // 1. VIDEO TRACK
        if (format.height > 0) {
            StringBuilder name = new StringBuilder();
            String resolution = format.height + "p";
            if (format.height >= 2160) resolution += " (4K UHD)";
            else if (format.height >= 1080) resolution += " (FHD)";
            else if (format.height >= 720) resolution += " (HD)";
            else if (format.height >= 480) resolution += " (SD)";
            name.append(resolution);
            if (format.frameRate > 0) name.append(" • ").append(Math.round(format.frameRate)).append(" fps");
            if (format.bitrate > 0) name.append(" • ").append(Math.round(format.bitrate / 1000f)).append(" kbps");
            return name.toString();
        }

        String mime = format.sampleMimeType != null ? format.sampleMimeType.toLowerCase(Locale.US) : "";
        boolean isAudio = format.channelCount > 0 || mime.startsWith("audio/");
        boolean isText = mime.startsWith("text/") || mime.startsWith("application/");

        // 2. RESOLVE LANGUAGE (Prioritize format.language, then extract from label if missing)
        String lang = resolveLanguageName(format.language);
        if (lang == null) {
            lang = extractLanguageFromLabel(format.label);
        }

        // Clean label if not spam and not duplicating language
        String cleanLabel = null;
        if (format.label != null && !isSpamLabel(format.label)) {
            String trimmed = format.label.trim();
            if (lang == null || !trimmed.equalsIgnoreCase(lang)) {
                cleanLabel = trimmed;
            }
        }

        // 3. AUDIO TRACK (e.g. "Hindi, 5.1 surround sound", "Bangla, 5.1 surround sound", "English, Stereo")
        if (isAudio) {
            String mainTitle = lang != null ? lang : (cleanLabel != null ? cleanLabel : "Audio");

            String channelDesc = "";
            if (format.channelCount == 6) {
                channelDesc = "5.1 surround sound";
            } else if (format.channelCount == 8) {
                channelDesc = "7.1 surround sound";
            } else if (format.channelCount == 2) {
                channelDesc = "Stereo";
            } else if (format.channelCount == 1) {
                channelDesc = "Mono";
            } else if (format.channelCount > 0) {
                channelDesc = format.channelCount + "ch surround sound";
            }

            StringBuilder audioName = new StringBuilder(mainTitle);
            if (!channelDesc.isEmpty()) {
                audioName.append(", ").append(channelDesc);
            }
            if (cleanLabel != null && lang != null && !cleanLabel.equalsIgnoreCase(lang)) {
                audioName.append(" (").append(cleanLabel).append(")");
            }
            return audioName.toString();
        }

        // 4. SUBTITLE / TEXT TRACK (e.g. "Bangla", "English [SDH]", "Hindi [Forced]")
        if (isText || (!isAudio && format.height <= 0)) {
            String mainTitle = lang != null ? lang : (cleanLabel != null ? cleanLabel : "Subtitle");
            StringBuilder subName = new StringBuilder(mainTitle);

            if ((format.roleFlags & C.ROLE_FLAG_FORCED) != 0 || (format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0) {
                subName.append(" [Forced]");
            } else if ((format.roleFlags & C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) {
                subName.append(" [SDH]");
            } else if ((format.roleFlags & C.ROLE_FLAG_COMMENTARY) != 0) {
                subName.append(" [Commentary]");
            }

            if (cleanLabel != null && lang != null && !cleanLabel.equalsIgnoreCase(lang)) {
                subName.append(" (").append(cleanLabel).append(")");
            }
            return subName.toString();
        }

        // Fallback for any other track
        if (lang != null) return lang;
        if (cleanLabel != null) return cleanLabel;
        return "Track";
    }

    private String resolveLanguageName(String langCode) {
        if (langCode == null || langCode.trim().isEmpty() || "und".equalsIgnoreCase(langCode.trim())) {
            return null;
        }
        String code = langCode.trim().toLowerCase(Locale.US);
        switch (code) {
            case "bn":
            case "ben":
            case "beng":
                return "Bangla";
            case "hi":
            case "hin":
                return "Hindi";
            case "en":
            case "eng":
                return "English";
            case "ur":
            case "urd":
                return "Urdu";
            case "ta":
            case "tam":
                return "Tamil";
            case "te":
            case "tel":
                return "Telugu";
            case "ml":
            case "mal":
                return "Malayalam";
            case "kn":
            case "kan":
                return "Kannada";
            case "mr":
            case "mar":
                return "Marathi";
            case "pa":
            case "pan":
                return "Punjabi";
            case "gu":
            case "guj":
                return "Gujarati";
            case "ar":
            case "ara":
                return "Arabic";
            case "es":
            case "spa":
                return "Spanish";
            case "fr":
            case "fra":
            case "fre":
                return "French";
            case "de":
            case "deu":
            case "ger":
                return "German";
            case "ja":
            case "jpn":
                return "Japanese";
            case "ko":
            case "kor":
                return "Korean";
            case "zh":
            case "chi":
            case "zho":
                return "Chinese";
            case "ru":
            case "rus":
                return "Russian";
            case "pt":
            case "por":
                return "Portuguese";
            case "it":
            case "ita":
                return "Italian";
            case "tr":
            case "tur":
                return "Turkish";
            case "id":
            case "ind":
                return "Indonesian";
            case "th":
            case "tha":
                return "Thai";
            case "vi":
            case "vie":
                return "Vietnamese";
            default:
                try {
                    Locale locale = new Locale(code);
                    String name = locale.getDisplayLanguage(Locale.ENGLISH);
                    if (name != null && !name.trim().isEmpty() && !name.equalsIgnoreCase(code)) {
                        return name;
                    }
                } catch (Exception ignored) {}
                if (code.length() > 0) {
                    return code.substring(0, 1).toUpperCase(Locale.US) + code.substring(1);
                }
                return code.toUpperCase(Locale.US);
        }
    }

    private boolean isSpamLabel(String label) {
        if (label == null || label.trim().isEmpty()) return true;
        String lower = label.toLowerCase(Locale.US);
        return lower.contains("encoder") || lower.contains("encode") ||
                lower.contains("joya9") || lower.contains("torrent") ||
                lower.contains(".tv") || lower.contains(".com") || lower.contains(".org") ||
                lower.contains(".net") || lower.contains(".me") || lower.contains(".to") ||
                lower.contains(".dev") || lower.contains(".in") ||
                lower.contains("www.") || lower.contains("http") || lower.contains("ripped") ||
                lower.contains("upload") || lower.contains("release") || lower.contains("psa") ||
                lower.contains("yify") || lower.contains("qxr") || lower.contains("tgx") ||
                lower.contains("dvdrip") || lower.contains("webrip") || lower.contains("hdrip");
    }

    private String extractLanguageFromLabel(String label) {
        if (label == null || label.trim().isEmpty()) return null;
        String lower = label.toLowerCase(Locale.US);
        if (lower.contains("bangla") || lower.contains("bengali")) return "Bangla";
        if (lower.contains("hindi")) return "Hindi";
        if (lower.contains("english") || lower.contains("eng")) return "English";
        if (lower.contains("tamil")) return "Tamil";
        if (lower.contains("telugu")) return "Telugu";
        if (lower.contains("malayalam")) return "Malayalam";
        if (lower.contains("kannada")) return "Kannada";
        if (lower.contains("marathi")) return "Marathi";
        if (lower.contains("urdu")) return "Urdu";
        if (lower.contains("punjabi")) return "Punjabi";
        if (lower.contains("gujarati")) return "Gujarati";
        if (lower.contains("arabic")) return "Arabic";
        if (lower.contains("spanish")) return "Spanish";
        if (lower.contains("french")) return "French";
        if (lower.contains("german")) return "German";
        if (lower.contains("japanese")) return "Japanese";
        if (lower.contains("korean")) return "Korean";
        if (lower.contains("chinese")) return "Chinese";
        if (lower.contains("russian")) return "Russian";
        return null;
    }

    // ==========================================
    // PLAYBACK SPEED
    // ==========================================

    private void showSpeedDialog() {

        if (player == null) return;

        String[] speeds = {
                "0.25x",
                "0.5x",
                "0.75x",
                "1.0x",
                "1.25x",
                "1.5x",
                "1.75x",
                "2.0x"
        };

        float[] values = {
                0.25f,
                0.5f,
                0.75f,
                1.0f,
                1.25f,
                1.5f,
                1.75f,
                2.0f
        };

        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(
                        speeds,
                        (dialog, which) ->
                                player.setPlaybackSpeed(values[which])
                )
                .show();
    }

    // ==========================================
    // RESIZE & FULLSCREEN
    // ==========================================

    private void cycleResizeMode() {
        if (playerView == null) return;
        switch (currentResizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                Toast.makeText(this, "Resize: Fill", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                Toast.makeText(this, "Resize: Zoom", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            default:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                Toast.makeText(this, "Resize: Fit", Toast.LENGTH_SHORT).show();
                break;
        }
        playerView.setResizeMode(currentResizeMode);
    }

    private void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    private void setFullscreen(boolean enable) {
        if (fullscreen == enable) return;
        fullscreen = enable;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (enable) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

            pageContent.removeView(playerContainer);
            rootContainer.addView(playerContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            pageScrollView.setVisibility(View.GONE);

            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

            rootContainer.removeView(playerContainer);
            int playerHeight = (int) (getResources().getDisplayMetrics().widthPixels * (9f / 16f));
            LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    playerHeight
            );
            pageContent.addView(playerContainer, 1, playerLp);
            pageScrollView.setVisibility(View.VISIBLE);

            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        updateFullscreenButton();
    }

    private void updateFullscreenButton() {
        if (fullscreenButton != null) {
            fullscreenButton.setText(fullscreen ? "⤡" : "⤢");
            fullscreenButton.setContentDescription(fullscreen ? "Exit fullscreen" : "Enter fullscreen");
        }
    }

    private void showPlayerSettings() {
        String[] options = {"Audio tracks", "Subtitle tracks", "Video tracks", "Resize mode", "Playback speed"};
        new AlertDialog.Builder(this)
                .setTitle("Player settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showTrackSelection(C.TRACK_TYPE_AUDIO, "Audio Tracks"); break;
                        case 1: showTrackSelection(C.TRACK_TYPE_TEXT, "Subtitle Tracks"); break;
                        case 2: showTrackSelection(C.TRACK_TYPE_VIDEO, "Video Tracks"); break;
                        case 3: cycleResizeMode(); break;
                        case 4: showSpeedDialog(); break;
                    }
                }).show();
    }

    // ==========================================
    // PICTURE IN PICTURE (VIDEO LAYOUT ONLY)
    // ==========================================

    private boolean isInPipMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            int width = 16;
            int height = 9;
            if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                width = player.getVideoSize().width;
                height = player.getVideoSize().height;
            }

            Rational aspect = new Rational(Math.max(1, width), Math.max(1, height));
            float f = aspect.floatValue();
            if (f < 0.418410f) aspect = new Rational(418, 1000);
            if (f > 2.390000f) aspect = new Rational(2390, 1000);

            Rect sourceRect = new Rect();
            playerContainer.getGlobalVisibleRect(sourceRect);

            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspect);

            if (!sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }

            setPictureInPictureParams(builder.build());
        } catch (Exception ignored) {}
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePipParams();
            try {
                hideControls();
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            } catch (Exception e) {
                Toast.makeText(this, "Cannot enter PiP mode", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && player.isPlaying() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideControls();
            pageScrollView.setVisibility(View.GONE);
            if (playerContainer.getParent() == pageContent) {
                pageContent.removeView(playerContainer);
                rootContainer.addView(playerContainer, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
        } else {
            if (!fullscreen) {
                if (playerContainer.getParent() == rootContainer) {
                    rootContainer.removeView(playerContainer);
                    int playerHeight = (int) (getResources().getDisplayMetrics().widthPixels * (9f / 16f));
                    LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            playerHeight
                    );
                    pageContent.addView(playerContainer, 1, playerLp);
                }
                pageScrollView.setVisibility(View.VISIBLE);
            }
            showControls();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isInPipMode()) return;
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!fullscreen) setFullscreen(true);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (fullscreen) setFullscreen(false);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen && !isInPipMode()) {
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    // ==========================================
    // MULTI-LINK & EPISODES BUTTONS
    // ==========================================

    private void handleStreamOrPlaylistPayload(String rawPayload) {
        if (rawPayload == null) return;
        String payload = rawPayload.trim();

        if (payload.startsWith("Playlist>")) {
            String listContent = payload.substring("Playlist>".length()).trim();
            List<EpisodeItem> episodes = parsePlaylist(listContent);
            if (!episodes.isEmpty()) {
                populateEpisodeButtons(episodes);
                return;
            }
        }

        if (payload.contains(",)") && payload.contains(",,")) {
            List<EpisodeItem> episodes = parsePlaylist(payload);
            if (!episodes.isEmpty()) {
                populateEpisodeButtons(episodes);
                return;
            }
        }

        hideEpisodesBar();
        urlInput.setText(payload);
    }

    private List<EpisodeItem> parsePlaylist(String raw) {
        List<EpisodeItem> list = new ArrayList<>();
        String[] items = raw.split(",\\)");
        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            int separatorIdx = trimmed.indexOf(",,");
            if (separatorIdx != -1) {
                String title = trimmed.substring(0, separatorIdx).trim();
                String url = trimmed.substring(separatorIdx + 2).trim();
                if (!url.isEmpty()) {
                    list.add(new EpisodeItem(title.isEmpty() ? "Item " + (list.size() + 1) : title, url));
                }
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                list.add(new EpisodeItem("Item " + (list.size() + 1), trimmed));
            }
        }
        return list;
    }

    private void populateEpisodeButtons(List<EpisodeItem> episodes) {
        episodesContainer.removeAllViews();
        episodeButtons.clear();
        selectedEpisodeIndex = -1;

        if (episodes.isEmpty()) {
            hideEpisodesBar();
            return;
        }

        for (int i = 0; i < episodes.size(); i++) {
            final int index = i;
            final EpisodeItem ep = episodes.get(i);

            Button epBtn = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(36)
            );
            lp.setMarginEnd(dp(8));
            epBtn.setLayoutParams(lp);
            epBtn.setText(ep.title);
            epBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            epBtn.setTypeface(Typeface.DEFAULT_BOLD);
            epBtn.setPadding(dp(14), 0, dp(14), 0);

            styleEpisodeButton(epBtn, false);

            epBtn.setOnClickListener(v -> selectEpisode(index, episodes));

            episodesContainer.addView(epBtn);
            episodeButtons.add(epBtn);
        }

        episodesScroll.setVisibility(View.VISIBLE);
        selectEpisode(0, episodes);
    }

    private void selectEpisode(int index, List<EpisodeItem> episodes) {
        if (index < 0 || index >= episodes.size()) return;
        selectedEpisodeIndex = index;

        for (int i = 0; i < episodeButtons.size(); i++) {
            styleEpisodeButton(episodeButtons.get(i), i == index);
        }

        EpisodeItem selected = episodes.get(index);
        urlInput.setText(selected.url);

        View targetBtn = episodeButtons.get(index);
        episodesScroll.smoothScrollTo(targetBtn.getLeft() - dp(16), 0);
    }

    private void styleEpisodeButton(Button btn, boolean selected) {
        if (selected) {
            btn.setTextColor(Color.WHITE);
            btn.setBackground(createRoundedDrawable(
                    Color.parseColor("#2563EB"),
                    dp(18),
                    Color.parseColor("#3B82F6"),
                    dp(1)
            ));
        } else {
            btn.setTextColor(Color.parseColor("#CBD5E1"));
            btn.setBackground(createRoundedDrawable(
                    Color.parseColor("#1E293B"),
                    dp(18),
                    Color.parseColor("#334155"),
                    dp(1)
            ));
        }
    }

    private void hideEpisodesBar() {
        episodesScroll.setVisibility(View.GONE);
        episodesContainer.removeAllViews();
        episodeButtons.clear();
        selectedEpisodeIndex = -1;
    }

    // ==========================================
    // WEBVIEW GITHUB LOADER & BRIDGE
    // ==========================================

    private void loadGitHubHtml() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(GITHUB_HTML_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder htmlBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    htmlBuilder.append(line).append("\n");
                }
                reader.close();

                String rawHtml = htmlBuilder.toString();
                String patchedHtml = patchHtmlForViewportAndBridge(rawHtml);

                new Handler(Looper.getMainLooper()).post(() -> {
                    webView.loadDataWithBaseURL("https://github.com", patchedHtml, "text/html", "UTF-8", null);
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(MainActivity.this, "Failed to load HTML from GitHub", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String patchHtmlForViewportAndBridge(String html) {
        String bridgeHook = "<script>\n" +
                "(function(){\n" +
                "  function sendToAndroid(val){\n" +
                "    if(window.AndroidBridge && window.AndroidBridge.postMessage) window.AndroidBridge.postMessage(val);\n" +
                "    if(window.CustomWebView && window.CustomWebView.setWebViewString) window.CustomWebView.setWebViewString(val);\n" +
                "  }\n" +
                "  window.sendData = function(url){ sendToAndroid(url); };\n" +
                "  window.setWebViewString = function(url){ sendToAndroid(url); };\n" +
                "})();\n" +
                "</script>\n";

        String metaTag = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">";
        String styleZeroMargin = "<style>html, body { margin: 0; padding: 0; width: 100%; box-sizing: border-box; } * { box-sizing: border-box; }</style>";

        if (html.contains("<head>")) {
            return html.replace("<head>", "<head>\n" + metaTag + "\n" + styleZeroMargin + "\n" + bridgeHook);
        } else {
            return metaTag + styleZeroMargin + bridgeHook + html;
        }
    }

    private void injectBridgeShim() {
        String js = "javascript:(function(){\n" +
                "  function forward(v){\n" +
                "    if(window.AndroidBridge && window.AndroidBridge.postMessage) window.AndroidBridge.postMessage(v);\n" +
                "    if(window.CustomWebView && window.CustomWebView.setWebViewString) window.CustomWebView.setWebViewString(v);\n" +
                "  }\n" +
                "  var oldSendData = window.sendData;\n" +
                "  window.sendData = function(val){\n" +
                "    forward(val);\n" +
                "    if(typeof oldSendData === 'function') { try{ oldSendData(val); }catch(e){} }\n" +
                "  };\n" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void setWebViewString(String value) {
            onBridgeValueReceived(value);
        }

        @JavascriptInterface
        public void postMessage(String value) {
            onBridgeValueReceived(value);
        }

        @JavascriptInterface
        public void sendData(String value) {
            onBridgeValueReceived(value);
        }
    }

    private void onBridgeValueReceived(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            handleStreamOrPlaylistPayload(rawValue);
        });
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            setFullscreen(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !isInPipMode()) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isInPipMode()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacks(progressRunnable);
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
