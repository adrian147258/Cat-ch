package com.meowclub.app;

import android.animation.*;import android.view.animation.*;import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.net.Uri;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.net.*;import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
 static final String API=BuildConfig.API_BASE_URL;
 final java.util.concurrent.ExecutorService net=java.util.concurrent.Executors.newSingleThreadExecutor();
 boolean publishing=false; int loadSerial=0;
 FrameLayout host, drawerLayer; LinearLayout drawerPanel; boolean navigating=false; String route="";
 final ArrayDeque<Screen> history=new ArrayDeque<>();
 final Handler ui=new Handler(Looper.getMainLooper());
 final java.util.concurrent.ExecutorService images=java.util.concurrent.Executors.newFixedThreadPool(2);
 final android.util.LruCache<String,Bitmap> imageCache=new android.util.LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 AnimatedCards feedCards,searchCards,collectionCards,profileCards; PullScroll activeScroll; TextView aboutProfile;
 String query=""; TextView popup;

 int cream=Color.rgb(255,249,245),pink=Color.rgb(239,128,153),ink=Color.rgb(99,65,55),muted=Color.rgb(159,137,129),pale=Color.rgb(251,233,233); LinearLayout root,body,nav; String page="广场",filter="全部"; android.content.SharedPreferences prefs; ArrayList<Post> posts=new ArrayList<>(); ArrayList<String> chosen=new ArrayList<>(); EditText story; String draft=""; int seq=0; boolean detailVisible=false; String profileTab="我的发布"; LinearLayout profileContent,profileTabs;
 class Post {String id,name,text,photo,breed; boolean mine; Post(String i,String n,String t,String p,String b,boolean m){id=i;name=n;text=t;photo=p;breed=b;mine=m;}}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(cream);getWindow().setNavigationBarColor(cream);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);prefs=getSharedPreferences("meow",0);try{JSONArray a=new JSONArray(prefs.getString("posts","[]"));for(int i=a.length()-1;i>=0;i--){JSONObject o=a.getJSONObject(i);posts.add(0,new Post(o.getString("id"),"我",o.getString("text"),o.getString("photo"),"我的猫",true));}}catch(Exception e){}startSplash();}
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} GradientDrawable bg(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;} TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;} LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;} LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;} void add(LinearLayout p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));} TextView button(String s,Runnable run){TextView t=txt(s,15,ink);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(46));t.setBackground(bg(pale,24));t.setOnClickListener(v->run.run());return t;} void gap(LinearLayout p,int h){add(p,new View(this),1,h);} void title(String s,String action,Runnable r){LinearLayout h=row();boolean sub=!history.isEmpty();TextView menu=button(sub?"‹":"☰",()->{if(sub)back();else openDrawer();});menu.setContentDescription(sub?"返回":"打开菜单");menu.setBackgroundColor(Color.TRANSPARENT);add(h,menu,42,44);TextView t=txt(s,23,ink);t.setTypeface(null,1);h.addView(t,new LinearLayout.LayoutParams(0,dp(60),1));if(action!=null)add(h,button(action,r),48,44);add(body,h,-1,-2);}
 void spring(View v){v.animate().cancel();v.setScaleX(.82f);v.setScaleY(.82f);v.animate().scaleX(1).scaleY(1).setDuration(430).setInterpolator(new OvershootInterpolator(2.3f)).start();}
 void note(String s){if(host==null)return;if(popup!=null)host.removeView(popup);TextView t=txt(s,14,Color.WHITE);popup=t;t.setPadding(dp(20),dp(10),dp(20),dp(10));t.setBackground(bg(ink,24));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.bottomMargin=dp(80);host.addView(t,lp);spring(t);t.setAlpha(0);t.animate().alpha(1).setDuration(430).start();ui.postDelayed(()->t.animate().alpha(0).setDuration(160).withEndAction(()->{host.removeView(t);if(popup==t)popup=null;}).start(),1800);}

 void base(boolean navigation){
  if(host==null){host=new FrameLayout(this);host.setBackgroundColor(cream);setContentView(host);
   host.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});}
  root=col();root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(Build.VERSION.SDK_INT>=29){ArrayList<Rect> exclusions=new ArrayList<>();if(!history.isEmpty()){int center=(b-t)/2;exclusions.add(new Rect(0,Math.max(0,center-dp(100)),dp(26),Math.min(b-t,center+dp(100))));}v.setSystemGestureExclusionRects(exclusions);}});root.setBackgroundColor(cream);host.addView(root,new FrameLayout.LayoutParams(-1,-1));
  activeScroll=new PullScroll();activeScroll.enabled=navigation;body=col();body.setPadding(dp(18),dp(8),dp(18),dp(22));activeScroll.setContent(body);
  root.addView(activeScroll,new LinearLayout.LayoutParams(-1,0,1));
  if(navigation){nav=row();nav.setPadding(dp(10),dp(2),dp(10),dp(2));nav.setBackground(bg(Color.rgb(255,245,240),24));
   for(String s:new String[]{"广场","发现","发布","我的"}){LinearLayout cell=col();cell.setGravity(Gravity.CENTER);
    if(s.equals("发布")){add(cell,new PlusIcon(),42,42);}else{add(cell,new CatIcon(s.equals(page)),34,25);TextView label=txt(s,11,s.equals(page)?pink:ink);label.setGravity(Gravity.CENTER);label.setPadding(0,0,0,0);label.setIncludeFontPadding(false);add(cell,label,-1,18);}
    cell.setContentDescription(s);cell.setOnClickListener(v->show(s));nav.addView(cell,new LinearLayout.LayoutParams(0,dp(50),1));}add(root,nav,-1,-2);
  }
 }
 boolean secondary(String p){return p.equals("发布")||p.equals("设置")||p.equals("帮助")||p.equals("关于");}
 void show(String p){
  if(navigating||drawerLayer!=null)return;
  if(p.equals(route)&&!detailVisible)return;
  if(secondary(p)){push(p,null);return;}
  Screen previous=root==null?null:new Screen();history.clear();route=p;page=p;detailVisible=false;filter="全部";build(p,null);
  if(previous==null)return;
  LinearLayout incoming=root; navigating=true; incoming.setAlpha(0);incoming.setTranslationY(dp(8));
  previous.root.animate().alpha(0).translationY(-dp(10)).setDuration(240).setInterpolator(new AccelerateDecelerateInterpolator()).start();
  incoming.animate().alpha(1).translationY(0).setStartDelay(80).setDuration(320).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{incoming.animate().setStartDelay(0);for(int i=host.getChildCount()-1;i>=0;i--){View child=host.getChildAt(i);if(child!=incoming&&child!=popup)host.removeView(child);}navigating=false;}).start();
 }
 void build(String p,Post post){base(!secondary(p)&&!p.equals("详情"));
  switch(p){case "广场":feed();break;case "发现":discover();break;case "收藏":collection();break;case "我的":profile();break;case "发布":publish();break;case "设置":settings();break;case "详情":detailContent(post);break;default:info(p);}
 }
 void push(String p,Post post){if(navigating)return;history.push(new Screen());route=p;detailVisible=p.equals("详情");build(p,post);LinearLayout next=root;navigating=true;
  next.setTranslationX(getResources().getDisplayMetrics().widthPixels);next.animate().translationX(0).setDuration(300).setInterpolator(new DecelerateInterpolator()).withEndAction(()->navigating=false).start();
 }
 void back(){if(navigating)return;if(drawerLayer!=null){closeDrawer(null);return;}if(history.isEmpty()){if(!page.equals("广场"))show("广场");else finish();return;}
  if(route.equals("发布")&&story!=null)draft=story.getText().toString();
  LinearLayout outgoing=root;Screen previous=history.peek();navigating=true;previous.root.setVisibility(View.VISIBLE);
  outgoing.animate().translationX(host.getWidth()).setDuration(300).setInterpolator(new DecelerateInterpolator()).withEndAction(()->finishBack(outgoing)).start();
 }
 void finishBack(LinearLayout outgoing){host.removeView(outgoing);history.pop().restore();navigating=false;refreshVisible();}
 void refreshBindings(View v){if(v.getTag() instanceof Runnable)((Runnable)v.getTag()).run();if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)refreshBindings(g.getChildAt(i));}}
 void refreshVisible(){refreshBindings(root);if(route.equals("广场"))updateFeed();else if(route.equals("发现"))renderSearch(query);else if(route.equals("收藏"))updateCollection();else if(route.equals("我的"))selectProfileTab(profileTab);}
 class Screen {
  LinearLayout root=MainActivity.this.root,body=MainActivity.this.body,nav=MainActivity.this.nav,pt=profileTabs,pc=profileContent;
  AnimatedCards fc=feedCards,sc=searchCards,cc=collectionCards,pr=profileCards;PullScroll scroll=activeScroll;TextView about=aboutProfile;EditText editor=story;
  String route=MainActivity.this.route,page=MainActivity.this.page,filter=MainActivity.this.filter,q=query;boolean detail=detailVisible;
  void restore(){MainActivity.this.root=root;MainActivity.this.body=body;MainActivity.this.nav=nav;profileTabs=pt;profileContent=pc;feedCards=fc;searchCards=sc;collectionCards=cc;profileCards=pr;activeScroll=scroll;aboutProfile=about;story=editor;MainActivity.this.route=route;MainActivity.this.page=page;MainActivity.this.filter=filter;query=q;detailVisible=detail;}
 }
 void tabs(String[] labels,java.util.function.Consumer<String> select){LinearLayout line=row();for(String s:labels){TextView t=button(s,()->{for(int i=0;i<line.getChildCount();i++){TextView child=(TextView)line.getChildAt(i);boolean selected=child.getText().toString().equals(s);child.setTextColor(selected?Color.WHITE:ink);child.setBackground(bg(selected?pink:pale,22));}select.accept(s);});t.setTextColor(s.equals(filter)?Color.WHITE:ink);t.setBackground(bg(s.equals(filter)?pink:pale,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.setMargins(dp(3),dp(5),dp(3),dp(12));line.addView(t,lp);}add(body,line,-1,-2);}
 ImageView photo(Post p,int h){
  ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(bg(pale,18));v.setClipToOutline(true);v.setContentDescription(p.text);v.setOnClickListener(w->{if(!p.id.isEmpty()&&!route.equals("详情"))detail(p);});v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(h)));
  final String key=p.photo;v.setTag(key);Bitmap thumb=imageCache.get(key+"_thumb");
  if(thumb==null&&!key.startsWith("https://"))try{InputStream in=key.startsWith("/")?new FileInputStream(key+".thumb"):getAssets().open("thumb_"+key);thumb=BitmapFactory.decodeStream(in);in.close();if(thumb!=null)imageCache.put(key+"_thumb",thumb);}catch(Exception ignored){}
  if(thumb==null&&!key.startsWith("https://"))try{BitmapFactory.Options tiny=new BitmapFactory.Options();tiny.inSampleSize=64;try(InputStream in=openPhoto(key)){thumb=BitmapFactory.decodeStream(in,null,tiny);}if(thumb!=null)imageCache.put(key+"_thumb",thumb);}catch(Exception ignored){}
  final Drawable placeholder=thumb==null?bg(pale,18):new BitmapDrawable(getResources(),thumb);v.setImageDrawable(placeholder);
  images.execute(()->{try{Bitmap bitmap=imageCache.get(key);if(bitmap==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=openPhoto(key)){BitmapFactory.decodeStream(in,null,o);}o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1600)o.inSampleSize*=2;o.inJustDecodeBounds=false;try(InputStream in=openPhoto(key)){bitmap=BitmapFactory.decodeStream(in,null,o);}if(bitmap==null)throw new IOException();imageCache.put(key,bitmap);}final Bitmap loaded=bitmap;
   ui.post(()->{if(!key.equals(v.getTag()))return;android.graphics.drawable.TransitionDrawable transition=new android.graphics.drawable.TransitionDrawable(new Drawable[]{placeholder,new BitmapDrawable(getResources(),loaded)});transition.setCrossFadeEnabled(true);v.setImageDrawable(transition);transition.startTransition(320);});
  }catch(Exception e){ui.post(()->v.setContentDescription("图片无法读取"));}});return v;
 }
 InputStream openPhoto(String p)throws IOException{if(p.startsWith("https://")){HttpURLConnection c=(HttpURLConnection)new URL(p).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);return c.getInputStream();}return p.startsWith("/")?new FileInputStream(p):getAssets().open(p);}
 boolean on(Post p,String k){return prefs.getBoolean(k+p.id,false);} void actions(LinearLayout parent,Post p){LinearLayout r=row();for(String k:new String[]{"like","save"}){TextView t=button("",()->{});Runnable refresh=()->{boolean b=on(p,k);t.setText(k.equals("like")?(b?"♥ 已点赞":"♡ 点赞"):(b?"★ 已收藏":"☆ 收藏"));t.setTextColor(b?pink:muted);};t.setTag(refresh);refresh.run();t.setBackgroundColor(Color.TRANSPARENT);t.setOnClickListener(v->{boolean before=on(p,k);boolean after=!before;prefs.edit().putBoolean(k+p.id,after).apply();refresh.run();spring(t);if(p.id.startsWith("cloud_"))syncReaction(p,k,after,before);});r.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));}add(parent,r,-1,-2);}
 void card(Post p){LinearLayout c=col();c.setPadding(dp(10),dp(5),dp(10),0);c.setBackground(bg(Color.WHITE,22));TextView user=txt("🐾  "+p.name+"    ·    "+(p.id.startsWith("local")?"本机发布":"猫猫日常"),13,muted);add(c,user,-1,40);c.addView(photo(p,245));add(c,txt(p.text,15,ink),-1,-2);actions(c,p);add(body,c,-1,-2);gap(body,14);}
 void feed(){title("猫猫广场", "⌕",()->show("发现"));add(body,txt("每一只小猫，都值得被喜欢  🐾",12,muted),-1,-2);
 tabs(new String[]{"全部","最新"},s->{filter=s;updateFeed();});feedCards=new AnimatedCards(false);add(body,feedCards,-1,-2);updateFeed();}
 void updateFeed(){ArrayList<Post> list=new ArrayList<>(posts);feedCards.update(list);}
 void grid(ArrayList<Post> data){if(data.isEmpty()){gap(body,50);add(body,txt("ฅ^•ﻌ•^ฅ\n这里还没有小猫哦",20,muted),-1,110);return;}for(int i=0;i<data.size();i+=2){LinearLayout r=row();for(int j=i;j<Math.min(i+2,data.size());j++){Post p=data.get(j);LinearLayout c=col();c.setBackground(bg(Color.WHITE,16));c.addView(photo(p,160));TextView t=txt(p.text,12,ink);t.setMaxLines(1);add(c,t,-1,32);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(4),dp(3),dp(5));r.addView(c,lp);}if(i+1==data.size())r.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));add(body,r,-1,-2);}}
 void discover(){title("发现",null,null);EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索猫咪、昵称或故事");search.setPadding(dp(16),0,dp(16),0);search.setBackground(bg(pale,24));add(body,search,-1,48);gap(body,16);TextView banner=txt("本周精选  ฅ^•ﻌ•^ฅ\n收集日常里的小小可爱",22,ink);banner.setPadding(dp(20),dp(16),dp(16),dp(16));banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xffffdfc2,0xffffe9e9}));add(body,banner,-1,105);gap(body,12);tabs(new String[]{"全部","橘猫","布偶","狸花"},s->{filter=s;renderSearch(search.getText().toString());});add(body,txt("猫猫日常  ·  今日份治愈",16,ink),-1,48);query="";searchCards=new AnimatedCards(true);add(body,searchCards,-1,-2);renderSearch("");search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderSearch(s.toString());}public void afterTextChanged(android.text.Editable e){}});}
 void renderSearch(String q){query=q;if(searchCards==null)return;ArrayList<Post> a=new ArrayList<>();for(Post p:posts)if((filter.equals("全部")||filter.equals(p.breed))&&(p.text+p.name+p.breed).contains(q))a.add(p);searchCards.update(a);}
 void collection(){title("我的收藏",null,null);add(body,txt("把心动的小猫，悄悄收藏起来",13,muted),-1,40);collectionCards=new AnimatedCards(true);add(body,collectionCards,-1,-2);updateCollection();}
 void updateCollection(){ArrayList<Post> a=new ArrayList<>();for(Post p:posts)if(on(p,"save"))a.add(p);collectionCards.update(a);}
 void profile(){
  title(prefs.getString("name","猫猫爱好者")+"的猫猫窝",null,null);
  TextView nestTitle=(TextView)((LinearLayout)body.getChildAt(0)).getChildAt(1);
  nestTitle.setSingleLine(true);nestTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
  nestTitle.setTag((Runnable)()->nestTitle.setText(prefs.getString("name","猫猫爱好者")+"的猫猫窝"));
  TextView cover=txt("☁       ฅ^•ﻌ•^ฅ       ☁\n有猫的日子，都是好日子",22,ink);
  cover.setGravity(Gravity.CENTER);cover.setBackground(bg(0xffffe2d8,25));
  String coverPath=prefs.getString("profile_cover","");
  if(!coverPath.isEmpty()&&new File(coverPath).isFile()){
   ImageView coverImage=photo(new Post("profile_cover","","",coverPath,"",true),125);
   coverImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
   coverImage.setBackground(bg(0xffffe2d8,25));coverImage.setClipToOutline(true);
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(coverPath,bounds);
   coverImage.setContentDescription("猫猫窝封面，点击更换图片");coverImage.setOnClickListener(v->pickProfileCover());add(body,coverImage,-1,125);
   if(bounds.outWidth>0&&bounds.outHeight>0){final float ratio=(float)bounds.outHeight/bounds.outWidth;coverImage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{int width=r-l;if(width<=0)return;int height=Math.max(dp(100),Math.min((int)(width*ratio),getResources().getDisplayMetrics().heightPixels*2/3));if(v.getLayoutParams().height!=height){v.getLayoutParams().height=height;v.requestLayout();}});}
  }else{cover.setContentDescription("猫猫窝封面，点击添加图片");cover.setOnClickListener(v->pickProfileCover());add(body,cover,-1,125);}
  gap(body,8);LinearLayout identity=row();TextView nickname=txt(prefs.getString("name","猫猫爱好者"),22,ink);nickname.setTag((Runnable)()->nickname.setText(prefs.getString("name","猫猫爱好者")));identity.addView(nickname,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView editCover=button("＋",()->pickProfileCover());editCover.setTextSize(26);editCover.setContentDescription("上传或更换猫猫窝封面图片");add(identity,editCover,48,48);add(body,identity,-1,-2);
  TextView signature=txt(profileSignature(),13,muted);signature.setTag((Runnable)()->signature.setText(profileSignature()));signature.setContentDescription("个性签名，点击编辑");signature.setOnClickListener(v->editProfileSignature());add(body,signature,-1,-2);
  int count=0,likes=0,saves=0;for(Post p:posts){if(p.mine)count++;if(on(p,"like"))likes++;if(on(p,"save"))saves++;}
  LinearLayout stats=row();String[] names={"我的发布","点赞","收藏"};int[] nums={count,likes,saves};
  for(int i=0;i<names.length;i++){final String tab=names[i];TextView t=txt(nums[i]+"\n"+(i==0?"发布":tab),15,ink);t.setGravity(Gravity.CENTER);t.setTag((Runnable)()->{int total=0;for(Post p:posts)if(tab.equals("我的发布")?p.mine:tab.equals("点赞")?on(p,"like"):on(p,"save"))total++;t.setText(total+"\n"+(tab.equals("我的发布")?"发布":tab));});t.setOnClickListener(v->selectProfileTab(tab));stats.addView(t,new LinearLayout.LayoutParams(0,dp(58),1));}
  add(body,stats,-1,-2);gap(body,8);
  profileTabs=row();profileTabs.setBackground(bg(pale,22));
  for(String name:new String[]{"我的发布","点赞","收藏","关于我"}){
   LinearLayout item=col();item.setGravity(Gravity.CENTER);TextView label=txt(name,14,ink);label.setPadding(0,0,0,0);label.setGravity(Gravity.CENTER);add(item,label,-1,37);
   View underline=new View(this);add(item,underline,28,2);gap(item,3);
   item.setContentDescription(name);item.setOnClickListener(v->selectProfileTab(name));profileTabs.addView(item,new LinearLayout.LayoutParams(0,dp(42),1));
  }
  add(body,profileTabs,-1,-2);gap(body,12);profileContent=col();add(body,profileContent,-1,-2);profileCards=new AnimatedCards(true);add(profileContent,profileCards,-1,-2);aboutProfile=txt("🐾  "+prefs.getString("name","猫猫爱好者")+"\n\n"+profileSignature()+"\n\n编辑个性签名  ›",15,ink);aboutProfile.setPadding(dp(18),dp(18),dp(18),dp(18));aboutProfile.setBackground(bg(Color.WHITE,20));aboutProfile.setOnClickListener(v->editProfileSignature());add(profileContent,aboutProfile,-1,-2);selectProfileTab(profileTab);
 }
 String profileSignature(){return prefs.getString("profile_signature","一只普通的猫奴，记录与小猫相伴的日常。");}
 void editProfileSignature(){
  EditText input=new EditText(this);input.setText(profileSignature());input.setHint("写下你的个性签名");input.setMinLines(3);input.setMaxLines(5);input.setGravity(Gravity.TOP);input.setPadding(dp(20),dp(16),dp(20),dp(16));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
  new AlertDialog.Builder(this).setTitle("编辑个性签名").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{prefs.edit().putString("profile_signature",input.getText().toString().trim()).apply();refreshBindings(root);selectProfileTab(profileTab);note("个性签名已保存");}).show();
 }
 void pickProfileCover(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,41);}
 void saveProfileCover(Uri uri){
  note("正在保存封面…");images.execute(()->{
   Bitmap bitmap=null;File target=null;
   try{
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
    try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
    if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Invalid image");
    opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1600)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;
    try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
    if(bitmap==null)throw new IOException("Invalid image");
    target=new File(getFilesDir(),"cover_"+System.nanoTime()+".jpg");
    try(FileOutputStream out=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Unable to save image");}
    final String saved=target.getAbsolutePath();ui.post(()->{String previous=prefs.getString("profile_cover","");prefs.edit().putString("profile_cover",saved).apply();if(!previous.isEmpty()&&!previous.equals(saved))new File(previous).delete();if(route.equals("我的")){body.removeAllViews();profile();}note("封面已保存");});
   }catch(Exception e){if(target!=null)target.delete();ui.post(()->note("图片未能读取，请换一张试试"));}finally{if(bitmap!=null)bitmap.recycle();}
  });
 }
 void selectProfileTab(String tab){
  profileTab=tab;if(profileTabs==null||profileContent==null)return;
  for(int i=0;i<profileTabs.getChildCount();i++){LinearLayout item=(LinearLayout)profileTabs.getChildAt(i);TextView label=(TextView)item.getChildAt(0);boolean selected=label.getText().toString().equals(tab);label.setTextColor(selected?pink:muted);label.setTypeface(null,selected?1:0);item.getChildAt(1).setBackground(selected?bg(pink,2):bg(Color.TRANSPARENT,2));item.setSelected(selected);}
  boolean about=tab.equals("关于我");aboutProfile.setVisibility(about?View.VISIBLE:View.GONE);profileCards.setVisibility(about?View.GONE:View.VISIBLE);
  if(about){aboutProfile.setText("🐾  "+prefs.getString("name","猫猫爱好者")+"\n\n"+profileSignature()+"\n\n编辑个性签名  ›");aboutProfile.setAlpha(0);aboutProfile.animate().alpha(1).setDuration(220).start();}else{ArrayList<Post> selected=new ArrayList<>();for(Post p:posts)if(tab.equals("我的发布")?p.mine:tab.equals("点赞")?on(p,"like"):on(p,"save"))selected.add(p);profileCards.update(selected);}
 }
 void detail(Post p){if(!navigating)push("详情",p);}
 void detailContent(Post p){title("猫猫日常",null,null);body.addView(photo(p,360));add(body,txt("🐾 "+p.name,19,ink),-1,55);add(body,txt(p.text,17,ink),-1,-2);actions(body,p);if(p.mine)add(body,button("删除这条发布",()->new AlertDialog.Builder(this).setMessage("删除这条发布？").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{if(p.id.startsWith("cloud_"))deleteCloud(p);else{posts.remove(p);savePosts();back();}}).show()),-1,48);}
 void publish(){title("发布猫猫",null,null);add(body,txt("让可爱发生  ฅ^•ﻌ•^ฅ",16,muted),-1,50);TextView upload=button(chosen.isEmpty()?"☁\n选择小猫照片\n最多 9 张":"已选择 "+chosen.size()+" 张 · 点击重新选择",()->{draft=story.getText().toString();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,9);});add(body,upload,-1,180);if(!chosen.isEmpty()){gap(body,10);LinearLayout r=row();for(String f:chosen){Post p=new Post("","","",f,"",true);ImageView v=photo(p,80);r.addView(v,new LinearLayout.LayoutParams(0,dp(80),1));}add(body,r,-1,-2);}gap(body,16);story=new EditText(this);story.setHint("写下关于这只小猫的故事…（可选）");story.setText(draft);story.setTextSize(15);story.setGravity(Gravity.TOP);story.setPadding(dp(16),dp(16),dp(16),dp(16));story.setBackground(bg(pale,20));story.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});add(body,story,-1,150);add(body,txt("最多 200 字，每张图片不超过 2MB · 发布后所有用户可见",12,muted),-1,42);gap(body,25);TextView submit=button("🐾  发布",()->{if(chosen.isEmpty()){note("先选一张小猫照片吧");return;}if(publishing){note("正在发布，请稍等");return;}draft=story.getText().toString().trim();publishCloud(new ArrayList<>(chosen),draft);});submit.setTextColor(Color.WHITE);submit.setBackground(bg(pink,28));add(body,submit,-1,54);}
 protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==41){if(res==RESULT_OK&&data!=null&&data.getData()!=null)saveProfileCover(data.getData());return;}if(req!=9||res!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<Math.min(9,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());chosen.clear();for(Uri uri:uris){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;InputStream in=getContentResolver().openInputStream(uri);BitmapFactory.decodeStream(in,null,o);in.close();o.inSampleSize=Math.max(1,Math.max(o.outWidth,o.outHeight)/1400);o.inJustDecodeBounds=false;in=getContentResolver().openInputStream(uri);Bitmap bitmap=BitmapFactory.decodeStream(in,null,o);in.close();if(bitmap==null)continue;File f=new File(getFilesDir(),"photo"+System.nanoTime()+".jpg");FileOutputStream out=new FileOutputStream(f);bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);out.close();Bitmap thumb=Bitmap.createScaledBitmap(bitmap,24,24,true);try(FileOutputStream small=new FileOutputStream(f.getAbsolutePath()+".thumb")){thumb.compress(Bitmap.CompressFormat.JPEG,75,small);}thumb.recycle();bitmap.recycle();chosen.add(f.getAbsolutePath());}catch(Exception e){note("有一张图片未能读取，请换一张试试");}}if(route.equals("发布")){body.removeAllViews();publish();}}
 void savePosts(){JSONArray a=new JSONArray();for(Post p:posts)if(p.mine&&p.id.startsWith("local"))try{JSONObject o=new JSONObject();o.put("id",p.id);o.put("text",p.text);o.put("photo",p.photo);a.put(o);}catch(Exception e){}prefs.edit().putString("posts",a.toString()).apply();}
 void settings(){title("设置",null,null);add(body,button("退出登录",()->{net.execute(()->{try{Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");callApi("POST","/api/auth/sign-out","{}".getBytes(StandardCharsets.UTF_8),h,true);}catch(Exception ignored){}prefs.edit().remove("account_token").remove("cloud_cache").commit();ui.post(()->{for(int i=posts.size()-1;i>=0;i--)if(posts.get(i).id.startsWith("cloud_"))posts.remove(i);showAuth();});});}),-1,50);gap(body,20);add(body,button("🐾   编辑昵称    ›",()->{EditText e=new EditText(this);e.setSingleLine();e.setText(prefs.getString("name","猫猫爱好者"));new AlertDialog.Builder(this).setTitle("你希望怎么称呼？").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){prefs.edit().putString("name",n).apply();note("昵称已保存");}}).show();}),-1,58);}
 void info(String p){title(p,null,null);TextView t=txt(p.equals("帮助")?"如何使用猫猫社\n\n在广场浏览小猫，点爱心点赞，点星星收藏。\n点底部加号选择照片，发布到自己的猫猫窝。\n列表滑到顶部后继续下拉，松手获取最新内容。\n从左边缘向右滑可以返回上一页。":"Cat-ch 0.5\n\n分享每一只可爱的小猫。\n这是联网测试版，支持邮箱密码或邮箱验证码登录，不提供评论。照片和帖子发布后对其他使用者可见。\n示例照片取自你提供的设计图。",16,ink);t.setPadding(dp(16),dp(18),dp(16),dp(18));t.setBackground(bg(Color.WHITE,20));add(body,t,-1,-2);}
 public void onBackPressed(){if(host==null){super.onBackPressed();return;}back();}
 static class ApiError extends IOException{ApiError(String m){super(m);}}
 byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>5_000_000)throw new IOException("响应过大");out.write(b,0,n);}return out.toByteArray();}
 JSONObject callApi(String method,String path,byte[] data,Map<String,String> extra,boolean withAuth)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Origin",API);
  if(withAuth)c.setRequestProperty("Authorization","Bearer "+getCloudToken());
  if(extra!=null)for(Map.Entry<String,String> e:extra.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
  if(data!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
  int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();byte[] payload=stream==null?new byte[0]:readAll(stream);if(stream!=null)stream.close();c.disconnect();
  JSONObject result;try{result=new JSONObject(new String(payload,StandardCharsets.UTF_8));}catch(Exception e){throw new ApiError("服务器暂时无法连接，请稍后重试");}
  if(status==401&&withAuth){prefs.edit().remove("account_token").apply();ui.post(()->showAuth());}if(status<200||status>=300)throw new ApiError(result.optString("message",result.optString("error","服务器暂时无法连接")));return result;
 }
 synchronized String getCloudToken()throws Exception{String token=prefs.getString("account_token","");if(token.isEmpty())throw new ApiError("请先登录");return token;}
 AuthPanel authPanel;
 void startSplash(){LinearLayout splash=col();splash.setGravity(Gravity.CENTER);splash.setBackgroundColor(cream);CatIcon cat=new CatIcon(true);splash.addView(cat,new LinearLayout.LayoutParams(dp(125),dp(100)));TextView brand=txt("Cat-ch",34,ink);brand.setGravity(Gravity.CENTER);add(splash,brand,-1,70);TextView subtitle=txt("接住每一份小可爱",15,muted);subtitle.setGravity(Gravity.CENTER);add(splash,subtitle,-1,40);setContentView(splash);cat.setScaleX(.6f);cat.setScaleY(.6f);cat.setAlpha(0);cat.animate().alpha(1).scaleX(1).scaleY(1).setDuration(700).setInterpolator(new OvershootInterpolator(1.4f)).start();ui.postDelayed(()->{if(isFinishing()||isDestroyed())return;if(prefs.getString("account_token","").isEmpty())showAuth();else enterApp();},1200);}
 JSONObject authRequest(String path,JSONObject data)throws Exception{Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");return callApi("POST","/api/auth/"+path,data.toString().getBytes(StandardCharsets.UTF_8),h,false);}
 interface AuthJob{JSONObject run()throws Exception;}
 void authTask(AuthJob job,boolean login){AuthPanel panel=authPanel;panel.setBusy(true);net.execute(()->{try{JSONObject result=job.run();if(login){String token=result.optString("token");if(token.isEmpty())throw new ApiError("请先验证邮箱，再登录");prefs.edit().putString("account_token",token).putString("name",result.optJSONObject("user")==null?"猫猫爱好者":result.getJSONObject("user").optString("name")).commit();}ui.post(()->{panel.setBusy(false);if(login)enterApp();else panel.showMessage("验证码已发送，请查看邮箱");});}catch(Exception e){ui.post(()->{panel.setBusy(false);panel.showMessage(e instanceof ApiError?e.getMessage():"连接失败，请稍后重试");});}});}
 void showAuth(){host=null;root=null;route="";history.clear();authPanel=new AuthPanel(this,new AuthPanel.Listener(){
 public void sendCode(String name,String email,String password,boolean registration){authTask(()->registration?authRequest("sign-up/email",new JSONObject().put("name",name).put("email",email).put("password",password)):authRequest("email-otp/send-verification-otp",new JSONObject().put("email",email).put("type","sign-in")),false);}
 public void register(String name,String email,String password,String code){authTask(()->{return authRequest("email-otp/verify-email",new JSONObject().put("email",email).put("otp",code));},true);}
 public void loginWithPassword(String email,String password){authTask(()->authRequest("sign-in/email",new JSONObject().put("email",email).put("password",password)),true);}
 public void loginWithCode(String email,String code){authTask(()->authRequest("sign-in/email-otp",new JSONObject().put("email",email).put("otp",code)),true);}
 });ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(cream);scroll.addView(authPanel);setContentView(scroll);}
 void enterApp(){host=null;root=null;route="";history.clear();restoreCloudCache();show("广场");net.execute(()->{String legacy=prefs.getString("cloud_token","");if(!legacy.isEmpty()){try{Map<String,String> h=new HashMap<>();h.put("X-Legacy-Token",legacy);callApi("POST","/migrate-anonymous",new byte[0],h,true);prefs.edit().remove("cloud_token").commit();}catch(Exception e){ui.post(()->note("旧发布暂未同步，稍后登录会重试"));}}loadCloud(false,null);});}
 void restoreCloudCache(){try{applyCloud(new JSONObject(prefs.getString("cloud_cache","{\"posts\":[]}")));}catch(Exception ignored){}}
 void applyCloud(JSONObject data)throws Exception{
  JSONArray a=data.getJSONArray("posts");ArrayList<Post> cloud=new ArrayList<>();android.content.SharedPreferences.Editor edit=prefs.edit();
  for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String id=o.getString("id"),path=o.getString("image");if(!id.startsWith("cloud_")||!path.equals("/images/"+id))continue;cloud.add(new Post(id,o.optString("name","猫猫爱好者"),o.optString("text",""),API+path,"小猫",o.optBoolean("mine")));edit.putBoolean("like"+id,o.optBoolean("liked"));edit.putBoolean("save"+id,o.optBoolean("saved"));}
  edit.apply();for(int i=posts.size()-1;i>=0;i--)if(posts.get(i).id.startsWith("cloud_"))posts.remove(i);posts.addAll(0,cloud);
 }
 void loadCloud(boolean notify,java.util.function.Consumer<Boolean> done){
  net.execute(()->{try{JSONObject response=callApi("GET","/posts",null,null,true);ui.post(()->{try{applyCloud(response);prefs.edit().putString("cloud_cache",response.toString()).apply();if(root!=null)refreshVisible();if(notify)note("已刷新");}catch(Exception e){if(notify)note("内容读取失败");}if(done!=null)done.accept(true);});}
   catch(Exception e){ui.post(()->{if(notify||posts.isEmpty())note("联网失败，请检查网络");if(done!=null)done.accept(false);});}});
 }
 void syncReaction(Post p,String kind,boolean after,boolean before){
  net.execute(()->{try{byte[] body=new JSONObject().put("active",after).toString().getBytes(StandardCharsets.UTF_8);Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");callApi("PUT","/posts/"+p.id+"/"+kind,body,h,true);}
   catch(Exception e){ui.post(()->{if(on(p,kind)==after)prefs.edit().putBoolean(kind+p.id,before).apply();if(root!=null)refreshVisible();note("操作没成功，请检查网络");});}});
 }
 void publishCloud(ArrayList<String> files,String text){
  publishing=true;note("正在上传猫猫照片…");String name=prefs.getString("name","猫猫爱好者");
  net.execute(()->{int sent=0;String error=null;for(String file:files){try{File f=new File(file);if(f.length()>2_000_000)throw new ApiError("图片超过 2MB，请重新选择");byte[] bytes;try(InputStream in=new FileInputStream(f)){bytes=readAll(in);}Map<String,String> h=new HashMap<>();h.put("Content-Type","image/jpeg");h.put("X-Meow-Text",URLEncoder.encode(text,"UTF-8").replace("+","%20"));h.put("X-Meow-Name",URLEncoder.encode(name,"UTF-8").replace("+","%20"));callApi("POST","/posts",bytes,h,true);sent++;}catch(Exception e){error=e instanceof ApiError?e.getMessage():"网络连接失败，请稍后重试";break;}}
   final int uploaded=sent;final String issue=error;ui.post(()->{publishing=false;for(int i=0;i<uploaded&&!chosen.isEmpty();i++)chosen.remove(0);if(issue==null){draft="";show("我的");note("发布成功！");}else{note((uploaded>0?"已发布 "+uploaded+" 张；":"")+issue);if(route.equals("发布")){body.removeAllViews();publish();}}loadCloud(false,null);});});
 }
 void deleteCloud(Post p){net.execute(()->{try{callApi("DELETE","/posts/"+p.id,null,null,true);ui.post(()->{posts.remove(p);back();note("已删除");loadCloud(false,null);});}catch(Exception e){ui.post(()->note("删除失败，请检查网络"));}});}
 class PlusIcon extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  PlusIcon(){super(MainActivity.this);setContentDescription("发布猫猫");}
  protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(pink);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,Math.min(cx,cy),paint);paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2.6f));paint.setStrokeCap(Paint.Cap.ROUND);float half=dp(9);c.drawLine(cx-half,cy,cx+half,cy,paint);c.drawLine(cx,cy-half,cx,cy+half,paint);}
 }
 class CatIcon extends View {Paint p=new Paint(3);boolean active;CatIcon(boolean a){super(MainActivity.this);active=a;}protected void onDraw(Canvas c){c.save();c.scale(getWidth()/40f,getHeight()/32f);p.setColor(active?pink:ink);p.setStyle(active?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(1.7f);Path q=new Path();q.moveTo(5,14);q.lineTo(6,3);q.quadTo(7,0,15,8);q.quadTo(20,6,25,8);q.quadTo(34,0,34,3);q.lineTo(36,15);q.cubicTo(40,33,1,33,5,14);c.drawPath(q,p);p.setStyle(Paint.Style.FILL);p.setColor(active?Color.WHITE:ink);c.drawCircle(14,17,1.3f,p);c.drawCircle(27,17,1.3f,p);p.setStyle(Paint.Style.STROKE);Path m=new Path();m.moveTo(17,21);m.quadTo(19,24,21,21);m.quadTo(23,24,25,21);c.drawPath(m,p);c.restore();}}
 void openDrawer(){
  if(navigating||drawerLayer!=null)return;drawerLayer=new FrameLayout(this);drawerLayer.setBackgroundColor(0x44000000);host.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));drawerLayer.setOnClickListener(v->closeDrawer(null));
  drawerPanel=col();drawerPanel.setPadding(dp(22),dp(30),dp(22),dp(22));drawerPanel.setBackgroundColor(cream);drawerPanel.setClickable(true);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.min(dp(290),host.getWidth()-dp(56)),-1,Gravity.LEFT);drawerLayer.addView(drawerPanel,lp);
  add(drawerPanel,txt("ฅ^•ﻌ•^ฅ",32,pink),-1,65);add(drawerPanel,txt("猫猫社",25,ink),-1,46);add(drawerPanel,txt("和可爱一起生活",14,muted),-1,38);gap(drawerPanel,30);
  for(String name:new String[]{"设置","帮助","关于"}){add(drawerPanel,button(name+"    ›",()->closeDrawer(()->show(name))),-1,52);gap(drawerPanel,12);}
  drawerPanel.setTranslationX(-lp.width);drawerPanel.animate().translationX(0).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
 }
 void closeDrawer(Runnable after){if(drawerLayer==null){if(after!=null)after.run();return;}FrameLayout layer=drawerLayer;LinearLayout panel=drawerPanel;panel.animate().translationX(-panel.getWidth()).setDuration(260).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(()->{host.removeView(layer);if(drawerLayer==layer)drawerLayer=null;if(after!=null)after.run();}).start();}
 float edgeX,edgeY;boolean edgePossible=false,edgeDragging=false;
 @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
  if(navigating)return true;
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){edgeX=e.getX();edgeY=e.getY();edgePossible=drawerLayer==null&&!history.isEmpty()&&edgeX<dp(26);edgeDragging=false;}
  if(edgePossible&&e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-edgeX,dy=e.getY()-edgeY;
   if(!edgeDragging&&Math.abs(dy)>dp(12)&&Math.abs(dy)>Math.abs(dx))edgePossible=false;
   if(edgePossible&&!edgeDragging&&dx>dp(10)&&dx>Math.abs(dy)*1.2f){edgeDragging=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();}
   if(edgeDragging){root.setTranslationX(Math.max(0,dx));return true;}
  }
  if((e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)&&edgeDragging){
   edgeDragging=false;edgePossible=false;
   if(e.getActionMasked()==MotionEvent.ACTION_UP&&root.getTranslationX()>root.getWidth()/2f)back();
   else{navigating=true;root.animate().translationX(0).setDuration(240).setInterpolator(new DecelerateInterpolator()).withEndAction(()->navigating=false).start();}return true;
  }
  return super.dispatchTouchEvent(e);
 }
 class AnimatedCards extends ViewGroup {
  int exiting=0,heldHeight=0;final boolean compact;final LinkedHashMap<String,View> cards=new LinkedHashMap<>();final ArrayList<String> order=new ArrayList<>();
  AnimatedCards(boolean compact){super(MainActivity.this);this.compact=compact;setClipChildren(false);setClipToPadding(false);setWillNotDraw(false);}
  View make(Post p){LinearLayout c=col();c.setPadding(dp(compact?0:10),dp(compact?0:5),dp(compact?0:10),0);c.setBackground(bg(Color.WHITE,compact?16:22));
   if(!compact)add(c,txt("🐾  "+p.name+"    ·    "+(p.id.startsWith("local")?"本机发布":"猫猫日常"),13,muted),-1,40);
   c.addView(photo(p,compact?160:245));TextView t=txt(p.text,compact?12:15,ink);if(compact)t.setMaxLines(1);add(c,t,-1,compact?32:-2);if(!compact)actions(c,p);return c;
  }
  void update(ArrayList<Post> data){HashSet<String> wanted=new HashSet<>();for(Post p:data)wanted.add(p.id);
   for(String id:new ArrayList<>(cards.keySet()))if(!wanted.contains(id)){View gone=cards.remove(id);exiting++;heldHeight=Math.max(heldHeight,getHeight());gone.animate().cancel();gone.animate().alpha(0).setDuration(160).withEndAction(()->{removeView(gone);exiting--;if(exiting==0){heldHeight=0;requestLayout();}invalidate();}).start();}
   order.clear();for(Post p:data){order.add(p.id);if(cards.containsKey(p.id))refreshBindings(cards.get(p.id));if(!cards.containsKey(p.id)){View child=make(p);child.setAlpha(0);child.setTag("new");cards.put(p.id,child);addView(child);}}
   requestLayout();invalidate();
  }
  protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws),g=dp(8),cw=compact?(width-g)/2:width;int y=0,rowH=0,index=0;
   for(String id:order){View v=cards.get(id);v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));rowH=Math.max(rowH,v.getMeasuredHeight());index++;if(!compact||index%2==0){y+=rowH+g;rowH=0;}}
   if(rowH>0)y+=rowH+g;setMeasuredDimension(width,Math.max(exiting>0?heldHeight:0,order.isEmpty()?dp(150):y));
  }
  protected void onLayout(boolean changed,int l,int t,int r,int b){int g=dp(8),cw=compact?(getWidth()-g)/2:getWidth(),y=0,rowH=0,index=0;
   for(String id:order){View v=cards.get(id);int x=compact?(index%2)*(cw+g):0;v.layout(0,0,cw,v.getMeasuredHeight());
    if("new".equals(v.getTag())){v.setTag(null);v.setX(x);v.setY(y);v.animate().alpha(1).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();}
    else if(Math.abs(v.getX()-x)>.1f||Math.abs(v.getY()-y)>.1f){v.animate().x(x).y(y).alpha(1).setDuration(320).setInterpolator(new AccelerateDecelerateInterpolator()).start();}
    rowH=Math.max(rowH,v.getMeasuredHeight());index++;if(!compact||index%2==0){y+=rowH+g;rowH=0;}
   }
  }
  protected void onDraw(Canvas c){super.onDraw(c);if(order.isEmpty()&&getChildCount()==0){Paint p=new Paint(3);p.setColor(muted);p.setTextSize(dp(15));p.setTextAlign(Paint.Align.CENTER);c.drawText("ฅ^•ﻌ•^ฅ",getWidth()/2f,dp(55),p);c.drawText("这里还没有小猫哦",getWidth()/2f,dp(90),p);}}
 }
 class PullScroll extends FrameLayout {
  final ScrollView scroll;final LinearLayout header;final TextView label;final ProgressBar spinner;
  boolean enabled,dragging,refreshing,hasBuzzed;float downX,downY,pull;ValueAnimator settle;final int threshold=dp(72);
  PullScroll(){super(MainActivity.this);setClipChildren(true);scroll=new ScrollView(MainActivity.this);scroll.setFillViewport(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);addView(scroll,new FrameLayout.LayoutParams(-1,-1));
   header=row();header.setGravity(Gravity.CENTER);spinner=new ProgressBar(MainActivity.this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(pink));add(header,spinner,22,22);label=txt("下拉刷新",13,muted);add(header,label,-2,40);addView(header,new FrameLayout.LayoutParams(-1,threshold,Gravity.TOP));header.setTranslationY(-threshold);header.setVisibility(INVISIBLE);
  }
  void setContent(View v){scroll.addView(v);}
  @Override public void requestDisallowInterceptTouchEvent(boolean disallow){
   // Keep observing downward drags at the top; ScrollView claims its touch slop
   // earlier than the refresh threshold, so forwarding that request would starve refresh.
   if(enabled&&!refreshing&&!scroll.canScrollVertically(-1))super.requestDisallowInterceptTouchEvent(false);
   else super.requestDisallowInterceptTouchEvent(disallow);
  }
  void position(float p){pull=p;scroll.setTranslationY(p);header.setTranslationY(p-threshold);header.setVisibility(p>0?VISIBLE:INVISIBLE);}
  public boolean onInterceptTouchEvent(MotionEvent e){if(!enabled||refreshing||!history.isEmpty())return false;
   if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();hasBuzzed=false;dragging=false;if(settle!=null)settle.cancel();}
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dy=e.getY()-downY,dx=e.getX()-downX;if(!scroll.canScrollVertically(-1)&&dy>dp(12)&&dy>Math.abs(dx)*1.4f){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}
   return false;
  }
  public boolean onTouchEvent(MotionEvent e){if(!dragging)return super.onTouchEvent(e);
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float amount=Math.min(dp(120),Math.max(0,(e.getY()-downY)*.5f));position(amount);boolean armed=amount>=threshold;label.setText(armed?"松手刷新":"下拉刷新");if(armed&&!hasBuzzed){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);hasBuzzed=true;android.util.Log.d("MeowMotion","refresh:armed");}return true;}
   if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);if(e.getActionMasked()==MotionEvent.ACTION_UP&&pull>=threshold){refreshing=true;android.util.Log.d("MeowMotion","refresh:start");label.setText("正在刷新…");animatePull(threshold,null);LinearLayout owner=root;
     loadCloud(true,ok->{if(root==owner)refreshVisible();label.setText(ok?"刷新完成":"刷新失败");ui.postDelayed(()->animatePull(0,()->refreshing=false),280);});
    }else animatePull(0,null);return true;}
   return true;
  }
  void animatePull(float target,Runnable done){if(settle!=null)settle.cancel();settle=ValueAnimator.ofFloat(pull,target);settle.setDuration(240);settle.setInterpolator(new DecelerateInterpolator());settle.addUpdateListener(a->position((float)a.getAnimatedValue()));settle.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){if(done!=null)done.run();}});settle.start();}
 }
 @Override protected void onDestroy(){super.onDestroy();net.shutdownNow();images.shutdownNow();ui.removeCallbacksAndMessages(null);}

}
