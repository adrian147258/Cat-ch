package com.meowclub.app;

import android.animation.*;import android.view.animation.*;import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.net.Uri;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.net.*;import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
 static final String API=BuildConfig.API_BASE_URL;
 static final String[] CAT_BREEDS={"橘猫","布偶","狸花","英短","美短","三花","其它"};
 final java.util.concurrent.ExecutorService net=java.util.concurrent.Executors.newSingleThreadExecutor();
 boolean publishing=false; int loadSerial=0;
 FrameLayout host, drawerLayer; LinearLayout drawerPanel; boolean navigating=false; String route="";
 final ArrayDeque<Screen> history=new ArrayDeque<>();
 final Handler ui=new Handler(Looper.getMainLooper());
 final java.util.concurrent.ExecutorService images=java.util.concurrent.Executors.newFixedThreadPool(2);
 final android.util.LruCache<String,Bitmap> imageCache=new android.util.LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 AnimatedCards feedCards,searchCards,collectionCards,profileCards; PullScroll activeScroll; TextView aboutProfile;
 String query=""; TextView popup;
 String draftBreed="";

 int cream=0xfff8f5f1,accent=0xff5b777f,ink=0xff373334,muted=0xff706b6a,pale=0xffe8edeb; LinearLayout root,body,nav; String page="广场",filter="全部"; android.content.SharedPreferences prefs; ArrayList<Post> posts=new ArrayList<>(); ArrayList<String> chosen=new ArrayList<>(); EditText story; String draft=""; int seq=0; boolean detailVisible=false; String profileTab="我的发布"; LinearLayout profileContent,profileTabs;
 class Post {String id,name,text,photo,breed,authorId=""; boolean mine; Post(String i,String n,String t,String p,String b,boolean m){id=i;name=n;text=t;photo=p;breed=b;mine=m;}}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(cream);getWindow().setNavigationBarColor(cream);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);prefs=getSharedPreferences("meow",0);try{JSONArray a=new JSONArray(prefs.getString("posts","[]"));for(int i=a.length()-1;i>=0;i--){JSONObject o=a.getJSONObject(i);posts.add(0,new Post(o.getString("id"),"我",o.getString("text"),o.getString("photo"),o.optString("breed",""),true));}}catch(Exception e){}startSplash();}
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} GradientDrawable bg(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(r>=12&&Color.alpha(c)>0)d.setStroke(dp(1),0xff514c4b);return d;} TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTypeface(AppFont.get(this));t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;} LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;} LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;} void add(LinearLayout p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));} TextView button(String s,Runnable run){TextView t=txt(s,15,ink);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(46));t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x335b777f),bg(0xfffffdfa,20),null));t.setElevation(dp(2));t.setOnClickListener(v->run.run());return t;} void gap(LinearLayout p,int h){add(p,new View(this),1,h);} void title(String s,String action,Runnable r){LinearLayout h=row();boolean sub=!history.isEmpty();TextView menu=button(sub?"‹":"☰",()->{if(sub)back();else openDrawer();});menu.setContentDescription(sub?"返回":"打开菜单");add(h,menu,44,44);TextView t=txt(s,23,ink);t.setTypeface(AppFont.get(this),Typeface.BOLD);h.addView(t,new LinearLayout.LayoutParams(0,dp(60),1));if(action!=null){if(action.equals("⌕")){View icon=new SearchIcon();icon.setContentDescription("搜索猫咪");icon.setBackground(bg(0xfffffdfa,16));icon.setElevation(dp(2));icon.setOnClickListener(v->r.run());add(h,icon,44,44);}else add(h,button(action,r),48,44);}add(body,h,-1,-2);}
 void spring(View v){v.animate().cancel();v.setScaleX(.82f);v.setScaleY(.82f);v.animate().scaleX(1).scaleY(1).setDuration(430).setInterpolator(new OvershootInterpolator(2.3f)).start();}
 void note(String s){if(host==null)return;if(popup!=null)host.removeView(popup);TextView t=txt(s,14,Color.WHITE);popup=t;t.setPadding(dp(20),dp(10),dp(20),dp(10));t.setBackground(bg(ink,24));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.bottomMargin=dp(80);host.addView(t,lp);spring(t);t.setAlpha(0);t.animate().alpha(1).setDuration(430).start();ui.postDelayed(()->t.animate().alpha(0).setDuration(160).withEndAction(()->{if(t.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.getParent()).removeView(t);if(popup==t)popup=null;}).start(),1800);}

 void base(boolean navigation){
  if(host==null){host=new FrameLayout(this);host.setBackgroundColor(cream);setContentView(host);
   host.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});}
  root=col();root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(Build.VERSION.SDK_INT>=29){ArrayList<Rect> exclusions=new ArrayList<>();if(!history.isEmpty()){int center=(b-t)/2;exclusions.add(new Rect(0,Math.max(0,center-dp(100)),dp(26),Math.min(b-t,center+dp(100))));}v.setSystemGestureExclusionRects(exclusions);}});root.setBackgroundColor(cream);host.addView(root,new FrameLayout.LayoutParams(-1,-1));
  activeScroll=new PullScroll();activeScroll.enabled=navigation;body=col();body.setPadding(dp(18),dp(8),dp(18),dp(22));activeScroll.setContent(body);
  root.addView(activeScroll,new LinearLayout.LayoutParams(-1,0,1));
  if(navigation){nav=row();nav.setPadding(dp(10),dp(2),dp(10),dp(2));nav.setBackground(bg(pale,24));
   int catIndex=0;
   for(String s:new String[]{"广场","发现","发布","抽卡","我的"}){LinearLayout cell=col();cell.setGravity(Gravity.CENTER);
    if(s.equals("发布")){add(cell,new PlusIcon(),42,42);}else{boolean selected=s.equals(page);if(selected)cell.setBackground(bg(0xffd4e5e6,16));add(cell,CatArt.image(this,catIndex++),42,38);TextView label=txt(s,11,selected?accent:ink);label.setGravity(Gravity.CENTER);label.setPadding(0,0,0,0);label.setIncludeFontPadding(false);add(cell,label,-1,18);}
    cell.setContentDescription(s);cell.setSelected(s.equals(page));cell.setOnClickListener(v->show(s));nav.addView(cell,new LinearLayout.LayoutParams(0,dp(64),1));}add(root,nav,-1,-2);
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
 void build(String p,Post post){base(!secondary(p)&&!p.equals("详情")&&!p.equals("猫友主页"));
  switch(p){case "广场":feed();break;case "发现":discover();break;case "抽卡":cardHome();break;case "收藏":collection();break;case "我的":profile();break;case "发布":publish();break;case "设置":settings();break;case "详情":detailContent(post);break;case "猫友主页":publicProfile(post);break;default:info(p);}
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
 void tabs(String[] labels,java.util.function.Consumer<String> select){LinearLayout line=row();for(String s:labels){TextView t=button(s,()->{for(int i=0;i<line.getChildCount();i++){TextView child=(TextView)line.getChildAt(i);boolean selected=child.getText().toString().equals(s);child.setTextColor(selected?Color.WHITE:ink);child.setBackground(bg(selected?accent:pale,22));}select.accept(s);});t.setTextColor(s.equals(filter)?Color.WHITE:ink);t.setBackground(bg(s.equals(filter)?accent:pale,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.setMargins(dp(3),dp(5),dp(3),dp(12));line.addView(t,lp);}add(body,line,-1,-2);}
 void breedFilters(EditText search){
  String[] labels=new String[CAT_BREEDS.length+1];
  labels[0]="全部";
  System.arraycopy(CAT_BREEDS,0,labels,1,CAT_BREEDS.length);
  ArrayList<TextView> buttons=new ArrayList<>();
  for(int start=0;start<labels.length;start+=4){
   LinearLayout line=row();
   for(int i=start;i<Math.min(start+4,labels.length);i++){
    String label=labels[i];
    TextView option=button(label,()->{
     filter=label;
     for(TextView item:buttons){
      boolean selected=item.getText().toString().equals(filter);
      item.setSelected(selected);
      item.setTextColor(selected?Color.WHITE:ink);
      item.setBackground(bg(selected?accent:pale,22));
     }
     renderSearch(search.getText().toString());
    });
    boolean selected=label.equals(filter);
    option.setSelected(selected);
    option.setTextColor(selected?Color.WHITE:ink);
    option.setBackground(bg(selected?accent:pale,22));
    buttons.add(option);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);
    lp.setMargins(dp(3),dp(4),dp(3),dp(4));
    line.addView(option,lp);
   }
   add(body,line,-1,-2);
  }
 }
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
 boolean on(Post p,String k){return prefs.getBoolean(k+p.id,false);} void actions(LinearLayout parent,Post p){LinearLayout r=row();for(String k:new String[]{"like","save"}){TextView t=button("",()->{});Runnable refresh=()->{boolean b=on(p,k);t.setText(k.equals("like")?(b?"♥ 已点赞":"♡ 点赞"):(b?"★ 已收藏":"☆ 收藏"));t.setTextColor(b?accent:muted);};t.setTag(refresh);refresh.run();t.setBackgroundColor(Color.TRANSPARENT);t.setOnClickListener(v->{boolean before=on(p,k);boolean after=!before;prefs.edit().putBoolean(k+p.id,after).apply();refresh.run();spring(t);if(p.id.startsWith("cloud_"))syncReaction(p,k,after,before);});r.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));}add(parent,r,-1,-2);}
 void card(Post p){LinearLayout c=col();c.setPadding(dp(10),dp(5),dp(10),0);c.setBackground(bg(0xfffffdfa,22));TextView user=txt("🐾  "+p.name+"    ·    "+(p.id.startsWith("local")?"本机发布":"猫猫日常"),13,muted);add(c,user,-1,40);c.addView(photo(p,245));add(c,txt(p.text,15,ink),-1,-2);actions(c,p);add(body,c,-1,-2);gap(body,14);}
 void feed(){title("猫猫广场", "⌕",()->show("发现"));add(body,txt("每一只小猫，都值得被喜欢  🐾",12,muted),-1,-2);
 tabs(new String[]{"全部","最新"},s->{filter=s;updateFeed();});feedCards=new AnimatedCards(false);add(body,feedCards,-1,-2);updateFeed();}
 void updateFeed(){ArrayList<Post> list=new ArrayList<>(posts);feedCards.update(list);}
 void grid(ArrayList<Post> data){if(data.isEmpty()){gap(body,50);add(body,txt("这里还没有小猫哦",20,muted),-1,110);return;}for(int i=0;i<data.size();i+=2){LinearLayout r=row();for(int j=i;j<Math.min(i+2,data.size());j++){Post p=data.get(j);LinearLayout c=col();c.setBackground(bg(0xfffffdfa,16));c.addView(photo(p,160));TextView t=txt(p.text,12,ink);t.setMaxLines(1);add(c,t,-1,32);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(4),dp(3),dp(5));r.addView(c,lp);}if(i+1==data.size())r.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));add(body,r,-1,-2);}}
 void discover(){title("发现",null,null);EditText search=new EditText(this);search.setTypeface(AppFont.get(this));search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索猫咪、昵称或故事");search.setPadding(dp(16),0,dp(16),0);search.setBackground(bg(pale,24));add(body,search,-1,48);gap(body,16);TextView banner=txt("本周精选\n收集日常里的小小可爱",22,ink);banner.setPadding(dp(20),dp(16),dp(16),dp(16));banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xffeee2cf,0xfff7f1e7}));add(body,banner,-1,105);gap(body,12);breedFilters(search);add(body,txt("猫猫日常  ·  今日份治愈",16,ink),-1,48);query="";searchCards=new AnimatedCards(true);add(body,searchCards,-1,-2);renderSearch("");search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderSearch(s.toString());}public void afterTextChanged(android.text.Editable e){}});}
 void renderSearch(String q){query=q;if(searchCards==null)return;ArrayList<Post> a=new ArrayList<>();for(Post p:posts)if((filter.equals("全部")||filter.equals(p.breed))&&(p.text+p.name+p.breed).contains(q))a.add(p);searchCards.update(a);}

 Dialog cardDialog; LinearLayout cardList; boolean cardBusy=false;
 void cardHome(){
  title("猫猫卡册",null,null);
  TextView draw=button("✦  抽取今日猫猫  ✦",()->startCardDraw());draw.setTextColor(Color.WHITE);draw.setTextSize(19);draw.setBackground(bg(accent,27));
  add(body,draw,-1,64);gap(body,16);add(body,txt("我的猫猫卡",18,ink),-1,44);
  LinearLayout list=col();cardList=list;add(body,list,-1,-2);
  Runnable pulse=new Runnable(){public void run(){if(!draw.isAttachedToWindow())return;draw.animate().scaleX(1.035f).scaleY(1.035f).setDuration(800).withEndAction(()->draw.animate().scaleX(1).scaleY(1).setDuration(800).withEndAction(this).start()).start();}};draw.post(pulse);
  fetchCards(list);
 }
 void fetchCards(LinearLayout target){fetchCards(target,null);}
 void fetchCards(LinearLayout target,java.util.function.Consumer<Boolean> done){
  String account=prefs.getString("account_user_id","");String token=prefs.getString("account_token","");String key="cards_cache_"+account;boolean cached=false;
  if(!account.isEmpty())try{String saved=prefs.getString(key,"");if(!saved.isEmpty()){renderCardCollection(target,new JSONArray(saved));cached=true;}}catch(Exception ignored){}
  if(!cached){target.removeAllViews();add(target,txt("正在加载卡册…",14,muted),-1,60);}final boolean hasCache=cached;
  net.execute(()->{try{JSONObject data=callApi("GET","/cards",null,null,true);JSONArray all=data.getJSONArray("cards");if(!token.equals(prefs.getString("account_token","")))return;if(!account.isEmpty())prefs.edit().putString(key,all.toString()).apply();ui.post(()->{if(target!=cardList||!route.equals("抽卡")||!token.equals(prefs.getString("account_token","")))return;renderCardCollection(target,all);if(done!=null)done.accept(true);});}catch(Exception ex){ui.post(()->{if(target!=cardList||!route.equals("抽卡"))return;if(!hasCache){target.removeAllViews();add(target,button("卡册加载失败，点此重试",()->fetchCards(target)),-1,60);}if(done!=null)done.accept(false);});}});
 }
 void renderCardCollection(LinearLayout target,JSONArray cards){
  target.removeAllViews();if(cards.length()==0){TextView empty=txt("还没有猫猫卡，来拆开第一包吧",15,muted);empty.setGravity(Gravity.CENTER);add(target,empty,-1,120);return;}
  for(int i=0;i<cards.length();i+=2){LinearLayout row=row();row.setGravity(Gravity.TOP);for(int j=i;j<Math.min(i+2,cards.length());j++){
   JSONObject c=cards.optJSONObject(j);if(c==null)continue;String tier=c.optString("tier"),date=c.optString("day");int index=c.optInt("card_index",-1);if(!tier.matches("green|blue|purple|gold")||index<0)continue;
   int color=tier.equals("gold")?0xffddb453:tier.equals("purple")?0xffbd8ee0:tier.equals("blue")?0xff8bbbe8:0xff91d2a3;
   String label=tier.equals("gold")?"金卡":tier.equals("purple")?"紫卡":tier.equals("blue")?"蓝卡":"绿卡";
   LinearLayout tile=col();tile.setPadding(dp(6),dp(6),dp(6),dp(6));tile.setBackground(bg(color,18));ImageView pic=photo(new Post("","","","cards/"+tier+"_"+index+".webp","",false),175);pic.setScaleType(ImageView.ScaleType.FIT_CENTER);add(tile,pic,-1,175);
   TextView info=txt(label+" · "+date,12,ink);info.setGravity(Gravity.CENTER);add(tile,info,-1,36);Runnable preview=()->{try{JSONObject selected=new JSONObject();selected.put("tier",tier);selected.put("index",index);showCardDraw(selected,true);}catch(Exception ignored){note("卡片预览失败");}};tile.setOnClickListener(v->preview.run());pic.setOnClickListener(v->preview.run());tile.setContentDescription(label+"，"+date+"，点击预览");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(4),dp(4),dp(4),dp(8));row.addView(tile,lp);
  }if(i+1>=cards.length())row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));add(target,row,-1,-2);}
 }
 void startCardDraw(){
  if(cardBusy)return;cardBusy=true;
  net.execute(()->{try{JSONObject result=callApi("POST","/cards/draw",new byte[0],null,true);ui.post(()->{cardBusy=false;if(!route.equals("抽卡"))return;showCardDraw(result);});}catch(Exception e){String message=e instanceof ApiError?e.getMessage():"连接失败，请稍后再试";ui.post(()->{cardBusy=false;note(message);});}});
 }
 void showCardDraw(JSONObject result){
  showCardDraw(result,false);
 }
 void showCardDraw(JSONObject result,boolean preview){
  try{
   String html;try(InputStream in=getAssets().open("card_draw.html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);html=new String(out.toByteArray(),StandardCharsets.UTF_8);}
   JSONObject card=new JSONObject();card.put("tier",result.getString("tier"));card.put("index",result.getInt("index"));card.put("preview",preview);html=html.replace("__HOST_DRAW_JSON__",card.toString());
   Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);FrameLayout frame=new FrameLayout(this);android.webkit.WebView web=new android.webkit.WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.setBackgroundColor(Color.WHITE);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
   TextView close=txt("✕",22,ink);close.setGravity(Gravity.CENTER);close.setBackground(bg(0xddffffff,22));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);cp.setMargins(0,dp(18),dp(16),0);frame.addView(close,cp);close.setOnClickListener(v->dialog.dismiss());dialog.setContentView(frame);
   android.hardware.SensorManager manager=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);android.hardware.Sensor sensor=manager==null?null:manager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);final float[] base={Float.NaN,Float.NaN};
   android.hardware.SensorEventListener listener=new android.hardware.SensorEventListener(){public void onAccuracyChanged(android.hardware.Sensor s,int a){} public void onSensorChanged(android.hardware.SensorEvent e){if(e.values.length<3)return;float[] matrix=new float[9],angles=new float[3];android.hardware.SensorManager.getRotationMatrixFromVector(matrix,e.values);android.hardware.SensorManager.getOrientation(matrix,angles);if(Float.isNaN(base[0])){base[0]=angles[1];base[1]=angles[2];}float x=Math.max(-1,Math.min(1,(angles[2]-base[1])*2.3f)),y=Math.max(-1,Math.min(1,(angles[1]-base[0])*2.3f));web.evaluateJavascript("window.setCardMotion && window.setCardMotion("+x+","+y+")",null);}};
   cardDialog=dialog;dialog.setOnDismissListener(d->{cardDialog=null;if(manager!=null)manager.unregisterListener(listener);web.stopLoading();web.destroy();if(!preview&&!isFinishing()&&!isDestroyed()&&route.equals("抽卡")&&cardList!=null)fetchCards(cardList);});dialog.show();if(manager!=null&&sensor!=null)manager.registerListener(listener,sensor,android.hardware.SensorManager.SENSOR_DELAY_UI);
   web.loadDataWithBaseURL("https://appassets.androidplatform.net/",html,"text/html","UTF-8",null);
  }catch(Exception e){note("卡包打开失败，抽中的卡已放进卡册");if(route.equals("抽卡")){body.removeAllViews();cardHome();}}
 }
 void collection(){title("我的收藏",null,null);add(body,txt("把心动的小猫，悄悄收藏起来",13,muted),-1,40);collectionCards=new AnimatedCards(true);add(body,collectionCards,-1,-2);updateCollection();}
 void updateCollection(){ArrayList<Post> a=new ArrayList<>();for(Post p:posts)if(on(p,"save"))a.add(p);collectionCards.update(a);}
 void profile(){
  title(prefs.getString("name","猫猫爱好者")+"的猫猫窝",null,null);
  TextView nestTitle=(TextView)((LinearLayout)body.getChildAt(0)).getChildAt(1);
  nestTitle.setSingleLine(true);nestTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
  nestTitle.setTag((Runnable)()->nestTitle.setText(prefs.getString("name","猫猫爱好者")+"的猫猫窝"));
  TextView cover=txt("有猫的日子，都是好日子\n点击添加封面",18,ink);
  cover.setGravity(Gravity.CENTER);cover.setBackground(bg(pale,25));
  String coverPath=prefs.getString("profile_cover","");
  boolean localCover=!coverPath.isEmpty()&&new File(coverPath).isFile();
  String publicCover=prefs.getString("profile_public_cover","");
  String accountId=prefs.getString("account_user_id","");
  if(!localCover&&isProfileCoverPath(accountId,publicCover))coverPath=API+publicCover;
  if(localCover||coverPath.startsWith("https://")){
   ImageView coverImage=photo(new Post("profile_cover","","",coverPath,"",true),125);
   coverImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
   coverImage.setBackground(bg(pale,25));coverImage.setClipToOutline(true);
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;if(localCover)BitmapFactory.decodeFile(coverPath,bounds);
   coverImage.setContentDescription("猫猫窝封面，点击更换图片");coverImage.setOnClickListener(v->pickProfileCover());add(body,coverImage,-1,125);
   if(bounds.outWidth>0&&bounds.outHeight>0){final float ratio=(float)bounds.outHeight/bounds.outWidth;coverImage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{int width=r-l;if(width<=0)return;int height=Math.max(dp(100),Math.min((int)(width*ratio),getResources().getDisplayMetrics().heightPixels*2/3));if(v.getLayoutParams().height!=height){v.getLayoutParams().height=height;v.requestLayout();}});}
  }else{cover.setContentDescription("猫猫窝封面，点击添加图片");cover.setOnClickListener(v->pickProfileCover());add(body,cover,-1,125);}
  gap(body,8);LinearLayout identity=row();TextView nickname=txt(prefs.getString("name","猫猫爱好者"),22,ink);nickname.setTag((Runnable)()->nickname.setText(prefs.getString("name","猫猫爱好者")));identity.addView(nickname,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView editCover=button("＋",()->pickProfileCover());editCover.setTextSize(26);editCover.setContentDescription("上传或更换猫猫窝封面图片");add(identity,editCover,48,48);add(body,identity,-1,-2);
  TextView signature=txt(profileSignatureDisplay()+"\n✎  编辑公开签名",14,ink);signature.setPadding(dp(14),dp(10),dp(14),dp(10));signature.setBackground(bg(0xfffffdfa,16));signature.setElevation(dp(2));signature.setTag((Runnable)()->signature.setText(profileSignatureDisplay()+"\n✎  编辑公开签名"));signature.setContentDescription("公开个性签名，点击编辑");signature.setOnClickListener(v->editProfileSignature());add(body,signature,-1,-2);
  String shareHint="签名、封面和作品对其他猫友可见";
  if((localCover&&!prefs.getBoolean("profile_cover_public",false))||(prefs.contains("profile_signature")&&!prefs.getBoolean("profile_signature_public",false)))shareHint="本机签名或封面尚未公开，重新保存后同步";
  add(body,txt(shareHint,12,muted),-1,-2);
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
  add(body,profileTabs,-1,-2);gap(body,12);profileContent=col();add(body,profileContent,-1,-2);profileCards=new AnimatedCards(true);add(profileContent,profileCards,-1,-2);aboutProfile=txt("🐾  "+prefs.getString("name","猫猫爱好者")+"\n\n"+profileSignatureDisplay()+"\n\n编辑个性签名  ›",15,ink);aboutProfile.setPadding(dp(18),dp(18),dp(18),dp(18));aboutProfile.setBackground(bg(0xfffffdfa,20));aboutProfile.setOnClickListener(v->editProfileSignature());add(profileContent,aboutProfile,-1,-2);selectProfileTab(profileTab);
 }
 String profileSignature(){return prefs.contains("profile_signature")?prefs.getString("profile_signature",""):prefs.getString("profile_public_signature","");}
 String profileSignatureDisplay(){String value=profileSignature();return value.isEmpty()?"还没有填写个性签名":value;}
 void editProfileSignature(){
  EditText input=new EditText(this);input.setTypeface(AppFont.get(this));input.setText(profileSignature());input.setHint("写下你的个性签名");input.setMinLines(3);input.setMaxLines(5);input.setGravity(Gravity.TOP);input.setPadding(dp(20),dp(16),dp(20),dp(16));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
  new AlertDialog.Builder(this).setTitle("编辑公开个性签名").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String value=input.getText().toString().trim();prefs.edit().putString("profile_signature",value).putBoolean("profile_signature_public",false).apply();refreshBindings(root);selectProfileTab(profileTab);syncProfileSignature(value);}).show();
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
    for(int quality=90;quality>=50;quality-=10){try(FileOutputStream out=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("Unable to save image");}if(target.length()<=2_000_000)break;}
    if(target.length()>2_000_000)throw new IOException("Image too large");
    final String saved=target.getAbsolutePath();ui.post(()->{String previous=prefs.getString("profile_cover","");prefs.edit().putString("profile_cover",saved).putBoolean("profile_cover_public",false).apply();if(!previous.isEmpty()&&!previous.equals(saved))new File(previous).delete();if(route.equals("我的")){body.removeAllViews();profile();}syncProfileCover(saved);});
   }catch(Exception e){if(target!=null)target.delete();ui.post(()->note("图片未能读取，请换一张试试"));}finally{if(bitmap!=null)bitmap.recycle();}
  });
 }
 void loadOwnProfile(){
  String token=prefs.getString("account_token","");
  if(token.isEmpty())return;
  net.execute(()->{
   try{
    Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);
    JSONObject data=callApi("GET","/profile",null,headers,false);
    if(!token.equals(prefs.getString("account_token","")))return;
    String id=data.optString("id","");
    String signature=data.optString("signature","");
    String cover=data.isNull("cover")?"":data.optString("cover","");
    if(!id.matches("[A-Za-z0-9_-]{1,128}"))return;
    if(!cover.isEmpty()&&!isProfileCoverPath(id,cover))cover="";
    boolean signaturePublic=prefs.contains("profile_signature")&&signature.equals(prefs.getString("profile_signature",""));
    String localCover=prefs.getString("profile_cover","");
    boolean coverPublic=!localCover.isEmpty()&&localCover.equals(prefs.getString("profile_synced_local_cover",""))&&cover.equals(prefs.getString("profile_synced_cover",""));
    android.content.SharedPreferences.Editor editor=prefs.edit().putString("account_user_id",id).putString("profile_public_signature",signature).putString("profile_public_cover",cover).putBoolean("profile_signature_public",signaturePublic).putBoolean("profile_cover_public",coverPublic);
    editor.apply();
    ui.post(()->{if(token.equals(prefs.getString("account_token",""))&&route.equals("我的")&&body!=null){body.removeAllViews();profile();}});
   }catch(Exception ignored){}
  });
 }
 void syncProfileSignature(String value){
  String token=prefs.getString("account_token","");
  if(token.isEmpty()){note("签名已保存在本机，请登录后公开");return;}
  note("正在同步公开签名…");
  net.execute(()->{
   try{
    if(!token.equals(prefs.getString("account_token","")))return;
    Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);headers.put("Content-Type","application/json");
    byte[] data=new JSONObject().put("signature",value).toString().getBytes(StandardCharsets.UTF_8);
    callApi("PUT","/profile",data,headers,false);
    if(!token.equals(prefs.getString("account_token","")))return;
    android.content.SharedPreferences.Editor editor=prefs.edit().putString("profile_public_signature",value);
    if(value.equals(prefs.getString("profile_signature","")))editor.putBoolean("profile_signature_public",true);
    editor.apply();
    ui.post(()->{if(!token.equals(prefs.getString("account_token","")))return;if(route.equals("我的")&&body!=null){body.removeAllViews();profile();}note("签名已公开");});
   }catch(Exception error){ui.post(()->{if(token.equals(prefs.getString("account_token","")))note("签名已保存在本机，公开同步失败，请重试");});}
  });
 }
 void syncProfileCover(String filePath){
  String token=prefs.getString("account_token","");
  if(token.isEmpty()){note("封面已保存在本机，请登录后公开");return;}
  note("正在同步公开封面…");
  net.execute(()->{
   try{
    if(!token.equals(prefs.getString("account_token",""))||!filePath.equals(prefs.getString("profile_cover","")))return;
    byte[] bytes;try(InputStream input=new FileInputStream(filePath)){bytes=readAll(input);}
    Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);headers.put("Content-Type","image/jpeg");
    JSONObject result=callApi("PUT","/profile/cover",bytes,headers,false);
    if(!token.equals(prefs.getString("account_token","")))return;
    String cover=result.optString("cover","");String userId=result.optString("id",prefs.getString("account_user_id",""));
    if(!isProfileCoverPath(userId,cover))throw new IOException("Invalid profile cover response");
    android.content.SharedPreferences.Editor editor=prefs.edit().putString("account_user_id",userId).putString("profile_public_cover",cover);
    if(filePath.equals(prefs.getString("profile_cover","")))editor.putString("profile_synced_local_cover",filePath).putString("profile_synced_cover",cover).putBoolean("profile_cover_public",true);
    editor.apply();
    ui.post(()->{if(!token.equals(prefs.getString("account_token","")))return;if(route.equals("我的")&&body!=null){body.removeAllViews();profile();}note("封面已公开");});
   }catch(Exception error){ui.post(()->{if(token.equals(prefs.getString("account_token","")))note("封面已保存在本机，公开同步失败，请重试");});}
  });
 }
 void selectProfileTab(String tab){
  profileTab=tab;if(profileTabs==null||profileContent==null)return;
  for(int i=0;i<profileTabs.getChildCount();i++){LinearLayout item=(LinearLayout)profileTabs.getChildAt(i);TextView label=(TextView)item.getChildAt(0);boolean selected=label.getText().toString().equals(tab);label.setTextColor(selected?accent:muted);label.setTypeface(AppFont.get(this),selected?Typeface.BOLD:Typeface.NORMAL);item.getChildAt(1).setBackground(selected?bg(accent,2):bg(Color.TRANSPARENT,2));item.setSelected(selected);}
  boolean about=tab.equals("关于我");aboutProfile.setVisibility(about?View.VISIBLE:View.GONE);profileCards.setVisibility(about?View.GONE:View.VISIBLE);
  if(about){aboutProfile.setText("🐾  "+prefs.getString("name","猫猫爱好者")+"\n\n"+profileSignatureDisplay()+"\n\n编辑个性签名  ›");aboutProfile.setAlpha(0);aboutProfile.animate().alpha(1).setDuration(220).start();}else{ArrayList<Post> selected=new ArrayList<>();for(Post p:posts)if(tab.equals("我的发布")?p.mine:tab.equals("点赞")?on(p,"like"):on(p,"save"))selected.add(p);profileCards.update(selected);}
 }
 TextView authorLabel(Post post,int size){
  TextView label=txt("",size,muted);
  label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
  Runnable update=()->{
   boolean canOpen=!post.mine&&!post.authorId.isEmpty();
   label.setText("🐾  "+post.name+(canOpen?"  ·  查看主页  ›":""));
   label.setTextColor(canOpen?accent:muted);
   label.setBackground(canOpen?bg(pale,14):null);
   label.setPadding(dp(12),dp(5),dp(12),dp(5));
   label.setElevation(canOpen?dp(2):0);
   label.setContentDescription(canOpen?"查看 "+post.name+" 的主页":post.name);
   label.setOnClickListener(canOpen?v->push("猫友主页",post):null);
  };
  label.setTag(update);update.run();return label;
 }
 void publicProfile(Post owner){
  title(owner.name+"的猫猫窝",null,null);
  LinearLayout content=col();add(body,content,-1,-2);
  loadPublicProfile(owner,content);
 }
 void loadPublicProfile(Post owner,LinearLayout target){
  target.removeAllViews();add(target,txt("正在加载主页…",14,muted),-1,50);
  String ownerId=owner.authorId;
  if(!ownerId.matches("[A-Za-z0-9_-]{1,128}")){target.removeAllViews();add(target,txt("这位猫友的主页暂不可用",14,muted),-1,50);return;}
  net.execute(()->{
   try{
    JSONObject data=callApi("GET","/profiles/"+ownerId,null,null,true);
    ui.post(()->{
     if(!target.isAttachedToWindow())return;
     try{
      target.removeAllViews();
      String name=owner.name.isEmpty()?data.optString("name","猫猫爱好者"):owner.name;
      String cover=data.isNull("cover")?"":data.optString("cover","");
      if(isProfileCoverPath(ownerId,cover)){
       ImageView image=photo(new Post("profile_cover_"+ownerId,"","",API+cover,"",false),180);
       image.setScaleType(ImageView.ScaleType.FIT_CENTER);
       image.setOnClickListener(null);image.setClickable(false);
       image.setContentDescription(name+"的主页封面");
       add(target,image,-1,180);
      }
      gap(target,12);
      TextView nickname=txt(name,22,ink);nickname.setTypeface(AppFont.get(this),Typeface.BOLD);add(target,nickname,-1,48);
      String signature=data.optString("signature","").trim();
      TextView intro=txt(signature.isEmpty()?"这位猫友还没有填写签名":signature,15,muted);
      intro.setPadding(dp(14),dp(12),dp(14),dp(12));intro.setBackground(bg(0xfffffdfa,18));add(target,intro,-1,-2);
      ArrayList<Post> works=new ArrayList<>();
      appendPublicWorks(data.getJSONArray("posts"),ownerId,name,works);
      gap(target,12);add(target,txt("作品  ·  "+data.optInt("post_count",works.size()),18,ink),-1,48);
      AnimatedCards cards=new AnimatedCards(true);add(target,cards,-1,-2);cards.update(works);
      JSONObject[] next={data.optJSONObject("next")};
      if(next[0]!=null){
       gap(target,12);
       TextView more=button("查看更多作品",()->{});
       more.setOnClickListener(v->loadMorePublicWorks(ownerId,name,target,cards,works,next,more));
       add(target,more,-1,52);
      }
     }catch(Exception error){showPublicProfileError(owner,target);}
    });
   }catch(Exception error){ui.post(()->{if(target.isAttachedToWindow())showPublicProfileError(owner,target);});}
  });
 }
 void appendPublicWorks(JSONArray items,String ownerId,String name,ArrayList<Post> works)throws Exception{
  HashSet<String> existing=new HashSet<>();for(Post post:works)existing.add(post.id);
  for(int i=0;i<items.length();i++){
   JSONObject item=items.getJSONObject(i);String id=item.getString("id"),path=item.getString("image");
   if(!id.startsWith("cloud_")||!path.equals("/images/"+id)||!existing.add(id))continue;
   String breed=item.isNull("breed")?"":item.optString("breed","");
   Post post=new Post(id,item.optString("name",name),item.optString("text",""),API+path,breed,false);
   post.authorId=ownerId;works.add(post);
  }
 }
 void loadMorePublicWorks(String ownerId,String name,LinearLayout target,AnimatedCards cards,ArrayList<Post> works,JSONObject[] next,TextView more){
  if(next[0]==null)return;
  JSONObject cursor=next[0];
  long before=cursor.optLong("before",-1);String beforeId=cursor.optString("before_id","");
  if(before<0||!beforeId.matches("cloud_[0-9a-f-]{36}")){more.setText("作品加载失败，请返回重试");more.setEnabled(false);return;}
  more.setEnabled(false);more.setText("正在加载…");
  net.execute(()->{
   try{
    JSONObject data=callApi("GET","/profiles/"+ownerId+"?before="+before+"&before_id="+beforeId,null,null,true);
    ui.post(()->{
     if(!target.isAttachedToWindow())return;
     try{
      appendPublicWorks(data.getJSONArray("posts"),ownerId,name,works);cards.update(works);
      next[0]=data.optJSONObject("next");
      if(next[0]==null)more.setVisibility(View.GONE);
      else{more.setText("查看更多作品");more.setEnabled(true);}
     }catch(Exception error){more.setText("加载失败，点击重试");more.setEnabled(true);}
    });
   }catch(Exception error){ui.post(()->{if(target.isAttachedToWindow()){more.setText("加载失败，点击重试");more.setEnabled(true);}});}
  });
 }
 void showPublicProfileError(Post owner,LinearLayout target){target.removeAllViews();add(target,button("主页加载失败，点击重试",()->loadPublicProfile(owner,target)),-1,52);}
 boolean isProfileCoverPath(String userId,String path){return userId.matches("[A-Za-z0-9_-]{1,128}")&&path.matches("/profile-covers/"+userId+"\\?v=[0-9a-f-]{36}");}
 void detail(Post p){if(!navigating)push("详情",p);}
 void detailContent(Post p){title("猫猫日常",null,null);body.addView(photo(p,360));add(body,authorLabel(p,19),-1,55);if(!p.breed.isEmpty())add(body,txt("种类 · "+p.breed,14,muted),-1,32);add(body,txt(p.text,17,ink),-1,-2);actions(body,p);if(p.id.startsWith("cloud_"))showComments(p);if(p.mine)add(body,button("删除这条发布",()->new AlertDialog.Builder(this).setMessage("删除这条发布？").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{if(p.id.startsWith("cloud_"))deleteCloud(p);else{posts.remove(p);savePosts();back();}}).show()),-1,48);}
 void showComments(Post post){
  LinearLayout section=col();gap(body,14);add(body,txt("评论与回复",19,ink),-1,44);add(body,section,-1,-2);TextView addComment=button("写评论",()->commentDialog(post,null,section));add(body,addComment,-1,48);loadComments(post,section);
 }
 void loadComments(Post post,LinearLayout section){section.removeAllViews();add(section,txt("正在加载评论…",14,muted),-1,40);net.execute(()->{try{JSONObject response=callApi("GET","/posts/"+post.id+"/comments",null,null,true);JSONArray comments=response.getJSONArray("comments");ui.post(()->{section.removeAllViews();if(comments.length()==0)add(section,txt("还没有评论，来打个招呼吧",14,muted),-1,40);for(int i=0;i<comments.length();i++){JSONObject item=comments.optJSONObject(i);if(item==null)continue;String parent=item.optString("parent_id","");if(parent.equals("null"))parent="";String id=item.optString("id"),name=item.optString("name","猫猫爱好者"),text=item.optString("text","");LinearLayout card=col();card.setPadding(dp(parent.isEmpty()?10:30),dp(6),dp(10),dp(6));add(card,txt((parent.isEmpty()?"🐾 ":"↳ ")+name+"："+text,15,ink),-1,-2);TextView reply=button("回复",()->commentDialog(post,id,section));reply.setTextSize(13);add(card,reply,60,42);add(section,card,-1,-2);}});}catch(Exception e){ui.post(()->{section.removeAllViews();add(section,txt("评论暂时加载失败，请稍后重试",14,muted),-1,45);});}});}
 void commentDialog(Post post,String parent,LinearLayout section){EditText input=new EditText(this);input.setTypeface(AppFont.get(this));input.setHint(parent==null?"写下你的评论":"写下你的回复");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});new AlertDialog.Builder(this).setTitle(parent==null?"发表评论":"回复评论").setView(input).setNegativeButton("取消",null).setPositiveButton("发送",(d,w)->{String text=input.getText().toString().trim();if(text.isEmpty()){note("请先输入内容");return;}net.execute(()->{try{JSONObject data=new JSONObject().put("text",text);if(parent!=null)data.put("parent_id",parent);Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");callApi("POST","/posts/"+post.id+"/comments",data.toString().getBytes(StandardCharsets.UTF_8),h,true);ui.post(()->loadComments(post,section));}catch(Exception e){ui.post(()->note("发送失败，请稍后重试"));}});}).show();}
 void publish(){
  title("发布猫猫",null,null);
  add(body,txt("让可爱发生",16,muted),-1,50);
  TextView upload=button(chosen.isEmpty()?"☁\n选择小猫照片\n最多 9 张":"已选择 "+chosen.size()+" 张 · 点击重新选择",()->{
   draft=story.getText().toString();
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   i.setType("image/*");
   i.addCategory(Intent.CATEGORY_OPENABLE);
   i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
   startActivityForResult(i,9);
  });
  add(body,upload,-1,180);
  if(!chosen.isEmpty()){
   gap(body,10);
   LinearLayout r=row();
   for(String f:chosen){
    Post p=new Post("","","",f,"",true);
    ImageView v=photo(p,80);
    r.addView(v,new LinearLayout.LayoutParams(0,dp(80),1));
   }
   add(body,r,-1,-2);
  }
  gap(body,16);
  TextView breed=button("",()->{});
  Runnable updateBreed=()->breed.setText(draftBreed.isEmpty()?"选择小猫种类（必选）  ›":"小猫种类："+draftBreed+"  ›");
  updateBreed.run();
  breed.setOnClickListener(v->new AlertDialog.Builder(this)
   .setTitle("选择小猫种类")
   .setItems(CAT_BREEDS,(dialog,which)->{draftBreed=CAT_BREEDS[which];updateBreed.run();})
   .setNegativeButton("取消",null).show());
  add(body,breed,-1,52);
  gap(body,16);
  story=new EditText(this);
  story.setHint("写下关于这只小猫的故事…（可选）");
  story.setText(draft);
  story.setTextSize(15);
  story.setGravity(Gravity.TOP);
  story.setPadding(dp(16),dp(16),dp(16),dp(16));
  story.setBackground(bg(pale,20));
  story.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
  add(body,story,-1,150);
  add(body,txt("最多 200 字，每张图片不超过 2MB · 发布后所有用户可见",12,muted),-1,42);
  gap(body,25);
  TextView submit=button("🐾  发布",()->{
   if(chosen.isEmpty()){note("先选一张小猫照片吧");return;}
   if(draftBreed.isEmpty()){note("请选择小猫种类");return;}
   if(publishing){note("正在发布，请稍等");return;}
   draft=story.getText().toString().trim();
   publishCloud(new ArrayList<>(chosen),draft,draftBreed);
  });
  submit.setTextColor(Color.WHITE);
  submit.setBackground(bg(accent,28));
  add(body,submit,-1,54);
 }
 protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==41){if(res==RESULT_OK&&data!=null&&data.getData()!=null)saveProfileCover(data.getData());return;}if(req!=9||res!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<Math.min(9,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());chosen.clear();for(Uri uri:uris){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;InputStream in=getContentResolver().openInputStream(uri);BitmapFactory.decodeStream(in,null,o);in.close();o.inSampleSize=Math.max(1,Math.max(o.outWidth,o.outHeight)/1400);o.inJustDecodeBounds=false;in=getContentResolver().openInputStream(uri);Bitmap bitmap=BitmapFactory.decodeStream(in,null,o);in.close();if(bitmap==null)continue;File f=new File(getFilesDir(),"photo"+System.nanoTime()+".jpg");FileOutputStream out=new FileOutputStream(f);bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);out.close();Bitmap thumb=Bitmap.createScaledBitmap(bitmap,24,24,true);try(FileOutputStream small=new FileOutputStream(f.getAbsolutePath()+".thumb")){thumb.compress(Bitmap.CompressFormat.JPEG,75,small);}thumb.recycle();bitmap.recycle();chosen.add(f.getAbsolutePath());}catch(Exception e){note("有一张图片未能读取，请换一张试试");}}if(route.equals("发布")){body.removeAllViews();publish();}}
 void savePosts(){JSONArray a=new JSONArray();for(Post p:posts)if(p.mine&&p.id.startsWith("local"))try{JSONObject o=new JSONObject();o.put("id",p.id);o.put("text",p.text);o.put("photo",p.photo);o.put("breed",p.breed);a.put(o);}catch(Exception e){}prefs.edit().putString("posts",a.toString()).apply();}
 void settings(){title("设置",null,null);add(body,button("退出登录",()->{net.execute(()->{try{Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");callApi("POST","/api/auth/sign-out","{}".getBytes(StandardCharsets.UTF_8),h,true);}catch(Exception ignored){}prefs.edit().remove("account_token").remove("account_user_id").remove("cloud_cache").remove("profile_public_signature").remove("profile_public_cover").putBoolean("profile_signature_public",false).putBoolean("profile_cover_public",false).commit();ui.post(()->{for(int i=posts.size()-1;i>=0;i--)if(posts.get(i).id.startsWith("cloud_"))posts.remove(i);showAuth();});});}),-1,50);gap(body,20);add(body,button("🐾   编辑昵称    ›",()->{EditText e=new EditText(this);e.setTypeface(AppFont.get(this));e.setSingleLine();e.setText(prefs.getString("name","猫猫爱好者"));new AlertDialog.Builder(this).setTitle("你希望怎么称呼？").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){prefs.edit().putString("name",n).apply();note("昵称已保存");}}).show();}),-1,58);}
 void info(String p){title(p,null,null);TextView t=txt(p.equals("帮助")?"如何使用猫猫社\n\n在广场浏览小猫，点爱心点赞，点星星收藏。\n点底部加号选择照片，发布到自己的猫猫窝。\n列表滑到顶部后继续下拉，松手获取最新内容。\n从左边缘向右滑可以返回上一页。":"Cat-ch 0.9.0\n\n分享每一只可爱的小猫。\n这是联网测试版，支持邮箱密码或邮箱验证码登录、评论回复和每日抽卡。照片和帖子发布后对其他使用者可见。\n示例照片取自你提供的设计图。",16,ink);t.setPadding(dp(16),dp(18),dp(16),dp(18));t.setBackground(bg(0xfffffdfa,20));add(body,t,-1,-2);}
 public void onBackPressed(){if(host==null){super.onBackPressed();return;}back();}
 static class ApiError extends IOException{ApiError(String m){super(m);}}
 byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>5_000_000)throw new IOException("响应过大");out.write(b,0,n);}return out.toByteArray();}
 JSONObject callApi(String method,String path,byte[] data,Map<String,String> extra,boolean withAuth)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Origin",API);
  try {
  if(withAuth)c.setRequestProperty("Authorization","Bearer "+getCloudToken());
  if(extra!=null)for(Map.Entry<String,String> e:extra.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
  if(data!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
  int status=c.getResponseCode();byte[] payload;try(InputStream stream=status>=400?c.getErrorStream():c.getInputStream()){payload=stream==null?new byte[0]:readAll(stream);}
  if(status==401&&withAuth){prefs.edit().remove("account_token").apply();ui.post(()->showAuth());}
  JSONObject result;try{result=new JSONObject(new String(payload,StandardCharsets.UTF_8));}catch(Exception e){throw new ApiError("服务器返回异常（HTTP "+status+"），请检查后端地址和服务状态");}
  if(status<200||status>=300)throw new ApiError(result.optString("message",result.optString("error","服务器暂时无法连接")));return result;
  }catch(java.net.UnknownHostException e){throw new ApiError("无法解析服务器域名，请检查网络和后端地址");
  }catch(java.net.SocketTimeoutException e){throw new ApiError("连接服务器超时，请检查网络或稍后重试");
  }catch(javax.net.ssl.SSLException e){throw new ApiError("无法建立安全连接，请检查手机时间和服务器证书");
  }catch(java.net.ConnectException e){throw new ApiError("无法连接服务器，请检查后端服务是否正常运行");
  }finally{c.disconnect();}
 }
 synchronized String getCloudToken()throws Exception{String token=prefs.getString("account_token","");if(token.isEmpty())throw new ApiError("请先登录");return token;}
 AuthPanel authPanel;
 void startSplash(){LinearLayout splash=col();splash.setGravity(Gravity.CENTER);splash.setBackgroundColor(cream);ImageView cat=new ImageView(this);cat.setScaleType(ImageView.ScaleType.FIT_CENTER);try(InputStream in=getAssets().open("splash_cat.png")){cat.setImageBitmap(BitmapFactory.decodeStream(in));}catch(IOException ignored){}splash.addView(cat,new LinearLayout.LayoutParams(dp(240),dp(290)));TextView brand=txt("Cat-ch",34,ink);brand.setGravity(Gravity.CENTER);add(splash,brand,-1,70);TextView subtitle=txt("接住每一份小可爱",15,muted);subtitle.setGravity(Gravity.CENTER);add(splash,subtitle,-1,40);setContentView(splash);cat.setScaleX(.6f);cat.setScaleY(.6f);cat.setAlpha(0);cat.animate().alpha(1).scaleX(1).scaleY(1).setDuration(700).setInterpolator(new OvershootInterpolator(1.4f)).start();ui.postDelayed(()->{if(isFinishing()||isDestroyed())return;if(prefs.getString("account_token","").isEmpty())showAuth();else enterApp();},1200);}
 JSONObject authRequest(String path,JSONObject data)throws Exception{Map<String,String> h=new HashMap<>();h.put("Content-Type","application/json");return callApi("POST","/api/auth/"+path,data.toString().getBytes(StandardCharsets.UTF_8),h,false);}
 interface AuthJob{JSONObject run()throws Exception;}
 void authTask(AuthJob job,boolean login){AuthPanel panel=authPanel;panel.setBusy(true);net.execute(()->{try{JSONObject result=job.run();if(login){String token=result.optString("token");if(token.isEmpty())throw new ApiError("请先验证邮箱，再登录");JSONObject loginUser=result.optJSONObject("user");String userId=loginUser==null?"":loginUser.optString("id","");android.content.SharedPreferences.Editor session=prefs.edit().putString("account_token",token).putString("name",loginUser==null?"猫猫爱好者":loginUser.optString("name")).putString("account_user_id",userId);if(!userId.equals(prefs.getString("account_user_id","")))session.remove("profile_public_signature").remove("profile_public_cover").putBoolean("profile_signature_public",false).putBoolean("profile_cover_public",false);session.commit();}ui.post(()->{panel.setBusy(false);if(login)enterApp();else panel.showMessage("验证码已发送，请查看邮箱");});}catch(Exception e){ui.post(()->{panel.setBusy(false);panel.showMessage(e instanceof ApiError?e.getMessage():"连接失败，请稍后重试");});}});}
 void showAuth(){host=null;root=null;route="";history.clear();authPanel=new AuthPanel(this,new AuthPanel.Listener(){
 public void sendCode(String name,String email,String password,boolean registration){authTask(()->registration?authRequest("sign-up/email",new JSONObject().put("name",name).put("email",email).put("password",password)):authRequest("email-otp/send-verification-otp",new JSONObject().put("email",email).put("type","sign-in")),false);}
 public void register(String name,String email,String password,String code){authTask(()->{return authRequest("email-otp/verify-email",new JSONObject().put("email",email).put("otp",code));},true);}
 public void loginWithPassword(String email,String password){authTask(()->authRequest("sign-in/email",new JSONObject().put("email",email).put("password",password)),true);}
 public void loginWithCode(String email,String code){authTask(()->authRequest("sign-in/email-otp",new JSONObject().put("email",email).put("otp",code)),true);}
 });ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(cream);scroll.addView(authPanel);setContentView(scroll);}
 void enterApp(){checkForUpdate();host=null;root=null;route="";history.clear();restoreCloudCache();show("广场");loadOwnProfile();net.execute(()->{String legacy=prefs.getString("cloud_token","");if(!legacy.isEmpty()){try{Map<String,String> h=new HashMap<>();h.put("X-Legacy-Token",legacy);callApi("POST","/migrate-anonymous",new byte[0],h,true);prefs.edit().remove("cloud_token").commit();}catch(Exception e){ui.post(()->note("旧发布暂未同步，稍后登录会重试"));}}loadCloud(false,null);});}
 void checkForUpdate(){net.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("https://api.github.com/repos/adrian147258/Cat-ch/releases?per_page=10").openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestProperty("Accept","application/vnd.github+json");if(c.getResponseCode()!=200)return;byte[] raw;try(InputStream in=c.getInputStream()){raw=readAll(in);}JSONArray releases=new JSONArray(new String(raw,StandardCharsets.UTF_8));String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;for(int i=0;i<releases.length();i++){JSONObject r=releases.optJSONObject(i);if(r==null||r.optBoolean("draft"))continue;String tag=r.optString("tag_name","");String version=tag.replaceFirst("^[vV]","").split("-")[0];if(!version.matches("[0-9]+(\\.[0-9]+)*")||compareVersions(version,current)<=0)continue;JSONArray assets=r.optJSONArray("assets");String url="";if(assets!=null)for(int j=0;j<assets.length();j++){JSONObject asset=assets.optJSONObject(j);if(asset!=null&&asset.optString("name").endsWith(".apk")){url=asset.optString("browser_download_url");break;}}if(!url.startsWith("https://github.com/adrian147258/Cat-ch/releases/download/"))continue;final String destination=url,display=tag;ui.post(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("发现新版本 "+display).setMessage("有新的 Cat-ch 版本，可以前往下载并安装。已安装的数据通常会保留。 ").setNegativeButton("稍后",null).setPositiveButton("前往下载",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(destination)))).show();});break;}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
 int compareVersions(String a,String b){String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<Math.max(x.length,y.length);i++){int n=i<x.length?Integer.parseInt(x[i]):0,m=i<y.length?Integer.parseInt(y[i]):0;if(n!=m)return Integer.compare(n,m);}return 0;}
 void restoreCloudCache(){try{applyCloud(new JSONObject(prefs.getString("cloud_cache","{\"posts\":[]}")));}catch(Exception ignored){}}
 void applyCloud(JSONObject data)throws Exception{
  JSONArray a=data.getJSONArray("posts");
  ArrayList<Post> cloud=new ArrayList<>();
  HashMap<String,Post> existing=new HashMap<>();
  for(Post post:posts)if(post.id.startsWith("cloud_"))existing.put(post.id,post);
  android.content.SharedPreferences.Editor edit=prefs.edit();
  for(int i=0;i<a.length();i++){
   JSONObject o=a.getJSONObject(i);
   String id=o.getString("id"),path=o.getString("image");
   if(!id.startsWith("cloud_")||!path.equals("/images/"+id))continue;
   String name=o.optString("name","猫猫爱好者"),text=o.optString("text","");
   String breed=o.isNull("breed")?"":o.optString("breed","");
   boolean mine=o.optBoolean("mine");
   Post post=existing.get(id);
   if(post==null)post=new Post(id,name,text,API+path,breed,mine);
   else{post.name=name;post.text=text;post.photo=API+path;post.breed=breed;post.mine=mine;}
   post.authorId=o.isNull("author_id")?"":o.optString("author_id","");
   cloud.add(post);
   edit.putBoolean("like"+id,o.optBoolean("liked"));
   edit.putBoolean("save"+id,o.optBoolean("saved"));
  }
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
 void publishCloud(ArrayList<String> files,String text,String breed){
  publishing=true;note("正在上传猫猫照片…");String name=prefs.getString("name","猫猫爱好者");
  net.execute(()->{int sent=0;String error=null;for(String file:files){try{File f=new File(file);if(f.length()>2_000_000)throw new ApiError("图片超过 2MB，请重新选择");byte[] bytes;try(InputStream in=new FileInputStream(f)){bytes=readAll(in);}Map<String,String> h=new HashMap<>();h.put("Content-Type","image/jpeg");h.put("X-Meow-Text",URLEncoder.encode(text,"UTF-8").replace("+","%20"));h.put("X-Meow-Name",URLEncoder.encode(name,"UTF-8").replace("+","%20"));h.put("X-Meow-Breed",URLEncoder.encode(breed,"UTF-8").replace("+","%20"));callApi("POST","/posts",bytes,h,true);sent++;}catch(Exception e){error=e instanceof ApiError?e.getMessage():"网络连接失败，请稍后重试";break;}}
   final int uploaded=sent;final String issue=error;ui.post(()->{publishing=false;for(int i=0;i<uploaded&&!chosen.isEmpty();i++)chosen.remove(0);if(issue==null){draft="";draftBreed="";show("我的");note("发布成功！");}else{note((uploaded>0?"已发布 "+uploaded+" 张；":"")+issue);if(route.equals("发布")){body.removeAllViews();publish();}}loadCloud(false,null);});});
 }
 void deleteCloud(Post p){net.execute(()->{try{callApi("DELETE","/posts/"+p.id,null,null,true);ui.post(()->{posts.remove(p);back();note("已删除");loadCloud(false,null);});}catch(Exception e){ui.post(()->note("删除失败，请检查网络"));}});}
 class SearchIcon extends View {
  Paint p=new Paint(3); SearchIcon(){super(MainActivity.this);}
  protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=dp(8);p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeCap(Paint.Cap.ROUND);c.drawCircle(x-dp(2),y-dp(2),r,p);c.drawLine(x+dp(4),y+dp(4),x+dp(10),y+dp(10),p);}
 }
 class PlusIcon extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  PlusIcon(){super(MainActivity.this);setContentDescription("发布猫猫");}
  protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(accent);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,Math.min(cx,cy),paint);paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2.6f));paint.setStrokeCap(Paint.Cap.ROUND);float half=dp(9);c.drawLine(cx-half,cy,cx+half,cy,paint);c.drawLine(cx,cy-half,cx,cy+half,paint);}
 }
 void openDrawer(){
  if(navigating||drawerLayer!=null)return;drawerLayer=new FrameLayout(this);drawerLayer.setBackgroundColor(0x44000000);host.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));drawerLayer.setOnClickListener(v->closeDrawer(null));
  drawerPanel=col();drawerPanel.setPadding(dp(22),dp(30),dp(22),dp(22));drawerPanel.setBackgroundColor(cream);drawerPanel.setClickable(true);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.min(dp(290),host.getWidth()-dp(56)),-1,Gravity.LEFT);drawerLayer.addView(drawerPanel,lp);
  add(drawerPanel,CatArt.image(this,0),72,65);add(drawerPanel,txt("猫猫社",25,ink),-1,46);add(drawerPanel,txt("和可爱一起生活",14,muted),-1,38);gap(drawerPanel,30);
  for(String name:new String[]{"设置","帮助","关于"}){add(drawerPanel,button((name.equals("设置")?"🐾  ":name.equals("帮助")?"♡  ":"✦  ")+name+"    ›",()->closeDrawer(()->show(name))),-1,52);gap(drawerPanel,12);}
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
  View make(Post p){LinearLayout c=col();c.setPadding(dp(compact?0:10),dp(compact?0:5),dp(compact?0:10),0);c.setBackground(bg(0xfffffdfa,compact?16:22));
   if(!compact)add(c,authorLabel(p,13),-1,48);
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
  protected void onDraw(Canvas c){super.onDraw(c);if(order.isEmpty()&&getChildCount()==0){Paint p=new Paint(3);p.setColor(muted);p.setTextSize(dp(15));p.setTextAlign(Paint.Align.CENTER);c.drawText("这里还没有小猫哦",getWidth()/2f,dp(75),p);}}
 }
 class PullScroll extends FrameLayout {
  final ScrollView scroll;final LinearLayout header;final TextView label;final ProgressBar spinner;
  boolean enabled,dragging,refreshing,hasBuzzed;float downX,downY,pull;ValueAnimator settle;final int threshold=dp(72);
  PullScroll(){super(MainActivity.this);setClipChildren(true);scroll=new ScrollView(MainActivity.this);scroll.setFillViewport(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);addView(scroll,new FrameLayout.LayoutParams(-1,-1));
   header=row();header.setGravity(Gravity.CENTER);spinner=new ProgressBar(MainActivity.this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));add(header,spinner,22,22);label=txt("下拉刷新",13,muted);add(header,label,-2,40);addView(header,new FrameLayout.LayoutParams(-1,threshold,Gravity.TOP));header.setTranslationY(-threshold);header.setVisibility(INVISIBLE);
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
     java.util.function.Consumer<Boolean> finish=ok->{if(root==owner&&!route.equals("抽卡"))refreshVisible();label.setText(ok?"刷新完成":"刷新失败");ui.postDelayed(()->animatePull(0,()->refreshing=false),280);};if(route.equals("抽卡")&&cardList!=null)fetchCards(cardList,finish);else loadCloud(true,finish);
    }else animatePull(0,null);return true;}
   return true;
  }
  void animatePull(float target,Runnable done){if(settle!=null)settle.cancel();settle=ValueAnimator.ofFloat(pull,target);settle.setDuration(240);settle.setInterpolator(new DecelerateInterpolator());settle.addUpdateListener(a->position((float)a.getAnimatedValue()));settle.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){if(done!=null)done.run();}});settle.start();}
 }
 @Override protected void onDestroy(){super.onDestroy();if(cardDialog!=null)cardDialog.dismiss();net.shutdownNow();images.shutdownNow();ui.removeCallbacksAndMessages(null);}

}
