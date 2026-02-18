package com.smart.keyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.content.Context;

import java.util.ArrayList;

public class SmartKeyboardService extends InputMethodService 
    implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard keyboard;
    private Keyboard symbolsKeyboard;
    private Keyboard ctrlKeyboard;
    private boolean isCaps = false;
    private boolean isCtrlMode = false;
    private boolean isSymbolMode = false;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}
            @Override
            public void onError(int error) {
                isListening = false;
                showToast("Voice error. Try again.");
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    getCurrentInputConnection().commitText(matches.get(0) + " ", 1);
                }
                isListening = false;
            }
            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    @Override
    public View onCreateInputView() {
        keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard_view, null);
        keyboard = new Keyboard(this, R.xml.qwerty);
        symbolsKeyboard = new Keyboard(this, R.xml.symbols);
        ctrlKeyboard = new Keyboard(this, R.xml.ctrl);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        
        if (isCtrlMode) {
            handleCtrlKey(primaryCode, ic);
            return;
        }
        
        switch (primaryCode) {
            case -1: 
                isCaps = !isCaps;
                keyboard.setShifted(isCaps);
                keyboardView.invalidateAllKeys();
                break;
            case -2:
                isSymbolMode = !isSymbolMode;
                keyboardView.setKeyboard(isSymbolMode ? symbolsKeyboard : keyboard);
                break;
            case -3:
                isCtrlMode = !isCtrlMode;
                if (isCtrlMode) {
                    keyboardView.setKeyboard(ctrlKeyboard);
                } else {
                    keyboardView.setKeyboard(keyboard);
                }
                break;
            case -4:
                ic.deleteSurroundingText(1, 0);
                break;
            case -5:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                break;
            case -6:
                startVoiceInput();
                break;
            case -7:
                ic.performContextMenuAction(android.R.id.selectAll);
                break;
            case -8:
                ic.performContextMenuAction(android.R.id.copy);
                break;
            case -9:
                ic.performContextMenuAction(android.R.id.paste);
                break;
            case -10:
                ic.performContextMenuAction(android.R.id.cut);
                break;
            case -11:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
                break;
            case -12:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB));
                break;
            case 32:
                ic.commitText(" ", 1);
                break;
            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code), 1);
        }
    }

    private void handleCtrlKey(int code, InputConnection ic) {
        isCtrlMode = false;
        keyboardView.setKeyboard(keyboard);
        
        switch (code) {
            case 1: // Ctrl+A
                ic.performContextMenuAction(android.R.id.selectAll);
                showToast("Select All");
                break;
            case 2: // Ctrl+C
                ic.performContextMenuAction(android.R.id.copy);
                showToast("Copied");
                break;
            case 3: // Ctrl+V
                ic.performContextMenuAction(android.R.id.paste);
                showToast("Pasted");
                break;
            case 4: // Ctrl+X
                ic.performContextMenuAction(android.R.id.cut);
                showToast("Cut");
                break;
            case 5: // Ctrl+Z
                KeyEvent ctrlZ = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
                ic.sendKeyEvent(ctrlZ);
                ic.sendKeyEvent(KeyEvent.changeAction(ctrlZ, KeyEvent.ACTION_UP));
                showToast("Undo");
                break;
        }
    }

    private void startVoiceInput() {
        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            return;
        }
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        
        isListening = true;
        speechRecognizer.startListening(intent);
        showToast("Listening...");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onPress(int primaryCode) {}
    @Override public void onRelease(int primaryCode) {}
    @Override public void onText(CharSequence text) {
        getCurrentInputConnection().commitText(text, 1);
    }
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
