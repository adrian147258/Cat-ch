package com.meowclub.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.File;
import java.util.*;

/** A draft belongs to a user id, never to a session token or another logged-in user. */
public final class DraftStore {
    public static final class Draft {
        public String id = UUID.randomUUID().toString(), text = "", breed = "";
        public boolean pending;
        public final ArrayList<String> photos = new ArrayList<>();
    }
    public final SharedPreferences prefs;
    public final File directory;
    public DraftStore(Context context) {
        prefs = context.getSharedPreferences("publish-drafts", Context.MODE_PRIVATE);
        directory = new File(context.getFilesDir(), "draft-photos");
        directory.mkdirs();
    }
    public String key(String account) { return "draft_" + ImageFiles.key(account); }
    public synchronized Draft read(String account) {
        Draft draft = new Draft();
        if (account.isEmpty()) return draft;
        try {
            JSONObject data = new JSONObject(prefs.getString(key(account), "{}"));
            draft.id = data.optString("id", draft.id);
            draft.text = data.optString("text", "");
            draft.breed = data.optString("breed", "");
            draft.pending = data.optBoolean("pending");
            JSONArray photos = data.optJSONArray("photos");
            if (photos != null) for (int i = 0; i < Math.min(9, photos.length()); i++) {
                File photo = new File(photos.getString(i));
                if (owned(photo) && photo.isFile()) draft.photos.add(photo.getAbsolutePath());
            }
        } catch (JSONException ignored) { }
        return draft;
    }
    public synchronized boolean save(String account, Draft draft) {
        if (account.isEmpty()) return false;
        try {
            JSONObject data = new JSONObject().put("id", draft.id).put("text", draft.text)
                .put("breed", draft.breed).put("pending", draft.pending).put("photos", new JSONArray(draft.photos));
            // Small metadata only; the caller can debounce typing. Commit before leaving or uploading.
            return prefs.edit().putString(key(account), data.toString()).commit();
        } catch (JSONException e) { return false; }
    }
    public synchronized boolean clear(String account, String expectedId) {
        Draft draft = read(account);
        if (!draft.id.equals(expectedId)) return false;
        if (!prefs.edit().remove(key(account)).commit()) return false;
        deletePhotos(draft.photos);
        return true;
    }
    public boolean owned(File file) {
        try { return directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile()); }
        catch (java.io.IOException e) { return false; }
    }
    public void deletePhotos(Collection<String> photos) {
        for (String path : photos) { File file = new File(path); if (owned(file)) file.delete(); }
    }
}
