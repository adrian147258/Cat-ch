package com.meowclub.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Survives Activity recreation without retaining an Activity or losing upload state. */
public final class UploadJob {
    public interface Listener { void changed(UploadJob job); }
    public static UploadJob current;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final String account, draftId;
    public final DraftStore store;
    public final DraftStore.Draft snapshot;
    public volatile int percent;
    public volatile boolean finished, succeeded, uncertain;
    public volatile String error = "";
    private Listener listener;

    public UploadJob(Context context, String account, DraftStore.Draft snapshot) {
        this.account = account; this.snapshot = snapshot; this.draftId = snapshot.id;
        store = new DraftStore(context.getApplicationContext());
    }
    public void attach(Listener listener) { this.listener = listener; listener.changed(this); }
    public void detach(Listener listener) { if (this.listener == listener) this.listener = null; }
    private void notifyChanged() { main.post(() -> { if (listener != null) listener.changed(this); }); }
    public void start(String api, String token, String name) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            boolean sentBody = false, gotResponse = false;
            try {
                String boundary = "Catch" + UUID.randomUUID().toString().replace("-", "");
                UploadBody body = new UploadBody(snapshot.photos, boundary);
                connection = (HttpURLConnection) new URL(api + "/posts").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Origin", api);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setRequestProperty("X-Meow-Text", encode(snapshot.text));
                connection.setRequestProperty("X-Meow-Name", encode(name));
                connection.setRequestProperty("X-Meow-Breed", encode(snapshot.breed));
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    sentBody = true;
                    body.write(output, value -> { percent = value; notifyChanged(); });
                }
                int status = connection.getResponseCode();
                gotResponse = true;
                if (status < 200 || status >= 300) {
                    String message = "发布失败（HTTP " + status + "），请稍后重试";
                    try (InputStream input = connection.getErrorStream()) {
                        if (input != null) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024]; int n;
                            while ((n = input.read(buffer)) != -1 && bytes.size() < 65536) bytes.write(buffer, 0, n);
                            JSONObject data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                            message = data.optString("message", data.optString("error", message));
                        }
                    } catch (Exception ignored) { }
                    if (status == 401) message = "登录已过期，草稿已保留，请重新登录后发布";
                    uncertain = status >= 500;
                    throw new IOException(message);
                }
                succeeded = true;
                store.clear(account, draftId);
            } catch (Exception failure) {
                uncertain = uncertain || (sentBody && !gotResponse);
                error = uncertain ? "网络中断，发布结果未确认；请先查看我的发布，避免重复上传" :
                    failure instanceof UnknownHostException ? "无法连接服务器，请检查网络后重试" :
                    failure instanceof SocketTimeoutException ? "连接超时，请稍后重试" :
                    failure.getMessage() == null ? "发布失败，请稍后重试" : failure.getMessage();
                snapshot.pending = uncertain;
                store.save(account, snapshot);
            } finally {
                if (connection != null) connection.disconnect();
                finished = true;
                notifyChanged();
            }
        }, "cat-photo-upload").start();
    }
    private static String encode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
}
