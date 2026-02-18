package com.smart.keyboard;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GeminiLiveService {
    private static final String TAG = "GeminiLive";
    private static final String API_KEY = "AIzaSyCm8cjmIqwD3c64p2nsjp5YgCKoyyeQv08";
    private static final String WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=" + API_KEY;

    private WebSocket webSocket;
    private OkHttpClient client;
    private AudioRecord audioRecord;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private GeminiCallback callback;

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;

    public interface GeminiCallback {
        void onTranscript(String text, boolean isFinal);
        void onError(String error);
        void onConnected();
        void onDisconnected();
    }

    public GeminiLiveService() {
        client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
    }

    public void start(GeminiCallback callback) {
        this.callback = callback;
        connectWebSocket();
    }

    private void connectWebSocket() {
        Request request = new Request.Builder()
            .url(WS_URL)
            .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected");
                sendSetupMessage();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received: " + text);
                parseResponse(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                if (callback != null) {
                    callback.onError(t.getMessage());
                    callback.onDisconnected();
                }
                stop();
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                if (callback != null) {
                    callback.onDisconnected();
                }
            }
        });
    }

    private void sendSetupMessage() {
        try {
            JSONObject setup = new JSONObject();
            JSONObject setupObj = new JSONObject();
            JSONObject model = new JSONObject();
            
            model.put("model", "gemini-2.0-flash-exp");
            
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseModalities", new JSONArray().put("TEXT"));
            model.put("generationConfig", generationConfig);
            
            setupObj.put("model", model);
            setup.put("setup", setupObj);

            webSocket.send(setup.toString());
            Log.d(TAG, "Sent setup: " + setup.toString());

            if (callback != null) {
                callback.onConnected();
            }
            
            startRecording();
            
        } catch (Exception e) {
            Log.e(TAG, "Setup error: " + e.getMessage());
            if (callback != null) {
                callback.onError("Setup failed: " + e.getMessage());
            }
        }
    }

    private void startRecording() {
        audioRecord = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            if (callback != null) {
                callback.onError("Audio record not initialized");
            }
            return;
        }

        isRecording.set(true);
        audioRecord.startRecording();

        new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int chunkCount = 0;

            while (isRecording.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    outputStream.write(buffer, 0, read);
                    chunkCount++;

                    if (chunkCount % 5 == 0 && outputStream.size() > 0) {
                        sendAudioChunk(outputStream.toByteArray());
                        outputStream.reset();
                    }
                }
            }
        }).start();
    }

    private void sendAudioChunk(byte[] audioData) {
        if (webSocket == null || !isRecording.get()) return;

        try {
            JSONObject message = new JSONObject();
            JSONObject realtimeInput = new JSONObject();
            JSONArray mediaChunks = new JSONArray();
            JSONObject mediaChunk = new JSONObject();
            
            JSONObject inlineData = new JSONObject();
            inlineData.put("mimeType", "audio/pcm;rate=16000");
            inlineData.put("data", android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP));
            
            mediaChunk.put("inlineData", inlineData);
            mediaChunks.put(mediaChunk);
            realtimeInput.put("mediaChunks", mediaChunks);
            message.put("realtimeInput", realtimeInput);

            webSocket.send(message.toString());
            Log.d(TAG, "Sent audio chunk: " + audioData.length + " bytes");

        } catch (Exception e) {
            Log.e(TAG, "Send audio error: " + e.getMessage());
        }
    }

    private void parseResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);

            if (json.has("serverContent")) {
                JSONObject serverContent = json.getJSONObject("serverContent");
                
                if (serverContent.has("modelTurn")) {
                    JSONObject modelTurn = serverContent.getJSONObject("modelTurn");
                    if (modelTurn.has("parts")) {
                        JSONArray parts = modelTurn.getJSONArray("parts");
                        StringBuilder text = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("text")) {
                                text.append(part.getString("text"));
                            }
                        }
                        if (text.length() > 0 && callback != null) {
                            callback.onTranscript(text.toString(), true);
                        }
                    }
                }
            }

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete");
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
        }
    }

    public void stop() {
        isRecording.set(false);
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (webSocket != null) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("audioStreamEnd", true);
                webSocket.send(msg.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error sending end: " + e.getMessage());
            }
            webSocket.close(1000, "User stopped");
            webSocket = null;
        }
    }

    public boolean isRecording() {
        return isRecording.get();
    }
}