package com.meowclub.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;

/** Loads four transparent illustrations from the supplied cat sprite sheet. */
final class CatArt {
 private static Bitmap[] cats;

 private static synchronized Bitmap cat(Context context,int index){
  if(cats==null){
   cats=new Bitmap[4];
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inSampleSize=2;
   try(InputStream input=context.getAssets().open("nav_cats.png")){
    Bitmap sheet=BitmapFactory.decodeStream(input,null,options);
    if(sheet!=null){
     int width=sheet.getWidth()/2,height=sheet.getHeight()/2;
     for(int i=0;i<cats.length;i++)cats[i]=Bitmap.createBitmap(sheet,(i%2)*width,(i/2)*height,width,height);
     sheet.recycle();
    }
   }catch(IOException ignored){}
  }
  return cats[index];
 }

 static ImageView image(Context context,int index){
  ImageView image=new ImageView(context);
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  image.setImageBitmap(cat(context,index));
  image.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
  return image;
 }
 private CatArt(){}
}
