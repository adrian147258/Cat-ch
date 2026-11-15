package com.meowclub.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Login and registration controls; the owner provides the authenticated API implementation. */
public final class AuthPanel extends LinearLayout {
 public interface Listener {
  void sendCode(String name, String email, String password, boolean registration);
  void register(String username, String email, String password, String verificationCode);
  void loginWithPassword(String email, String password);
  void loginWithCode(String email, String verificationCode);
  void sendResetCode(String email);
  void resetPassword(String email, String verificationCode, String newPassword);
 }
 private static final int ACCENT=0xff5b777f,INK=0xff373334,PALE=0xffe8edeb;
 private final Listener listener;
 private LinearLayout fields, tabs;
 private EditText name,email,password,code;
 private String mode="password";
 private TextView message,submit,send;
 private boolean busy;
 public AuthPanel(Context context,Listener listener){super(context);this.listener=listener;setOrientation(VERTICAL);setPadding(dp(22),dp(16),dp(22),dp(16));setBackgroundColor(0xfff8f5f1);render();}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff514c4b);return d;}
 private TextView label(String text,int size,int color){TextView t=new TextView(getContext());t.setTypeface(AppFont.get(getContext()));t.setText(text);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER);return t;}
 private TextView control(String text,Runnable action){TextView t=label(text,15,INK);t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x335b777f),background(0xfffffdfa,20),null));t.setElevation(dp(2));t.setOnClickListener(v->{if(!busy)action.run();});return t;}
 private EditText input(String hint,int type){EditText e=new EditText(getContext());e.setSingleLine(true);e.setTextSize(15);e.setTextColor(INK);e.setInputType(type);e.setTypeface(AppFont.get(getContext()));e.setPadding(dp(16),dp(17),dp(16),0);e.setBackgroundColor(Color.TRANSPARENT);e.setContentDescription(hint);e.setTag(hint);return e;}
 private View floating(EditText e){FrameLayout box=new FrameLayout(getContext());box.setBackground(background(PALE,16));box.addView(e,new FrameLayout.LayoutParams(-1,-1));TextView title=label((String)e.getTag(),15,INK);title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);title.setPivotX(0);title.setPivotY(0);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(24));lp.leftMargin=dp(16);lp.topMargin=dp(17);box.addView(title,lp);title.setOnClickListener(v->{e.requestFocus();((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(e,0);});Runnable update=()->{boolean up=e.hasFocus()||e.length()>0;title.animate().translationY(up?-dp(12):0).scaleX(up?.76f:1f).scaleY(up?.76f:1f).setDuration(160).start();title.setTextColor(e.hasFocus()?ACCENT:INK);};e.setOnFocusChangeListener((v,f)->update.run());e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){update.run();}public void afterTextChanged(Editable x){}});return box;}
 private void addField(View v,int height){LayoutParams lp=new LayoutParams(-1,dp(60));lp.bottomMargin=dp(12);fields.addView(v instanceof EditText?floating((EditText)v):v,lp);}
 private void render(){
  removeAllViews();ImageView cat=CatArt.image(getContext(),0);addView(cat,new LayoutParams(-1,dp(85)));TextView title=label("欢迎来到 Cat-ch",23,INK);title.setTypeface(AppFont.get(getContext()),Typeface.BOLD);addView(title,new LayoutParams(-1,dp(45)));
  tabs=new LinearLayout(getContext());String[] titles={"密码登录","验证码登录","注册"};String[] modes={"password","code","register"};
  for(int i=0;i<titles.length;i++){final String m=modes[i];TextView t=control(titles[i],()->{mode=m;render();});if(mode.equals(m)){t.setTextColor(Color.WHITE);t.setBackground(background(ACCENT,24));}LayoutParams p=new LayoutParams(0,dp(42),1);p.setMargins(dp(3),0,dp(3),0);tabs.addView(t,p);}LayoutParams tp=new LayoutParams(-1,dp(42));tp.setMargins(0,dp(16),0,dp(20));addView(tabs,tp);
  fields=new LinearLayout(getContext());fields.setOrientation(VERTICAL);addView(fields,new LayoutParams(-1,-2));
  name=null;password=null;code=null;if(mode.equals("register")){name=input("用户名",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);addField(name,48);}
  email=input("邮箱",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);addField(email,48);
  if(!mode.equals("code")){password=input(mode.equals("register")||mode.equals("reset")?"设置密码（至少 10 位）":"密码",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);addField(password,48);}
  if(!mode.equals("password")){LinearLayout row=new LinearLayout(getContext());code=input("邮箱验证码",InputType.TYPE_CLASS_NUMBER);row.addView(floating(code),new LayoutParams(0,dp(60),1));send=control("发送验证码",()->{String address=value(email);if(!validEmail(address)){showMessage("请填写正确的邮箱");return;}if(mode.equals("reset")){listener.sendResetCode(address);return;}if(mode.equals("register")&&(value(name).length()<2||password.length()<10)){showMessage("先填写用户名和至少 10 位密码");return;}listener.sendCode(value(name),address,password==null?"":password.getText().toString(),mode.equals("register"));});LayoutParams sp=new LayoutParams(dp(112),dp(48));sp.leftMargin=dp(8);row.addView(send,sp);addField(row,48);}
  message=label("",13,0xff97503d);addView(message,new LayoutParams(-1,dp(34)));submit=control(mode.equals("register")?"注册":mode.equals("reset")?"重置密码":"登录",this::submit);submit.setTextColor(Color.WHITE);submit.setBackground(background(ACCENT,25));addView(submit,new LayoutParams(-1,dp(50)));if(mode.equals("password")||mode.equals("reset")){TextView link=control(mode.equals("reset")?"返回密码登录":"忘记密码？",()->{mode=mode.equals("reset")?"password":"reset";render();});LayoutParams lp=new LayoutParams(-1,dp(46));lp.topMargin=dp(10);addView(link,lp);}
 }
 private String value(EditText e){return e==null?"":e.getText().toString().trim();}
 private boolean validEmail(String address){return !TextUtils.isEmpty(address)&&android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()&&address.length()<=254;}
 private void submit(){String address=value(email);if(!validEmail(address)){showMessage("请填写正确的邮箱");return;}String secret=password==null?"":password.getText().toString();String otp=value(code);if(mode.equals("reset")){if(secret.length()<10||secret.length()>128){showMessage("密码需为 10–128 个字符");return;}if(!otp.matches("[0-9]{6}")){showMessage("请输入 6 位邮箱验证码");return;}listener.resetPassword(address,otp,secret);}else if(mode.equals("register")){String username=value(name);if(username.length()<2||username.length()>24){showMessage("用户名需为 2–24 个字符");return;}if(secret.length()<10||secret.length()>128){showMessage("密码需为 10–128 个字符");return;}if(!otp.matches("[0-9]{6}")){showMessage("请输入 6 位邮箱验证码");return;}listener.register(username,address,secret,otp);}else if(mode.equals("code")){if(!otp.matches("[0-9]{6}")){showMessage("请输入 6 位邮箱验证码");return;}listener.loginWithCode(address,otp);}else{if(secret.isEmpty()){showMessage("请输入密码");return;}listener.loginWithPassword(address,secret);}}
 public void resetSucceeded(){mode="password";render();showMessage("密码已重置，请使用新密码登录");}
 public void showMessage(String text){message.setText(text);if(mode.equals("register")&&text.startsWith("验证码已发送")){name.setEnabled(false);email.setEnabled(false);password.setEnabled(false);}}
 public void setBusy(boolean busy){this.busy=busy;submit.setAlpha(busy?.5f:1f);if(send!=null)send.setAlpha(busy?.5f:1f);}
 @Override protected void onDetachedFromWindow(){if(password!=null)password.setText("");if(code!=null)code.setText("");super.onDetachedFromWindow();}
}
