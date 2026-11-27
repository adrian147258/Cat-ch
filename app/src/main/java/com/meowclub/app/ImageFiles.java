package com.meowclub.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** File and sizing rules shared by the photo cache and importer. */
public final class ImageFiles {
    public static final long MAX_ORIGINAL = 30_000_000;
    public static final long MAX_UPLOAD = 5_000_000;

    public static String key(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            String digits = "0123456789abcdef";
            char[] result = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                result[2 * i] = digits.charAt((hash[i] & 255) >>> 4);
                result[2 * i + 1] = digits.charAt(hash[i] & 15);
            }
            return new String(result);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static int sampleSize(int width, int height, int target) {
        if (width <= 0 || height <= 0 || target <= 0) throw new IllegalArgumentException("Invalid image size");
        int sample = 1;
        while (Math.max(width, height) / sample > target) sample *= 2;
        return sample;
    }

    public static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            total += n;
            if (total > limit) throw new IOException("图片过大，原图最多支持 30MB");
            out.write(buffer, 0, n);
        }
        if (total == 0) throw new IOException("图片为空");
        return total;
    }
}
