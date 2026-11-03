
package com.raselahomed467.advancedmedia3player;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.graphics.Rect;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;

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
import okhttp3.Request;
import okhttp3.Response;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;

    private LinearLayout rootLayout;
    private TextView titleBar;
    private LinearLayout inputScroll;
    private WebView eventWebView;
    private Thread htmlLoadThread;
    private FrameLayout lockOverlay;
    private TextView unlockButton;
    private TextView fullscreenButton;
    private boolean controlsLocked = false;
    private boolean wasFullscreenBeforePip = false;

    private FrameLayout playerContainer;
    private LinearLayout leftControls;
    private LinearLayout rightControls;

    private EditText urlInput;
    private TextView statusText;

    private boolean fullscreen = false;
    private int resizeMode = 0;

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

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (controlsLocked) {
                            setControlsLocked(false);
                        } else if (fullscreen) {
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
        rootLayout.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(52)));

        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setControllerShowTimeoutMs(3500);
        playerView.setControllerHideOnTouch(true);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowSubtitleButton(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setBackgroundColor(Color.BLACK);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        leftControls = new LinearLayout(this);
        leftControls.setOrientation(LinearLayout.VERTICAL);
        leftControls.setGravity(Gravity.CENTER);
        // Audio and subtitle selection are grouped under the gear menu.

        rightControls = new LinearLayout(this);
        rightControls.setOrientation(LinearLayout.VERTICAL);
        rightControls.setGravity(Gravity.CENTER);

        addSideButton(rightControls, "⚙", "Settings", this::showPlayerSettings);

        fullscreenButton = addSideButton(
                rightControls, "⛶", "Enter fullscreen", this::toggleFullscreen
        );
        addSideButton(rightControls, "▣", "Picture in Picture", this::enterPictureInPicture);
        addSideButton(rightControls, "🔒", "Lock controls", () -> setControlsLocked(true));

        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(
                dp(48), -2, Gravity.CENTER_VERTICAL | Gravity.END
        );
        rightParams.rightMargin = dp(5);
        playerContainer.addView(rightControls, rightParams);

        lockOverlay = new FrameLayout(this);
        lockOverlay.setBackgroundColor(Color.TRANSPARENT);
        lockOverlay.setVisibility(View.GONE);
        lockOverlay.setClickable(true);
        lockOverlay.setOnTouchListener((v, event) -> true);
        unlockButton = new TextView(this);
        unlockButton.setText("🔓");
        unlockButton.setTextColor(Color.WHITE);
        unlockButton.setTextSize(22);
        unlockButton.setGravity(Gravity.CENTER);
        unlockButton.setContentDescription("Unlock player controls");
        GradientDrawable unlockBg = new GradientDrawable();
        unlockBg.setColor(Color.argb(210, 35, 48, 67));
        unlockBg.setCornerRadius(dp(14));
        unlockButton.setBackground(unlockBg);
        FrameLayout.LayoutParams unlockParams = new FrameLayout.LayoutParams(
                dp(52), dp(52), Gravity.CENTER_VERTICAL | Gravity.END
        );
        unlockParams.rightMargin = dp(8);
        lockOverlay.addView(unlockButton, unlockParams);
        unlockButton.setOnClickListener(v -> setControlsLocked(false));
        playerContainer.addView(lockOverlay, new FrameLayout.LayoutParams(-1, -1));

        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    if (!controlsLocked && !isInPipMode()) {
                        int sideVisibility = visibility == View.VISIBLE ? View.VISIBLE : View.GONE;
                        leftControls.setVisibility(sideVisibility);
                        rightControls.setVisibility(sideVisibility);
                    }
                }
        );

        rootLayout.addView(playerContainer, new LinearLayout.LayoutParams(-1, 0, 0.30f));

        inputScroll = new LinearLayout(this);
        inputScroll.setOrientation(LinearLayout.VERTICAL);
        inputScroll.setPadding(dp(14), dp(10), dp(14), dp(10));
        inputScroll.setBackgroundColor(BG_COLOR);

        inputScroll.addView(sectionTitle("STREAM / DRM / REQUEST SETTINGS"),
                new LinearLayout.LayoutParams(-1, -2));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        urlInput = new EditText(this);
        urlInput.setSingleLine(false);
        urlInput.setMinLines(2);
        urlInput.setMaxLines(3);
        urlInput.setHint("Video URL | Referer=...&Cookie=...");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.GRAY);
        urlInput.setTextSize(13);
        urlInput.setPadding(dp(12), dp(8), dp(10), dp(8));
        urlInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(75, 95, 120)));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, dp(72), 1f);
        urlParams.rightMargin = dp(8);
        inputRow.addView(urlInput, urlParams);

        TextView playButton = makeActionButton("▶ PLAY", true);
        playButton.setTextSize(13);
        playButton.setOnClickListener(v -> playStream());
        inputRow.addView(playButton, new LinearLayout.LayoutParams(dp(92), dp(52)));
        inputScroll.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Ready - Enter a stream URL or choose an item below");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(11);
        statusText.setPadding(dp(3), dp(5), dp(3), dp(5));
        inputScroll.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        eventWebView = new WebView(this);
        eventWebView.getSettings().setJavaScriptEnabled(true);
        eventWebView.getSettings().setDomStorageEnabled(true);
        eventWebView.getSettings().setLoadsImagesAutomatically(true);
        eventWebView.getSettings().setAllowFileAccess(false);
        eventWebView.getSettings().setAllowContentAccess(false);
        eventWebView.setBackgroundColor(Color.rgb(13, 17, 23));
        eventWebView.setWebChromeClient(new WebChromeClient());
        eventWebView.setWebViewClient(new WebViewClient());
        eventWebView.addJavascriptInterface(new HtmlItemBridge(), "CustomWebView");
        inputScroll.addView(eventWebView, new LinearLayout.LayoutParams(-1, 0, 1f));
        loadEventHtml();

        rootLayout.addView(inputScroll, new LinearLayout.LayoutParams(-1, 0, 0.70f));
        setContentView(rootLayout);
    }

    // ==========================================
    // SIDE CONTROL BUTTONS
    // ==========================================

    private TextView addSideButton(
            LinearLayout parent,
            String symbol,
            String description,
            Runnable action
    ) {

        TextView button = new TextView(this);

        button.setText(symbol);
        button.setTextColor(Color.WHITE);
        button.setTextSize(19);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(185, 35, 48, 67));
        background.setCornerRadius(dp(12));

        button.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(40),
                        dp(40)
                );

        params.topMargin = dp(3);
        params.bottomMargin = dp(3);

        parent.addView(button, params);

        button.setOnClickListener(v -> {
            action.run();
            if (playerView != null && !isInPipMode() && !controlsLocked) {
                playerView.showController();
            }
        });
        return button;
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
                    } else if (state == Player.STATE_READY) {
                        setStatus("Playing / Ready");
                    } else if (state == Player.STATE_ENDED) {
                        setStatus("Playback ended");
                    } else if (state == Player.STATE_IDLE) {
                        setStatus("Ready");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {

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
    // AUDIO / SUBTITLE TRACK SELECTION
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
        if (format.height > 0) {
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

        if (format.label != null && !format.label.trim().isEmpty()) {
            name.append(format.label.trim());
        } else if (format.language != null && !format.language.equalsIgnoreCase("und")) {
            String language = format.language.trim().toLowerCase(Locale.US);
            if (language.length() == 3) {
                if ("eng".equals(language)) language = "en";
                else if ("ben".equals(language) || "beng".equals(language)) language = "bn";
                else if ("hin".equals(language)) language = "hi";
                else if ("urd".equals(language)) language = "ur";
                else if ("ara".equals(language)) language = "ar";
                else if ("spa".equals(language)) language = "es";
                else if ("fra".equals(language)) language = "fr";
                else if ("deu".equals(language) || "ger".equals(language)) language = "de";
                else if ("por".equals(language)) language = "pt";
                else if ("jpn".equals(language)) language = "ja";
                else if ("kor".equals(language)) language = "ko";
                else if ("chi".equals(language) || "zho".equals(language)) language = "zh";
            }
            Locale locale = Locale.forLanguageTag(language);
            String displayName = locale.getDisplayLanguage(Locale.getDefault());
            if (displayName == null || displayName.isEmpty()) displayName = format.language;
            name.append(displayName);
        } else {
            name.append("Track");
        }

        String mime = format.sampleMimeType;
        if (mime != null && mime.startsWith("audio/")) {
            String codec;
            switch (mime) {
                case "audio/ac3": codec = "AC3"; break;
                case "audio/eac3": codec = "E-AC3"; break;
                case "audio/vnd.dts": codec = "DTS"; break;
                case "audio/vnd.dts.hd": codec = "DTS-HD"; break;
                case "audio/true-hd": codec = "TrueHD"; break;
                case "audio/mp4a-latm": codec = "AAC"; break;
                case "audio/opus": codec = "Opus"; break;
                default: codec = mime.substring("audio/".length()).toUpperCase(Locale.US); break;
            }
            name.append(" • ").append(codec);
            if (format.channelCount > 0) {
                String channels;
                switch (format.channelCount) {
                    case 1: channels = "Mono"; break;
                    case 2: channels = "Stereo"; break;
                    case 6: channels = "5.1"; break;
                    case 8: channels = "7.1"; break;
                    default: channels = format.channelCount + "ch"; break;
                }
                name.append(" • ").append(channels);
            }
        } else if (mime != null && (mime.startsWith("text/") || mime.startsWith("application/"))) {
            if ("text/vtt".equals(mime)) name.append(" • WebVTT");
            else if ("application/ttml+xml".equals(mime)) name.append(" • TTML");
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
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            titleBar.setVisibility(View.GONE);
            inputScroll.setVisibility(View.GONE);
            playerContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams attrs = getWindow().getAttributes();
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(attrs);
            }
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            setStatus("Fullscreen Mode");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            getWindow().setStatusBarColor(BG_COLOR);
            getWindow().setNavigationBarColor(BG_COLOR);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams attrs = getWindow().getAttributes();
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                getWindow().setAttributes(attrs);
            }
            titleBar.setVisibility(View.VISIBLE);
            inputScroll.setVisibility(View.VISIBLE);
            playerContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 0.30f));
            setStatus("Normal Mode");
        }
        updateFullscreenButton();
    }

    private void updateFullscreenButton() {
        if (fullscreenButton != null) {
            fullscreenButton.setText(fullscreen ? "⤡" : "⤢");
            fullscreenButton.setContentDescription(fullscreen ? "Exit fullscreen" : "Enter fullscreen");
        }
    }

    private void setControlsLocked(boolean locked) {
        controlsLocked = locked;
        if (lockOverlay != null) lockOverlay.setVisibility(locked ? View.VISIBLE : View.GONE);
        if (leftControls != null) leftControls.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (rightControls != null) rightControls.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (playerView != null && !locked) playerView.showController();
        setStatus(locked ? "Player controls locked" : "Player controls unlocked");
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
    // PICTURE IN PICTURE
    // ==========================================

    private boolean isInPipMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
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
            // Android PiP is activity-based. Restrict the activity content to the player view
            // and hint the system to crop to the video's visible bounds.
            Rect sourceRect = new Rect();
            playerContainer.getGlobalVisibleRect(sourceRect);
            wasFullscreenBeforePip = fullscreen;
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            if (!sourceRect.isEmpty()) builder.setSourceRectHint(sourceRect);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(false);
            enterPictureInPictureMode(builder.build());
        } catch (Exception e) {
            toast("PiP error: " + e.getMessage());
        }
    }

    // PiP is entered only when the user taps the PiP button, not automatically on Home.
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            titleBar.setVisibility(View.GONE);
            inputScroll.setVisibility(View.GONE);
            leftControls.setVisibility(View.GONE);
            rightControls.setVisibility(View.GONE);
            if (lockOverlay != null) lockOverlay.setVisibility(View.GONE);
            playerContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            fullscreen = wasFullscreenBeforePip;
            if (fullscreen) {
                titleBar.setVisibility(View.GONE);
                inputScroll.setVisibility(View.GONE);
                playerContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
            } else {
                titleBar.setVisibility(View.VISIBLE);
                inputScroll.setVisibility(View.VISIBLE);
                playerContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 0.30f));
            }
            if (controlsLocked) {
                setControlsLocked(true);
            } else if (playerView != null) {
                playerView.showController();
            }
            updateFullscreenButton();
        }
    }

    private class HtmlItemBridge {
        @JavascriptInterface
        public void setWebViewString(String value) {
            if (value == null) return;
            runOnUiThread(() -> {
                String output = value.trim();
                if (output.startsWith("Playlist>")) output = output.substring("Playlist>".length()).trim();
                if (urlInput != null && !output.isEmpty()) {
                    urlInput.setText(output);
                    urlInput.setSelection(urlInput.getText().length());
                    setStatus("Link selected from web content");
                }
            });
        }
    }

    private void loadEventHtml() {
        final String htmlUrl = "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/EventCode.html";
        htmlLoadThread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(htmlUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                String html = response.body().string();
                runOnUiThread(() -> {
                    if (eventWebView != null && !isFinishing()) {
                        eventWebView.loadDataWithBaseURL(
                                "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                        );
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Could not load web content: " + e.getMessage()));
            }
        }, "event-html-loader");
        htmlLoadThread.start();
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

        if (htmlLoadThread != null) {
            htmlLoadThread.interrupt();
            htmlLoadThread = null;
        }
        if (eventWebView != null) {
            eventWebView.removeJavascriptInterface("CustomWebView");
            eventWebView.destroy();
            eventWebView = null;
        }

        if (player != null) {

            playerView.setPlayer(null);

            player.release();

            player = null;
        }
    }
}
