package com.raselahomed467.advancedmedia3player;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;

    private LinearLayout rootLayout;
    private TextView titleBar;
    private ScrollView inputScroll;

    private FrameLayout playerContainer;

    // Custom Controls Elements
    private FrameLayout customControlsOverlay;
    private ProgressBar bufferingProgress;
    private TextView btnPlayPause;
    private TextView timeCurrent;
    private TextView timeDuration;
    private SeekBar customSeekBar;

    private EditText urlInput;
    private TextView statusText;

    private boolean fullscreen = false;
    private int resizeMode = 0;
    private boolean isControlsVisible = true;
    private boolean isUserSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::hideControls;

    // Progress bar updater
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && !isUserSeeking) {
                long current = player.getCurrentPosition();
                long duration = player.getDuration();

                if (duration > 0) {
                    customSeekBar.setMax((int) duration);
                    customSeekBar.setProgress((int) current);
                    timeDuration.setText(formatTime(duration));
                } else {
                    timeDuration.setText("LIVE");
                }
                timeCurrent.setText(formatTime(current));

                if (player.isPlaying()) {
                    btnPlayPause.setText("❚❚");
                } else {
                    btnPlayPause.setText("▶");
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    private final int BG_COLOR = Color.rgb(9, 13, 22);
    private final int BUTTON_COLOR = Color.rgb(35, 48, 67);
    private final int PRIMARY_COLOR = Color.rgb(0, 126, 220);

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG_COLOR);
        getWindow().setNavigationBarColor(BG_COLOR);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        buildScreen();
        initializePlayer();

        handler.post(updateProgressRunnable);

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (fullscreen) {
                            toggleFullscreen();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    // ==========================================
    // BUILD USER INTERFACE
    // ==========================================

    private void buildScreen() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG_COLOR);

        titleBar = new TextView(this);
        titleBar.setText("ADVANCED MEDIA3 PLAYER");
        titleBar.setTextColor(Color.WHITE);
        titleBar.setTextSize(18);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        titleBar.setTypeface(null, android.graphics.Typeface.BOLD);

        rootLayout.addView(
                titleBar,
                new LinearLayout.LayoutParams(-1, dp(52))
        );

        // Player Container
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        // Disable ExoPlayer's built-in default controller completely
        playerView.setUseController(false);

        playerView.setResizeMode(
                AspectRatioFrameLayout.RESIZE_MODE_FIT
        );
        playerView.setBackgroundColor(Color.BLACK);

        playerContainer.addView(
                playerView,
                new FrameLayout.LayoutParams(-1, -1)
        );

        // Buffering Indicator in center
        bufferingProgress = new ProgressBar(this);
        bufferingProgress.setIndeterminate(true);
        bufferingProgress.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bufferingProgress.setIndeterminateTintList(
                    ColorStateList.valueOf(Color.rgb(85, 194, 255))
            );
        }

        FrameLayout.LayoutParams bufferParams = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.CENTER
        );
        playerContainer.addView(bufferingProgress, bufferParams);

        // Build Custom Controller Overlay
        buildCustomControllerOverlay();

        // Clicking anywhere on playerView toggles controller visibility
        playerView.setOnClickListener(v -> toggleControls());

        rootLayout.addView(
                playerContainer,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        0.30f
                )
        );

        // Input section
        inputScroll = new ScrollView(this);
        inputScroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(20)
        );

        inputScroll.addView(content);

        rootLayout.addView(
                inputScroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        0.70f
                )
        );

        content.addView(
                sectionTitle("STREAM / DRM / REQUEST SETTINGS")
        );

        urlInput = addField(
                content,
                "Video URL | Referer=...&Cookie=...&drmScheme=...",
                true
        );

        urlInput.setMinLines(3);
        urlInput.setTextSize(13);

        // Play Button
        TextView playButton = makeActionButton(
                "▶   PLAY STREAM",
                true
        );

        playButton.setOnClickListener(v -> playStream());

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(-1, dp(52));

        playParams.topMargin = dp(8);
        playParams.bottomMargin = dp(8);

        content.addView(playButton, playParams);

        // Status
        statusText = new TextView(this);
        statusText.setText("Ready - Enter a stream URL");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(12);
        statusText.setPadding(dp(4), dp(12), dp(4), dp(4));

        content.addView(statusText);

        setContentView(rootLayout);
    }

    // ==========================================
    // CUSTOM CONTROLLER OVERLAY & TIMELINE
    // ==========================================

    private void buildCustomControllerOverlay() {
        customControlsOverlay = new FrameLayout(this);
        customControlsOverlay.setLayoutParams(
                new FrameLayout.LayoutParams(-1, -1)
        );

        // Clicking empty space on the overlay toggles hide/show
        customControlsOverlay.setOnClickListener(v -> toggleControls());

        // Bottom panel container with smooth dark gradient
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable shadowGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(130, 0, 0, 0), Color.argb(220, 0, 0, 0)}
        );
        bottomPanel.setBackground(shadowGradient);
        bottomPanel.setPadding(dp(14), dp(16), dp(14), dp(10));
        bottomPanel.setClickable(true);
        bottomPanel.setOnClickListener(v -> resetAutoHide());

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM
        );
        customControlsOverlay.addView(bottomPanel, bottomParams);

        // ------------------------------------------
        // ROW 1: BUTTONS ROW (As shown in screenshot)
        // ------------------------------------------
        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER);
        buttonsRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        // 1. Aspect Ratio / Fit Toggle
        addControllerButton(buttonsRow, "⛶", "Aspect Ratio", this::cycleResizeMode);

        // 2. Audio Tracks
        addControllerButton(buttonsRow, "♫", "Audio Track", () ->
                showTrackSelection(C.TRACK_TYPE_AUDIO, "Audio Tracks")
        );

        // 3. PiP
        addControllerButton(buttonsRow, "⧉", "PiP", this::enterPictureInPicture);

        // 4. Rewind 10s
        addControllerButton(buttonsRow, "◀◀", "Rewind 10s", () -> {
            if (player != null) {
                long pos = Math.max(0, player.getCurrentPosition() - 10000);
                player.seekTo(pos);
                updateSeekViews(pos);
                resetAutoHide();
            }
        });

        // 5. Play / Pause Button (slightly larger)
        btnPlayPause = addControllerButton(buttonsRow, "▶", "Play/Pause", () -> {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                    btnPlayPause.setText("▶");
                } else {
                    player.play();
                    btnPlayPause.setText("❚❚");
                }
                resetAutoHide();
            }
        });
        btnPlayPause.setTextSize(20);

        // 6. Forward 10s
        addControllerButton(buttonsRow, "▶▶", "Forward 10s", () -> {
            if (player != null) {
                long dur = player.getDuration();
                long pos = player.getCurrentPosition() + 10000;
                if (dur > 0 && pos > dur) pos = dur;
                player.seekTo(pos);
                updateSeekViews(pos);
                resetAutoHide();
            }
        });

        // 7. Subtitles
        addControllerButton(buttonsRow, "CC", "Subtitles", () ->
                showTrackSelection(C.TRACK_TYPE_TEXT, "Subtitle Tracks")
        );

        // 8. Settings Menu (Quality / Tracks / Speeds)
        addControllerButton(buttonsRow, "⚙", "Settings", this::showSettingsDialog);

        // 9. Fullscreen Toggle
        addControllerButton(buttonsRow, "⊞", "Fullscreen", this::toggleFullscreen);

        bottomPanel.addView(buttonsRow);

        // ------------------------------------------
        // ROW 2: TIMELINE / PROGRESS BAR ROW
        // ------------------------------------------
        LinearLayout timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        timelineRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timelineParams =
                new LinearLayout.LayoutParams(-1, -2);
        timelineParams.topMargin = dp(6);
        timelineRow.setLayoutParams(timelineParams);

        // Current Time TextView
        timeCurrent = new TextView(this);
        timeCurrent.setText("00:00");
        timeCurrent.setTextColor(Color.WHITE);
        timeCurrent.setTextSize(13);
        timeCurrent.setTypeface(null, android.graphics.Typeface.BOLD);
        timeCurrent.setPadding(0, 0, dp(4), 0);
        timelineRow.addView(timeCurrent);

        // Custom SeekBar
        customSeekBar = new SeekBar(this);
        customSeekBar.setMax(1000);
        customSeekBar.setPadding(dp(8), dp(4), dp(8), dp(4));

        // White circular thumb
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setSize(dp(14), dp(14));
        customSeekBar.setThumb(thumb);
        customSeekBar.setThumbOffset(dp(7));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            customSeekBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
            customSeekBar.setProgressBackgroundTintList(
                    ColorStateList.valueOf(Color.argb(90, 255, 255, 255))
            );
            customSeekBar.setSecondaryProgressTintList(
                    ColorStateList.valueOf(Color.argb(140, 255, 255, 255))
            );
        }

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(0, -2, 1.0f);
        seekParams.leftMargin = dp(4);
        seekParams.rightMargin = dp(4);
        timelineRow.addView(customSeekBar, seekParams);

        customSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    timeCurrent.setText(formatTime(progress));
                    resetAutoHide();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                handler.removeCallbacks(autoHideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    player.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
                resetAutoHide();
            }
        });

        // Duration TextView
        timeDuration = new TextView(this);
        timeDuration.setText("00:00");
        timeDuration.setTextColor(Color.WHITE);
        timeDuration.setTextSize(13);
        timeDuration.setTypeface(null, android.graphics.Typeface.BOLD);
        timeDuration.setPadding(dp(4), 0, 0, 0);
        timelineRow.addView(timeDuration);

        bottomPanel.addView(timelineRow);

        playerContainer.addView(customControlsOverlay);
        resetAutoHide();
    }

    private TextView addControllerButton(
            LinearLayout parent,
            String symbol,
            String description,
            Runnable action
    ) {
        TextView button = new TextView(this);
        button.setText(symbol);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(160, 32, 44, 60));
        background.setCornerRadius(dp(8));
        button.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(38), dp(36));
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);

        parent.addView(button, params);

        button.setOnClickListener(v -> {
            action.run();
            resetAutoHide();
        });

        return button;
    }

    private void updateSeekViews(long pos) {
        if (customSeekBar != null) {
            customSeekBar.setProgress((int) pos);
        }
        if (timeCurrent != null) {
            timeCurrent.setText(formatTime(pos));
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private void toggleControls() {
        if (isInPipMode()) return;

        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        if (customControlsOverlay != null) {
            customControlsOverlay.setVisibility(View.VISIBLE);
            isControlsVisible = true;
            resetAutoHide();
        }
    }

    private void hideControls() {
        if (isUserSeeking || isInPipMode()) return;

        if (customControlsOverlay != null) {
            customControlsOverlay.setVisibility(View.GONE);
            isControlsVisible = false;
        }
    }

    private void resetAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
        if (isControlsVisible) {
            handler.postDelayed(autoHideRunnable, 4000);
        }
    }

    private void showSettingsDialog() {
        if (player == null) return;

        String[] options = {
                "Video Quality / Tracks",
                "Audio Tracks",
                "Subtitle Tracks",
                "Playback Speed",
                "Aspect Ratio (Fit / Fill / Zoom)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showTrackSelection(C.TRACK_TYPE_VIDEO, "Video Quality");
                            break;
                        case 1:
                            showTrackSelection(C.TRACK_TYPE_AUDIO, "Audio Tracks");
                            break;
                        case 2:
                            showTrackSelection(C.TRACK_TYPE_TEXT, "Subtitle Tracks");
                            break;
                        case 3:
                            showSpeedDialog();
                            break;
                        case 4:
                            cycleResizeMode();
                            break;
                    }
                })
                .show();
    }

    private TextView makeActionButton(
            String text,
            boolean primary
    ) {

        TextView button = new TextView(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(primary ? 15 : 12);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable background = new GradientDrawable();
        background.setColor(
                primary ? PRIMARY_COLOR : BUTTON_COLOR
        );
        background.setCornerRadius(dp(10));

        button.setBackground(background);
        button.setClickable(true);

        return button;
    }

    private TextView sectionTitle(String text) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextColor(Color.rgb(85, 194, 255));
        view.setTextSize(12);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(8));

        return view;
    }

    private EditText addField(
            LinearLayout parent,
            String hint,
            boolean multiline
    ) {

        EditText field = new EditText(this);

        field.setSingleLine(!multiline);
        field.setHint(hint);

        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.GRAY);
        field.setTextSize(14);

        field.setPadding(
                dp(13),
                dp(8),
                dp(13),
                dp(8)
        );

        field.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(75, 95, 120)
                )
        );

        if (multiline) {
            field.setMinLines(2);
            field.setGravity(Gravity.TOP | Gravity.START);
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1,
                        multiline ? dp(100) : dp(54)
                );

        params.bottomMargin = dp(8);

        parent.addView(field, params);

        return field;
    }

    // ==========================================
    // FFmpeg + AUDIO DOWNMIX + EXOPLAYER
    // ==========================================

    private void initializePlayer() {

        try {

            ChannelMixingAudioProcessor downmixProcessor =
                    new ChannelMixingAudioProcessor();

            // Mono to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(1, 2)
            );

            // Stereo to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(2, 2)
            );

            // 3.0 to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(3, 2)
            );

            // 4.0 to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(4, 2)
            );

            // 5.0 to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(5, 2)
            );

            // 5.1 to Stereo
            downmixProcessor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(6, 2)
            );

            // Custom Renderers Factory
            DefaultRenderersFactory renderersFactory =
                    new DefaultRenderersFactory(this) {

                @Override
                protected AudioSink buildAudioSink(
                        android.content.Context context,
                        boolean enableFloatOutput,
                        boolean enableAudioOutputPlaybackParams) {

                    return new DefaultAudioSink.Builder(context)
                            .setAudioProcessors(
                                    new AudioProcessor[]{
                                            downmixProcessor
                                    }
                            )
                            .build();
                }
            };

            // Enable FFmpeg extension renderer
            renderersFactory.setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            );

            // Allow decoder fallback
            renderersFactory.setEnableDecoderFallback(true);

            // Create ExoPlayer
            player = new ExoPlayer.Builder(
                    this,
                    renderersFactory
            ).build();

            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {

                @Override
                public void onPlaybackStateChanged(int state) {

                    if (state == Player.STATE_BUFFERING) {
                        setStatus("Buffering...");
                        if (bufferingProgress != null) {
                            bufferingProgress.setVisibility(View.VISIBLE);
                        }
                    } else if (state == Player.STATE_READY) {
                        setStatus("Playing / Ready");
                        if (bufferingProgress != null) {
                            bufferingProgress.setVisibility(View.GONE);
                        }
                    } else if (state == Player.STATE_ENDED) {
                        setStatus("Playback ended");
                        if (bufferingProgress != null) {
                            bufferingProgress.setVisibility(View.GONE);
                        }
                    } else if (state == Player.STATE_IDLE) {
                        setStatus("Ready");
                        if (bufferingProgress != null) {
                            bufferingProgress.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (btnPlayPause != null) {
                        btnPlayPause.setText(isPlaying ? "❚❚" : "▶");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (bufferingProgress != null) {
                        bufferingProgress.setVisibility(View.GONE);
                    }
                    setStatus(
                            "Playback error: "
                                    + error.getErrorCodeName()
                                    + " - "
                                    + error.getMessage()
                    );
                }
            });

        } catch (Exception e) {
            toast("Player initialization error: " + e.getMessage());
        }
    }

    // ==========================================
    // URL + PARAMETERS
    // ==========================================

    private static class StreamConfig {
        String url = "";
        String drmScheme = "";
        String drmLicense = "";

        Map<String, String> headers = new LinkedHashMap<>();
    }

    private StreamConfig parseStreamInput(String raw) {

        StreamConfig config = new StreamConfig();

        if (raw == null) return config;

        int pipe = raw.indexOf('|');

        if (pipe < 0) {
            config.url = raw.trim();
            return config;
        }

        config.url = raw.substring(0, pipe).trim();

        String parameterText =
                raw.substring(pipe + 1).trim();

        for (String part : parameterText.split("&")) {

            int equals = part.indexOf('=');

            if (equals <= 0) continue;

            String key =
                    part.substring(0, equals)
                            .trim()
                            .toLowerCase(Locale.US);

            String value =
                    part.substring(equals + 1).trim();

            if (value.isEmpty()) continue;

            switch (key) {

                case "drmscheme":
                    config.drmScheme = value.toLowerCase(Locale.US);
                    break;

                case "drmlicense":
                    config.drmLicense = value;
                    break;

                case "cookie":
                    config.headers.put("Cookie", value);
                    break;

                case "referer":
                    config.headers.put("Referer", value);
                    break;

                case "user-agent":
                    config.headers.put("User-Agent", value);
                    break;

                case "origin":
                    config.headers.put("Origin", value);
                    break;

                case "authorization":
                    config.headers.put("Authorization", value);
                    break;
            }
        }

        // Auto-detect DRM type if omitted
        if (config.drmScheme.isEmpty()
                && !config.drmLicense.isEmpty()) {

            if (config.drmLicense.contains(":")
                    && !config.drmLicense.startsWith("http")) {
                config.drmScheme = "clearkey";
            } else {
                config.drmScheme = "widevine";
            }
        }

        return config;
    }

    // ==========================================
    // PLAY STREAM
    // ==========================================

    private void playStream() {

        StreamConfig config =
                parseStreamInput(value(urlInput));

        if (config.url.isEmpty()) {
            toast("প্রথমে Video URL দিন");
            return;
        }

        if (player == null) {
            toast("Player is not ready");
            return;
        }

        try {

            OkHttpClient client =
                    new OkHttpClient.Builder().build();

            OkHttpDataSource.Factory httpFactory =
                    new OkHttpDataSource.Factory(client);

            if (!config.headers.isEmpty()) {
                httpFactory.setDefaultRequestProperties(
                        config.headers
                );
            }

            DefaultMediaSourceFactory sourceFactory =
                    new DefaultMediaSourceFactory(this)
                            .setDataSourceFactory(httpFactory);

            MediaItem mediaItem =
                    buildMediaItem(config);

            // Local ClearKey manager
            if ("clearkey".equals(config.drmScheme)) {

                String keyResponse =
                        createClearKeyResponse(config.drmLicense);

                DrmSessionManager clearKeyManager =
                        new DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                        C.CLEARKEY_UUID,
                                        FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(
                                        new LocalMediaDrmCallback(
                                                keyResponse.getBytes(
                                                        StandardCharsets.UTF_8
                                                )
                                        )
                                );

                sourceFactory.setDrmSessionManagerProvider(
                        mediaItem1 -> clearKeyManager
                );

            } else {

                // Default Widevine DRM manager
                DefaultDrmSessionManagerProvider drmProvider =
                        new DefaultDrmSessionManagerProvider();

                drmProvider.setDrmHttpDataSourceFactory(
                        httpFactory
                );

                sourceFactory.setDrmSessionManagerProvider(
                        drmProvider
                );
            }

            player.setMediaSource(
                    sourceFactory.createMediaSource(mediaItem)
            );

            player.prepare();
            player.play();

            setStatus("Loading stream...");

        } catch (Exception e) {

            setStatus("Could not start: " + e.getMessage());
            toast("Stream error: " + e.getMessage());
        }
    }

    // ==========================================
    // MEDIA ITEM + DRM
    // ==========================================

    private MediaItem buildMediaItem(StreamConfig config) {

        MediaItem.Builder item =
                new MediaItem.Builder()
                        .setUri(Uri.parse(config.url));

        if ("clearkey".equals(config.drmScheme)) {

            item.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(
                            C.CLEARKEY_UUID
                    ).build()
            );

        } else if ("widevine".equals(config.drmScheme)) {

            if (config.drmLicense.isEmpty()) {
                throw new IllegalArgumentException(
                        "Widevine drmLicense URL is missing"
                );
            }

            MediaItem.DrmConfiguration.Builder drm =
                    new MediaItem.DrmConfiguration.Builder(
                            C.WIDEVINE_UUID
                    )
                            .setLicenseUri(config.drmLicense);

            if (!config.headers.isEmpty()) {
                drm.setLicenseRequestHeaders(config.headers);
            }

            item.setDrmConfiguration(drm.build());
        }

        return item.build();
    }

    // ==========================================
    // CLEARKEY JSON RESPONSE
    // ==========================================

    private String createClearKeyResponse(String license) {

        String[] parts = license.split(":", 2);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "ClearKey format must be KID:KEY"
            );
        }

        byte[] kid = hexToBytes(parts[0]);
        byte[] key = hexToBytes(parts[1]);

        if (kid.length != 16 || key.length != 16) {
            throw new IllegalArgumentException(
                    "ClearKey KID and KEY must be 32 hexadecimal characters"
            );
        }

        String encodedKid = Base64.encodeToString(
                kid,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP
        );

        String encodedKey = Base64.encodeToString(
                key,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP
        );

        return "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                + encodedKid
                + "\",\"k\":\""
                + encodedKey
                + "\"}],\"type\":\"temporary\"}";
    }

    private byte[] hexToBytes(String hex) {

        String clean = hex.replace("-", "").trim();

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
                .setTrackNameProvider(
                        this::getTrackName
                )
                .build()
                .show();
    }

    private String getTrackName(Format format) {

        StringBuilder name = new StringBuilder();

        if (format.label != null
                && !format.label.trim().isEmpty()) {

            name.append(format.label.trim());

        } else if (format.language != null
                && !format.language.equals("und")) {

            Locale language =
                    Locale.forLanguageTag(format.language);

            String displayName =
                    language.getDisplayLanguage(Locale.getDefault());

            name.append(
                    displayName.isEmpty()
                            ? format.language
                            : displayName
            );

        } else {
            name.append("Track");
        }

        String mime = format.sampleMimeType;

        if (mime != null) {

            String codec;

            switch (mime) {
                case "audio/ac3":
                    codec = "AC3";
                    break;

                case "audio/eac3":
                    codec = "E-AC3";
                    break;

                case "audio/vnd.dts":
                    codec = "DTS";
                    break;

                case "audio/vnd.dts.hd":
                    codec = "DTS-HD";
                    break;

                case "audio/true-hd":
                    codec = "TrueHD";
                    break;

                case "audio/mp4a-latm":
                    codec = "AAC";
                    break;

                case "audio/opus":
                    codec = "Opus";
                    break;

                case "text/vtt":
                    codec = "WebVTT";
                    break;

                case "application/ttml+xml":
                    codec = "TTML";
                    break;

                default:
                    codec = mime;
                    break;
            }

            name.append(" • ").append(codec);
        }

        if (format.channelCount > 0) {

            String channels;

            switch (format.channelCount) {
                case 1:
                    channels = "Mono";
                    break;

                case 2:
                    channels = "Stereo";
                    break;

                case 6:
                    channels = "5.1";
                    break;

                case 8:
                    channels = "7.1";
                    break;

                default:
                    channels = format.channelCount + "ch";
                    break;
            }

            name.append(" • ").append(channels);
        }

        if (format.width > 0 && format.height > 0) {
            name.append(" • ").append(format.width).append("x").append(format.height);
        }

        return name.toString();
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
                "2.0x",
                "2.5x",
                "3.0x"
        };

        float[] values = {
                0.25f,
                0.5f,
                0.75f,
                1f,
                1.25f,
                1.5f,
                2f,
                2.5f,
                3f
        };

        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds, (dialog, which) ->
                        player.setPlaybackSpeed(values[which])
                )
                .show();
    }

    // ==========================================
    // VIDEO RESIZE
    // ==========================================

    private void cycleResizeMode() {

        if (playerView == null) return;

        resizeMode++;

        if (resizeMode > 2) {
            resizeMode = 0;
        }

        if (resizeMode == 0) {

            playerView.setResizeMode(
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
            );

            toast("Resize: FIT");

        } else if (resizeMode == 1) {

            playerView.setResizeMode(
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
            );

            toast("Resize: FILL");

        } else {

            playerView.setResizeMode(
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            );

            toast("Resize: ZOOM");
        }
    }

    // ==========================================
    // FULLSCREEN
    // ==========================================

    private void toggleFullscreen() {

        fullscreen = !fullscreen;

        if (fullscreen) {

            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            );

            titleBar.setVisibility(View.GONE);
            inputScroll.setVisibility(View.GONE);

            playerContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            -1,
                            0,
                            1f
                    )
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                WindowManager.LayoutParams attrs =
                        getWindow().getAttributes();

                attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

                getWindow().setAttributes(attrs);
            }

            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            int flags =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            getWindow().getDecorView().setSystemUiVisibility(flags);

            setStatus("Fullscreen Mode");

        } else {

            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            );

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE
            );

            getWindow().setStatusBarColor(BG_COLOR);
            getWindow().setNavigationBarColor(BG_COLOR);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                WindowManager.LayoutParams attrs =
                        getWindow().getAttributes();

                attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;

                getWindow().setAttributes(attrs);
            }

            titleBar.setVisibility(View.VISIBLE);
            inputScroll.setVisibility(View.VISIBLE);

            playerContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            -1,
                            0,
                            0.30f
                    )
            );

            setStatus("Normal Mode");
        }
    }

    // ==========================================
    // PICTURE IN PICTURE
    // ==========================================

    private boolean isInPipMode() {

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && isInPictureInPictureMode();
    }

    private void enterPictureInPicture() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Picture-in-Picture requires Android 8.0 or newer");
            return;
        }

        if (player == null || player.getMediaItemCount() == 0) {
            toast("প্রথমে ভিডিও চালু করুন");
            return;
        }

        try {

            PictureInPictureParams params =
                    new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9))
                            .build();

            enterPictureInPictureMode(params);

        } catch (Exception e) {

            toast("PiP error: " + e.getMessage());
        }
    }

    @Override
    public void onUserLeaveHint() {

        super.onUserLeaveHint();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && player != null
                && player.isPlaying()) {

            enterPictureInPicture();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode,
            Configuration newConfig
    ) {

        super.onPictureInPictureModeChanged(
                isInPictureInPictureMode,
                newConfig
        );

        if (isInPictureInPictureMode) {

            titleBar.setVisibility(View.GONE);
            inputScroll.setVisibility(View.GONE);

            if (customControlsOverlay != null) {
                customControlsOverlay.setVisibility(View.GONE);
            }

            playerContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            -1,
                            0,
                            1f
                    )
            );

        } else {

            if (!fullscreen) {

                titleBar.setVisibility(View.VISIBLE);
                inputScroll.setVisibility(View.VISIBLE);

                playerContainer.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                -1,
                                0,
                                0.30f
                        )
                );
            }

            if (customControlsOverlay != null) {
                showControls();
            }
        }
    }

    // ==========================================
    // STATUS + TOAST
    // ==========================================

    private void setStatus(String message) {

        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private void toast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String value(EditText edit) {

        return edit == null
                ? ""
                : edit.getText().toString().trim();
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================

    @Override
    protected void onStop() {

        super.onStop();

        if (player != null && !isInPipMode()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(null);

        if (player != null) {

            playerView.setPlayer(null);

            player.release();

            player = null;
        }
    }
}
