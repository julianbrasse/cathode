package com.cathode.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar pageProgress;
    private LinearLayout bottomBar;
    private LinearLayout bookmarksPanel;
    private LinearLayout bookmarksRow1;
    private LinearLayout bookmarksRow2;
    private FrameLayout searchContainer;
    private EditText urlEdit;
    private ImageButton btnExit;
    private ImageButton btnClear;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnGo;
    private Button btnBookmarks;
    private ImageView virtualCursor;
    private TextView hudToast;

    private FrameLayout exitDialogContainer;
    private Button btnExitConfirm;
    private Button btnExitCancel;

    private int currentCursorResId = R.drawable.cursor_pointer;
    private int edgeScrollTicks = 0;
    private RectF activeModalRect = null;
    private boolean containerCanScrollUp = true;
    private boolean containerCanScrollDown = true;
    private boolean isInsideIframe = false;
    private long lastModalCheckTime = 0;

    private float cursorX = 960f;
    private float cursorY = 540f;
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    private boolean isBarVisible = false;
    private boolean isSearchingAtTop = false;
    private boolean isNavBarMode = false;

    private Handler handler;
    private Runnable hideBarRunnable;
    private Runnable hideHudRunnable;
    private SharedPreferences prefs;

    private static final String PREF_BOOKMARKS = "bookmarks_list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("cathode_browser_prefs", MODE_PRIVATE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1920;
        screenHeight = dm.heightPixels > 0 ? dm.heightPixels : 1080;
        cursorX = screenWidth / 2f;
        cursorY = screenHeight / 2f;

        initViews();
        setupWebView();
        setupBottomBar();
        setupBookmarks();

        loadUrl("https://www.google.com");
        handleIntent(getIntent());

        // Default state: web content has focus, navbar hidden
        webView.requestFocus();
        hideBottomBar();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        exitNavBarMode();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            loadUrl(intent.getDataString());
            return;
        }
        if (Intent.ACTION_SEARCH.equals(intent.getAction()) ||
            "android.intent.action.SEARCH".equals(intent.getAction()) ||
            intent.getBooleanExtra("open_search", false)) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    enterSearchMode();
                }
            }, 250);
        }
    }

    private void initViews() {
        webView = findViewById(R.id.web_view);
        pageProgress = findViewById(R.id.page_progress);
        if (pageProgress != null) {
            ColorStateList blueTint = ColorStateList.valueOf(Color.parseColor("#2979FF"));
            pageProgress.setProgressTintList(blueTint);
            pageProgress.setIndeterminateTintList(blueTint);
        }
        bottomBar = findViewById(R.id.bottom_bar);
        bookmarksPanel = findViewById(R.id.bookmarks_panel);
        bookmarksRow1 = findViewById(R.id.bookmarks_row1);
        bookmarksRow2 = findViewById(R.id.bookmarks_row2);
        searchContainer = findViewById(R.id.search_container);
        urlEdit = findViewById(R.id.url_edit);
        btnExit = findViewById(R.id.btn_exit);
        btnClear = findViewById(R.id.btn_clear);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnGo = findViewById(R.id.btn_go);
        btnBookmarks = findViewById(R.id.btn_bookmarks);
        virtualCursor = findViewById(R.id.virtual_cursor);
        hudToast = findViewById(R.id.hud_toast);

        exitDialogContainer = findViewById(R.id.exit_dialog_container);
        btnExitConfirm = findViewById(R.id.btn_exit_confirm);
        btnExitCancel = findViewById(R.id.btn_exit_cancel);

        virtualCursor.setX(cursorX);
        virtualCursor.setY(cursorY);

        hideBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSearchingAtTop && !isNavBarMode) {
                    hideBottomBar();
                }
            }
        };

        hideHudRunnable = new Runnable() {
            @Override
            public void run() {
                hudToast.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        hudToast.setVisibility(View.GONE);
                    }
                }).start();
            }
        };
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Modern high-res Desktop Chrome User Agent for rich CSS layouts
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageProgress.setVisibility(View.VISIBLE);
                urlEdit.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageProgress.setVisibility(View.GONE);
                urlEdit.setText(url);
                if (!isNavBarMode && !isSearchingAtTop) {
                    hideBottomBar();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                android.util.Log.d("CathodeConsole", cm.message());
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress.setProgress(newProgress);
                if (newProgress == 100) {
                    pageProgress.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupBottomBar() {
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExitDialog();
            }
        });

        btnExit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnBack.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) webView.goBack();
                isNavBarMode = true;
                virtualCursor.setVisibility(View.GONE);
                showBottomBar();
                btnBack.requestFocus();
            }
        });

        btnBack.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnExit.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnForward.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoForward()) webView.goForward();
                isNavBarMode = true;
                virtualCursor.setVisibility(View.GONE);
                showBottomBar();
                btnForward.requestFocus();
            }
        });

        btnForward.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnBack.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnRefresh.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
                isNavBarMode = true;
                virtualCursor.setVisibility(View.GONE);
                showBottomBar();
                btnRefresh.requestFocus();
            }
        });

        btnRefresh.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnForward.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        urlEdit.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearchOrNavigate();
            }
        });

        btnGo.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnClear.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnBookmarks.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                urlEdit.setText("");
                urlEdit.requestFocus();
            }
        });

        btnClear.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        urlEdit.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnGo.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        urlEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (searchContainer != null) {
                    searchContainer.setSelected(hasFocus);
                    searchContainer.setActivated(hasFocus);
                }
                if (hasFocus) {
                    showBottomBar();
                }
            }
        });

        urlEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterSearchMode();
            }
        });

        urlEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    performSearchOrNavigate();
                    return true;
                }
                return false;
            }
        });

        // Fast D-pad navigation across URL edit
        urlEdit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnRefresh.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnClear.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (isSearchingAtTop) {
                            exitSearchMode();
                        } else {
                            exitNavBarMode();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        enterSearchMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnBookmarks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBookmarksDrawer();
            }
        });

        btnBookmarks.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnGo.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (bookmarksPanel != null && bookmarksPanel.getVisibility() == View.VISIBLE) {
                            if (bookmarksRow2.getChildCount() > 0) {
                                bookmarksRow2.getChildAt(0).requestFocus();
                            } else if (bookmarksRow1.getChildCount() > 0) {
                                bookmarksRow1.getChildAt(0).requestFocus();
                            }
                        } else {
                            openBookmarksDrawer();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (bookmarksPanel != null && bookmarksPanel.getVisibility() == View.VISIBLE) {
                            closeBookmarksDrawer();
                        }
                        exitNavBarMode();
                        return true;
                    }
                }
                return false;
            }
        });

        btnExitConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
                System.exit(0);
            }
        });

        btnExitConfirm.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnExitCancel.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        hideExitDialog();
                        return true;
                    }
                }
                return false;
            }
        });

        btnExitCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideExitDialog();
            }
        });

        btnExitCancel.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnExitConfirm.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        hideExitDialog();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void enterNavBarMode(View targetView) {
        isNavBarMode = true;
        virtualCursor.setVisibility(View.GONE);
        bookmarksPanel.setVisibility(View.GONE);
        showBottomBar();
        if (targetView != null) {
            targetView.requestFocus();
        } else {
            urlEdit.requestFocus();
        }
    }

    private void enterSearchMode() {
        isSearchingAtTop = true;
        isNavBarMode = true;
        virtualCursor.setVisibility(View.GONE);
        bookmarksPanel.setVisibility(View.GONE);
        if (searchContainer != null) {
            searchContainer.setSelected(true);
            searchContainer.setActivated(true);
        }
        glideBarToTop();
        urlEdit.requestFocus();
        urlEdit.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(urlEdit, InputMethodManager.SHOW_FORCED);
    }

    private void exitSearchMode() {
        hideKeyboard();
        if (searchContainer != null) {
            searchContainer.setSelected(false);
            searchContainer.setActivated(false);
        }
        isSearchingAtTop = false;
        glideBarToBottom();
    }

    private void glideBarToBottom() {
        isSearchingAtTop = false;
        bottomBar.animate().translationY(0f).alpha(1f).setDuration(220).start();
    }

    private void exitNavBarMode() {
        isNavBarMode = false;
        hideBottomBar();
        if (bookmarksPanel != null) bookmarksPanel.setVisibility(View.GONE);
        urlEdit.clearFocus();
        if (bottomBar.hasFocus()) bottomBar.clearFocus();
        webView.requestFocus();
        virtualCursor.setVisibility(View.VISIBLE);
        edgeScrollTicks = 0;
        updateCursorIcon(R.drawable.cursor_pointer);
    }

    private void updateCursorIcon(int resId) {
        if (currentCursorResId != resId) {
            currentCursorResId = resId;
            virtualCursor.setImageResource(resId);
        }
    }

    private void showExitDialog() {
        exitDialogContainer.setVisibility(View.VISIBLE);
        exitDialogContainer.bringToFront();
        virtualCursor.setVisibility(View.GONE);
        hideKeyboard();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (btnExitCancel != null) {
                    btnExitCancel.requestFocus();
                }
            }
        }, 150);
    }

    private void hideExitDialog() {
        exitDialogContainer.setVisibility(View.GONE);
        if (isNavBarMode) {
            btnExit.requestFocus();
        } else {
            webView.requestFocus();
            virtualCursor.setVisibility(View.VISIBLE);
            updateCursorIcon(R.drawable.cursor_pointer);
        }
    }

    private void glideBarToTop() {
        isSearchingAtTop = true;
        isBarVisible = true;
        handler.removeCallbacks(hideBarRunnable);
        bookmarksPanel.setVisibility(View.GONE);

        float targetY = -(screenHeight - dpToPx(58) - dpToPx(48));
        bottomBar.animate().translationY(targetY).alpha(1f).setDuration(240).start();
    }

    private void performSearchOrNavigate() {
        String input = urlEdit.getText().toString().trim();
        if (input.isEmpty()) return;

        exitSearchMode();

        if (input.startsWith("http://") || input.startsWith("https://")) {
            loadUrl(input);
        } else if (input.contains(".") && !input.contains(" ")) {
            loadUrl("https://" + input);
        } else {
            loadUrl("https://www.google.com/search?q=" + android.net.Uri.encode(input));
        }
    }

    private void loadUrl(String url) {
        webView.loadUrl(url);
    }

    private void checkActiveModal(final float x, final float y) {
        long now = SystemClock.uptimeMillis();
        if (now - lastModalCheckTime < 200) return;
        lastModalCheckTime = now;

        String js = "(function(cx, cy) {" +
            "  var dpr = window.devicePixelRatio || 1;" +
            "  var el = document.elementFromPoint(cx / dpr, cy / dpr);" +
            "  while (el && el !== document.body && el !== document.documentElement) {" +
            "    var isTextarea = (el.tagName === 'TEXTAREA');" +
            "    var isIframe = (el.tagName === 'IFRAME');" +
            "    var s = window.getComputedStyle(el);" +
            "    var oy = s.overflowY || s.overflow;" +
            "    var isScrollable = (oy === 'scroll' || oy === 'auto') && (el.scrollHeight > el.clientHeight + 4);" +
            "    if (isTextarea || isIframe || isScrollable) {" +
            "      var r = el.getBoundingClientRect();" +
            "      var top = r.top * dpr;" +
            "      var bottom = r.bottom * dpr;" +
            "      var left = r.left * dpr;" +
            "      var right = r.right * dpr;" +
            "      var canUp = isIframe ? true : (el.scrollTop > 2);" +
            "      var canDown = isIframe ? true : (el.scrollTop + el.clientHeight < el.scrollHeight - 4);" +
            "      if (r.height * dpr < (window.innerHeight * dpr - 20) || r.width * dpr < (window.innerWidth * dpr - 20)) {" +
            "        return JSON.stringify({top: top, bottom: bottom, left: left, right: right, canUp: canUp, canDown: canDown, isIframe: isIframe});" +
            "      }" +
            "    }" +
            "    el = el.parentElement;" +
            "  }" +
            "  return '';" +
            "})(" + x + ", " + y + ");";

        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.equals("null") || value.equals("\"\"") || value.isEmpty()) {
                    if (activeModalRect != null && !activeModalRect.contains(x, y)) {
                        activeModalRect = null;
                        containerCanScrollUp = true;
                        containerCanScrollDown = true;
                        isInsideIframe = false;
                    }
                    return;
                }
                try {
                    String clean = value;
                    if (clean.startsWith("\"") && clean.endsWith("\"")) {
                        clean = clean.substring(1, clean.length() - 1).replace("\\\"", "\"");
                    }
                    JSONObject obj = new JSONObject(clean);
                    float top = (float) obj.getDouble("top");
                    float bottom = (float) obj.getDouble("bottom");
                    float left = (float) obj.getDouble("left");
                    float right = (float) obj.getDouble("right");
                    activeModalRect = new RectF(left, top, right, bottom);
                    containerCanScrollUp = obj.optBoolean("canUp", true);
                    containerCanScrollDown = obj.optBoolean("canDown", true);
                    isInsideIframe = obj.optBoolean("isIframe", false);
                } catch (Exception e) {
                    if (activeModalRect != null && !activeModalRect.contains(x, y)) {
                        activeModalRect = null;
                        containerCanScrollUp = true;
                        containerCanScrollDown = true;
                        isInsideIframe = false;
                    }
                }
            }
        });
    }

    private void scrollWeb(int dy) {
        long now = SystemClock.uptimeMillis();
        boolean insideModal = (activeModalRect != null && activeModalRect.contains(cursorX, cursorY));

        float targetX = cursorX;
        float targetY = cursorY;

        if (insideModal && activeModalRect != null) {
            targetX = activeModalRect.centerX();
            float safeTop = Math.max(activeModalRect.top, dpToPx(40));
            float safeBottom = (activeModalRect.bottom >= screenHeight - dpToPx(80)) ?
                    (screenHeight - dpToPx(72)) : activeModalRect.bottom;
            targetY = (safeTop + safeBottom) / 2f;
        }

        // Layer 1: Hardware mouse-wheel generic motion event (discrete, non-flinging, penetrates iframes natively)
        try {
            MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
            props[0] = new MotionEvent.PointerProperties();
            props[0].id = 0;
            props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
            coords[0] = new MotionEvent.PointerCoords();
            coords[0].x = targetX;
            coords[0].y = targetY;
            coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, dy > 0 ? -1.0f : 1.0f);

            MotionEvent scrollEvent = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1, props, coords,
                0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_MOUSE, 0
            );
            webView.dispatchGenericMotionEvent(scrollEvent);
            scrollEvent.recycle();
        } catch (Exception ignored) {}

        // Layer 2: Deterministic DOM scroll (exact pixel calibration for textareas, sub-containers, and root page)
        String js = "(function(cx, cy, dy) {" +
            "  var dpr = window.devicePixelRatio || 1;" +
            "  var el = document.elementFromPoint(cx / dpr, cy / dpr);" +
            "  var target = null;" +
            "  while (el && el !== document.body && el !== document.documentElement) {" +
            "    var isTextarea = (el.tagName === 'TEXTAREA');" +
            "    var s = window.getComputedStyle(el);" +
            "    var oy = s.overflowY || s.overflow;" +
            "    if (isTextarea || ((oy === 'scroll' || oy === 'auto') && el.scrollHeight > el.clientHeight + 4)) {" +
            "      target = el; break;" +
            "    }" +
            "    el = el.parentElement;" +
            "  }" +
            "  if (target) {" +
            "    target.scrollTop += dy;" +
            "  } else {" +
            "    window.scrollBy(0, dy);" +
            "  }" +
            "})(" + targetX + ", " + targetY + ", " + dy + ");";
        webView.evaluateJavascript(js, null);

        // Immediate check to update container scroll limits
        lastModalCheckTime = 0;
        checkActiveModal(cursorX, cursorY);
    }

    private void showBottomBar() {
        isBarVisible = true;
        handler.removeCallbacks(hideBarRunnable);
        if (isSearchingAtTop) {
            float targetY = -(screenHeight - dpToPx(58) - dpToPx(48));
            bottomBar.animate().translationY(targetY).alpha(1f).setDuration(200).start();
        } else {
            bottomBar.animate().translationY(0f).alpha(1f).setDuration(200).start();
        }
    }

    private void hideBottomBar() {
        if (isSearchingAtTop) return;
        isBarVisible = false;
        float hideDistance = dpToPx(120);
        bottomBar.animate().translationY(hideDistance).alpha(0f).setDuration(240).start();
        bookmarksPanel.setVisibility(View.GONE);
    }

    private void scheduleHideBottomBar(int delayMs) {
        handler.removeCallbacks(hideBarRunnable);
        handler.postDelayed(hideBarRunnable, delayMs);
    }

    private void showHudToast(String message) {
        handler.removeCallbacks(hideHudRunnable);
        hudToast.setText(message);
        hudToast.setVisibility(View.VISIBLE);
        hudToast.setAlpha(1f);
        handler.postDelayed(hideHudRunnable, 2200);
    }

    private void openBookmarksDrawer() {
        isNavBarMode = true;
        virtualCursor.setVisibility(View.GONE);
        refreshBookmarksUI();
        bookmarksPanel.setVisibility(View.VISIBLE);
        bookmarksPanel.bringToFront();
        showBottomBar();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bookmarksRow1.getChildCount() > 0) {
                    bookmarksRow1.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    }

    private void closeBookmarksDrawer() {
        bookmarksPanel.setVisibility(View.GONE);
        if (isNavBarMode) {
            btnBookmarks.requestFocus();
        } else {
            exitNavBarMode();
        }
    }

    private void setupBookmarks() {
        String existing = prefs.getString(PREF_BOOKMARKS, "");
        if (existing.isEmpty() || existing.equals("[]")) {
            try {
                JSONArray defaults = new JSONArray();
                addBookmarkJson(defaults, "Google", "https://www.google.com");
                addBookmarkJson(defaults, "YouTube", "https://www.youtube.com");
                addBookmarkJson(defaults, "Wikipedia", "https://www.wikipedia.org");
                addBookmarkJson(defaults, "Reddit", "https://www.reddit.com");
                addBookmarkJson(defaults, "GitHub", "https://github.com");
                addBookmarkJson(defaults, "Archive", "https://archive.org");
                prefs.edit().putString(PREF_BOOKMARKS, defaults.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    private void addBookmarkJson(JSONArray arr, String title, String url) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("title", title);
        obj.put("url", url);
        arr.put(obj);
    }

    private void refreshBookmarksUI() {
        bookmarksRow1.removeAllViews();
        bookmarksRow2.removeAllViews();

        // 1. First button in Row 1: "+ Save Current Page"
        Button btnAdd = new Button(this);
        btnAdd.setText("+ Save Current Page");
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setTextSize(13f);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setIncludeFontPadding(false);
        btnAdd.setBackgroundResource(R.drawable.btn_pill_selector);
        btnAdd.setFocusable(true);
        LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(40));
        lpAdd.setMarginEnd(dpToPx(8));
        btnAdd.setLayoutParams(lpAdd);
        btnAdd.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentPageBookmark();
            }
        });
        btnAdd.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        closeBookmarksDrawer();
                        exitNavBarMode();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (bookmarksRow2.getChildCount() > 0) {
                            bookmarksRow2.getChildAt(0).requestFocus();
                        } else {
                            btnBookmarks.requestFocus();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        closeBookmarksDrawer();
                        return true;
                    }
                }
                return false;
            }
        });
        bookmarksRow1.addView(btnAdd);

        // 2. Distribute bookmarks evenly across Row 1 and Row 2
        try {
            String jsonStr = prefs.getString(PREF_BOOKMARKS, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            int half = (arr.length() + 1) / 2;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.getJSONObject(i);
                final String title = b.getString("title");
                final String url = b.getString("url");
                final boolean inRow1 = (i < half);

                Button chip = new Button(this);
                chip.setText(title);
                chip.setTextColor(Color.WHITE);
                chip.setTextSize(13f);
                chip.setGravity(Gravity.CENTER);
                chip.setIncludeFontPadding(false);
                chip.setBackgroundResource(R.drawable.btn_pill_selector);
                chip.setFocusable(true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(40));
                lp.setMarginEnd(dpToPx(8));
                chip.setLayoutParams(lp);
                chip.setPadding(dpToPx(16), 0, dpToPx(16), 0);

                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeBookmarksDrawer();
                        exitNavBarMode();
                        loadUrl(url);
                    }
                });

                final int chipIdx = inRow1 ? (i + 1) : (i - half);
                chip.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                if (inRow1) {
                                    closeBookmarksDrawer();
                                    exitNavBarMode();
                                } else {
                                    int target = Math.min(chipIdx, bookmarksRow1.getChildCount() - 1);
                                    if (target >= 0) bookmarksRow1.getChildAt(target).requestFocus();
                                }
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                if (inRow1 && bookmarksRow2.getChildCount() > 0) {
                                    int target = Math.min(chipIdx, bookmarksRow2.getChildCount() - 1);
                                    if (target >= 0) bookmarksRow2.getChildAt(target).requestFocus();
                                } else {
                                    btnBookmarks.requestFocus();
                                }
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                closeBookmarksDrawer();
                                return true;
                            }
                        }
                        return false;
                    }
                });

                if (inRow1) {
                    bookmarksRow1.addView(chip);
                } else {
                    bookmarksRow2.addView(chip);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveCurrentPageBookmark() {
        String currentUrl = webView.getUrl();
        String currentTitle = webView.getTitle();
        if (currentUrl == null || currentUrl.isEmpty()) return;
        if (currentTitle == null || currentTitle.isEmpty()) currentTitle = currentUrl;

        try {
            String jsonStr = prefs.getString(PREF_BOOKMARKS, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            addBookmarkJson(arr, currentTitle, currentUrl);
            prefs.edit().putString(PREF_BOOKMARKS, arr.toString()).apply();
            showHudToast("Saved to Bookmarks");
            refreshBookmarksUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 0. Exit Dialog handling
        if (exitDialogContainer != null && exitDialogContainer.getVisibility() == View.VISIBLE) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    hideExitDialog();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnExitConfirm.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnExitCancel.requestFocus();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // Dedicated Page Scrolling Keys (Channel Up/Down, Page Up/Down, Media Rewind/FF)
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                scrollWeb(350);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                scrollWeb(-350);
                return true;
            }
        }

        // 1. Color Remote Shortcuts (Red = Bookmarks, Green = Reload, Blue = Search)
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
                openBookmarksDrawer();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_GREEN) {
                webView.reload();
                showHudToast("Reloading page...");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
                enterSearchMode();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_WINDOW) {
                if (isNavBarMode) {
                    exitNavBarMode();
                } else {
                    enterNavBarMode(urlEdit);
                }
                return true;
            }
        }

        // 2. BACK key handling
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (exitDialogContainer != null && exitDialogContainer.getVisibility() == View.VISIBLE) {
                    hideExitDialog();
                    return true;
                }
                if (bookmarksPanel != null && bookmarksPanel.getVisibility() == View.VISIBLE) {
                    closeBookmarksDrawer();
                    return true;
                }
                if (isSearchingAtTop) {
                    exitSearchMode();
                    return true;
                }
                // When already on the Navigation Bar: clicking BACK goes to previous page in browser history!
                if (isNavBarMode) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                        showBottomBar();
                        btnBack.requestFocus();
                    } else {
                        showHudToast("No previous page");
                    }
                    return true;
                }
                // When browsing in content mode: BACK key triggers showing the navigation bar!
                enterNavBarMode(urlEdit);
                return true;
            }
            return true;
        }

        // 3. Navigation Bar Mode (Direct D-Pad tabbing; UP/DOWN exits)
        if (isNavBarMode || (bottomBar != null && bottomBar.hasFocus())) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (bookmarksPanel != null && bookmarksPanel.getVisibility() == View.VISIBLE) {
                    // Inside bookmarks panel: do NOT intercept DPAD_UP / DPAD_DOWN!
                    // Let child views handle row navigation between Row 1, Row 2, and bottom bar.
                } else {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (isSearchingAtTop) {
                            exitSearchMode();
                        } else {
                            exitNavBarMode();
                        }
                        return true;
                    }
                }

                if (!isSearchingAtTop && urlEdit != null && urlEdit.hasFocus()) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnRefresh.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnClear.requestFocus();
                        return true;
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // 4. Content / Virtual Cursor Mode (When browsing web pages)
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

            if (action == KeyEvent.ACTION_DOWN) {
                float step = (event.getRepeatCount() > 2) ? 44f : 22f;
                float cursorSize = dpToPx(26);
                float minX = dpToPx(8);
                float maxX = screenWidth - cursorSize - dpToPx(8);
                float minY = dpToPx(8);
                float maxY = screenHeight - cursorSize - dpToPx(8);

                // Check if cursor is currently within an active modal / popup / scrollable container
                final boolean insideModal;
                final float effectiveMinY;
                final float effectiveMaxY;

                if (activeModalRect != null && activeModalRect.contains(cursorX, cursorY)) {
                    insideModal = true;
                    effectiveMinY = Math.max(minY, activeModalRect.top + dpToPx(6));
                    float modalBottom = (activeModalRect.bottom >= screenHeight - dpToPx(80)) ?
                            (screenHeight - dpToPx(72)) : (activeModalRect.bottom - dpToPx(8));
                    effectiveMaxY = Math.min(maxY, modalBottom);
                } else {
                    insideModal = false;
                    effectiveMinY = minY;
                    effectiveMaxY = maxY;
                }

                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (cursorY <= effectiveMinY + 4f) {
                            boolean canExit = (insideModal && isInsideIframe && edgeScrollTicks >= 20) ||
                                              (insideModal && !isInsideIframe && !containerCanScrollUp && edgeScrollTicks >= 2);
                            if (canExit) {
                                // End of scrolling reached at top of modal: allow cursor to exit modal
                                activeModalRect = null;
                                isInsideIframe = false;
                                cursorY = Math.max(minY, cursorY - step);
                                updateCursorIcon(R.drawable.cursor_pointer);
                                edgeScrollTicks = 0;
                            } else {
                                edgeScrollTicks++;
                                if (edgeScrollTicks == 1) {
                                    // Only switch to scroll icon after the first extra UP scroll
                                    updateCursorIcon(R.drawable.cursor_scroll_up);
                                } else if (edgeScrollTicks >= 2) {
                                    updateCursorIcon(R.drawable.cursor_scroll_up);
                                    scrollWeb(insideModal ? -75 : -100);
                                }
                            }
                        } else {
                            edgeScrollTicks = 0;
                            cursorY = Math.max(effectiveMinY, cursorY - step);
                            // On normal move to edge, KEEP NORMAL POINTER ICON!
                            updateCursorIcon(R.drawable.cursor_pointer);
                        }
                        break;

                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (cursorY >= effectiveMaxY - 4f) {
                            boolean canExit = (insideModal && isInsideIframe && edgeScrollTicks >= 20) ||
                                              (insideModal && !isInsideIframe && !containerCanScrollDown && edgeScrollTicks >= 2);
                            if (canExit) {
                                // End of scrolling reached at bottom of modal: allow cursor to exit modal
                                activeModalRect = null;
                                isInsideIframe = false;
                                cursorY = Math.min(maxY, cursorY + step);
                                updateCursorIcon(R.drawable.cursor_pointer);
                                edgeScrollTicks = 0;
                            } else {
                                edgeScrollTicks++;
                                if (edgeScrollTicks == 1) {
                                    // Only switch to scroll icon after the first extra DOWN scroll
                                    updateCursorIcon(R.drawable.cursor_scroll_down);
                                } else if (edgeScrollTicks >= 2) {
                                    updateCursorIcon(R.drawable.cursor_scroll_down);
                                    scrollWeb(insideModal ? 75 : 100);
                                }
                            }
                        } else {
                            edgeScrollTicks = 0;
                            cursorY = Math.min(effectiveMaxY, cursorY + step);
                            // On normal move to edge, KEEP NORMAL POINTER ICON!
                            updateCursorIcon(R.drawable.cursor_pointer);
                        }
                        break;

                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        edgeScrollTicks = 0;
                        cursorX = Math.max(minX, cursorX - step);
                        if (insideModal && activeModalRect != null && cursorX < activeModalRect.left) {
                            activeModalRect = null;
                            isInsideIframe = false;
                        }
                        updateCursorIcon(R.drawable.cursor_pointer);
                        break;

                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        edgeScrollTicks = 0;
                        cursorX = Math.min(maxX, cursorX + step);
                        if (insideModal && activeModalRect != null && cursorX > activeModalRect.right) {
                            activeModalRect = null;
                            isInsideIframe = false;
                        }
                        updateCursorIcon(R.drawable.cursor_pointer);
                        break;
                }
                virtualCursor.setX(cursorX);
                virtualCursor.setY(cursorY);
                checkActiveModal(cursorX, cursorY);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                handleCursorClick(cursorX, cursorY);
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void handleCursorClick(float x, float y) {
        // Visual press animation on cursor
        virtualCursor.animate().scaleX(0.65f).scaleY(0.65f).setDuration(45).withEndAction(new Runnable() {
            @Override
            public void run() {
                virtualCursor.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start();
            }
        }).start();

        // 0. If Exit Dialog is open, check button clicks
        if (exitDialogContainer != null && exitDialogContainer.getVisibility() == View.VISIBLE) {
            Rect confirmRect = new Rect();
            btnExitConfirm.getGlobalVisibleRect(confirmRect);
            if (confirmRect.contains((int) x, (int) y)) {
                btnExitConfirm.performClick();
                return;
            }
            Rect cancelRect = new Rect();
            btnExitCancel.getGlobalVisibleRect(cancelRect);
            if (cancelRect.contains((int) x, (int) y)) {
                btnExitCancel.performClick();
                return;
            }
            return;
        }

        // 1. If Bookmarks Panel is open, check bookmark clicks
        if (bookmarksPanel.getVisibility() == View.VISIBLE) {
            Rect panelRect = new Rect();
            bookmarksPanel.getGlobalVisibleRect(panelRect);
            if (panelRect.contains((int) x, (int) y)) {
                for (int i = 0; i < bookmarksRow1.getChildCount(); i++) {
                    View child = bookmarksRow1.getChildAt(i);
                    Rect r = new Rect();
                    child.getGlobalVisibleRect(r);
                    if (r.contains((int) x, (int) y)) {
                        child.performClick();
                        return;
                    }
                }
                for (int i = 0; i < bookmarksRow2.getChildCount(); i++) {
                    View child = bookmarksRow2.getChildAt(i);
                    Rect r = new Rect();
                    child.getGlobalVisibleRect(r);
                    if (r.contains((int) x, (int) y)) {
                        child.performClick();
                        return;
                    }
                }
                return;
            }
        }

        // 2. If Bottom Bar is visible, check button clicks
        if (isBarVisible) {
            Rect barRect = new Rect();
            bottomBar.getGlobalVisibleRect(barRect);
            if (barRect.contains((int) x, (int) y)) {
                View[] buttons = new View[]{btnExit, btnBack, btnForward, btnRefresh, btnClear, btnGo, btnBookmarks};
                for (View btn : buttons) {
                    Rect r = new Rect();
                    btn.getGlobalVisibleRect(r);
                    if (r.contains((int) x, (int) y)) {
                        btn.performClick();
                        return;
                    }
                }
                Rect urlRect = new Rect();
                urlEdit.getGlobalVisibleRect(urlRect);
                if (urlRect.contains((int) x, (int) y)) {
                    enterSearchMode();
                    return;
                }
                return;
            }
        }

        // Otherwise hide bottom bar/bookmarks if open and click web page
        if (bookmarksPanel.getVisibility() == View.VISIBLE || isBarVisible) {
            bookmarksPanel.setVisibility(View.GONE);
            hideBottomBar();
        }

        // Dispatch hardware-level touch event to WebView
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent eventDown = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent eventUp = MotionEvent.obtain(downTime, eventTime + 35, MotionEvent.ACTION_UP, x, y, 0);

        webView.dispatchTouchEvent(eventDown);
        webView.dispatchTouchEvent(eventUp);

        eventDown.recycle();
        eventUp.recycle();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkActiveModal(cursorX, cursorY);
            }
        }, 300);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View view = getCurrentFocus();
            if (view == null) view = (urlEdit != null) ? urlEdit : getWindow().getDecorView();
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
        webView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        hideKeyboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            if (webView != null) webView.freeMemory();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideKeyboard();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}
