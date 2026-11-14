package com.meowclub.app;
import android.content.Context;
import android.graphics.Typeface;
final class AppFont {
 private static Typeface font;
 static synchronized Typeface get(Context context) {
  if (font == null) font = Typeface.createFromAsset(context.getAssets(), "LXGWWenKai-Regular.ttf");
  return font;
 }
}
