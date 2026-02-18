package com.smart.keyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SmartKeyboardService extends InputMethodService {

    private View mainView;
    private InputConnection ic;

    private LinearLayout keyboardContainer;
    private LinearLayout panelVoice, panelTranslate, panelClipboard;
    private RecyclerView emojiGrid;

    private ImageView btnLang, btnTranslate, btnVoice, btnClipboard, btnEmoji;
    private TextView voiceStatus, voiceHint;
    private EditText transInput;
    private TextView transResult, transFrom, transTo;
    private ImageView transSwap;
    private LinearLayout clipboardItems;
    private TextView clipboardEmpty;
    private View[] voiceBars = new View[5];

    private SpeechRecognizer speechRecognizer;
    private boolean isRecording = false;
    private boolean isShifted = false;
    private boolean isCapsLock = false;
    private boolean isLangBangla = false;
    private boolean isTransSwapped = false;
    private String currentMode = "keyboard";

    private ClipboardManager clipboardManager;
    private List<String> clipboardHistory = new ArrayList<>();

    private Button shiftBtn, mode123Btn;
    private boolean isNumberMode = false;

    private String[] emojis = {"😊","😂","❤️","👍","🔥","🤔","🙏","✨","🥺","🚀","💻","✅","⚡","🎉","💡","🌈","🍕","☕","🐱","🐶","🌍","💪","😎","😢","😍","🤣","🙌","👋","🤝","🎉"};

    private static final String[][] EN_LAYOUT = {
        {"q","w","e","r","t","y","u","i","o","p"},
        {"a","s","d","f","g","h","j","k","l"},
        {"SHIFT","z","x","c","v","b","n","m","DEL"}
    };

    private static final String[][] BN_LAYOUT = {
        {"দ","ধ","ে","র","ত","থ","উ","ই","ও","প"},
        {"আ","স","ড","ফ","গ","হ","জ","ক","ল"},
        {"SHIFT","য","ষ","চ","ভ","ব","ন","ম","DEL"}
    };

    private static final String[][] NUM_LAYOUT = {
        {"1","2","3","4","5","6","7","8","9","0"},
        {"-","/",":",";","(",")","$","&","@","\""},
        {"#~","[","]","{","}","#","%","*","+","="}
    };

    private static final String[][] NUM_SHIFT_LAYOUT = {
        {"!","@","#","$","%","^","&","*","(",")"},
        {"_","\\","|","~","<",">","€","£","¥","•"},
        {"#~","?","!",",",".","'","`","; ",":","…"}
    };

    @Override
    public View onCreateInputView() {
        mainView = LayoutInflater.from(this).inflate(R.layout.keyboard_main, null);
        ic = getCurrentInputConnection();

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        initViews();
        initToolbar();
        buildKeyboard();
        initVoice();
        initTranslate();
        initClipboard();
        initEmoji();

        return mainView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        ic = getCurrentInputConnection();
    }

    private void initViews() {
        btnLang = mainView.findViewById(R.id.btn_lang);
        btnTranslate = mainView.findViewById(R.id.btn_translate);
        btnVoice = mainView.findViewById(R.id.btn_voice);
        btnClipboard = mainView.findViewById(R.id.btn_clipboard);
        btnEmoji = mainView.findViewById(R.id.btn_emoji);

        panelVoice = mainView.findViewById(R.id.panel_voice);
        panelTranslate = mainView.findViewById(R.id.panel_translate);
        panelClipboard = mainView.findViewById(R.id.panel_clipboard);
        emojiGrid = mainView.findViewById(R.id.emoji_grid);
        keyboardContainer = mainView.findViewById(R.id.keyboard_container);

        voiceStatus = mainView.findViewById(R.id.voice_status);
        voiceHint = mainView.findViewById(R.id.voice_hint);

        transInput = mainView.findViewById(R.id.trans_input);
        transResult = mainView.findViewById(R.id.trans_result);
        transFrom = mainView.findViewById(R.id.trans_from);
        transTo = mainView.findViewById(R.id.trans_to);
        transSwap = mainView.findViewById(R.id.trans_swap);

        clipboardItems = mainView.findViewById(R.id.clipboard_items);
        clipboardEmpty = mainView.findViewById(R.id.clipboard_empty);

        voiceBars[0] = mainView.findViewById(R.id.bar1);
        voiceBars[1] = mainView.findViewById(R.id.bar2);
        voiceBars[2] = mainView.findViewById(R.id.bar3);
        voiceBars[3] = mainView.findViewById(R.id.bar4);
        voiceBars[4] = mainView.findViewById(R.id.bar5);
    }

    private void initToolbar() {
        btnLang.setOnClickListener(v -> {
            isLangBangla = !isLangBangla;
            btnLang.setColorFilter(isLangBangla ? Color.parseColor("#3b82f6") : Color.parseColor("#9ca3af"));
            if (isNumberMode) isNumberMode = false;
            buildKeyboard();
        });

        btnTranslate.setOnClickListener(v -> togglePanel("translate"));
        btnClipboard.setOnClickListener(v -> {
            updateClipboardFromSystem();
            togglePanel("clipboard");
        });
        btnEmoji.setOnClickListener(v -> togglePanel("emoji"));

        btnVoice.setOnClickListener(v -> {
            if ("voice".equals(currentMode)) {
                toggleVoice();
            } else {
                togglePanel("voice");
            }
        });
    }

    private void togglePanel(String panel) {
        hideAllPanels();
        resetToolbarColors();
        keyboardContainer.setVisibility(View.GONE);

        if (currentMode.equals(panel)) {
            currentMode = "keyboard";
            keyboardContainer.setVisibility(View.VISIBLE);
            return;
        }

        currentMode = panel;

        switch (panel) {
            case "translate":
                panelTranslate.setVisibility(View.VISIBLE);
                btnTranslate.setColorFilter(Color.parseColor("#3b82f6"));
                break;
            case "voice":
                panelVoice.setVisibility(View.VISIBLE);
                btnVoice.setColorFilter(Color.parseColor("#ef4444"));
                break;
            case "clipboard":
                panelClipboard.setVisibility(View.VISIBLE);
                btnClipboard.setColorFilter(Color.parseColor("#3b82f6"));
                updateClipboardUI();
                break;
            case "emoji":
                emojiGrid.setVisibility(View.VISIBLE);
                btnEmoji.setColorFilter(Color.parseColor("#3b82f6"));
                break;
        }
    }

    private void hideAllPanels() {
        panelVoice.setVisibility(View.GONE);
        panelTranslate.setVisibility(View.GONE);
        panelClipboard.setVisibility(View.GONE);
        emojiGrid.setVisibility(View.GONE);
    }

    private void resetToolbarColors() {
        btnTranslate.setColorFilter(Color.parseColor("#9ca3af"));
        btnVoice.setColorFilter(isRecording ? Color.parseColor("#ef4444") : Color.parseColor("#9ca3af"));
        btnClipboard.setColorFilter(Color.parseColor("#9ca3af"));
        btnEmoji.setColorFilter(Color.parseColor("#9ca3af"));
    }

    private void buildKeyboard() {
        keyboardContainer.removeAllViews();

        String[][] layout;
        if (isNumberMode) {
            layout = isShifted ? NUM_SHIFT_LAYOUT : NUM_LAYOUT;
        } else {
            layout = isLangBangla ? BN_LAYOUT : EN_LAYOUT;
        }

        for (int row = 0; row < layout.length; row++) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(android.view.Gravity.CENTER);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, dp(2), 0, dp(2));
            rowView.setLayoutParams(rowParams);

            if (row == 1) rowView.setPadding(dp(14), 0, dp(14), 0);

            for (String key : layout[row]) {
                Button keyBtn = createKeyButton(key, row);
                rowView.addView(keyBtn);
            }
            keyboardContainer.addView(rowView);
        }

        addBottomRow();
        keyboardContainer.setVisibility(View.VISIBLE);
    }

    private Button createKeyButton(String key, int row) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params;
        String display = key;
        int bgRes = R.drawable.key_bg;

        switch (key) {
            case "SHIFT":
                display = isShifted ? "⇧" : "⇧";
                bgRes = R.drawable.key_bg_shift;
                params = new LinearLayout.LayoutParams(dp(52), dp(48));
                btn.setActivated(isShifted);
                shiftBtn = btn;
                break;
            case "#~":
                display = isShifted ? "=\\<" : "#~";
                params = new LinearLayout.LayoutParams(dp(52), dp(48));
                break;
            case "DEL":
                display = "⌫";
                bgRes = R.drawable.key_bg_special;
                params = new LinearLayout.LayoutParams(dp(52), dp(48));
                break;
            default:
                if (isShifted && !isNumberMode && key.length() == 1) {
                    display = key.toUpperCase();
                }
                params = new LinearLayout.LayoutParams(0, dp(48), 1f);
                break;
        }

        btn.setLayoutParams(params);
        btn.setText(display);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(key.length() > 2 ? 12f : 18f);
        btn.setBackgroundResource(bgRes);
        btn.setAllCaps(false);
        btn.setPadding(0, 0, 0, 0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);

        btn.setOnClickListener(v -> handleKey(key));
        btn.setOnLongClickListener(v -> {
            if (key.equals("DEL")) {
                ic.deleteSurroundingText(100, 0);
                return true;
            }
            if (key.equals("SHIFT")) {
                isCapsLock = !isCapsLock;
                isShifted = true;
                buildKeyboard();
                return true;
            }
            return false;
        });

        return btn;
    }

    private void addBottomRow() {
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, 0);
        bottomRow.setLayoutParams(rowParams);

        Button btnMode = new Button(this);
        btnMode.setText(isNumberMode ? "ABC" : "123");
        styleSpecialKey(btnMode, dp(56));
        btnMode.setOnClickListener(v -> {
            isNumberMode = !isNumberMode;
            isShifted = false;
            buildKeyboard();
        });
        mode123Btn = btnMode;
        bottomRow.addView(btnMode);

        Button btnComma = new Button(this);
        btnComma.setText(",");
        styleKey(btnComma, dp(44));
        btnComma.setOnClickListener(v -> sendText(","));
        bottomRow.addView(btnComma);

        Button btnSpace = new Button(this);
        btnSpace.setText(isLangBangla ? "স্পেস" : "space");
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        btnSpace.setLayoutParams(spaceParams);
        btnSpace.setTextColor(Color.WHITE);
        btnSpace.setTextSize(14f);
        btnSpace.setBackgroundResource(R.drawable.key_bg);
        btnSpace.setPadding(0, 0, 0, 0);
        btnSpace.setOnClickListener(v -> sendText(" "));
        bottomRow.addView(btnSpace);

        Button btnDot = new Button(this);
        btnDot.setText(".");
        styleKey(btnDot, dp(44));
        btnDot.setOnClickListener(v -> sendText("."));
        bottomRow.addView(btnDot);

        Button btnEnter = new Button(this);
        btnEnter.setText("↵");
        styleSpecialKey(btnEnter, dp(56));
        btnEnter.setBackgroundResource(R.drawable.key_bg_enter);
        btnEnter.setOnClickListener(v -> sendEnter());
        bottomRow.addView(btnEnter);

        keyboardContainer.addView(bottomRow);
    }

    private void styleKey(Button btn, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        btn.setLayoutParams(params);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18f);
        btn.setBackgroundResource(R.drawable.key_bg);
        btn.setPadding(0, 0, 0, 0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
    }

    private void styleSpecialKey(Button btn, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        btn.setLayoutParams(params);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14f);
        btn.setBackgroundResource(R.drawable.key_bg_special);
        btn.setPadding(0, 0, 0, 0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
    }

    private void handleKey(String key) {
        if (ic == null) ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (key) {
            case "SHIFT":
                isShifted = !isShifted;
                if (isCapsLock) isCapsLock = false;
                buildKeyboard();
                break;
            case "#~":
                isShifted = !isShifted;
                buildKeyboard();
                break;
            case "DEL":
                ic.deleteSurroundingText(1, 0);
                break;
            default:
                String out = key;
                if (isShifted && !isNumberMode && key.length() == 1) {
                    out = key.toUpperCase();
                }
                sendText(out);
                if (isShifted && !isCapsLock) {
                    isShifted = false;
                    buildKeyboard();
                }
                break;
        }
    }

    private void sendText(String text) {
        if (ic == null) ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void sendEnter() {
        if (ic == null) ic = getCurrentInputConnection();
        if (ic != null) {
            EditorInfo ei = getCurrentInputEditorInfo();
            int action = (ei != null) ? ei.imeOptions & EditorInfo.IME_MASK_ACTION : EditorInfo.IME_ACTION_NONE;
            
            if (action != EditorInfo.IME_ACTION_NONE) {
                ic.performEditorAction(action);
            } else {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            }
        }
    }

    private void initVoice() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                voiceStatus.setText("LISTENING");
                voiceHint.setText("Speak now...");
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                voiceStatus.setText("PROCESSING");
            }
            @Override public void onError(int error) {
                isRecording = false;
                voiceStatus.setText("ERROR");
                voiceHint.setText("Try again");
                stopVoiceAnimation();
                btnVoice.setColorFilter(Color.parseColor("#9ca3af"));
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    sendText(matches.get(0) + " ");
                }
                voiceHint.setText(matches != null && !matches.isEmpty() ? matches.get(0) : "");
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    voiceHint.setText(matches.get(0));
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void toggleVoice() {
        if (isRecording) {
            speechRecognizer.stopListening();
            isRecording = false;
            voiceStatus.setText("VOICE MODE");
            voiceHint.setText("Tap mic to start");
            stopVoiceAnimation();
            btnVoice.setColorFilter(Color.parseColor("#9ca3af"));
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isLangBangla ? "bn-BD" : "en-US");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            speechRecognizer.startListening(intent);
            isRecording = true;
            startVoiceAnimation();
            btnVoice.setColorFilter(Color.parseColor("#ef4444"));
        }
    }

    private void startVoiceAnimation() {
        for (int i = 0; i < voiceBars.length; i++) {
            View bar = voiceBars[i];
            ValueAnimator anim = ValueAnimator.ofInt(dp(16), dp(80));
            anim.setDuration(400);
            anim.setStartDelay(i * 80);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> {
                ViewGroup.LayoutParams p = bar.getLayoutParams();
                p.height = (int) a.getAnimatedValue();
                bar.setLayoutParams(p);
            });
            anim.start();
        }
    }

    private void stopVoiceAnimation() {
        for (View bar : voiceBars) {
            bar.clearAnimation();
            ViewGroup.LayoutParams p = bar.getLayoutParams();
            p.height = dp(16);
            bar.setLayoutParams(p);
        }
    }

    private void initTranslate() {
        transSwap.setOnClickListener(v -> {
            isTransSwapped = !isTransSwapped;
            transFrom.setText(isTransSwapped ? "English" : "Bangla");
            transTo.setText(isTransSwapped ? "Bangla" : "English");
            transResult.setText("");
        });

        transInput.setOnEditorActionListener((v, actionId, event) -> {
            String text = transInput.getText().toString().trim();
            if (!text.isEmpty()) {
                transResult.setText("Translation needs API");
            }
            return true;
        });
    }

    private void initClipboard() {
        clipboardHistory.add("I am on my way!");
        clipboardHistory.add("কেমন আছেন?");
    }

    private void updateClipboardFromSystem() {
        if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                String text = clip.getItemAt(0).getText().toString();
                if (!TextUtils.isEmpty(text) && !clipboardHistory.contains(text)) {
                    clipboardHistory.add(0, text);
                    if (clipboardHistory.size() > 10) {
                        clipboardHistory.remove(clipboardHistory.size() - 1);
                    }
                }
            }
        }
    }

    private void initEmoji() {
        emojiGrid.setLayoutManager(new GridLayoutManager(this, 7));
        EmojiAdapter adapter = new EmojiAdapter(emojis, emoji -> {
            sendText(emoji);
        });
        emojiGrid.setAdapter(adapter);
    }

    private void updateClipboardUI() {
        clipboardItems.removeAllViews();

        if (clipboardHistory.isEmpty()) {
            clipboardEmpty.setVisibility(View.VISIBLE);
        } else {
            clipboardEmpty.setVisibility(View.GONE);
            for (String item : clipboardHistory) {
                TextView tv = new TextView(this);
                tv.setText(item);
                tv.setBackgroundResource(R.drawable.clipboard_item_bg);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(14f);
                tv.setPadding(dp(12), dp(12), dp(12), dp(12));
                tv.setMaxLines(3);
                tv.setEllipsize(TextUtils.TruncateAt.END);

                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                p.setMargins(0, 0, 0, dp(6));
                tv.setLayoutParams(p);

                tv.setOnClickListener(v -> sendText(item));
                tv.setOnLongClickListener(v -> {
                    clipboardHistory.remove(item);
                    updateClipboardUI();
                    return true;
                });

                clipboardItems.addView(tv);
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}