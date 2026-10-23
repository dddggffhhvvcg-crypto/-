package com.mychat.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static {
        System.loadLibrary("chatcore");
    }

    public native String registerEmailNative(String email, String password);
    public native String getRoomChannel(String roomKey);
    public native String encryptPacket(String author, String message, String secretKey);
    public native String decryptPacket(String hexPacket, String secretKey);

    private static final int COL_BG = Color.parseColor("#050507");
    private static final int COL_CARD = Color.parseColor("#121216");
    private static final int COL_INPUT = Color.parseColor("#1A1A22");
    private static final int COL_STROKE = Color.parseColor("#2A2A36");
    private static final int COL_ACCENT = Color.parseColor("#FFFFFF");
    private static final int COL_ACCENT_SOFT = Color.parseColor("#262633");
    private static final int COL_TEXT_MAIN = Color.parseColor("#F4F4F6");
    private static final int COL_TEXT_SUB = Color.parseColor("#8E8E9D");

    private SharedPreferences prefs;
    private FrameLayout rootContainer;

    private EditText roomKeyInput;
    private EditText messageInput;
    private LinearLayout chatMessagesLayout;
    private ScrollView chatScrollView;
    private LinearLayout pageChats;
    private ScrollView pageSettings;

    private TextView tabChatsBtn;
    private TextView tabSettingsBtn;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isPolling = false;
    private int lastLineCount = 0;
    private String currentRoom = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("messenger_auth", Context.MODE_PRIVATE);

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(COL_BG);
        setContentView(rootContainer);

        if (prefs.getString("email", "").isEmpty()) {
            showEmailRegistrationScreen();
        } else {
            showMainMessengerScreen();
        }
    }

    private void showEmailRegistrationScreen() {
        isPolling = false;
        rootContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(24), dp(48), dp(24), dp(48));

        SmoothBadgeView badge = new SmoothBadgeView(this, "✦");
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(130), dp(130));
        badgeParams.bottomMargin = dp(20);
        layout.addView(badge, badgeParams);

        TextView title = new TextView(this);
        title.setText("Добро пожаловать");
        title.setTextColor(COL_TEXT_MAIN);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Создайте аккаунт для доступа к мессенджеру");
        subtitle.setTextColor(COL_TEXT_SUB);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(32));
        layout.addView(subtitle);

        LinearLayout formCard = createCardContainer();
        formCard.setPadding(dp(18), dp(20), dp(18), dp(20));

        EditText nickEdit = createPillInput("Ваше имя", "");
        LinearLayout.LayoutParams inParams1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inParams1.bottomMargin = dp(14);
        formCard.addView(nickEdit, inParams1);

        EditText emailEdit = createPillInput("Электронная почта", "");
        emailEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams inParams2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inParams2.bottomMargin = dp(14);
        formCard.addView(emailEdit, inParams2);

        EditText passEdit = createPillInput("Пароль (от 4 символов)", "");
        passEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        formCard.addView(passEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(24);
        layout.addView(formCard, cardParams);

        View sliderView = createSlideTrack("Сдвиньте для входа", () -> {
            String nick = nickEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String pass = passEdit.getText().toString().trim();

            if (nick.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, введите имя", Toast.LENGTH_SHORT).show();
                return false;
            }

            String result = registerEmailNative(email, pass);
            if (result.startsWith("ERROR:")) {
                Toast.makeText(this, result.substring(6), Toast.LENGTH_SHORT).show();
                return false;
            }

            prefs.edit()
                    .putString("nick", nick)
                    .putString("email", email)
                    .putString("token", result)
                    .apply();

            showMainMessengerScreen();
            return true;
        });

        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        layout.addView(sliderView, sliderParams);

        scroll.addView(layout);
        rootContainer.addView(scroll);
    }

    private void showMainMessengerScreen() {
        rootContainer.removeAllViews();

        pageChats = buildChatsPage();
        rootContainer.addView(pageChats, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageSettings = buildSettingsPage();
        pageSettings.setVisibility(View.GONE);
        rootContainer.addView(pageSettings, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bottomBarRow = new LinearLayout(this);
        bottomBarRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomBarRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pillNav = new LinearLayout(this);
        pillNav.setOrientation(LinearLayout.HORIZONTAL);
        pillNav.setGravity(Gravity.CENTER_VERTICAL);
        pillNav.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(COL_CARD);
        navBg.setCornerRadius(dp(36));
        navBg.setStroke(dp(1), COL_STROKE);
        pillNav.setBackground(navBg);

        tabChatsBtn = createNavTab("💬  Чаты", true);
        tabSettingsBtn = createNavTab("⚙  Настройки", false);

        tabChatsBtn.setOnClickListener(v -> switchTab(true));
        tabSettingsBtn.setOnClickListener(v -> switchTab(false));

        pillNav.addView(tabChatsBtn);
        pillNav.addView(tabSettingsBtn);
        bottomBarRow.addView(pillNav);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        navParams.bottomMargin = dp(20);
        rootContainer.addView(bottomBarRow, navParams);

        if (!isPolling) {
            isPolling = true;
            startPollingLoop();
        }
    }

    private void switchTab(boolean showChats) {
        pageChats.setVisibility(showChats ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(showChats ? View.GONE : View.VISIBLE);
        styleNavTab(tabChatsBtn, showChats);
        styleNavTab(tabSettingsBtn, !showChats);
    }

    private LinearLayout buildChatsPage() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(28), dp(16), dp(96));

        roomKeyInput = createPillInput("🔍  Название или ключ чата...", "general");
        container.addView(roomKeyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chatScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.setMargins(0, dp(14), 0, dp(14));

        chatMessagesLayout = new LinearLayout(this);
        chatMessagesLayout.setOrientation(LinearLayout.VERTICAL);
        chatMessagesLayout.setPadding(dp(4), dp(8), dp(4), dp(8));
        chatScrollView.addView(chatMessagesLayout);
        container.addView(chatScrollView, scrollParams);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        messageInput = createPillInput("Сообщение...", "");
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        msgParams.rightMargin = dp(10);
        inputRow.addView(messageInput, msgParams);

        TextView sendBtn = new TextView(this);
        sendBtn.setText("↑");
        sendBtn.setTextColor(COL_BG);
        sendBtn.setTextSize(24);
        sendBtn.setTypeface(Typeface.DEFAULT_BOLD);
        sendBtn.setGravity(Gravity.CENTER);

        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(COL_ACCENT);
        sendBg.setCornerRadius(dp(28));
        sendBtn.setBackground(sendBg);
        sendBtn.setOnClickListener(v -> sendMessage());

        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(dp(54), dp(54)));
        container.addView(inputRow);

        return container;
    }

    private ScrollView buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(28), dp(16), dp(100));

        String nick = prefs.getString("nick", "User");
        String email = prefs.getString("email", "user@mail.ru");
        String token = prefs.getString("token", "ID-0000");

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setPadding(dp(6), dp(16), dp(6), dp(20));

        SmoothBadgeView avatar = new SmoothBadgeView(this, nick.substring(0, 1).toUpperCase());
        profileRow.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout profileInfo = new LinearLayout(this);
        profileInfo.setOrientation(LinearLayout.VERTICAL);
        profileInfo.setPadding(dp(16), 0, 0, 0);

        TextView nameTv = new TextView(this);
        nameTv.setText(nick);
        nameTv.setTextColor(COL_TEXT_MAIN);
        nameTv.setTextSize(20);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        profileInfo.addView(nameTv);

        TextView subTv = new TextView(this);
        subTv.setText(email);
        subTv.setTextColor(COL_TEXT_SUB);
        subTv.setTextSize(13);
        profileInfo.addView(subTv);

        profileRow.addView(profileInfo);
        container.addView(profileRow);

        LinearLayout appCard = createCardContainer();
        appCard.addView(createSettingsRow("🆔", "Ваш ID", token, null));
        appCard.addView(createDivider());
        
        View logoutRow = createSettingsRow("🚪", "Выйти из аккаунта", "Сменить профиль", null);
        logoutRow.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            showEmailRegistrationScreen();
        });
        appCard.addView(logoutRow);
        container.addView(appCard);

        scroll.addView(container);
        return scroll;
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        String key = roomKeyInput.getText().toString().trim();
        String nick = prefs.getString("nick", "Anon");

        if (text.isEmpty()) return;
        if (key.isEmpty()) key = "general";

        messageInput.setText("");
        final String finalKey = key;
        final String finalNick = nick;

        executor.execute(() -> {
            try {
                String encryptedHex = encryptPacket(finalNick, text, finalKey);
                String channel = getRoomChannel(finalKey);

                URL url = new URL("https://ntfy.sh/" + channel);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                byte[] out = encryptedHex.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(MainActivity.this, "Ошибка отправки", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void startPollingLoop() {
        executor.execute(() -> {
            while (isPolling) {
                try {
                    if (roomKeyInput == null) {
                        Thread.sleep(1000);
                        continue;
                    }
                    String key = roomKeyInput.getText().toString().trim();
                    if (key.isEmpty()) key = "general";
                    String room = getRoomChannel(key);

                    if (!room.equals(currentRoom)) {
                        currentRoom = room;
                        lastLineCount = 0;
                        mainHandler.post(() -> {
                            if (chatMessagesLayout != null) {
                                chatMessagesLayout.removeAllViews();
                                addMessageBubble("Чат", "Добро пожаловать в комнату!", false);
                            }
                        });
                    }

                    URL url = new URL("https://ntfy.sh/" + room + "/raw?poll=1&since=12h");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        int index = 0;
                        String myNick = prefs.getString("nick", "Anon");

                        while ((line = reader.readLine()) != null) {
                            index++;
                            if (index > lastLineCount && !line.trim().isEmpty()) {
                                String decrypted = decryptPacket(line.trim(), key);
                                if (!decrypted.isEmpty()) {
                                    String[] parts = decrypted.split("::", 2);
                                    String author = parts.length > 1 ? parts[0] : "Участник";
                                    String body = parts.length > 1 ? parts[1] : decrypted;
                                    boolean isMine = author.equals(myNick);

                                    mainHandler.post(() -> addMessageBubble(author, body, isMine));
                                }
                                lastLineCount = index;
                            }
                        }
                        reader.close();
                    }
                    conn.disconnect();
                    Thread.sleep(2000);
                } catch (Exception ignored) {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
                }
            }
        });
    }

    private void addMessageBubble(String author, String body, boolean isMine) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isMine ? COL_ACCENT_SOFT : COL_CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), isMine ? Color.parseColor("#3B3B4F") : COL_STROKE);
        bubble.setBackground(bg);

        TextView authorTv = new TextView(this);
        authorTv.setText(author);
        authorTv.setTextColor(COL_TEXT_SUB);
        authorTv.setTextSize(12);
        authorTv.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.addView(authorTv);

        TextView bodyTv = new TextView(this);
        bodyTv.setText(body);
        bodyTv.setTextColor(COL_TEXT_MAIN);
        bodyTv.setTextSize(15);
        bodyTv.setPadding(0, dp(4), 0, 0);
        bubble.addView(bodyTv);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        params.gravity = isMine ? Gravity.END : Gravity.START;

        chatMessagesLayout.addView(bubble, params);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private class SmoothBadgeView extends View {
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final String symbol;

        public SmoothBadgeView(Context context, String symbol) {
            super(context);
            this.symbol = symbol;
            shapePaint.setColor(COL_CARD);
            shapePaint.setStyle(Paint.Style.FILL);

            borderPaint.setColor(COL_STROKE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1.5f));

            textPaint.setColor(COL_TEXT_MAIN);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.85f;

            path.reset();
            int points = 12;
            for (int i = 0; i <= points * 10; i++) {
                double angle = 2.0 * Math.PI * i / (points * 10);
                float radius = r + (float) Math.sin(angle * points) * (r * 0.08f);
                float x = cx + (float) (radius * Math.cos(angle));
                float y = cy + (float) (radius * Math.sin(angle));
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();

            canvas.drawPath(path, shapePaint);
            canvas.drawPath(path, borderPaint);

            textPaint.setTextSize(r * 0.75f);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(symbol, cx, textY, textPaint);
        }
    }

    private View createSlideTrack(String labelText, SlideCallback callback) {
        FrameLayout track = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(COL_CARD);
        trackBg.setCornerRadius(dp(32));
        trackBg.setStroke(dp(1), COL_STROKE);
        track.setBackground(trackBg);
        track.setPadding(dp(6), dp(6), dp(6), dp(6));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(COL_TEXT_SUB);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        track.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView thumb = new TextView(this);
        thumb.setText("→");
        thumb.setTextColor(COL_BG);
        thumb.setTextSize(22);
        thumb.setTypeface(Typeface.DEFAULT_BOLD);
        thumb.setGravity(Gravity.CENTER);

        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(COL_ACCENT);
        thumbBg.setCornerRadius(dp(26));
        thumb.setBackground(thumbBg);

        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT);
        track.addView(thumb, thumbParams);

        thumb.setOnTouchListener(new View.OnTouchListener() {
            float downX = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int maxSlide = track.getWidth() - v.getWidth() - dp(12);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        if (dx < 0) dx = 0;
                        if (dx > maxSlide) dx = maxSlide;
                        v.setTranslationX(dx);
                        label.setAlpha(1f - (dx / Math.max(1, maxSlide)));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (v.getTranslationX() > maxSlide * 0.75f) {
                            v.setTranslationX(maxSlide);
                            boolean ok = callback.onSlideComplete();
                            if (!ok) {
                                v.animate().translationX(0).setDuration(200).start();
                                label.animate().alpha(1f).setDuration(200).start();
                            }
                        } else {
                            v.animate().translationX(0).setDuration(200).start();
                            label.animate().alpha(1f).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });

        return track;
    }

    private interface SlideCallback {
        boolean onSlideComplete();
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COL_CARD);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), COL_STROKE);
        card.setBackground(bg);
        return card;
    }

    private EditText createPillInput(String hint, String defaultText) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(defaultText);
        edit.setHintTextColor(COL_TEXT_SUB);
        edit.setTextColor(COL_TEXT_MAIN);
        edit.setTextSize(15);
        edit.setSingleLine(true);
        edit.setPadding(dp(20), dp(16), dp(20), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COL_INPUT);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), COL_STROKE);
        edit.setBackground(bg);
        return edit;
    }

    private View createSettingsRow(String icon, String title, String subtitle, String pillText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(18);
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(COL_ACCENT_SOFT);
        iconBg.setCornerRadius(dp(20));
        iconView.setBackground(iconBg);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, dp(10), 0);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(COL_TEXT_MAIN);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(COL_TEXT_SUB);
        s.setTextSize(13);
        textCol.addView(s);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(COL_TEXT_SUB);
        arrow.setTextSize(22);
        row.addView(arrow);

        return row;
    }

    private View createDivider() {
        View v = new View(this);
        v.setBackgroundColor(COL_STROKE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private TextView createNavTab(String title, boolean active) {
        TextView tab = new TextView(this);
        tab.setText(title);
        tab.setTextSize(14);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setPadding(dp(22), dp(12), dp(22), dp(12));
        styleNavTab(tab, active);
        return tab;
    }

    private void styleNavTab(TextView tab, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        if (active) {
            bg.setColor(COL_ACCENT_SOFT);
            bg.setStroke(dp(1), Color.parseColor("#3B3B4F"));
            tab.setTextColor(COL_TEXT_MAIN);
        } else {
            bg.setColor(Color.TRANSPARENT);
            tab.setTextColor(COL_TEXT_SUB);
        }
        tab.setBackground(bg);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        isPolling = false;
        executor.shutdownNow();
        super.onDestroy();
    }
}
