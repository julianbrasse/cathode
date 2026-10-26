package com.cathode.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
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
    private EditText urlEdit;
    private ImageButton btnClear;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnGo;
    private Button btnBookmarks;
    private ImageView virtualCursor;
    private TextView hudToast;

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
    }

    private void handleIntent(Intent intent) {
        if (intent != null && (Intent.ACTION_SEARCH.equals(intent.getAction()) ||
                               "android.intent.action.SEARCH".equals(intent.getAction()) ||
                               intent.getBooleanExtra("open_search", false))) {
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
        bottomBar = findViewById(R.id.bottom_bar);
        bookmarksPanel = findViewById(R.id.bookmarks_panel);
        bookmarksRow1 = findViewById(R.id.bookmarks_row1);
        bookmarksRow2 = findViewById(R.id.bookmarks_row2);
        urlEdit = findViewById(R.id.url_edit);
        btnClear = findViewById(R.id.btn_clear);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnGo = findViewById(R.id.btn_go);
        btnBookmarks = findViewById(R.id.btn_bookmarks);
        virtualCursor = findViewById(R.id.virtual_cursor);
        hudToast = findViewById(R.id.hud_toast);

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
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress.setProgress(newProgress);
                if (newProgress == 100) {
                    pageProgress.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupBottomBar() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) webView.goBack();
                exitNavBarMode();
            }
        });

        btnBack.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
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
                exitNavBarMode();
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
                exitNavBarMode();
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
                        openBookmarksDrawer();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        exitNavBarMode();
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

    private void exitNavBarMode() {
        isNavBarMode = false;
        hideBottomBar();
        bookmarksPanel.setVisibility(View.GONE);
        urlEdit.clearFocus();
        if (bottomBar.hasFocus()) bottomBar.clearFocus();
        webView.requestFocus();
        virtualCursor.setVisibility(View.VISIBLE);
    }

    private void enterSearchMode() {
        isSearchingAtTop = true;
        isNavBarMode = true;
        virtualCursor.setVisibility(View.GONE);
        bookmarksPanel.setVisibility(View.GONE);
        glideBarToTop();
        urlEdit.requestFocus();
        urlEdit.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(urlEdit, InputMethodManager.SHOW_FORCED);
    }

    private void exitSearchMode() {
        hideKeyboard();
        isSearchingAtTop = false;
        exitNavBarMode();
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

    private void scrollWeb(int dy) {
        webView.scrollBy(0, dy);
        webView.evaluateJavascript("window.scrollBy({top: " + dy + ", behavior: 'instant'});", null);
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
                addBookmarkJson(defaults, "Play Suisse", "https://www.playsuisse.ch");
                addBookmarkJson(defaults, "Swisscom TV", "https://www.blueplus.ch");
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
                if (bookmarksPanel.getVisibility() == View.VISIBLE) {
                    closeBookmarksDrawer();
                    return true;
                }
                if (isSearchingAtTop) {
                    exitSearchMode();
                    return true;
                }
                if (isNavBarMode) {
                    exitNavBarMode();
                    return true;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                    return true;
                } else {
                    enterNavBarMode(urlEdit);
                    return true;
                }
            }
            return true;
        }

        // 3. Navigation Bar Mode (Direct D-Pad tabbing; UP/DOWN exits)
        if (isNavBarMode) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (isSearchingAtTop) {
                        exitSearchMode();
                    } else if (bookmarksPanel.getVisibility() == View.VISIBLE) {
                        closeBookmarksDrawer();
                    } else {
                        exitNavBarMode();
                    }
                    return true;
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
                float step = (event.getRepeatCount() > 2) ? 42f : 20f;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        cursorY = Math.max(0, cursorY - step);
                        // At very top edge: open nav bar
                        if (cursorY <= 15) {
                            enterNavBarMode(urlEdit);
                            return true;
                        }
                        // Upper boundary: scroll web page UP
                        if (cursorY < 180) {
                            scrollWeb(-130);
                        }
                        break;

                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        cursorY = Math.min(screenHeight, cursorY + step);
                        // Bottom boundary: scroll web page DOWN (does NOT open navbar)
                        if (cursorY > screenHeight - 180) {
                            scrollWeb(130);
                        }
                        break;

                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        cursorX = Math.max(0, cursorX - step);
                        break;

                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        cursorX = Math.min(screenWidth, cursorX + step);
                        break;
                }
                virtualCursor.setX(cursorX);
                virtualCursor.setY(cursorY);
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

        // If bottom bar or bookmarks was visible, hide them on click
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
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
