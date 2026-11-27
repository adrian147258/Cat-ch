package com.meowclub.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Streaming multipart body: bounded memory and progress based on bytes actually written. */
public final class UploadBody {
    public interface Progress { void update(int percent) throws IOException; }
    public final ArrayList<File> files = new ArrayList<>();
    public final ArrayList<Long> sizes = new ArrayList<>();
    public final byte[] prefix, suffix;
    public final long length;
    public UploadBody(List<String> paths, String boundary) throws IOException {
        if (paths.isEmpty() || paths.size() > 9) throw new IOException("请选择 1 至 9 张图片");
        prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"cat.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        suffix = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        long total = suffix.length;
        for (String path : paths) {
            File file = new File(path);
            long size = file.length();
            if (!file.isFile() || size <= 0 || size > ImageFiles.MAX_UPLOAD) throw new IOException("照片不可用或压缩后超过 5MB，请重新选择");
            files.add(file); sizes.add(size); total += prefix.length + size + 2;
        }
        length = total;
    }
    public void write(OutputStream output, Progress progress) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long written = 0;
        int previous = -1;
        for (int i = 0; i < files.size(); i++) {
            output.write(prefix); written += prefix.length;
            long sent = 0;
            try (InputStream input = new FileInputStream(files.get(i))) {
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    sent += n;
                    if (sent > sizes.get(i)) throw new IOException("照片已变化，请重新选择");
                    output.write(buffer, 0, n); written += n;
                    int percent = (int) (written * 100 / length);
                    if (percent != previous) { previous = percent; progress.update(percent); }
                }
            }
            if (sent != sizes.get(i)) throw new IOException("照片已变化，请重新选择");
            output.write('\r'); output.write('\n'); written += 2;
        }
        output.write(suffix);
        progress.update(100);
    }
}
