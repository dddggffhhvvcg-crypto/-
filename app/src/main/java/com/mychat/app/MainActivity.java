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

    private static final int COL_BG = Color.parseColor("#000000");
    private static final int COL_CARD = Color.parseColor("#0F0F12");
    private static final int COL_INPUT = Color.parseColor("#08080A");
    private static final int COL_STROKE = Color.parseColor("#222228");
    private static final int COL_ACCENT = Color.parseColor("#E8EAED");
    private static final int COL_ACCENT_SOFT = Color.parseColor("#1F2026");
    private static final int COL_TEXT_MAIN = Color.parseColor("#FFFFFF");
    private static final int COL_TEXT_SUB = Color.parseColor("#7A7D85");

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
        layout.setPadding(dp(22), dp(44), dp(22), dp(48));

        WaveBadgeView badge = new WaveBadgeView(this, "✦");
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        badgeParams.bottomMargin = dp(16);
        layout.addView(badge, badgeParams);

        TextView title = new TextView(this);
        title.setText("Создание аккаунта");
        title.setTextColor(COL_TEXT_MAIN);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Регистрация по Email через C++ ядро");
        subtitle.setTextColor(COL_TEXT_SUB);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(26));
        layout.addView(subtitle);

        LinearLayout formCard = createCardContainer();
        formCard.setPadding(dp(16), dp(18), dp(16), dp(18));

        EditText nickEdit = createPillInput("Отображаемое имя", "");
        LinearLayout.LayoutParams inParams1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inParams1.bottomMargin = dp(12);
        formCard.addView(nickEdit, inParams1);

        EditText emailEdit = createPillInput("Ваш Email", "");
        emailEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams inParams2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inParams2.bottomMargin = dp(12);
        formCard.addView(emailEdit, inParams2);

        EditText passEdit = createPillInput("Пароль (от 4 символов)", "");
        passEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        formCard.addView(passEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(22);
        layout.addView(formCard, cardParams);

        View sliderView = createSlideTrack("Потяните для регистрации", () -> {
            String nick = nickEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String pass = passEdit.getText().toString().trim();

            if (nick.isEmpty()) {
                Toast.makeText(this, "Укажите имя", Toast.LENGTH_SHORT).show();
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        sliderParams.bottomMargin = dp(20);
        layout.addView(sliderView, sliderParams);

        LinearLayout infoCard = createCardContainer();
        infoCard.addView(createTextSection("Защита аккаунта",
                "Почта и пароль обрабатываются внутри нативной библиотеки C++ и превращаются в криптографический ключ."));
        infoCard.addView(createDivider());
        infoCard.addView(createTextSection("Приватные комнаты",
                "Сообщения шифруются на устройстве до отправки в сеть."));
        layout.addView(infoCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        navBg.setCornerRadius(dp(34));
        navBg.setStroke(dp(1), COL_STROKE);
        pillNav.setBackground(navBg);

        tabChatsBtn = createNavTab("💬  Чаты", true);
        tabSettingsBtn = createNavTab("⚙  Настройки", false);

        tabChatsBtn.setOnClickListener(v -> switchTab(true));
        tabSettingsBtn.setOnClickListener(v -> switchTab(false));

        pillNav.addView(tabChatsBtn);
        pillNav.addView(tabSettingsBtn);
        bottomBarRow.addView(pillNav);

        TextView quickCircleBtn = new TextView(this);
        quickCircleBtn.setText("🔑");
        quickCircleBtn.setTextSize(17);
        quickCircleBtn.setGravity(Gravity.CENTER);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setColor(COL_CARD);
        circleBg.setCornerRadius(dp(26));
        circleBg.setStroke(dp(1), COL_STROKE);
        quickCircleBtn.setBackground(circleBg);
        quickCircleBtn.setOnClickListener(v -> {
            switchTab(true);
            if (roomKeyInput != null) roomKeyInput.requestFocus();
        });

        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(50), dp(50));
        circleParams.leftMargin = dp(10);
        bottomBarRow.addView(quickCircleBtn, circleParams);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        navParams.bottomMargin = dp(18);
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
        container.setPadding(dp(16), dp(26), dp(16), dp(88));

        roomKeyInput = createPillInput("🔍  Ключ секретного канала...", "main_room");
        container.addView(roomKeyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chatScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.setMargins(0, dp(12), 0, dp(12));

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
        sendBtn.setText("→");
        sendBtn.setTextColor(COL_BG);
        sendBtn.setTextSize(22);
        sendBtn.setTypeface(Typeface.DEFAULT_BOLD);
        sendBtn.setGravity(Gravity.CENTER);

        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(COL_ACCENT);
        sendBg.setCornerRadius(dp(26));
        sendBtn.setBackground(sendBg);
        sendBtn.setOnClickListener(v -> sendMessage());

        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(dp(52), dp(52)));
        container.addView(inputRow);

        return container;
    }

    private ScrollView buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(26), dp(16), dp(96));

        String nick = prefs.getString("nick", "User");
        String email = prefs.getString("email", "user@mail.ru");
        String token = prefs.getString("token", "ID-0000");

        TextView searchBar = new TextView(this);
        searchBar.setText("🔍   Поиск в настройках");
        searchBar.setTextColor(COL_TEXT_SUB);
        searchBar.setTextSize(15);
        searchBar.setPadding(dp(22), dp(15), dp(22), dp(15));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(COL_CARD);
        searchBg.setCornerRadius(dp(30));
        searchBg.setStroke(dp(1), COL_STROKE);
        searchBar.setBackground(searchBg);
        container.addView(searchBar);

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setPadding(dp(6), dp(22), dp(6), dp(18));

        WaveBadgeView avatar = new WaveBadgeView(this, nick.substring(0, 1).toUpperCase());
        profileRow.addView(avatar, new LinearLayout.LayoutParams(dp(62), dp(62)));

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

        LinearLayout tokenBar = createCardContainer();
        tokenBar.addView(createSettingsRow("🆔", "Крипто-ID аккаунта", token, "C++"));
        container.addView(tokenBar);

        addSectionTitle(container, "Безопасность и сеть");
        LinearLayout secCard = createCardContainer();
        secCard.addView(createSettingsRow("🔒", "Шифрование пакетов", "Потоковый XOR + контроль целостности", "Вкл"));
        secCard.addView(createDivider());
        secCard.addView(createSettingsRow("⚡", "Комната по умолчанию", "Смена ключа на вкладке чатов", null));
        container.addView(secCard);

        addSectionTitle(container, "Аккаунт и интерфейс");
        LinearLayout appCard = createCardContainer();
        appCard.addView(createSettingsRow("🌑", "Тема оформления", "Чистый черный OLED фон", null));
        appCard.addView(createDivider());
        appCard.addView(createSettingsRow("✉", "Почта входа", email, null));
        appCard.addView(createDivider());

        View logoutRow = createSettingsRow("🚪", "Выйти из аккаунта", "Очистить токен и сменить почту", null);
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
        if (key.isEmpty()) key = "main_room";

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
                        Toast.makeText(MainActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show()
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
                    if (key.isEmpty()) key = "main_room";
                    String room = getRoomChannel(key);

                    if (!room.equals(currentRoom)) {
                        currentRoom = room;
                        lastLineCount = 0;
                        mainHandler.post(() -> {
                            if (chatMessagesLayout != null) {
                                chatMessagesLayout.removeAllViews();
                                addMessageBubble("Система", "Канал зашифрован. ID: " + room, false);
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
                                    String author = parts.length > 1 ? parts[0] : "Система";
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
        bubble.setPadding(dp(18), dp(12), dp(18), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isMine ? COL_ACCENT_SOFT : COL_CARD);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), isMine ? Color.parseColor("#383A42") : COL_STROKE);
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

    private class WaveBadgeView extends View {
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();
        private final String symbol;

        public WaveBadgeView(Context context, String symbol) {
            super(context);
            this.symbol = symbol;
            shapePaint.setColor(COL_ACCENT_SOFT);
            shapePaint.setStyle(Paint.Style.FILL);

            borderPaint.setColor(COL_STROKE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));

            textPaint.setColor(COL_TEXT_MAIN);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float baseRadius = Math.min(cx, cy) * 0.82f;
            float waveAmp = baseRadius * 0.11f;
            int waves = 12;

            wavePath.reset();
            int steps = 180;
            for (int i = 0; i <= steps; i++) {
                double angle = 2.0 * Math.PI * i / steps;
                float r = baseRadius + (float) Math.sin(angle * waves) * waveAmp;
                float x = cx + (float) (r * Math.cos(angle));
                float y = cy + (float) (r * Math.sin(angle));
                if (i == 0) wavePath.moveTo(x, y);
                else wavePath.lineTo(x, y);
            }
            wavePath.close();
            canvas.drawPath(wavePath, shapePaint);
            canvas.drawPath(wavePath, borderPaint);

            textPaint.setTextSize(baseRadius * 0.72f);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(symbol, cx, textY, textPaint);
        }
    }

    private interface SlideCallback {
        boolean onSlideComplete();
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
                        if (v.getTranslationX() > maxSlide * 0.72f) {
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
        edit.setPadding(dp(22), dp(15), dp(22), dp(15));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COL_INPUT);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), COL_STROKE);
        edit.setBackground(bg);
        return edit;
    }

    private View createTextSection(String titleText, String descText) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(18), dp(14), dp(18), dp(14));

        TextView t = new TextView(this);
        t.setText(titleText);
        t.setTextColor(COL_TEXT_MAIN);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        block.addView(t);

        TextView d = new TextView(this);
        d.setText(descText);
        d.setTextColor(COL_TEXT_SUB);
        d.setTextSize(13);
        d.setPadding(0, dp(4), 0, 0);
        block.addView(d);
        return block;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(COL_TEXT_SUB);
        header.setTextSize(13);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(12), dp(18), dp(12), dp(8));
        parent.addView(header);
    }

    private View createSettingsRow(String icon, String title, String subtitle, String pillText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(17);
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(COL_ACCENT_SOFT);
        iconBg.setCornerRadius(dp(20));
        iconView.setBackground(iconBg);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, dp(10), 0);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(COL_TEXT_MAIN);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(COL_TEXT_SUB);
        s.setTextSize(13);
        textCol.addView(s);

        if (pillText != null) {
            TextView badge = new TextView(this);
            badge.setText(pillText);
            badge.setTextColor(COL_BG);
            badge.setTextSize(12);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(dp(12), dp(5), dp(12), dp(5));
            GradientDrawable bBg = new GradientDrawable();
            bBg.setColor(COL_ACCENT);
            bBg.setCornerRadius(dp(16));
            badge.setBackground(bBg);
            row.addView(badge);
        }

        TextView arrow = new TextView(this);
        arrow.setText("  ›");
        arrow.setTextColor(COL_TEXT_SUB);
        arrow.setTextSize(20);
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
        tab.setPadding(dp(20), dp(11), dp(20), dp(11));
        styleNavTab(tab, active);
        return tab;
    }

    private void styleNavTab(TextView tab, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(26));
        if (active) {
            bg.setColor(COL_ACCENT_SOFT);
            bg.setStroke(dp(1), Color.parseColor("#343640"));
            tab.setTextColor(COL_TEXT_MAIN);
        } else {
            bg.setColor(Color.TRANSPARENT);
            tab.setTextColor(COL_TEXT_SUB);
        }
        tab.setBackground(bg);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        isPolling = false;
        executor.shutdownNow();
        super.onDestroy();
    }
}
