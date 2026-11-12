package com.raselahomed467.advancedmedia3player;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;

import android.provider.Settings;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.VideoSize;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;

    private LinearLayout rootLayout;
    private LinearLayout topBarLayout;
    private TextView titleBar;
    private EditText searchEditText;
    private FrameLayout searchToggleBtn;
    private ImageView searchToggleIcon;
    private TextView searchToggleClose;
    private FrameLayout sideMenuOverlay;
    private LinearLayout sideMenuPanel;
    private TextView menuDisplayValue, menuPipValue, menuSpeedValue;
    private ImageView speedButton;
    private boolean speedButtonVisible = true;
    private boolean autoPipEnabled = true;
    private int settingsTab = 0;
    private boolean isSearchOpen = false;
    private LinearLayout inputScroll;
    private WebView eventWebView;
    private WebView playlistWebView;
    private FrameLayout webContentContainer;
    private volatile String cachedPlaylistTemplate = null;
    private volatile boolean isPlaylistViewActive = false;
    private Thread playlistLoadThread = null;
    private Thread htmlLoadThread;

    private static final String CACHED_EVENT_HTML_FILE = "cached_event_code.html";
    private static final String CACHED_PLAYLIST_HTML_FILE = "cached_playlist.html";
    private static final String EVENT_HTML_URL = "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/EventCode.html";
    private static final String PLAYLIST_HTML_URL = "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/Playlist.html";
    private boolean isEventHtmlLoaded = false;

    private FrameLayout playerContainer;
    private FrameLayout customControlsOverlay;
    private LinearLayout bottomPanel;
    private TextView btnPlayPause;
    private ImageView fullscreenButton;
    private SeekBar customSeekBar;
    private TextView timeCurrent;
    private TextView timeDuration;
    private ProgressBar bufferingProgress;

    // Splash Screen & Deep Link
    // In-App Miniplayer (Floating PiP inside app)
    private FrameLayout mainAppContainer;
    private FrameLayout miniControlsOverlay;
    private LinearLayout miniTopBar;
    private TextView btnMiniExpand;
    private TextView btnMiniClose;
    private TextView btnMiniPlayPause;
    private Drawable miniVignetteDrawable;
    private boolean isMiniPlayerActive = false;
    private boolean isMiniControlsVisible = true;
    private boolean wasMiniPlayerBeforePip = false;
    private boolean wasFullscreenBeforePip = false;
    private final Runnable miniAutoHideRunnable = this::hideMiniControls;

    private FrameLayout splashLayout;
    private boolean isSplashDismissed = false;
    private long splashStartTime = 0;
    private final Runnable splashDismissRunnable = this::dismissSplashScreen;

    // Gesture detection & Center HUD
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private float currentScaleX = 1.0f;
    private float currentScaleY = 1.0f;
    private int scaleDirection = 0; // 0 = none, 1 = horizontal, 2 = vertical
    private FrameLayout gestureHudCard;
    private TextView gestureHudIcon;
    private TextView gestureHudText;
    private ProgressBar gestureHudProgress;
    private final Handler gestureHudHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideGestureHudRunnable = () -> {
        if (gestureHudCard != null) {
            gestureHudCard.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        gestureHudCard.setVisibility(View.GONE);
                        gestureHudCard.setAlpha(1f);
                    })
                    .start();
        }
    };

    // Tracking swipe for Volume & Brightness
    private boolean isSwipeGestureActive = false;
    private boolean isSwipeVolume = false;
    private float swipeStartY = 0f;
    private float swipeStartX = 0f;
    private int initialVolume = 0;
    private float initialBrightness = 0.5f;

    private EditText urlInput;
    private TextView statusText;

    private HorizontalScrollView episodesScroll;
    private LinearLayout episodesContainer;
    private final List<TextView> episodeButtonViews = new ArrayList<>();

    private boolean fullscreen = false;
    private int navBottomInset = 0;
    private int resizeMode = 1; // default: FILL so video covers the whole player

    private boolean isControlsVisible = true;
    private boolean isUserSeeking = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoHideRunnable = this::hideControls;

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && !isUserSeeking) {
                long pos = Math.max(0, player.getCurrentPosition());
                long dur = Math.max(0, player.getDuration());

                if (dur > 0) {
                    customSeekBar.setMax((int) dur);
                    customSeekBar.setProgress((int) pos);
                    timeCurrent.setText(formatTime(pos));
                    timeDuration.setText(formatTime(dur));
                } else {
                    timeCurrent.setText(formatTime(pos));
                    timeDuration.setText("LIVE");
                }

                if (btnPlayPause != null) {
                    btnPlayPause.setText(player.isPlaying() ? "❚❚" : "▶");
                }
                if (btnMiniPlayPause != null) {
                    btnMiniPlayPause.setText(player.isPlaying() ? "❚❚" : "▶");
                }
            }
            handler.postDelayed(this, 800);
        }
    };

    private static final int BG_COLOR = Color.rgb(18, 24, 38);
    private static final int PRIMARY_COLOR = Color.rgb(41, 128, 255);
    private static final int BUTTON_COLOR = Color.rgb(33, 44, 61);

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================

    // Lock font scale so text keeps the same size whatever the phone's font setting is
    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = 1.0f;
        // Same dp scale on every phone: the short side is always 360dp wide
        DisplayMetrics dm = newBase.getResources().getDisplayMetrics();
        int shortSidePx = Math.min(dm.widthPixels, dm.heightPixels);
        if (shortSidePx > 0) {
            config.densityDpi = Math.max(120, Math.round(shortSidePx * 160f / 360f));
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge: bars are transparent and the app draws behind them
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat barIcons =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        barIcons.setAppearanceLightStatusBars(false);
        barIcons.setAppearanceLightNavigationBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams cutoutAttrs = getWindow().getAttributes();
            cutoutAttrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(cutoutAttrs);
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        initGestureDetectors();
        buildScreen();
        initializePlayer();

        handler.post(updateProgressRunnable);

        handleIncomingIntent(getIntent());

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (splashLayout != null && splashLayout.getVisibility() == View.VISIBLE && !isSplashDismissed) {
                            dismissSplashScreen();
                        } else if (sideMenuOverlay != null && sideMenuOverlay.getVisibility() == View.VISIBLE) {
                            closeSideMenu();
                        } else if (isSearchOpen) {
                            closeSearch();
                        } else if (fullscreen) {
                            toggleFullscreen();
                        } else if (!isMiniPlayerActive && player != null && playerContainer != null
                                && playerContainer.getVisibility() == View.VISIBLE
                                && (player.isPlaying() || player.getPlayWhenReady())) {
                            // YouTube-style In-App Miniplayer: Back shrinks video to floating miniplayer without minimizing the app!
                            enterInAppMiniPlayer();
                        } else if (isPlaylistViewActive) {
                            // Close playlist tab and return to Home (mini player stays playing if active!)
                            closePlaylistTab();
                        } else if (isMiniPlayerActive) {
                            // Back while in miniplayer on Home -> close the miniplayer
                            closeMiniPlayer();
                        } else if (playerContainer != null && playerContainer.getVisibility() == View.VISIBLE) {
                            if (player != null) {
                                player.stop();
                            }
                            playerContainer.setVisibility(View.GONE);
                            updatePipParams();
                            hideEpisodeButtons();
                        } else if (eventWebView != null && eventWebView.canGoBack()) {
                            eventWebView.goBack();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    // ==========================================
    // BUILD USER INTERFACE
    // ==========================================

    private void buildScreen() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG_COLOR);
        rootLayout.setFitsSystemWindows(false);

        // Top Bar (Horizontal: App Title / Search Input + Search/Close Toggle Button)
        topBarLayout = new LinearLayout(this);
        topBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        topBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        topBarLayout.setPadding(dp(16), dp(6), dp(16), dp(6));

        // 0. Side menu button (menu.png) at the far left of the header
        ImageView menuBtn = new ImageView(this);
        menuBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        menuBtn.setColorFilter(Color.WHITE);
        menuBtn.setPadding(dp(9), dp(9), dp(9), dp(9));
        int menuResId = getResources().getIdentifier("menu", "drawable", getPackageName());
        if (menuResId != 0) {
            menuBtn.setImageResource(menuResId);
        }
        menuBtn.setContentDescription("Menu");
        menuBtn.setFocusable(true);
        menuBtn.setClickable(true);
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.argb(160, 35, 48, 67));
        menuBg.setCornerRadius(dp(10));
        menuBtn.setBackground(menuBg);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        menuParams.rightMargin = dp(10);
        topBarLayout.addView(menuBtn, menuParams);
        menuBtn.setOnClickListener(v -> openSideMenu());

        // 1. App Title (RasVibe)
        titleBar = new TextView(this);
        titleBar.setText("RasVibe");
        titleBar.setTextColor(Color.WHITE);
        titleBar.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        topBarLayout.addView(titleBar, titleParams);

        // 2. Search EditText (Hidden by default, shown when search toggle is clicked)
        searchEditText = new EditText(this);
        searchEditText.setHint("Search movies, matches...");
        searchEditText.setHintTextColor(Color.argb(130, 255, 255, 255));
        searchEditText.setTextColor(Color.WHITE);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchEditText.setSingleLine(true);
        searchEditText.setVisibility(View.GONE);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.argb(160, 30, 42, 58));
        searchBg.setCornerRadius(dp(10));
        searchBg.setStroke(dp(1), Color.argb(50, 255, 255, 255));
        searchEditText.setBackground(searchBg);
        searchEditText.setPadding(dp(14), 0, dp(14), 0);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        searchParams.rightMargin = dp(10);
        topBarLayout.addView(searchEditText, searchParams);

        // Realtime Web Search as user types
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterWebView(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 3. Search / Close Toggle Button on the far right
        searchToggleBtn = new FrameLayout(this);
        searchToggleBtn.setFocusable(true);
        searchToggleBtn.setClickable(true);

        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(Color.argb(160, 35, 48, 67));
        toggleBg.setCornerRadius(dp(10));
        searchToggleBtn.setBackground(toggleBg);

        searchToggleIcon = new ImageView(this);
        searchToggleIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchToggleIcon.setColorFilter(Color.WHITE);
        searchToggleIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        int searchResId = getResources().getIdentifier("search", "drawable", getPackageName());
        if (searchResId != 0) {
            searchToggleIcon.setImageResource(searchResId);
        }
        searchToggleBtn.addView(searchToggleIcon, new FrameLayout.LayoutParams(-1, -1));

        searchToggleClose = new TextView(this);
        searchToggleClose.setText("\u2715");
        searchToggleClose.setTextColor(Color.WHITE);
        searchToggleClose.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchToggleClose.setGravity(Gravity.CENTER);
        searchToggleClose.setVisibility(View.GONE);
        searchToggleBtn.addView(searchToggleClose, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        topBarLayout.addView(searchToggleBtn, toggleParams);

        searchToggleBtn.setOnClickListener(v -> toggleSearch());

        rootLayout.addView(topBarLayout, new LinearLayout.LayoutParams(-1, dp(52)));

        // Player Container (Initially hidden until a video link is selected)
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setClickable(true);
        playerContainer.setOnClickListener(v -> toggleControls());
        playerContainer.setVisibility(View.GONE);
        updatePipParams();

        playerView = new PlayerView(this);
        // Disable ExoPlayer default built-in controls completely
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setClickable(true);
        playerView.setOnClickListener(v -> toggleControls());
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

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

        // Custom Controller Overlay
        buildCustomControls();
        playerContainer.addView(customControlsOverlay, new FrameLayout.LayoutParams(-1, -1));

        // In-App Miniplayer Controls Overlay
        buildMiniControls();

        // Center Gesture Notification HUD
        buildGestureHud();
        playerContainer.addView(gestureHudCard, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        rootLayout.addView(playerContainer, new LinearLayout.LayoutParams(-1, 0, 0.30f));

        // Bottom Section: URL input + Play button + Web content
        inputScroll = new LinearLayout(this);
        inputScroll.setOrientation(LinearLayout.VERTICAL);
        inputScroll.setBackgroundColor(BG_COLOR);

        // Dynamic Episode / Multi-Link Buttons Horizontal Row
        episodesScroll = new HorizontalScrollView(this);
        episodesScroll.setHorizontalScrollBarEnabled(false);
        episodesScroll.setPadding(dp(14), dp(8), dp(14), dp(4));
        episodesScroll.setVisibility(View.GONE);

        episodesContainer = new LinearLayout(this);
        episodesContainer.setOrientation(LinearLayout.HORIZONTAL);
        episodesScroll.addView(episodesContainer, new FrameLayout.LayoutParams(-2, -2));

        inputScroll.addView(episodesScroll, new LinearLayout.LayoutParams(-1, -2));

        // Header container for Input & Status (Permanently hidden per user requirement)
        LinearLayout inputHeader = new LinearLayout(this);
        inputHeader.setOrientation(LinearLayout.VERTICAL);
        inputHeader.setPadding(dp(14), dp(8), dp(14), dp(6));
        inputHeader.setVisibility(View.GONE);

        inputHeader.addView(sectionTitle("STREAM / DRM / REQUEST SETTINGS"),
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
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        urlInput.setPadding(dp(12), dp(8), dp(10), dp(8));
        urlInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(75, 95, 120)));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, dp(72), 1f);
        urlParams.rightMargin = dp(8);
        inputRow.addView(urlInput, urlParams);

        TextView playButton = makeActionButton("▶ PLAY", true);
        playButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        playButton.setOnClickListener(v -> playStream());
        inputRow.addView(playButton, new LinearLayout.LayoutParams(dp(92), dp(52)));
        inputHeader.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Ready - Enter a stream URL or choose an item below");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        statusText.setPadding(dp(3), dp(4), dp(3), dp(4));
        inputHeader.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        inputScroll.addView(inputHeader, new LinearLayout.LayoutParams(-1, -2));

        // Separate in-memory WebViews act as Home and Playlist tabs.
        webContentContainer = new FrameLayout(this);
        LinearLayout.LayoutParams webContainerParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        webContainerParams.setMargins(0, 0, 0, 0);
        HtmlItemBridge bridge = new HtmlItemBridge();

        eventWebView = new WebView(this);
        configureWebViewSettings(eventWebView);
        eventWebView.addJavascriptInterface(bridge, "CustomWebView");
        eventWebView.addJavascriptInterface(bridge, "AppInventor");
        webContentContainer.addView(eventWebView, new FrameLayout.LayoutParams(-1, -1));

        playlistWebView = new WebView(this);
        configureWebViewSettings(playlistWebView);
        playlistWebView.setVisibility(View.GONE);
        playlistWebView.addJavascriptInterface(bridge, "CustomWebView");
        playlistWebView.addJavascriptInterface(bridge, "AppInventor");
        webContentContainer.addView(playlistWebView, new FrameLayout.LayoutParams(-1, -1));

        inputScroll.addView(webContentContainer, webContainerParams);

        // Instantly load cached EventCode.html from local storage if available
        String cachedHtml = loadHtmlFromLocal(CACHED_EVENT_HTML_FILE);
        if (cachedHtml != null && !cachedHtml.trim().isEmpty()) {
            isEventHtmlLoaded = true;
            eventWebView.loadDataWithBaseURL(
                    "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/",
                    cachedHtml,
                    "text/html",
                    "UTF-8",
                    null
            );
        }

        // Also check if cached Playlist template exists
        String cachedPlaylist = loadHtmlFromLocal(CACHED_PLAYLIST_HTML_FILE);
        if (cachedPlaylist != null && !cachedPlaylist.trim().isEmpty()) {
            cachedPlaylistTemplate = cachedPlaylist;
        }

        // Fetch and update templates in background during splash screen
        preloadHtmlTemplates();

        rootLayout.addView(inputScroll, new LinearLayout.LayoutParams(-1, 0, 0.70f));

        mainAppContainer = new FrameLayout(this);
        mainAppContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mainAppContainer.addView(rootLayout, new FrameLayout.LayoutParams(-1, -1));

        buildSideMenu(mainAppContainer);
        buildSplashScreen(mainAppContainer);

        setContentView(mainAppContainer);

        // Pad the content away from system bars and cutouts in normal mode;
        // in fullscreen the player gets the whole screen, edge to edge.
        ViewCompat.setOnApplyWindowInsetsListener(mainAppContainer, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            if (fullscreen) {
                navBottomInset = 0;
                rootLayout.setPadding(0, 0, 0, 0);
                inputScroll.setPadding(0, 0, 0, 0);
            } else {
                navBottomInset = bars.bottom;
                // Top, left, right: keep clear of status bar and cutouts
                rootLayout.setPadding(bars.left, bars.top, bars.right, 0);
                // Bottom: the background runs behind the nav bar; the input area stops above it or the keyboard
                inputScroll.setPadding(0, 0, 0, Math.max(bars.bottom, imeBottom));
            }
            return insets;
        });
        scheduleSplashDismiss();
    }

    // ==========================================
    // CUSTOM CONTROLLER & TIMELINE OVERLAY
    // ==========================================

    private void buildCustomControls() {
        customControlsOverlay = new FrameLayout(this);
        customControlsOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        customControlsOverlay.setClickable(true);
        customControlsOverlay.setFocusable(true);

        // Setup gestures (Single-tap, Double-tap seek, Swipe Volume/Brightness, Two-finger Zoom)
        setupOverlayGestures();

        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable shadowGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(140, 0, 0, 0), Color.argb(220, 0, 0, 0)}
        );
        bottomPanel.setBackground(shadowGradient);
        bottomPanel.setPadding(dp(12), dp(10), dp(12), dp(8));
        bottomPanel.setClickable(true);
        bottomPanel.setOnClickListener(v -> resetAutoHide());

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM
        );
        customControlsOverlay.addView(bottomPanel, bottomParams);

        // ------------------------------------------
        // ROW 1: BUTTONS ROW (ALL HORIZONTAL)
        // ------------------------------------------
        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER);
        buttonsRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        // 1. Picture in Picture (PiP) Mode Icon (using picture_in_picture.png)
        addControllerIconButton(buttonsRow, "picture_in_picture", "▣", "Mini Player", this::toggleInAppMiniPlayer);

        // 1b. Playback speed (using speed.png), placed between PiP and Rewind
        speedButton = addControllerIconButton(buttonsRow, "speed", "⏱", "Playback speed", this::showSpeedDialog);

        // 2. Rewind 10s
        addControllerButton(buttonsRow, "◀◀", "Rewind 10s", this::rewind10s);

        // 3. Play / Pause Button (highlighted)
        btnPlayPause = addControllerButton(buttonsRow, "▶", "Play/Pause", this::togglePlayPause);
        btnPlayPause.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);

        // 4. Forward 10s
        addControllerButton(buttonsRow, "▶▶", "Forward 10s", this::forward10s);

        // 5. Settings (Gear) icon (using video_settings.png)
        addControllerIconButton(buttonsRow, "video_settings", "⚙", "Settings", this::showPlayerSettings);

        // 6. Fullscreen Toggle (using fullscreen_enter.png / fullscreen_exit.png)
        fullscreenButton = addControllerIconButton(buttonsRow, "fullscreen_enter", "⤢", "Enter fullscreen", this::toggleFullscreen);

        bottomPanel.addView(buttonsRow);

        // ------------------------------------------
        // ROW 2: TIMELINE / PROGRESS BAR ROW (HORIZONTAL)
        // ------------------------------------------
        LinearLayout timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        timelineRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(-1, -2);
        timelineParams.topMargin = dp(4);
        timelineRow.setLayoutParams(timelineParams);

        timeCurrent = new TextView(this);
        timeCurrent.setText("00:00");
        timeCurrent.setTextColor(Color.WHITE);
        timeCurrent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeCurrent.setTypeface(null, android.graphics.Typeface.BOLD);
        timelineRow.addView(timeCurrent, new LinearLayout.LayoutParams(-2, -2));

        customSeekBar = new SeekBar(this);
        customSeekBar.setMax(100);
        customSeekBar.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            customSeekBar.setProgressTintList(ColorStateList.valueOf(PRIMARY_COLOR));
            customSeekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        }
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, -2, 1f);
        seekParams.leftMargin = dp(8);
        seekParams.rightMargin = dp(8);
        timelineRow.addView(customSeekBar, seekParams);

        timeDuration = new TextView(this);
        timeDuration.setText("00:00");
        timeDuration.setTextColor(Color.WHITE);
        timeDuration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeDuration.setTypeface(null, android.graphics.Typeface.BOLD);
        timelineRow.addView(timeDuration, new LinearLayout.LayoutParams(-2, -2));

        customSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    timeCurrent.setText(formatTime(progress));
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

        bottomPanel.addView(timelineRow);
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
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 35, 48, 67));
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(40));
        params.leftMargin = dp(5);
        params.rightMargin = dp(5);
        parent.addView(button, params);

        button.setOnClickListener(v -> {
            action.run();
            resetAutoHide();
        });
        return button;
    }

    private ImageView addControllerIconButton(
            LinearLayout parent,
            String drawableName,
            String fallbackSymbol,
            String description,
            Runnable action
    ) {
        ImageView button = new ImageView(this);
        int resId = getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (resId != 0) {
            button.setImageResource(resId);
        }
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setColorFilter(Color.WHITE);
        button.setPadding(dp(11), dp(9), dp(11), dp(9));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 35, 48, 67));
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(40));
        params.leftMargin = dp(5);
        params.rightMargin = dp(5);
        parent.addView(button, params);

        button.setOnClickListener(v -> {
            action.run();
            resetAutoHide();
        });
        return button;
    }

    // ==========================================
    // GESTURE & HUD NOTIFICATION SYSTEM
    // ==========================================

    private void buildGestureHud() {
        gestureHudCard = new FrameLayout(this);
        gestureHudCard.setVisibility(View.GONE);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(Color.argb(220, 18, 22, 28));
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        gestureHudCard.setBackground(cardBg);
        gestureHudCard.setPadding(dp(20), dp(14), dp(20), dp(14));

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);

        gestureHudIcon = new TextView(this);
        gestureHudIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        gestureHudIcon.setTextColor(Color.WHITE);
        gestureHudIcon.setGravity(Gravity.CENTER);
        contentLayout.addView(gestureHudIcon, new LinearLayout.LayoutParams(-2, -2));

        gestureHudText = new TextView(this);
        gestureHudText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        gestureHudText.setTextColor(Color.WHITE);
        gestureHudText.setTypeface(null, android.graphics.Typeface.BOLD);
        gestureHudText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.topMargin = dp(4);
        contentLayout.addView(gestureHudText, textParams);

        gestureHudProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        gestureHudProgress.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(110), dp(6));
        progressParams.topMargin = dp(8);
        gestureHudProgress.setLayoutParams(progressParams);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            gestureHudProgress.setProgressTintList(ColorStateList.valueOf(Color.rgb(85, 194, 255)));
            gestureHudProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(80, 255, 255, 255)));
        }
        contentLayout.addView(gestureHudProgress);

        gestureHudCard.addView(contentLayout, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void showGestureHud(String icon, String text, int progress) {
        if (gestureHudCard == null || isInPipMode()) return;
        gestureHudHandler.removeCallbacks(hideGestureHudRunnable);
        gestureHudIcon.setText(icon);
        gestureHudText.setText(text);
        if (progress >= 0) {
            gestureHudProgress.setVisibility(View.VISIBLE);
            gestureHudProgress.setProgress(Math.max(0, Math.min(100, progress)));
        } else {
            gestureHudProgress.setVisibility(View.GONE);
        }
        gestureHudCard.setAlpha(1f);
        gestureHudCard.setVisibility(View.VISIBLE);
        gestureHudHandler.postDelayed(hideGestureHudRunnable, 1200);
    }

    private void fastForward15s() {
        if (player == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition() + 15000;
        if (dur > 0 && pos > dur) pos = dur;
        player.seekTo(pos);
        updateSeekViews(pos);
        showGestureHud("⏩", "+15s Forward", -1);
    }

    private void rewind15s() {
        if (player == null) return;
        long pos = Math.max(0, player.getCurrentPosition() - 15000);
        player.seekTo(pos);
        updateSeekViews(pos);
        showGestureHud("⏪", "-15s Rewind", -1);
    }

    private void initGestureDetectors() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (customControlsOverlay == null || customControlsOverlay.getWidth() == 0) {
                    return false;
                }
                float x = e.getX();
                float halfWidth = customControlsOverlay.getWidth() / 2f;

                if (x > halfWidth) {
                    fastForward15s();
                } else {
                    rewind15s();
                }
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                scaleDirection = 0;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float spanX = detector.getCurrentSpanX();
                float prevSpanX = detector.getPreviousSpanX();
                float spanY = detector.getCurrentSpanY();
                float prevSpanY = detector.getPreviousSpanY();

                float diffX = Math.abs(spanX - prevSpanX);
                float diffY = Math.abs(spanY - prevSpanY);

                if (scaleDirection == 0) {
                    if (diffX > diffY && diffX > dp(5)) {
                        scaleDirection = 1; // Horizontal gesture
                    } else if (diffY > diffX && diffY > dp(5)) {
                        scaleDirection = 2; // Vertical gesture
                    }
                }

                if (scaleDirection == 1) {
                    // Horizontal Zoom In / Zoom Out (width change only)
                    if (prevSpanX > 10f && spanX > 10f) {
                        float factorX = spanX / prevSpanX;
                        currentScaleX = Math.max(0.70f, Math.min(currentScaleX * factorX, 4.0f));
                        if (playerView != null) {
                            playerView.setScaleX(currentScaleX);
                        }
                        int percent = Math.round(currentScaleX * 100);
                        showGestureHud("↔️", "Horizontal: " + percent + "%", -1);
                    }
                } else if (scaleDirection == 2) {
                    // Vertical Zoom In / Zoom Out (height change only)
                    if (prevSpanY > 10f && spanY > 10f) {
                        float factorY = spanY / prevSpanY;
                        currentScaleY = Math.max(0.70f, Math.min(currentScaleY * factorY, 4.0f));
                        if (playerView != null) {
                            playerView.setScaleY(currentScaleY);
                        }
                        int percent = Math.round(currentScaleY * 100);
                        showGestureHud("↕️", "Vertical: " + percent + "%", -1);
                    }
                } else {
                    // Slight diagonal motion or fallback
                    float factor = detector.getScaleFactor();
                    if (diffX >= diffY) {
                        currentScaleX = Math.max(0.70f, Math.min(currentScaleX * factor, 4.0f));
                        if (playerView != null) playerView.setScaleX(currentScaleX);
                        showGestureHud("↔️", "Horizontal: " + Math.round(currentScaleX * 100) + "%", -1);
                    } else {
                        currentScaleY = Math.max(0.70f, Math.min(currentScaleY * factor, 4.0f));
                        if (playerView != null) playerView.setScaleY(currentScaleY);
                        showGestureHud("↕️", "Vertical: " + Math.round(currentScaleY * 100) + "%", -1);
                    }
                }
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                scaleDirection = 0;
                // Snap smoothly to 1.0 if close to original aspect ratio
                if (Math.abs(currentScaleX - 1.0f) < 0.08f) {
                    currentScaleX = 1.0f;
                    if (playerView != null) playerView.setScaleX(1.0f);
                }
                if (Math.abs(currentScaleY - 1.0f) < 0.08f) {
                    currentScaleY = 1.0f;
                    if (playerView != null) playerView.setScaleY(1.0f);
                }
                if (currentScaleX == 1.0f && currentScaleY == 1.0f) {
                    if (playerView != null) {
                        playerView.setScaleX(1.0f);
                        playerView.setScaleY(1.0f);
                        playerView.setTranslationX(0);
                        playerView.setTranslationY(0);
                    }
                    showGestureHud("🔍", "Fit to Screen (100%)", -1);
                }
            }
        });
    }

    private void setupOverlayGestures() {
        customControlsOverlay.setOnTouchListener((v, event) -> {
            if (isInPipMode()) return false;

            // Two-finger Pinch to Zoom
            if (event.getPointerCount() >= 2) {
                isSwipeGestureActive = false;
                return scaleGestureDetector.onTouchEvent(event);
            }

            // Detect Single Tap and Double Tap (+15s / -15s)
            boolean gestureHandled = gestureDetector.onTouchEvent(event);

            // Single finger swipe for Volume (Right half) and Brightness (Left half)
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();
            float viewHeight = v.getHeight() > 0 ? (float) v.getHeight() : dp(220);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    swipeStartX = x;
                    swipeStartY = y;
                    isSwipeGestureActive = false;
                    isSwipeVolume = (x > v.getWidth() / 2f);

                    if (isSwipeVolume) {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            initialVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                        }
                    } else {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        initialBrightness = lp.screenBrightness;
                        if (initialBrightness < 0) {
                            try {
                                int sys = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                                initialBrightness = sys / 255.0f;
                            } catch (Exception ignored) {
                                initialBrightness = 0.5f;
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = swipeStartY - y; // Upwards swipe increases value
                    float deltaX = x - swipeStartX;

                    if (!isSwipeGestureActive) {
                        if (Math.abs(deltaY) > dp(14) && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                            isSwipeGestureActive = true;
                        }
                    }

                    if (isSwipeGestureActive) {
                        float deltaFraction = deltaY / viewHeight;

                        if (isSwipeVolume) {
                            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            if (am != null) {
                                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                int targetVol = Math.max(0, Math.min(maxVol, initialVolume + Math.round(deltaFraction * maxVol)));
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);

                                int percent = Math.round(((float) targetVol / (float) maxVol) * 100);
                                String icon = (targetVol == 0) ? "🔇" : (percent < 50 ? "🔉" : "🔊");
                                showGestureHud(icon, "Volume: " + percent + "%", percent);
                            }
                        } else {
                            float targetBrightness = Math.max(0.01f, Math.min(1.0f, initialBrightness + deltaFraction));
                            WindowManager.LayoutParams lp = getWindow().getAttributes();
                            lp.screenBrightness = targetBrightness;
                            getWindow().setAttributes(lp);

                            int percent = Math.round(targetBrightness * 100);
                            String icon = (percent < 35) ? "🔅" : "☀️";
                            showGestureHud(icon, "Brightness: " + percent + "%", percent);
                        }
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwipeGestureActive) {
                        isSwipeGestureActive = false;
                        return true;
                    }
                    break;
            }

            return gestureHandled;
        });
    }

    private void showControls() {
        if (isInPipMode()) return;
        bottomPanel.setVisibility(View.VISIBLE);
        isControlsVisible = true;
        resetAutoHide();
    }

    private void hideControls() {
        if (isUserSeeking || isInPipMode()) return;
        bottomPanel.setVisibility(View.GONE);
        isControlsVisible = false;
    }

    private void toggleControls() {
        if (isInPipMode()) return;
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void resetAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
        if (isControlsVisible && !isInPipMode()) {
            handler.postDelayed(autoHideRunnable, 4000);
        }
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlayPause.setText("▶");
        } else {
            player.play();
            btnPlayPause.setText("❚❚");
        }
    }

    private void rewind10s() {
        if (player == null) return;
        long pos = Math.max(0, player.getCurrentPosition() - 10000);
        player.seekTo(pos);
        updateSeekViews(pos);
    }

    private void forward10s() {
        if (player == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition() + 10000;
        if (dur > 0 && pos > dur) pos = dur;
        player.seekTo(pos);
        updateSeekViews(pos);
    }

    private void updateSeekViews(long pos) {
        customSeekBar.setProgress((int) pos);
        timeCurrent.setText(formatTime(pos));
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hours = totalSec / 3600;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, min, sec);
        }
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    private TextView makeActionButton(
            String text,
            boolean primary
    ) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, primary ? 15 : 12);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? PRIMARY_COLOR : BUTTON_COLOR);
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setClickable(true);
        return button;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(85, 194, 255));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(6));
        return view;
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

            // Subtitles disabled by default
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
            );

            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {

                @Override
                public void onPlaybackStateChanged(int state) {

                    if (state == Player.STATE_BUFFERING) {
                        if (bufferingProgress != null) bufferingProgress.setVisibility(View.VISIBLE);
                        setStatus("Buffering...");
                    } else if (state == Player.STATE_READY) {
                        if (bufferingProgress != null) bufferingProgress.setVisibility(View.GONE);
                        setStatus("Playing / Ready");
                    } else if (state == Player.STATE_ENDED) {
                        if (bufferingProgress != null) bufferingProgress.setVisibility(View.GONE);
                        setStatus("Playback ended");
                    } else if (state == Player.STATE_IDLE) {
                        if (bufferingProgress != null) bufferingProgress.setVisibility(View.GONE);
                        setStatus("Ready");
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePipParams();
                }

                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    updatePipParams();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (bufferingProgress != null) bufferingProgress.setVisibility(View.GONE);
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

        String rawInput = value(urlInput);
        if (rawInput.contains("Playlist>")) {
            handleWebViewData(rawInput);
            return;
        }

        StreamConfig config =
                parseStreamInput(rawInput);

        if (config.url.isEmpty()) {
            toast("প্রথমে Video URL দিন");
            return;
        }

        if (player == null) {
            toast("Player is not ready");
            return;
        }

        if (isMiniPlayerActive) {
            exitInAppMiniPlayer();
        }
        if (playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
            updatePipParams();
        }
        if (inputScroll != null && !isMiniPlayerActive) {
            inputScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 0.70f));
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

            btnPlayPause.setText("❚❚");
            resetAutoHide();
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
                .setShowDisableOption(true)
                .setTrackNameProvider(
                        this::getTrackName
                )
                .build()
                .show();
    }

    private String getLanguageDisplayName(String langCode) {
        if (langCode == null || langCode.trim().isEmpty() || "und".equalsIgnoreCase(langCode.trim())) {
            return null;
        }
        String code = langCode.trim().toLowerCase(Locale.US);
        switch (code) {
            case "ben": case "beng": case "bn": return "Bangla";
            case "hin": case "hi": return "Hindi";
            case "eng": case "en": return "English";
            case "urd": case "ur": return "Urdu";
            case "ara": case "ar": return "Arabic";
            case "spa": case "es": return "Spanish";
            case "fra": case "fre": case "fr": return "French";
            case "deu": case "ger": case "de": return "German";
            case "por": case "pt": return "Portuguese";
            case "jpn": case "ja": return "Japanese";
            case "kor": case "ko": return "Korean";
            case "zho": case "chi": case "zh": return "Chinese";
            case "rus": case "ru": return "Russian";
            case "tam": case "ta": return "Tamil";
            case "tel": case "te": return "Telugu";
            case "mal": case "ml": return "Malayalam";
            case "kan": case "kn": return "Kannada";
            case "mar": case "mr": return "Marathi";
            case "pan": case "pa": return "Punjabi";
            case "guj": case "gu": return "Gujarati";
            case "tur": case "tr": return "Turkish";
            case "ita": case "it": return "Italian";
            case "ind": case "id": return "Indonesian";
            case "tha": case "th": return "Thai";
            case "vie": case "vi": return "Vietnamese";
            default:
                try {
                    Locale loc = Locale.forLanguageTag(code);
                    String display = loc.getDisplayLanguage(Locale.ENGLISH);
                    if (display != null && !display.trim().isEmpty() && !display.equalsIgnoreCase(code)) {
                        return display;
                    }
                } catch (Exception ignored) {}
                return code.toUpperCase(Locale.US);
        }
    }

    private boolean isGarbageLabel(String label) {
        if (label == null || label.trim().isEmpty()) return true;
        String lower = label.toLowerCase(Locale.US);
        return lower.contains("encoder") || lower.contains("encoded") ||
               lower.contains("http") || lower.contains("www.") ||
               lower.contains(".tv") || lower.contains(".com") ||
               lower.contains(".org") || lower.contains(".net") ||
               lower.contains(".xyz") || lower.contains("joya9");
    }

    private String getAudioChannelsDescription(int channelCount) {
        switch (channelCount) {
            case 1: return "Mono";
            case 2: return "Stereo";
            case 6: return "5.1 surround sound";
            case 8: return "7.1 surround sound";
            default: return channelCount > 0 ? channelCount + " channels" : "";
        }
    }

    private String getAudioCodecName(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case "audio/ac3": return "AC-3";
            case "audio/eac3": return "E-AC3";
            case "audio/eac3-joc": return "E-AC3 (Dolby Atmos)";
            case "audio/vnd.dts": return "DTS";
            case "audio/vnd.dts.hd": return "DTS-HD";
            case "audio/true-hd": return "TrueHD";
            case "audio/mp4a-latm": return "AAC";
            case "audio/opus": return "Opus";
            case "audio/flac": return "FLAC";
            case "audio/mpeg": return "MP3";
            case "audio/vorbis": return "Vorbis";
            default:
                if (mime.startsWith("audio/")) {
                    return mime.substring("audio/".length()).toUpperCase(Locale.US);
                }
                return "";
        }
    }

    private String getTrackName(Format format) {
        // 1. Video Tracks
        if (format.height > 0) {
            StringBuilder sb = new StringBuilder();
            String resolution = format.height + "p";
            if (format.height >= 2160) resolution += " (4K UHD)";
            else if (format.height >= 1080) resolution += " (FHD)";
            else if (format.height >= 720) resolution += " (HD)";
            else if (format.height >= 480) resolution += " (SD)";
            sb.append(resolution);
            if (format.frameRate > 0) sb.append(" • ").append(Math.round(format.frameRate)).append(" fps");
            if (format.bitrate > 0) sb.append(" • ").append(Math.round(format.bitrate / 1000f)).append(" kbps");
            return sb.toString();
        }

        String mime = format.sampleMimeType;
        boolean isAudio = mime != null && mime.startsWith("audio/");
        boolean isText = mime != null && (mime.startsWith("text/") || mime.startsWith("application/"));

        // Language resolution
        String langName = getLanguageDisplayName(format.language);

        // Filter label if it is spam or encoder junk
        String cleanLabel = null;
        if (format.label != null && !format.label.trim().isEmpty() && !isGarbageLabel(format.label)) {
            cleanLabel = format.label.trim();
        }

        StringBuilder name = new StringBuilder();

        // Title / Name: Prioritize Language, fallback to clean label, fallback to generic
        if (langName != null) {
            name.append(langName);
            if (cleanLabel != null && !cleanLabel.equalsIgnoreCase(langName)) {
                name.append(" (").append(cleanLabel).append(")");
            }
        } else if (cleanLabel != null) {
            name.append(cleanLabel);
        } else {
            name.append(isAudio ? "Audio Track" : (isText ? "Subtitle" : "Track"));
        }

        // Audio details: Codec and Channels
        if (isAudio) {
            String codec = getAudioCodecName(mime);
            if (!codec.isEmpty()) {
                name.append(" • ").append(codec);
            }
            String channels = getAudioChannelsDescription(format.channelCount);
            if (!channels.isEmpty()) {
                name.append(" • ").append(channels);
            }
        }

        // Text details: Subtitle format
        if (isText) {
            if ("text/vtt".equals(mime)) name.append(" • WebVTT");
            else if ("application/ttml+xml".equals(mime)) name.append(" • TTML");
        }

        // Flags
        if ((format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0) {
            name.append(" [Forced]");
        }
        if ((format.roleFlags & C.ROLE_FLAG_CAPTION) != 0) {
            name.append(" [CC]");
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

    // Default / PiP video scaling: FILL so the video covers the whole player area
    private void applyFillResizeMode() {
        resizeMode = 1;
        if (playerView != null) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        }
    }

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
    // FULLSCREEN (STATUS BAR & NAVIGATION BAR HIDE)
    // ==========================================

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (topBarLayout != null) topBarLayout.setVisibility(View.GONE);
            inputScroll.setVisibility(View.GONE);
            playerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            applyFullscreenSystemUi(true);
            setStatus("Fullscreen Mode");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            applyFullscreenSystemUi(false);
            if (topBarLayout != null) topBarLayout.setVisibility(View.VISIBLE);
            inputScroll.setVisibility(View.VISIBLE);
            playerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.30f
            ));
            setStatus("Normal Mode");
        }
        updateFullscreenButton();
    }

    private void applyFullscreenSystemUi(boolean isFull) {
        try {
            if (rootLayout != null) {
                rootLayout.setFitsSystemWindows(false);
                if (isFull) rootLayout.setPadding(0, 0, 0, 0);
                if (mainAppContainer != null) ViewCompat.requestApplyInsets(mainAppContainer);
            }
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

            if (isFull) {
                // Completely hide both Status Bar and Navigation Bar
                controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );

                // Legacy system UI flags for older Android / MIUI versions
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                getWindow().getDecorView().setSystemUiVisibility(flags);

                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WindowManager.LayoutParams attrs = getWindow().getAttributes();
                    attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    getWindow().setAttributes(attrs);
                }
            } else {
                // Restore Status Bar and Navigation Bar
                controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());

                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WindowManager.LayoutParams attrs = getWindow().getAttributes();
                    attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                    getWindow().setAttributes(attrs);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen && !isInPipMode()) {
            applyFullscreenSystemUi(true);
        }
    }

    private void updateFullscreenButton() {
        if (fullscreenButton != null) {
            int iconRes = getResources().getIdentifier(
                    fullscreen ? "fullscreen_exit" : "fullscreen_enter",
                    "drawable",
                    getPackageName()
            );
            if (iconRes != 0) {
                fullscreenButton.setImageResource(iconRes);
            }
            fullscreenButton.setContentDescription(fullscreen ? "Exit fullscreen" : "Enter fullscreen");
        }
    }

    // ==========================================
    // PLAYER SETTINGS (Video / Audio / Subtitle tabs)
    // ==========================================

    private void showPlayerSettings() {
        if (player == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_COLOR);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        listArea.setPadding(dp(8), dp(4), dp(8), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(listArea);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(280)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Player settings")
                .setView(root)
                .setPositiveButton("Close", null)
                .create();
        renderSettingsTab(settingsTab, tabRow, listArea, dialog);
        dialog.show();
    }

    private void renderSettingsTab(final int tab, final LinearLayout tabRow,
                                   final LinearLayout listArea, final AlertDialog dialog) {
        settingsTab = tab;

        tabRow.removeAllViews();
        String[] names = {"Video", "Audio", "Subtitle"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            boolean selected = index == tab;
            TextView tabView = new TextView(this);
            tabView.setText(names[i]);
            tabView.setGravity(Gravity.CENTER);
            tabView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            tabView.setPadding(0, dp(12), 0, dp(12));
            tabView.setTextColor(selected ? Color.WHITE : Color.argb(160, 255, 255, 255));
            tabView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tabView.setBackgroundColor(selected ? Color.argb(70, 255, 255, 255) : Color.TRANSPARENT);
            tabView.setFocusable(true);
            tabView.setClickable(true);
            tabView.setOnClickListener(v -> renderSettingsTab(index, tabRow, listArea, dialog));
            tabRow.addView(tabView, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        listArea.removeAllViews();
        if (tab == 0) {
            buildVideoOptions(listArea, tabRow, dialog);
        } else if (tab == 1) {
            buildAudioOptions(listArea, tabRow, dialog);
        } else {
            buildSubtitleOptions(listArea, tabRow, dialog);
        }
    }

    private void addOptionRow(LinearLayout listArea, String label, boolean selected, Runnable onPick) {
        TextView row = new TextView(this);
        row.setText((selected ? "\u25CF  " : "\u25CB  ") + label);
        row.setTextColor(selected ? Color.WHITE : Color.argb(200, 255, 255, 255));
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        row.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> onPick.run());
        listArea.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addInfoRow(LinearLayout listArea, String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(Color.argb(160, 255, 255, 255));
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        listArea.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildVideoOptions(final LinearLayout listArea, final LinearLayout tabRow, final AlertDialog dialog) {
        final TrackSelectionParameters params = player.getTrackSelectionParameters();
        final Tracks tracks = player.getCurrentTracks();

        boolean hasVideoOverride = false;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_VIDEO && params.overrides.containsKey(g.getMediaTrackGroup())) {
                hasVideoOverride = true;
            }
        }
        addOptionRow(listArea, "Auto", !hasVideoOverride, () -> {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build());
            renderSettingsTab(0, tabRow, listArea, dialog);
        });

        int count = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                final Tracks.Group group = g;
                final int trackIndex = i;
                Format f = g.getTrackFormat(i);
                String label = (f.width > 0 && f.height > 0)
                        ? f.width + " \u00D7 " + f.height
                        : "Video track " + (count + 1);
                if (f.bitrate > 0) {
                    label += ", " + String.format(Locale.US, "%.2f Mbps", f.bitrate / 1_000_000f);
                }
                boolean selected = params.overrides.containsKey(g.getMediaTrackGroup())
                        && params.overrides.get(g.getMediaTrackGroup()).trackIndices.contains(i);
                addOptionRow(listArea, label, selected, () -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                            .build());
                    renderSettingsTab(0, tabRow, listArea, dialog);
                });
                count++;
            }
        }
        if (count == 0) {
            addInfoRow(listArea, "No video tracks found");
        }
    }

    private void buildAudioOptions(final LinearLayout listArea, final LinearLayout tabRow, final AlertDialog dialog) {
        final TrackSelectionParameters params = player.getTrackSelectionParameters();
        final Tracks tracks = player.getCurrentTracks();

        boolean hasAudioOverride = false;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO && params.overrides.containsKey(g.getMediaTrackGroup())) {
                hasAudioOverride = true;
            }
        }
        addOptionRow(listArea, "Auto", !hasAudioOverride, () -> {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build());
            renderSettingsTab(1, tabRow, listArea, dialog);
        });

        int count = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                final Tracks.Group group = g;
                final int trackIndex = i;
                Format f = g.getTrackFormat(i);
                String label = getTrackName(f);
                if (label == null || label.trim().isEmpty()) {
                    label = "Audio track " + (count + 1);
                }
                boolean selected = params.overrides.containsKey(g.getMediaTrackGroup())
                        && params.overrides.get(g.getMediaTrackGroup()).trackIndices.contains(i);
                addOptionRow(listArea, label, selected, () -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                            .build());
                    renderSettingsTab(1, tabRow, listArea, dialog);
                });
                count++;
            }
        }
        if (count == 0) {
            addInfoRow(listArea, "No audio tracks found");
        }
    }

    private void buildSubtitleOptions(final LinearLayout listArea, final LinearLayout tabRow, final AlertDialog dialog) {
        final TrackSelectionParameters params = player.getTrackSelectionParameters();
        final Tracks tracks = player.getCurrentTracks();

        boolean textOff = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        addOptionRow(listArea, "Off", textOff, () -> {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build());
            renderSettingsTab(2, tabRow, listArea, dialog);
        });

        int count = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                final Tracks.Group group = g;
                final int trackIndex = i;
                Format f = g.getTrackFormat(i);
                String label = getTrackName(f);
                if (label == null || label.trim().isEmpty()) {
                    label = "Subtitle track " + (count + 1);
                }
                boolean selected = !textOff
                        && params.overrides.containsKey(g.getMediaTrackGroup())
                        && params.overrides.get(g.getMediaTrackGroup()).trackIndices.contains(i);
                addOptionRow(listArea, label, selected, () -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                            .build());
                    renderSettingsTab(2, tabRow, listArea, dialog);
                });
                count++;
            }
        }
        if (count == 0) {
            addInfoRow(listArea, "No subtitle tracks in this video");
        }
    }

    // ==========================================
    // SIDE MENU (opened from the header menu button)
    // ==========================================

    private void buildSideMenu(FrameLayout parent) {
        sideMenuOverlay = new FrameLayout(this);
        sideMenuOverlay.setBackgroundColor(Color.argb(150, 0, 0, 0));
        sideMenuOverlay.setVisibility(View.GONE);
        sideMenuOverlay.setClickable(true);
        sideMenuOverlay.setOnClickListener(v -> closeSideMenu());

        sideMenuPanel = new LinearLayout(this);
        sideMenuPanel.setOrientation(LinearLayout.VERTICAL);
        sideMenuPanel.setBackgroundColor(BG_COLOR);
        sideMenuPanel.setPadding(dp(16), dp(40), dp(16), dp(16));
        sideMenuPanel.setClickable(true); // keeps taps inside the panel from closing it
        sideMenuPanel.setTranslationX(-dp(270));

        TextView header = new TextView(this);
        header.setText("RasVibe");
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(20));
        sideMenuPanel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        menuDisplayValue = addSideMenuRow("Video display", () -> {
            cycleResizeMode();
            refreshSideMenuValues();
        });
        menuPipValue = addSideMenuRow("Auto Picture-in-Picture", () -> {
            autoPipEnabled = !autoPipEnabled;
            refreshSideMenuValues();
        });
        menuSpeedValue = addSideMenuRow("Show speed button", () -> {
            speedButtonVisible = !speedButtonVisible;
            if (speedButton != null) {
                speedButton.setVisibility(speedButtonVisible ? View.VISIBLE : View.GONE);
            }
            refreshSideMenuValues();
        });

        sideMenuOverlay.addView(sideMenuPanel, new FrameLayout.LayoutParams(dp(270), -1, Gravity.START));
        parent.addView(sideMenuOverlay, new FrameLayout.LayoutParams(-1, -1));
        refreshSideMenuValues();
    }

    private TextView addSideMenuRow(String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 35, 48, 67));
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        row.setFocusable(true);
        row.setClickable(true);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView value = new TextView(this);
        value.setTextColor(Color.rgb(255, 210, 90));
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        value.setTypeface(null, Typeface.BOLD);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        sideMenuPanel.addView(row, params);
        return value;
    }

    private void refreshSideMenuValues() {
        if (menuDisplayValue != null) menuDisplayValue.setText(resizeModeName());
        if (menuPipValue != null) menuPipValue.setText(autoPipEnabled ? "On" : "Off");
        if (menuSpeedValue != null) menuSpeedValue.setText(speedButtonVisible ? "On" : "Off");
    }

    private String resizeModeName() {
        if (resizeMode == 0) return "Fit";
        if (resizeMode == 1) return "Fill";
        return "Zoom";
    }

    private void openSideMenu() {
        if (sideMenuOverlay == null || sideMenuPanel == null) return;
        refreshSideMenuValues();
        sideMenuOverlay.setVisibility(View.VISIBLE);
        sideMenuPanel.animate().translationX(0).setDuration(200).start();
    }

    private void closeSideMenu() {
        if (sideMenuOverlay == null || sideMenuPanel == null) return;
        sideMenuPanel.animate().translationX(-dp(270)).setDuration(200)
                .withEndAction(() -> sideMenuOverlay.setVisibility(View.GONE))
                .start();
    }

    // ==========================================
    // IN-APP MINIPLAYER (FLOATING OVER DISPLAY)
    // ==========================================

    private void buildMiniControls() {
        miniControlsOverlay = new FrameLayout(this);
        miniControlsOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        miniControlsOverlay.setVisibility(View.GONE);
        miniControlsOverlay.setClickable(true);
        miniControlsOverlay.setFocusable(false);

        // Dark gradient vignette for readability
        miniVignetteDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(170, 0, 0, 0), Color.argb(40, 0, 0, 0), Color.argb(190, 0, 0, 0)}
        );
        miniControlsOverlay.setBackground(miniVignetteDrawable);

        // Top bar: Expand button on left, Close button on right
        miniTopBar = new LinearLayout(this);
        miniTopBar.setOrientation(LinearLayout.HORIZONTAL);
        miniTopBar.setPadding(dp(6), dp(6), dp(6), 0);
        miniTopBar.setGravity(Gravity.CENTER_VERTICAL);

        btnMiniExpand = new TextView(this);
        btnMiniExpand.setText("⛶");
        btnMiniExpand.setTextColor(Color.WHITE);
        btnMiniExpand.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btnMiniExpand.setGravity(Gravity.CENTER);
        GradientDrawable expBg = new GradientDrawable();
        expBg.setColor(Color.argb(180, 20, 28, 42));
        expBg.setCornerRadius(dp(15));
        expBg.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        btnMiniExpand.setBackground(expBg);
        btnMiniExpand.setClickable(true);
        btnMiniExpand.setOnClickListener(v -> exitInAppMiniPlayer());
        miniTopBar.addView(btnMiniExpand, new LinearLayout.LayoutParams(dp(30), dp(30)));

        View spacer = new View(this);
        miniTopBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        btnMiniClose = new TextView(this);
        btnMiniClose.setText("✕");
        btnMiniClose.setTextColor(Color.WHITE);
        btnMiniClose.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        btnMiniClose.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.argb(210, 220, 38, 38));
        closeBg.setCornerRadius(dp(15));
        closeBg.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        btnMiniClose.setBackground(closeBg);
        btnMiniClose.setClickable(true);
        btnMiniClose.setOnClickListener(v -> closeMiniPlayer());
        miniTopBar.addView(btnMiniClose, new LinearLayout.LayoutParams(dp(30), dp(30)));

        miniControlsOverlay.addView(miniTopBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Center Play/Pause button
        btnMiniPlayPause = new TextView(this);
        btnMiniPlayPause.setText("❚❚");
        btnMiniPlayPause.setTextColor(Color.WHITE);
        btnMiniPlayPause.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        btnMiniPlayPause.setGravity(Gravity.CENTER);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setColor(Color.argb(190, 18, 24, 38));
        playBg.setCornerRadius(dp(22));
        playBg.setStroke(dp(1), Color.argb(140, 255, 255, 255));
        btnMiniPlayPause.setBackground(playBg);
        btnMiniPlayPause.setClickable(true);
        btnMiniPlayPause.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                    btnMiniPlayPause.setText("▶");
                } else {
                    player.play();
                    btnMiniPlayPause.setText("❚❚");
                }
                resetMiniAutoHide();
            }
        });
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        miniControlsOverlay.addView(btnMiniPlayPause, playParams);

        // Touch & Drag handler for the miniplayer overlay
        miniControlsOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private float startTranslationX, startTranslationY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // If controls are visible, let direct clicks on buttons go through
                if (isMiniControlsVisible) {
                    if (isTouchOnMiniView(btnMiniClose, event)) {
                        return false;
                    }
                    if (isTouchOnMiniView(btnMiniExpand, event)) {
                        return false;
                    }
                    if (isTouchOnMiniView(btnMiniPlayPause, event)) {
                        return false;
                    }
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startTranslationX = playerContainer.getTranslationX();
                        startTranslationY = playerContainer.getTranslationY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!isDragging && (Math.hypot(dx, dy) > dp(6))) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            playerContainer.setTranslationX(startTranslationX + dx);
                            playerContainer.setTranslationY(startTranslationY + dy);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            v.performClick();
                            // Tap on the mini card toggles button visibility!
                            // Only btnMiniExpand restores full player view.
                            toggleMiniControls();
                        }
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });

        playerContainer.addView(miniControlsOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private boolean isTouchOnMiniView(View view, MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        Rect r = new Rect();
        view.getGlobalVisibleRect(r);
        r.inset(-dp(6), -dp(6));
        return r.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private void showMiniControls() {
        if (!isMiniPlayerActive || miniControlsOverlay == null) return;
        if (miniVignetteDrawable != null) {
            miniControlsOverlay.setBackground(miniVignetteDrawable);
        }
        if (miniTopBar != null) miniTopBar.setVisibility(View.VISIBLE);
        if (btnMiniPlayPause != null) {
            btnMiniPlayPause.setVisibility(View.VISIBLE);
            if (player != null) {
                btnMiniPlayPause.setText(player.isPlaying() ? "❚❚" : "▶");
            }
        }
        isMiniControlsVisible = true;
        resetMiniAutoHide();
    }

    private void hideMiniControls() {
        if (miniControlsOverlay == null) return;
        handler.removeCallbacks(miniAutoHideRunnable);
        miniControlsOverlay.setBackground(null);
        if (miniTopBar != null) miniTopBar.setVisibility(View.GONE);
        if (btnMiniPlayPause != null) btnMiniPlayPause.setVisibility(View.GONE);
        isMiniControlsVisible = false;
    }

    private void toggleMiniControls() {
        if (isMiniControlsVisible) {
            hideMiniControls();
        } else {
            showMiniControls();
        }
    }

    private void resetMiniAutoHide() {
        handler.removeCallbacks(miniAutoHideRunnable);
        if (isMiniControlsVisible && isMiniPlayerActive) {
            handler.postDelayed(miniAutoHideRunnable, 3500);
        }
    }

    private void toggleInAppMiniPlayer() {
        if (isMiniPlayerActive) {
            exitInAppMiniPlayer();
        } else {
            enterInAppMiniPlayer();
        }
    }

    private void enterInAppMiniPlayer() {
        if (isMiniPlayerActive || playerContainer == null || mainAppContainer == null) {
            return;
        }

        if (fullscreen) {
            toggleFullscreen();
        }

        isMiniPlayerActive = true;

        // Reset any prior translation from dragging
        playerContainer.setTranslationX(0);
        playerContainer.setTranslationY(0);

        // Hide full controls and gesture HUD
        hideControls();
        if (customControlsOverlay != null) {
            customControlsOverlay.setVisibility(View.GONE);
        }
        if (gestureHudCard != null) {
            gestureHudCard.setVisibility(View.GONE);
        }

        // Apply rounded card styling and elevation (Android 5.0+ / API 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable miniBg = new GradientDrawable();
            miniBg.setColor(Color.BLACK);
            miniBg.setCornerRadius(dp(12));
            miniBg.setStroke(dp(1), Color.argb(90, 255, 255, 255));
            playerContainer.setBackground(miniBg);
            playerContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            playerContainer.setClipToOutline(true);
            playerContainer.setElevation(dp(10));
        }

        // Detach from rootLayout
        if (playerContainer.getParent() != null) {
            ((ViewGroup) playerContainer.getParent()).removeView(playerContainer);
        }

        // In rootLayout, inputScroll takes full remaining height below topBar
        if (inputScroll != null) {
            inputScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        }

        // Float at bottom-right inside mainAppContainer
        int miniW = dp(210);
        int miniH = dp(118);
        FrameLayout.LayoutParams miniParams = new FrameLayout.LayoutParams(miniW, miniH, Gravity.BOTTOM | Gravity.END);
        miniParams.rightMargin = dp(16);
        miniParams.bottomMargin = dp(24) + navBottomInset;
        playerContainer.setLayoutParams(miniParams);

        mainAppContainer.addView(playerContainer, miniParams);
        playerContainer.setVisibility(View.VISIBLE);
        updatePipParams();

        // Show mini controls overlay with auto-hide
        if (miniControlsOverlay != null) {
            miniControlsOverlay.setVisibility(View.VISIBLE);
            showMiniControls();
        }

        setStatus("Mini Player active");
    }

    private void exitInAppMiniPlayer() {
        if (!isMiniPlayerActive || playerContainer == null || rootLayout == null) {
            return;
        }

        isMiniPlayerActive = false;

        // Reset translation
        playerContainer.setTranslationX(0);
        playerContainer.setTranslationY(0);

        // Hide mini controls
        hideMiniControls();
        if (miniControlsOverlay != null) {
            miniControlsOverlay.setVisibility(View.GONE);
        }

        // Restore normal background and clear elevation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playerContainer.setBackgroundColor(Color.BLACK);
            playerContainer.setOutlineProvider(null);
            playerContainer.setClipToOutline(false);
            playerContainer.setElevation(0);
        }

        // Detach from mainAppContainer
        if (playerContainer.getParent() != null) {
            ((ViewGroup) playerContainer.getParent()).removeView(playerContainer);
        }

        // Re-attach into rootLayout at index 1 (between topBarLayout and inputScroll)
        LinearLayout.LayoutParams normalParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.30f
        );
        playerContainer.setLayoutParams(normalParams);

        int targetIndex = 1;
        if (rootLayout.getChildCount() < 1) {
            targetIndex = 0;
        } else if (targetIndex > rootLayout.getChildCount()) {
            targetIndex = rootLayout.getChildCount();
        }
        rootLayout.addView(playerContainer, targetIndex, normalParams);

        // Restore inputScroll weight to 0.70f
        if (inputScroll != null) {
            inputScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 0.70f));
        }

        if (customControlsOverlay != null) {
            customControlsOverlay.setVisibility(View.VISIBLE);
        }

        playerContainer.setVisibility(View.VISIBLE);

        updatePipParams();
        resetAutoHide();
        setStatus("Normal Mode");
    }

    private void closeMiniPlayer() {
        if (player != null) {
            player.stop();
        }

        if (isMiniPlayerActive) {
            isMiniPlayerActive = false;
            playerContainer.setTranslationX(0);
            playerContainer.setTranslationY(0);

            hideMiniControls();
            if (miniControlsOverlay != null) {
                miniControlsOverlay.setVisibility(View.GONE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                playerContainer.setBackgroundColor(Color.BLACK);
                playerContainer.setOutlineProvider(null);
                playerContainer.setClipToOutline(false);
                playerContainer.setElevation(0);
            }

            if (playerContainer.getParent() != null) {
                ((ViewGroup) playerContainer.getParent()).removeView(playerContainer);
            }

            // Put back into rootLayout hidden so view hierarchy is consistent
            LinearLayout.LayoutParams normalParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.30f
            );
            int targetIndex = Math.min(1, rootLayout.getChildCount());
            rootLayout.addView(playerContainer, targetIndex, normalParams);
        }

        if (playerContainer != null) {
            playerContainer.setVisibility(View.GONE);
            updatePipParams();
        }

        if (inputScroll != null) {
            inputScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        }

        hideEpisodeButtons();
        setStatus("Player closed");
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
            Rational aspectRatio = new Rational(16, 9);
            if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                int vw = player.getVideoSize().width;
                int vh = player.getVideoSize().height;
                float r = (float) vw / (float) vh;
                if (r >= 0.42f && r <= 2.39f) {
                    aspectRatio = new Rational(vw, vh);
                }
            }

            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            Rect sourceRect = new Rect();
            playerView.getGlobalVisibleRect(sourceRect);
            if (!sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(canAutoEnterPip());
            }

            setPictureInPictureParams(builder.build());
        } catch (Exception ignored) {}
    }

    private void setPlayerContainerMatchParent() {
        if (playerContainer == null) return;
        ViewParent parent = playerContainer.getParent();
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            );
            playerContainer.setLayoutParams(flp);
        } else if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            );
            llp.weight = 0;
            playerContainer.setLayoutParams(llp);
        } else {
            ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                playerContainer.setLayoutParams(lp);
            }
        }
    }

    // Auto PiP only when video is really playing and visible.
    private boolean canAutoEnterPip() {
        if (player == null || playerContainer == null) return false;
        if (playerContainer.getVisibility() != View.VISIBLE) return false;
        if (player.getMediaItemCount() == 0) return false;
        int state = player.getPlaybackState();
        if (state == androidx.media3.common.Player.STATE_IDLE
                || state == androidx.media3.common.Player.STATE_ENDED) return false;
        return player.isPlaying();
    }

    private void enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        if (!canAutoEnterPip()) {
            return;
        }
        try {
            wasMiniPlayerBeforePip = isMiniPlayerActive;
            wasFullscreenBeforePip = fullscreen;
            applyFillResizeMode();

            // Reset orientation so PiP transitions cleanly
            if (fullscreen) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }

            // Immediately hide everything except video view
            if (splashLayout != null) splashLayout.setVisibility(View.GONE);
            if (topBarLayout != null) topBarLayout.setVisibility(View.GONE);
            if (inputScroll != null) inputScroll.setVisibility(View.GONE);
            if (customControlsOverlay != null) customControlsOverlay.setVisibility(View.GONE);
            if (bottomPanel != null) bottomPanel.setVisibility(View.GONE);
            if (gestureHudCard != null) gestureHudCard.setVisibility(View.GONE);
            hideMiniControls();
            if (miniControlsOverlay != null) miniControlsOverlay.setVisibility(View.GONE);

            playerContainer.setTranslationX(0);
            playerContainer.setTranslationY(0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                playerContainer.setOutlineProvider(null);
                playerContainer.setClipToOutline(false);
                playerContainer.setElevation(0);
                playerContainer.setBackgroundColor(Color.BLACK);
            }

            setPlayerContainerMatchParent();
            playerContainer.setVisibility(View.VISIBLE);
            updatePipParams();

            Rational aspectRatio = new Rational(16, 9);
            if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                int vw = player.getVideoSize().width;
                int vh = player.getVideoSize().height;
                float r = (float) vw / (float) vh;
                if (r >= 0.42f && r <= 2.39f) {
                    aspectRatio = new Rational(vw, vh);
                }
            }

            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            Rect sourceRect = new Rect();
            playerView.getGlobalVisibleRect(sourceRect);
            if (!sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(canAutoEnterPip());
            }

            enterPictureInPictureMode(builder.build());

        } catch (Exception e) {
            restoreLayoutFromPip();
            toast("PiP error: " + e.getMessage());
        }
    }

    private void restoreLayoutFromPip() {
        if (wasMiniPlayerBeforePip) {
            wasMiniPlayerBeforePip = false;
            isMiniPlayerActive = false; // reset flag so enterInAppMiniPlayer() re-attaches cleanly
            enterInAppMiniPlayer();
            if (topBarLayout != null) topBarLayout.setVisibility(View.VISIBLE);
            if (inputScroll != null) inputScroll.setVisibility(View.VISIBLE);
            applyFullscreenSystemUi(false);
            return;
        }

        // Ensure playerContainer is inside rootLayout
        if (playerContainer.getParent() != rootLayout) {
            if (playerContainer.getParent() != null) {
                ((ViewGroup) playerContainer.getParent()).removeView(playerContainer);
            }
            rootLayout.addView(playerContainer, Math.min(1, rootLayout.getChildCount()));
        }

        fullscreen = wasFullscreenBeforePip;
        if (fullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (topBarLayout != null) topBarLayout.setVisibility(View.GONE);
            if (inputScroll != null) inputScroll.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            playerContainer.setLayoutParams(lp);
            applyFullscreenSystemUi(true);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (topBarLayout != null) topBarLayout.setVisibility(View.VISIBLE);
            if (inputScroll != null) {
                inputScroll.setVisibility(View.VISIBLE);
                inputScroll.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.70f));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.30f
            );
            playerContainer.setLayoutParams(lp);
            applyFullscreenSystemUi(false);
        }
        if (customControlsOverlay != null) {
            customControlsOverlay.setVisibility(View.VISIBLE);
        }
        showControls();
        updateFullscreenButton();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            wasMiniPlayerBeforePip = isMiniPlayerActive;
            wasFullscreenBeforePip = fullscreen;
            applyFillResizeMode();

            if (splashLayout != null) splashLayout.setVisibility(View.GONE);
            if (topBarLayout != null) topBarLayout.setVisibility(View.GONE);
            if (inputScroll != null) inputScroll.setVisibility(View.GONE);
            if (customControlsOverlay != null) customControlsOverlay.setVisibility(View.GONE);
            if (bottomPanel != null) bottomPanel.setVisibility(View.GONE);
            if (gestureHudCard != null) gestureHudCard.setVisibility(View.GONE);
            hideMiniControls();
            if (miniControlsOverlay != null) miniControlsOverlay.setVisibility(View.GONE);

            playerContainer.setTranslationX(0);
            playerContainer.setTranslationY(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                playerContainer.setOutlineProvider(null);
                playerContainer.setClipToOutline(false);
                playerContainer.setElevation(0);
                playerContainer.setBackgroundColor(Color.BLACK);
            }
            setPlayerContainerMatchParent();
        } else {
            // Restored from PiP
            restoreLayoutFromPip();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Auto-enter PiP when Home button/gesture is pressed while video is playing (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePipParams();
            if (autoPipEnabled && canAutoEnterPip()) {
                enterPictureInPicture();
            }
        }
    }

    // ==========================================
    // WEBVIEW BRIDGE & DYNAMIC MULTI-LINK BUTTONS
    // ==========================================

    private static class EpisodeItem {
        final String title;
        final String url;

        EpisodeItem(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private class HtmlItemBridge {
        @JavascriptInterface
        public void setWebViewString(String value) {
            if (value == null) return;
            runOnUiThread(() -> handleWebViewData(value));
        }
    }

    private void handleWebViewData(String raw) {
        if (raw == null) return;
        String clean = raw.trim();

        // Check if the link contains Playlist>
        if ("ACTION>RETRY".equalsIgnoreCase(clean)) {
            setStatus("Retrying content load...");
            preloadHtmlTemplates();
            return;
        }

        if (clean.contains("Playlist>")) {
            String targetUrl = clean.replace("Playlist>", "").trim();
            if (urlInput != null) {
                urlInput.setText(targetUrl);
                urlInput.setSelection(urlInput.getText().length());
            }
            hideEpisodeButtons();
            // Video does NOT stop and does NOT hide. If active, it shrinks into the In-App Miniplayer!
            if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                if (!isMiniPlayerActive) {
                    enterInAppMiniPlayer();
                }
            } else {
                if (playerContainer != null) {
                    playerContainer.setVisibility(View.GONE);
                    updatePipParams();
                }
            }
            loadPlaylistHtml(targetUrl);
            return;
        }

        // Multi-link parsing: check for ,, and ,) separators
        if (clean.contains(",,") && (clean.contains(",)") || clean.contains(","))) {
            String[] rawItems = clean.split(",\\)");
            List<EpisodeItem> episodes = new ArrayList<>();
            for (String it : rawItems) {
                it = it.trim();
                if (it.isEmpty()) continue;
                String[] parts = it.split(",,", 2);
                if (parts.length == 2) {
                    String title = parts[0].trim();
                    String link = parts[1].trim();
                    if (!title.isEmpty() && !link.isEmpty()) {
                        episodes.add(new EpisodeItem(title, link));
                    }
                }
            }

            if (!episodes.isEmpty()) {
                renderEpisodeButtons(episodes);
                return;
            }
        }

        // Single link fallback: put link into urlInput, reveal player, and auto-play
        hideEpisodeButtons();
        if (!clean.isEmpty()) {
            if (urlInput != null) {
                urlInput.setText(clean);
                urlInput.setSelection(urlInput.getText().length());
            }
            setStatus("Playing from web content");
            if (playerContainer != null) {
                playerContainer.setVisibility(View.VISIBLE);
                updatePipParams();
            }
            playStream();
        }
    }

    private void renderEpisodeButtons(List<EpisodeItem> episodes) {
        if (episodesContainer == null || episodesScroll == null) return;

        episodesContainer.removeAllViews();
        episodeButtonViews.clear();

        for (int i = 0; i < episodes.size(); i++) {
            final int index = i;
            final EpisodeItem item = episodes.get(i);

            TextView btn = new TextView(this);
            btn.setText(item.title);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(14), dp(8), dp(14), dp(8));
            btn.setClickable(true);
            btn.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dp(8);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> selectEpisode(episodes, index));

            episodesContainer.addView(btn);
            episodeButtonViews.add(btn);
        }

        episodesScroll.setVisibility(View.VISIBLE);

        // Select the first episode by default and output its URL
        selectEpisode(episodes, 0);
    }

    private void selectEpisode(List<EpisodeItem> episodes, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= episodes.size()) return;

        EpisodeItem selectedItem = episodes.get(selectedIndex);

        // Highlight selected button, dim others
        for (int i = 0; i < episodeButtonViews.size(); i++) {
            TextView btn = episodeButtonViews.get(i);
            boolean isSelected = (i == selectedIndex);

            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(14));
            gd.setColor(isSelected ? PRIMARY_COLOR : BUTTON_COLOR);
            btn.setBackground(gd);
            btn.setTextColor(isSelected ? Color.WHITE : Color.rgb(200, 210, 225));
        }

        String itemUrl = (selectedItem.url != null) ? selectedItem.url.trim() : "";
        if (itemUrl.contains("Playlist>")) {
            String targetUrl = itemUrl.replace("Playlist>", "").trim();
            if (urlInput != null) {
                urlInput.setText(targetUrl);
                urlInput.setSelection(urlInput.getText().length());
            }
            setStatus("Selected: " + selectedItem.title);
            if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                if (!isMiniPlayerActive) {
                    enterInAppMiniPlayer();
                }
            } else {
                if (playerContainer != null) {
                    playerContainer.setVisibility(View.GONE);
                    updatePipParams();
                }
            }
            loadPlaylistHtml(targetUrl);
            return;
        }

        // Put URL into urlInput
        if (urlInput != null) {
            urlInput.setText(itemUrl);
            urlInput.setSelection(urlInput.getText().length());
        }

        setStatus("Selected: " + selectedItem.title);

        // Make player visible and auto-play selected episode
        if (playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
            updatePipParams();
        }
        playStream();
    }

    private void hideEpisodeButtons() {
        if (episodesContainer != null) {
            episodesContainer.removeAllViews();
        }
        episodeButtonViews.clear();
        if (episodesScroll != null) {
            episodesScroll.setVisibility(View.GONE);
        }
    }

    // ==========================================
    // TOP BAR SEARCH CONTROLS
    // ==========================================

    private void toggleSearch() {
        if (isSearchOpen) {
            closeSearch();
        } else {
            openSearch();
        }
    }

    private void openSearch() {
        isSearchOpen = true;
        if (titleBar != null) {
            titleBar.setVisibility(View.GONE);
        }
        if (searchEditText != null) {
            searchEditText.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        if (searchToggleBtn != null) {
            applySearchToggleIcon(true);
        }
    }

    // Shows the search icon when closed, and a ✕ when open
    private void applySearchToggleIcon(boolean isOpen) {
        if (searchToggleIcon != null) searchToggleIcon.setVisibility(isOpen ? View.GONE : View.VISIBLE);
        if (searchToggleClose != null) searchToggleClose.setVisibility(isOpen ? View.VISIBLE : View.GONE);
    }

    private void closeSearch() {
        isSearchOpen = false;
        if (searchEditText != null) {
            searchEditText.setText("");
            searchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
            searchEditText.setVisibility(View.GONE);
        }
        if (titleBar != null) {
            titleBar.setVisibility(View.VISIBLE);
        }
        if (searchToggleBtn != null) {
            applySearchToggleIcon(false);
        }
        filterWebView("");
    }

    private void configureWebViewSettings(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }
        wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        wv.setBackgroundColor(Color.rgb(13, 17, 23));
        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Guarantee the EventCode.html loader container is dismissed even if external CDN images hang
                String js = "(function(){ var el = document.getElementById('loader-container'); if(el) el.style.display='none'; })();";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    view.evaluateJavascript(js, null);
                } else {
                    view.loadUrl("javascript:" + js);
                }
            }
        });
    }

    private void filterWebView(String query) {
        WebView target = isPlaylistViewActive ? playlistWebView : eventWebView;
        if (target == null) return;
        String cleanQuery = (query != null)
                ? query.replace("\\", "\\\\").replace("'", "\\'").trim()
                : "";
        String js = "if (typeof filterMatches === 'function') { filterMatches('" + cleanQuery + "'); }";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            target.evaluateJavascript(js, null);
        } else {
            target.loadUrl("javascript:" + js);
        }
    }

    private void saveHtmlToLocal(String filename, String content) {
        if (content == null || content.trim().isEmpty()) return;
        try {
            File file = new File(getFilesDir(), filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private String loadHtmlFromLocal(String filename) {
        try {
            File file = new File(getFilesDir(), filename);
            if (!file.exists() || file.length() == 0) return null;
            byte[] buffer = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read = fis.read(buffer);
                if (read > 0) {
                    return new String(buffer, 0, read, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getErrorHtml(String message) {
        return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<style>body{background:#0d1117;color:#c9d1d9;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:80vh;margin:0;padding:20px;text-align:center;box-sizing:border-box;}"
                + "h3{color:#fff;margin:0 0 10px 0;font-size:18px;}p{color:#8b949e;font-size:13px;margin:0 0 20px 0;line-height:1.4;}"
                + "button{background:#10b981;color:#fff;border:none;padding:12px 28px;font-size:14px;font-weight:bold;border-radius:8px;cursor:pointer;outline:none;}</style></head>"
                + "<body><h3>Failed to load home content</h3><p>"
                + (message != null ? message.replace("<", "&lt;").replace(">", "&gt;") : "Network connection error")
                + "</p><button onclick=\"if(window.AppInventor)window.AppInventor.setWebViewString('ACTION>RETRY');else if(window.CustomWebView)window.CustomWebView.setWebViewString('ACTION>RETRY');else location.reload();\">Tap to Retry</button></body></html>";
    }

    private void preloadHtmlTemplates() {
        isPlaylistViewActive = false;
        htmlLoadThread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build();

            // 1. Fetch EventCode.html
            try {
                Request request = new Request.Builder().url(EVENT_HTML_URL).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        final String eventHtml = response.body().string();
                        saveHtmlToLocal(CACHED_EVENT_HTML_FILE, eventHtml);
                        runOnUiThread(() -> {
                            if (eventWebView != null && !isFinishing()) {
                                if (!isEventHtmlLoaded || !isSplashDismissed) {
                                    isEventHtmlLoaded = true;
                                    eventWebView.loadDataWithBaseURL(
                                            "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/",
                                            eventHtml,
                                            "text/html",
                                            "UTF-8",
                                            null
                                    );
                                }
                                onHtmlContentLoaded();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isEventHtmlLoaded && eventWebView != null && !isFinishing()) {
                        eventWebView.loadDataWithBaseURL(
                                "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/",
                                getErrorHtml("Could not load content: " + e.getMessage()),
                                "text/html",
                                "UTF-8",
                                null
                        );
                    }
                    dismissSplashScreen();
                });
            }

            // 2. Fetch Playlist.html template in background and save to local storage
            try {
                Request plRequest = new Request.Builder().url(PLAYLIST_HTML_URL).build();
                try (Response plResp = client.newCall(plRequest).execute()) {
                    if (plResp.isSuccessful() && plResp.body() != null) {
                        final String plHtml = plResp.body().string();
                        cachedPlaylistTemplate = plHtml;
                        saveHtmlToLocal(CACHED_PLAYLIST_HTML_FILE, plHtml);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "templates-preloader");
        htmlLoadThread.start();
    }

    private void loadPlaylistHtml(final String targetUrl) {
        setStatus("Loading playlist...");

        if (cachedPlaylistTemplate != null && !cachedPlaylistTemplate.isEmpty()) {
            applyAndDisplayPlaylistHtml(cachedPlaylistTemplate, targetUrl);
            return;
        }

        String localTemplate = loadHtmlFromLocal(CACHED_PLAYLIST_HTML_FILE);
        if (localTemplate != null && !localTemplate.isEmpty()) {
            cachedPlaylistTemplate = localTemplate;
            applyAndDisplayPlaylistHtml(localTemplate, targetUrl);
            return;
        }

        playlistLoadThread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder().url(PLAYLIST_HTML_URL).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                String htmlTemplate = response.body().string();
                cachedPlaylistTemplate = htmlTemplate;
                saveHtmlToLocal(CACHED_PLAYLIST_HTML_FILE, htmlTemplate);
                runOnUiThread(() -> applyAndDisplayPlaylistHtml(htmlTemplate, targetUrl));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Could not load playlist: " + e.getMessage());
                    toast("Playlist error: " + e.getMessage());
                });
            }
        }, "playlist-html-loader");
        playlistLoadThread.start();
    }

    private void applyAndDisplayPlaylistHtml(String template, String targetUrl) {
        if (playlistWebView == null || isFinishing()) return;

        String finalHtml = template.replace("INPUT URL", targetUrl);
        isPlaylistViewActive = true;
        eventWebView.setVisibility(View.GONE);
        playlistWebView.setVisibility(View.VISIBLE);
        playlistWebView.loadDataWithBaseURL(
                "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/",
                finalHtml,
                "text/html",
                "UTF-8",
                null
        );
        setStatus("Playlist loaded");
    }

    private void closePlaylistTab() {
        isPlaylistViewActive = false;
        if (playlistWebView != null) {
            playlistWebView.setVisibility(View.GONE);
        }
        if (eventWebView != null) {
            eventWebView.setVisibility(View.VISIBLE);
        }
        setStatus("Ready");
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

    // ==========================================
    // SPLASH SCREEN & DEEP LINK INTENT HANDLING
    // ==========================================

    private void buildSplashScreen(FrameLayout container) {
        splashLayout = new FrameLayout(this);
        splashLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        splashLayout.setClickable(true);
        splashLayout.setFocusable(true);

        // Elegant dark gradient background matching screenshot
        GradientDrawable splashBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(12, 16, 28),
                        Color.rgb(8, 11, 20),
                        Color.rgb(4, 6, 12)
                }
        );
        splashLayout.setBackground(splashBg);

        // Center group: App Icon, Title, Subtitle, Progress Bar
        LinearLayout centerGroup = new LinearLayout(this);
        centerGroup.setOrientation(LinearLayout.VERTICAL);
        centerGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(-2, -2);
        centerParams.gravity = Gravity.CENTER;
        splashLayout.addView(centerGroup, centerParams);

        // 1. App Icon Card (Squircle / Rounded White Container)
        FrameLayout iconCard = new FrameLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(108), dp(108));
        cardParams.bottomMargin = dp(20);
        iconCard.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(28));
        iconCard.setBackground(cardBg);

        ImageView appIconView = new ImageView(this);
        int iconRes = getResources().getIdentifier("app_icon", "drawable", getPackageName());
        if (iconRes == 0) {
            iconRes = getApplicationInfo().icon;
        }
        if (iconRes != 0) {
            appIconView.setImageResource(iconRes);
        }
        appIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(76), dp(76));
        iconParams.gravity = Gravity.CENTER;
        iconCard.addView(appIconView, iconParams);

        centerGroup.addView(iconCard);

        // 2. App Name: "RasVibe"
        TextView splashTitle = new TextView(this);
        splashTitle.setText("RasVibe");
        splashTitle.setTextColor(Color.WHITE);
        splashTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        splashTitle.setTypeface(null, Typeface.BOLD);
        splashTitle.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            splashTitle.setLetterSpacing(0.02f);
        }
        centerGroup.addView(splashTitle, new LinearLayout.LayoutParams(-2, -2));

        // 3. Subtitle / Tagline: "Stream More. Watch Better."
        TextView splashSubtitle = new TextView(this);
        splashSubtitle.setText("Stream More. Watch Better.");
        splashSubtitle.setTextColor(Color.argb(200, 180, 195, 215));
        splashSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        splashSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-2, -2);
        subtitleParams.topMargin = dp(8);
        centerGroup.addView(splashSubtitle, subtitleParams);

        // 4. Stylish Circular Progress Bar
        ProgressBar splashProgressBar = new ProgressBar(this);
        splashProgressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            splashProgressBar.setIndeterminateTintList(
                    ColorStateList.valueOf(Color.rgb(85, 194, 255))
            );
        }
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.topMargin = dp(42);
        centerGroup.addView(splashProgressBar, progressParams);

        // Bottom footer layout (Version & Thank you note)
        LinearLayout bottomGroup = new LinearLayout(this);
        bottomGroup.setOrientation(LinearLayout.VERTICAL);
        bottomGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-2, -2);
        bottomParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomParams.bottomMargin = dp(36);
        splashLayout.addView(bottomGroup, bottomParams);

        // Version text
        TextView versionText = new TextView(this);
        versionText.setText("VERSION 1.0");
        versionText.setTextColor(Color.argb(230, 255, 255, 255));
        versionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        versionText.setTypeface(null, Typeface.BOLD);
        versionText.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            versionText.setLetterSpacing(0.08f);
        }
        bottomGroup.addView(versionText, new LinearLayout.LayoutParams(-2, -2));

        // Thank you text
        TextView thanksText = new TextView(this);
        thanksText.setText("Thank you for choosing RasVibe");
        thanksText.setTextColor(Color.argb(160, 160, 175, 195));
        thanksText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        thanksText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams thanksParams = new LinearLayout.LayoutParams(-2, -2);
        thanksParams.topMargin = dp(6);
        bottomGroup.addView(thanksText, thanksParams);

        // Allow user to tap anywhere to dismiss instantly
        splashLayout.setOnClickListener(v -> dismissSplashScreen());

        container.addView(splashLayout);
    }

    private void scheduleSplashDismiss() {
        splashStartTime = System.currentTimeMillis();
        // Maximum fallback timeout of 4000ms if network is offline or slow
        handler.postDelayed(splashDismissRunnable, 4000);
    }

    private void onHtmlContentLoaded() {
        if (isSplashDismissed) return;
        long elapsed = System.currentTimeMillis() - splashStartTime;
        long minDisplayTime = 1800; // Minimum splash display time for branding
        if (elapsed >= minDisplayTime) {
            dismissSplashScreen();
        } else {
            handler.removeCallbacks(splashDismissRunnable);
            handler.postDelayed(splashDismissRunnable, minDisplayTime - elapsed);
        }
    }

    private void dismissSplashScreen() {
        if (isSplashDismissed || splashLayout == null) return;
        isSplashDismissed = true;
        handler.removeCallbacks(splashDismissRunnable);
        splashLayout.animate()
                .alpha(0f)
                .setDuration(450)
                .withEndAction(() -> {
                    if (splashLayout != null) {
                        splashLayout.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String streamUrl = null;
        Uri data = intent.getData();
        if (data != null) {
            streamUrl = data.toString();
        } else if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                String trimmed = sharedText.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")
                        || trimmed.startsWith("rtsp://") || trimmed.startsWith("rtmp://")
                        || trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                    streamUrl = trimmed;
                }
            }
        }

        if (streamUrl != null && !streamUrl.trim().isEmpty()) {
            final String finalUrl = streamUrl.trim();
            dismissSplashScreen();
            runOnUiThread(() -> playStreamWithUrl(finalUrl));
        }
    }

    private void playStreamWithUrl(String streamUrl) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) return;
        hideEpisodeButtons();
        String cleanUrl = streamUrl.trim();
        if (urlInput != null) {
            urlInput.setText(cleanUrl);
            urlInput.setSelection(urlInput.getText().length());
        }
        setStatus("Playing external stream");
        if (playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
            updatePipParams();
        }
        playStream();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacksAndMessages(null);
        if (splashDismissRunnable != null) {
            handler.removeCallbacks(splashDismissRunnable);
        }

        if (htmlLoadThread != null) {
            htmlLoadThread.interrupt();
            htmlLoadThread = null;
        }
        if (playlistLoadThread != null) {
            playlistLoadThread.interrupt();
            playlistLoadThread = null;
        }
        if (playlistWebView != null) {
            playlistWebView.removeJavascriptInterface("CustomWebView");
            playlistWebView.removeJavascriptInterface("AppInventor");
            playlistWebView.destroy();
            playlistWebView = null;
        }
        if (eventWebView != null) {
            eventWebView.removeJavascriptInterface("CustomWebView");
            eventWebView.removeJavascriptInterface("AppInventor");
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
