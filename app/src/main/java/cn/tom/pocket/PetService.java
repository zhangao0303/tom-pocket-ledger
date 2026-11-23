package cn.tom.pocket;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

public final class PetService extends Service implements RecognitionListener {
    static final String STOP="cn.tom.pocket.STOP";
    private WindowManager wm;private TextView pet;private View editor;private WindowManager.LayoutParams position;
    private final Handler handler=new Handler(Looper.getMainLooper());private SpeechRecognizer speech;
    private boolean held=false,moved=false,listening=false,waiting=false,released=false;private int baseX,baseY;private float downX,downY;private String partial="",bufferedResult=null;
    private final Runnable begin=()->startListening();
    private final Runnable timeout=()->{cancelSpeech();toast("识别超时，请检查系统语音服务。可打开汤姆手动记账。");};
    private final Runnable holdLimit=()->{if(listening){released=true;finishListening();}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){cancelSpeech();closeEditor();}};
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,f);}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("tom-pet","汤姆悬浮宠物",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PetService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(this,"tom-pet").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("汤姆在陪你记账").setContentText("长按说话，松手停止；拖动可移动。只在长按时尝试录音。").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"关闭悬浮宠物",stop).build()).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(12,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(12,n);
            if(pet==null)showPet();
        }catch(Exception ex){toast("无法开启悬浮宠物，请回到应用检查权限后重试。");stopSelf();}
        return START_NOT_STICKY;
    }
    private int dp(int n){return EntryEditor.dp(this,n);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void showPet(){
        pet=new TextView(this);pet.setGravity(Gravity.CENTER);pet.setTextSize(12);pet.setTextColor(Color.rgb(80,51,32));pet.setContentDescription("汤姆：长按说话记账，松手停止，拖动移动");android.graphics.drawable.Drawable icon=getDrawable(cn.tom.pocket.R.drawable.tom_icon);icon.setBounds(0,0,dp(56),dp(56));pet.setCompoundDrawables(null,icon,null,null);pet.setCompoundDrawablePadding(dp(1));pet.setPadding(dp(7),dp(5),dp(7),dp(5));pet.setText("长按说话");
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(255,243,224));bg.setCornerRadius(dp(23));bg.setStroke(dp(1),Color.rgb(233,202,168));pet.setBackground(bg);pet.setElevation(dp(5));
        position=new WindowManager.LayoutParams(dp(88),dp(100),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);position.gravity=Gravity.TOP|Gravity.LEFT;
        android.content.SharedPreferences pref=getSharedPreferences("tom-pet-position",0);position.x=pref.getInt("x",dp(14));position.y=pref.getInt("y",dp(200));clamp();wm.addView(pet,position);
        pet.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(editor!=null||waiting)return true;held=true;moved=false;released=false;downX=event.getRawX();downY=event.getRawY();baseX=position.x;baseY=position.y;handler.postDelayed(begin,ViewConfiguration.getLongPressTimeout());return true;
                case MotionEvent.ACTION_MOVE:
                    if(!held)return true;float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(!moved&&Math.hypot(dx,dy)>dp(18)){moved=true;handler.removeCallbacks(begin);cancelSpeech();held=true;pet.setText("拖动中");}
                    if(moved){position.x=baseX+(int)dx;position.y=baseY+(int)dy;clamp();wm.updateViewLayout(pet,position);}return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(begin);boolean wasHeld=held;held=false;
                    if(moved){getSharedPreferences("tom-pet-position",0).edit().putInt("x",position.x).putInt("y",position.y).apply();pet.setText("长按说话");}
                    else if(listening){released=true;finishListening();}
                    else if(wasHeld&&!waiting)toast("长按汤姆开始说话，松手停止。拖动可以移动位置。");return true;
                case MotionEvent.ACTION_CANCEL:cancelSpeech();return true;
                default:return true;
            }
        });
    }
    private void clamp(){android.util.DisplayMetrics m=getResources().getDisplayMetrics();position.x=Math.max(0,Math.min(position.x,Math.max(0,m.widthPixels-dp(88))));position.y=Math.max(dp(25),Math.min(position.y,Math.max(dp(25),m.heightPixels-dp(125))));}
    private void startListening(){
        if(!held||moved||waiting)return;
        if(!SpeechRecognizer.isRecognitionAvailable(this)){toast("手机没有可用的系统语音识别服务。请打开汤姆，使用输入法语音或手动记账。");return;}
        try{destroySpeech();speech=SpeechRecognizer.createSpeechRecognizer(this);speech.setRecognitionListener(this);partial="";listening=true;released=false;pet.setText("正在聆听\n松手停止");pet.setTextColor(Color.rgb(190,73,23));
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);speech.startListening(intent);handler.postDelayed(holdLimit,20000);
        }catch(Exception ex){cancelSpeech();toast("语音启动失败，请检查系统语音服务与麦克风权限。");}
    }
    private void finishListening(){if(!listening)return;if(bufferedResult!=null){String result=bufferedResult;cancelSpeech();if(!result.trim().isEmpty())review(result.trim());else toast("没有识别到内容，请重试。");return;}listening=false;waiting=true;handler.removeCallbacks(holdLimit);pet.setText("正在整理…");handler.postDelayed(timeout,12000);try{speech.stopListening();}catch(Exception ex){cancelSpeech();toast("语音服务未响应，请重试。");}}
    private void cancelSpeech(){held=false;listening=false;waiting=false;released=false;partial="";bufferedResult=null;handler.removeCallbacks(begin);handler.removeCallbacks(timeout);handler.removeCallbacks(holdLimit);destroySpeech();if(pet!=null){pet.setText("长按说话");pet.setTextColor(Color.rgb(80,51,32));}}
    private void destroySpeech(){if(speech!=null){speech.setRecognitionListener(null);speech.cancel();speech.destroy();speech=null;}}
    private void closeEditor(){if(editor!=null){try{wm.removeView(editor);}catch(Exception ignored){}editor=null;}}
    private void review(String result){
        closeEditor();Book.Entry e;String hint="识别内容："+result;
        try{e=Book.parse(result);}catch(Exception ex){e=new Book.Entry();e.note=result.length()>200?result.substring(0,200):result;hint+="\n"+ex.getMessage();}
        editor=EntryEditor.create(this,e,hint,()->closeEditor());WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(390)),Math.min(getResources().getDisplayMetrics().heightPixels-dp(120),dp(650)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{wm.addView(editor,p);}catch(Exception ex){editor=null;toast("无法显示确认窗口，请回到应用检查悬浮窗权限。");}
    }
    @Override public void onReadyForSpeech(Bundle b){} @Override public void onBeginningOfSpeech(){} @Override public void onRmsChanged(float v){} @Override public void onBufferReceived(byte[] b){} @Override public void onEvent(int t,Bundle b){}
    @Override public void onEndOfSpeech(){if(listening){pet.setText("识别中\n松手确认");handler.removeCallbacks(holdLimit);handler.postDelayed(timeout,12000);}}
    @Override public void onPartialResults(Bundle b){ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(list!=null&&!list.isEmpty())partial=list.get(0);}
    @Override public void onResults(Bundle b){
        ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String result=list!=null&&!list.isEmpty()?list.get(0):partial;
        // Some system services finish early on silence. Do not restart recording while a finger remains down.
        boolean stillHeld=held&&!moved;cancelSpeech();
        if(stillHeld){held=true;bufferedResult=result;listening=true;waiting=false;pet.setText("已听清\n松手确认");return;}
        if(!result.trim().isEmpty())review(result.trim());else toast("没有识别到内容，请再长按说一次。");
    }
    @Override public void onError(int code){cancelSpeech();String msg=code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"麦克风权限未开启，请回到汤姆应用授权。":code==SpeechRecognizer.ERROR_NETWORK||code==SpeechRecognizer.ERROR_NETWORK_TIMEOUT?"语音识别网络连接失败，请联网后再试。":code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"没听清，请再长按说一次。":"系统语音服务暂时不可用（"+code+"），可回到应用手动记账。";toast(msg);}
    @Override public void onDestroy(){cancelSpeech();closeEditor();if(pet!=null){try{wm.removeView(pet);}catch(Exception ignored){}pet=null;}try{unregisterReceiver(screen);}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
