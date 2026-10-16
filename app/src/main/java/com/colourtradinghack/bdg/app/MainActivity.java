package com.colourtradinghack.bdg.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Hosts the website in a WebView. Every option you chose in Site2App lives in AppConfig.java.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_NOTIFICATIONS = 2001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private SwipeRefreshLayout swipe;
    private FrameLayout splash;
    private ValueCallback<Uri[]> filePathCallback;
    private long startedAt;
    private boolean splashHidden = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        super.onCreate(savedInstanceState);
        startedAt = System.currentTimeMillis();

        if (AppConfig.KEEP_SCREEN_ON) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFFFFFFF);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView(web);

        if (AppConfig.PULL_TO_REFRESH) {
            swipe = new SwipeRefreshLayout(this);
            swipe.addView(web);
            swipe.setColorSchemeColors(AppConfig.BRAND_COLOR);
            swipe.setOnRefreshListener(() -> web.reload());
            // Only start a refresh when the page itself is scrolled to the top.
            swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0);
            root.addView(swipe, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.addView(web);
        }

        if (AppConfig.SPLASH_ENABLED) {
            splash = new FrameLayout(this);
            splash.setBackgroundColor(AppConfig.SPLASH_COLOR);
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.splash_logo);
            int size = (int) (160 * getResources().getDisplayMetrics().density);
            splash.addView(logo, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            root.addView(splash, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Safety net: never leave the splash up if a page hangs.
            handler.postDelayed(this::hideSplash, 12000);
        }

        setContentView(root);
        applySystemUi();
        prepareNotifications();

        boolean restored = savedInstanceState != null && web.restoreState(savedInstanceState) != null;
        if (!restored && !loadFromIntent(getIntent())) {
            web.loadUrl(AppConfig.START_URL);
        }
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(AppConfig.ZOOM);
        s.setBuiltInZoomControls(AppConfig.ZOOM);
        s.setDisplayZoomControls(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(w, true);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleLink(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (swipe != null) swipe.setRefreshing(false);
                hideSplash();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if (swipe != null) swipe.setRefreshing(false);
                    showError(view);
                }
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Files the page offers for download are handed to the device (browser or download app).
        w.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                openExternal(Uri.parse(url)));
    }

    private void showError(WebView v) {
        String html = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;display:flex;min-height:100vh;align-items:center;"
                + "justify-content:center;font-family:sans-serif;text-align:center;color:#333;background:#fafafa\">"
                + "<div style=\"padding:24px\"><h2 style=\"margin:0 0 8px\">Can't connect</h2>"
                + "<p style=\"margin:0 0 20px;color:#666\">Check your internet connection and try again.</p>"
                + "<a href=\"" + AppConfig.START_URL + "\" style=\"display:inline-block;padding:12px 22px;"
                + "border-radius:8px;background:#" + String.format("%06X", AppConfig.BRAND_COLOR & 0xFFFFFF)
                + ";color:#fff;text-decoration:none\">Try again</a></div></body></html>";
        v.loadDataWithBaseURL(AppConfig.START_URL, html, "text/html", "UTF-8", null);
    }

    // ------------------------------------------------------------ Link rules

    /** Returns true when the app handled the link itself (opened externally). */
    private boolean handleLink(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if (scheme.equals("about") || scheme.equals("javascript") || scheme.equals("blob")
                || scheme.equals("data")) {
            return false;
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            openExternal(uri); // tel:, mailto:, sms:, geo:, market:, intent: and app deep links
            return true;
        }
        if (opensExternally(uri)) {
            openExternal(uri);
            return true;
        }
        return false;
    }

    private boolean opensExternally(Uri uri) {
        String url = uri.toString();
        String host = uri.getHost() == null ? "" : uri.getHost();
        for (String[] rule : AppConfig.LINK_RULES) {
            if (ruleMatches(rule[0], rule[1], url, host)) {
                return "external".equals(rule[2]);
            }
        }
        if (AppConfig.SAME_DOMAIN_INTERNAL && hostMatches(host, startHost())) {
            return false;
        }
        return "external".equals(AppConfig.DEFAULT_LINK_ACTION);
    }

    private static boolean ruleMatches(String type, String value, String url, String host) {
        switch (type) {
            case "domain":
                return hostMatches(host, value);
            case "startswith":
                return stripScheme(url).startsWith(stripScheme(value));
            case "contains":
                return url.toLowerCase().contains(value.toLowerCase());
            case "regex":
                try {
                    return Pattern.compile(value).matcher(url).find();
                } catch (Exception e) {
                    return false;
                }
            default:
                return false;
        }
    }

    private static String stripScheme(String s) {
        s = s.trim().toLowerCase();
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("www.")) s = s.substring(4);
        return s;
    }

    private static String cleanHost(String v) {
        v = stripScheme(v);
        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);
        if (v.startsWith("*.")) v = v.substring(2);
        return v;
    }

    private static boolean hostMatches(String host, String value) {
        String h = cleanHost(host);
        String v = cleanHost(value);
        if (v.isEmpty()) return false;
        return h.equals(v) || h.endsWith("." + v);
    }

    private static String startHost() {
        String h = Uri.parse(AppConfig.START_URL).getHost();
        return h == null ? "" : h;
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | URISyntaxException e) {
            // Nothing on this device can open the link.
        }
    }

    // ------------------------------------------------------------- Splash/UI

    private void hideSplash() {
        if (splash == null || splashHidden) return;
        splashHidden = true;
        long elapsed = System.currentTimeMillis() - startedAt;
        long wait = Math.max(0, AppConfig.SPLASH_MIN_MS - elapsed);
        handler.postDelayed(() -> splash.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> splash.setVisibility(View.GONE)).start(), wait);
    }

    private void applySystemUi() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (AppConfig.FULLSCREEN) {
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            getWindow().setStatusBarColor(AppConfig.BRAND_COLOR);
            controller.setAppearanceLightStatusBars(ColorUtils.calculateLuminance(AppConfig.BRAND_COLOR) > 0.5);
        }
    }

    private void prepareNotifications() {
        if (!AppConfig.FIREBASE) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "default_channel", "Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATIONS);
        }
    }

    /** Opens the "url" extra sent with a push notification, if it belongs to this site. */
    private boolean loadFromIntent(Intent intent) {
        if (intent == null) return false;
        String target = intent.getStringExtra("url");
        if (target == null) return false;
        Uri u = Uri.parse(target);
        String scheme = u.getScheme();
        if (("http".equals(scheme) || "https".equals(scheme))
                && u.getHost() != null && hostMatches(u.getHost(), startHost())) {
            web.loadUrl(target);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------- Lifecycle

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && AppConfig.FULLSCREEN) applySystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadFromIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
