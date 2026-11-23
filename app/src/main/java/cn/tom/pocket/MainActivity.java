package cn.tom.pocket;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

public final class MainActivity extends Activity {
    private LinearLayout root,list;private EditText month;private TextView summary;private boolean explained=false;
    private int dp(int n){return EntryEditor.dp(this,n);}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->action.run());return b;}
    private void notice(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("知道了",null).show();}
    @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);root=EntryEditor.column(this);scroll.addView(root);setContentView(scroll);
        root.addView(EntryEditor.text(this,"汤姆 · 长按说话记账",25));root.addView(EntryEditor.text(this,"长按约半秒开始聆听，松手停止并核对账单。短按提示；拖动移动；锁屏取消识别。",15));
        button("开启悬浮宠物",()->explainAndStart());button("关闭悬浮宠物",()->stopService(new Intent(this,PetService.class)));
        button("手动记账 / 输入法语音",()->edit(new Book.Entry()));
        root.addView(EntryEditor.text(this,"账目只保存在此App。它与旧网页账本分开，不会自动迁移。请定期导出；卸载或清除App数据会删除账目。",13));
        root.addView(EntryEditor.text(this,"小米设置：允许悬浮窗 / 后台弹出界面，按需允许后台自启动，并在电量设置中选择无限制。系统仍可能关闭后台；关闭后请回到这里重新开启。",13));
        month=EntryEditor.input(root,"月份 YYYY-MM",LocalDate.now().toString().substring(0,7),android.text.InputType.TYPE_CLASS_DATETIME);button("查看本月账目",()->refresh());button("导出全部账目 CSV",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"汤姆账本-"+LocalDate.now()+".csv");startActivityForResult(i,30);});
        summary=EntryEditor.text(this,"",17);root.addView(summary);list=EntryEditor.column(this);root.addView(list);refresh();
    }
    private void explainAndStart(){if(!explained){new AlertDialog.Builder(this).setTitle("开启汤姆悬浮记账").setMessage("需要悬浮窗和麦克风权限。只在长按后调用系统语音服务；语音可能通过系统服务联网处理。没有接入自建语音服务器。部分小米手机没有可用的系统语音服务，此时请使用App内的输入法语音。\n\n运行时会显示常驻服务通知；可随时从通知或本页面关闭。只在你从本页面手动开启后运行，不会开机自动启动。").setNegativeButton("取消",null).setPositiveButton("继续设置",(d,w)->{explained=true;startPet();}).show();}else startPet();}
    private void startPet(){
        if(!Settings.canDrawOverlays(this)){try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));notice("请允许显示在其他应用上层，返回后再点“开启悬浮宠物”。");}catch(Exception ex){notice("请在系统设置→应用→汤姆悬浮记账中开启悬浮窗权限。");}return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);return;}
        launch();
    }
    private void launch(){try{startForegroundService(new Intent(this,PetService.class));}catch(Exception ex){notice("暂时无法开启，请保持应用在前台并检查权限后重试。");}}
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] values){super.onRequestPermissionsResult(code,p,values);if(code==10){if(values.length>0&&values[0]==PackageManager.PERMISSION_GRANTED)startPet();else notice("麦克风未授权。仍可在本页面手动记账。");}if(code==11)launch();}
    private void edit(Book.Entry e){Dialog dialog=new Dialog(this);dialog.setContentView(EntryEditor.create(this,e,"核对金额、日期和分类后确认保存。",()->{dialog.dismiss();refresh();}));dialog.show();dialog.getWindow().setLayout(-1,-2);}
    private void refresh(){if(summary==null)return;list.removeAllViews();String m=month.getText().toString().trim();if(!m.matches("\\d{4}-(0[1-9]|1[0-2])")){summary.setText("月份请填写 YYYY-MM");return;}try{List<Book.Entry> entries=Book.read(this);entries.sort((a,b)->b.date.compareTo(a.date));long income=0,expense=0;int count=0;for(Book.Entry e:entries){if(!e.date.startsWith(m))continue;if(e.type.equals("income"))income+=e.cents;else expense+=e.cents;count++;LinearLayout row=EntryEditor.column(this);row.addView(EntryEditor.text(this,e.date+" · "+e.category+"\n"+e.note+"\n"+(e.type.equals("income")?"+":"−")+Book.money(e.cents)+"元",15));LinearLayout controls=new LinearLayout(this);Button edit=new Button(this);edit.setText("修改");edit.setOnClickListener(v->edit(e));Button remove=new Button(this);remove.setText("删除");remove.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("删除这笔账？\n"+e.note+"\n"+Book.money(e.cents)+"元\n无法在App内恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Book.delete(this,e.id);refresh();}catch(Exception ex){notice(ex.getMessage());}}).show());controls.addView(edit);controls.addView(remove);row.addView(controls);list.addView(row);}summary.setText(count+"笔 · 收入 ¥"+Book.money(income)+"\n支出 ¥"+Book.money(expense)+" · 结余 ¥"+Book.money(income-expense));}catch(Exception ex){summary.setText("读取失败："+ex.getMessage());}}
    @Override public void onResume(){super.onResume();refresh();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==30&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{StringBuilder csv=new StringBuilder("\uFEFF日期,类型,金额（人民币）,分类,备注\r\n");for(Book.Entry e:Book.read(this))csv.append(cell(e.date)).append(',').append(cell(e.type.equals("income")?"收入":"支出")).append(',').append(cell(Book.money(e.cents))).append(',').append(cell(e.category)).append(',').append(cell(e.note)).append("\r\n");try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new Exception("无法打开文件");out.write(csv.toString().getBytes(StandardCharsets.UTF_8));}notice("账目已导出。CSV可查看，当前不支持导入恢复。");}catch(Exception ex){notice("导出失败："+ex.getMessage());}}}
    private String cell(String s){if(s.matches("(?s)^\\s*[=+@\\-].*")||s.matches("(?s)^[\\t\\r\\n].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
