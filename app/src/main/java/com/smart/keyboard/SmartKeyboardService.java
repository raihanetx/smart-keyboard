package com.smart.keyboard;

import android.animation.ValueAnimator;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class SmartKeyboardService extends InputMethodService {

    private View mainView;
    
    private LinearLayout panelVoice;
    private LinearLayout panelTranslate;
    private LinearLayout panelClipboard;
    private RecyclerView emojiGrid;
    private LinearLayout keyboardContainer;
    
    private ImageView btnLang, btnTranslate, btnVoice, btnClipboard, btnEmoji;
    private TextView voiceStatus, voiceHint;
    private EditText transInput;
    private TextView transResult;
    private TextView transFrom, transTo;
    private ImageView transSwap;
    private LinearLayout clipboardItems;
    private TextView clipboardEmpty;
    
    private SpeechRecognizer speechRecognizer;
    private boolean isRecording = false;
    private boolean isShifted = false;
    private boolean isLangBangla = false;
    private boolean isTransSwapped = false;
    private String currentMode = "keyboard";
    
    private List<String> clipboardHistory = new ArrayList<>();
    private String[] emojis = {"😊","😂","❤️","👍","🔥","🤔","🙏","✨","🥺","🚀","💻","✅","⚡","🎉","💡","🌈","🍕","☕","🐱","🐶","🌍"};
    
    private View[] voiceBars = new View[5];

    private static final String[][] EN_LAYOUT = {
        {"q","w","e","r","t","y","u","i","o","p"},
        {"a","s","d","f","g","h","j","k","l"},
        {"SHIFT","z","x","c","v","b","n","m","BACKSPACE"}
    };
    
    private static final String[][] EN_SHIFTED = {
        {"Q","W","E","R","T","Y","U","I","O","P"},
        {"A","S","D","F","G","H","J","K","L"},
        {"SHIFT","Z","X","C","V","B","N","M","BACKSPACE"}
    };
    
    private static final String[][] BN_LAYOUT = {
        {"দ","ধ","ে","র","ত","থ","উ","ই","ও","প"},
        {"আ","স","ড","ফ","গ","হ","জ","ক","ল"},
        {"SHIFT","য","ষ","চ","ভ","ব","ন","ম","BACKSPACE"}
    };
    
    private static final String[][] BN_SHIFTED = {
        {"দ্","ধ্","ৈ","ড়","ৎ","থ্","ঊ","ঈ","ঔ","ফ"},
        {"অ","ষ","ঢ","ঁ","ঘ","ঃ","ঝ","খ","ল"},
        {"SHIFT","য়","ঢ়","ছ","ভ্","ব্ব","ণ","ম্ম","BACKSPACE"}
    };

    @Override
    public View onCreateInputView() {
        mainView = LayoutInflater.from(this).inflate(R.layout.keyboard_main, null);
        
        initViews();
        initToolbar();
        initKeyboard();
        initVoice();
        initTranslate();
        initClipboard();
        initEmoji();
        
        return mainView;
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
            buildKeyboard();
            showKeyboard();
        });
        
        btnTranslate.setOnClickListener(v -> togglePanel("translate"));
        btnVoice.setOnClickListener(v -> {
            if (currentMode.equals("voice") && isRecording) {
                toggleVoice();
            } else {
                togglePanel("voice");
                if (!isRecording) toggleVoice();
            }
        });
        btnClipboard.setOnClickListener(v -> togglePanel("clipboard"));
        btnEmoji.setOnClickListener(v -> togglePanel("emoji"));
    }
    
    private void togglePanel(String panel) {
        hideAllPanels();
        resetToolbarColors();
        
        if (currentMode.equals(panel)) {
            currentMode = "keyboard";
            showKeyboard();
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
        keyboardContainer.setVisibility(View.GONE);
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
    
    private void initKeyboard() {
        buildKeyboard();
    }
    
    private void buildKeyboard() {
        keyboardContainer.removeAllViews();
        
        String[][] layout = getCurrentLayout();
        
        for (int row = 0; row < layout.length; row++) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, dp(2), 0, dp(2));
            rowView.setLayoutParams(rowParams);
            
            if (row == 1) {
                rowView.setPadding(dp(12), 0, dp(12), 0);
            }
            
            for (String key : layout[row]) {
                Button keyBtn = createKeyButton(key);
                rowView.addView(keyBtn);
            }
            
            keyboardContainer.addView(rowView);
        }
        
        addBottomRow();
    }
    
    private Button createKeyButton(String key) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params;
        
        String display = key;
        int bgRes = R.drawable.key_bg;
        
        switch (key) {
            case "SHIFT":
                display = "⇧";
                bgRes = R.drawable.key_bg_shift;
                params = new LinearLayout.LayoutParams(dp(48), dp(48));
                btn.setActivated(isShifted);
                break;
            case "BACKSPACE":
                display = "⌫";
                bgRes = R.drawable.key_bg_special;
                params = new LinearLayout.LayoutParams(dp(48), dp(48));
                break;
            default:
                params = new LinearLayout.LayoutParams(0, dp(48), 1f);
                break;
        }
        
        btn.setLayoutParams(params);
        btn.setText(display);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
        btn.setBackgroundResource(bgRes);
        btn.setAllCaps(false);
        btn.setPadding(0, 0, 0, 0);
        
        btn.setOnClickListener(v -> handleKey(key));
        
        return btn;
    }
    
    private void addBottomRow() {
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(8), 0, 0);
        bottomRow.setLayoutParams(rowParams);
        
        Button btn123 = new Button(this);
        btn123.setText("123");
        styleSpecialKey(btn123, dp(56));
        btn123.setOnClickListener(v -> toggleSymbols());
        bottomRow.addView(btn123);
        
        Button btnComma = new Button(this);
        btnComma.setText(",");
        styleKey(btnComma, dp(48));
        btnComma.setOnClickListener(v -> sendText(","));
        bottomRow.addView(btnComma);
        
        Button btnSpace = new Button(this);
        btnSpace.setText(isLangBangla ? "স্পেস" : "space");
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        btnSpace.setLayoutParams(spaceParams);
        btnSpace.setTextColor(Color.WHITE);
        btnSpace.setTextSize(14);
        btnSpace.setBackgroundResource(R.drawable.key_bg);
        btnSpace.setOnClickListener(v -> sendText(" "));
        bottomRow.addView(btnSpace);
        
        Button btnDot = new Button(this);
        btnDot.setText(".");
        styleKey(btnDot, dp(48));
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
    
    private void toggleSymbols() {
        isShifted = !isShifted;
        if (currentMode.equals("keyboard")) {
            buildKeyboard();
        }
    }
    
    private void styleKey(Button btn, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        btn.setLayoutParams(params);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
        btn.setBackgroundResource(R.drawable.key_bg);
        btn.setPadding(0, 0, 0, 0);
    }
    
    private void styleSpecialKey(Button btn, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        btn.setLayoutParams(params);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setBackgroundResource(R.drawable.key_bg_special);
        btn.setPadding(0, 0, 0, 0);
    }
    
    private String[][] getCurrentLayout() {
        if (isLangBangla) {
            return isShifted ? BN_SHIFTED : BN_LAYOUT;
        } else {
            return isShifted ? EN_SHIFTED : EN_LAYOUT;
        }
    }
    
    private void handleKey(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        
        switch (key) {
            case "SHIFT":
                isShifted = !isShifted;
                buildKeyboard();
                break;
            case "BACKSPACE":
                ic.deleteSurroundingText(1, 0);
                break;
            default:
                sendText(key);
                if (isShifted) {
                    isShifted = false;
                    buildKeyboard();
                }
                break;
        }
    }
    
    private void sendText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }
    
    private void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText("\n", 1);
        }
    }
    
    private void showKeyboard() {
        hideAllPanels();
        keyboardContainer.setVisibility(View.VISIBLE);
        currentMode = "keyboard";
    }
    
    private void initVoice() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                isRecording = false;
                voiceStatus.setText("ERROR");
                stopVoiceAnimation();
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    sendText(matches.get(0) + " ");
                }
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
            
            speechRecognizer.startListening(intent);
            isRecording = true;
            voiceStatus.setText("LISTENING");
            voiceHint.setText("Speak now...");
            startVoiceAnimation();
            btnVoice.setColorFilter(Color.parseColor("#ef4444"));
        }
    }
    
    private void startVoiceAnimation() {
        for (int i = 0; i < voiceBars.length; i++) {
            final View bar = voiceBars[i];
            ValueAnimator anim = ValueAnimator.ofInt(dp(16), dp(80));
            anim.setDuration(500);
            anim.setStartDelay(i * 100);
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
            String text = transInput.getText().toString();
            if (!text.isEmpty()) {
                transResult.setText("Translation requires API key");
            }
            return true;
        });
    }
    
    private void initClipboard() {
        clipboardHistory.add("I am on my way!");
        clipboardHistory.add("কেমন আছেন?");
    }
    
    private void initEmoji() {
        emojiGrid.setLayoutManager(new GridLayoutManager(this, 7));
        emojiGrid.setAdapter(new EmojiAdapter(emojis, this::sendText));
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
                tv.setPadding(dp(12), dp(12), dp(12), dp(12));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                p.setMargins(0, 0, 0, dp(8));
                tv.setLayoutParams(p);
                tv.setOnClickListener(v -> sendText(item));
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