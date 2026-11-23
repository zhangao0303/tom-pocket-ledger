package cn.tom.pocket;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.Arrays;

final class EntryEditor {
    interface Done {void run();}
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String s,int size){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(59,42,34));v.setPadding(0,dp(c,6),0,dp(c,6));return v;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);l.setPadding(dp(c,18),dp(c,14),dp(c,18),dp(c,14));l.setBackgroundColor(Color.rgb(255,248,240));return l;}
    static EditText input(LinearLayout l,String label,String value,int type){Context c=l.getContext();l.addView(text(c,label,13));EditText e=new EditText(c);e.setSingleLine();e.setText(value);e.setInputType(type);e.setTextSize(16);l.addView(e,new LinearLayout.LayoutParams(-1,dp(c,48)));return e;}
    static Spinner spinner(LinearLayout l,String label,String[] items,int index){Context c=l.getContext();l.addView(text(c,label,13));Spinner s=new Spinner(c);s.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,items));s.setSelection(Math.max(0,index));l.addView(s,new LinearLayout.LayoutParams(-1,dp(c,48)));return s;}
    static View create(Context c,Book.Entry entry,String hint,Done done){
        LinearLayout l=column(c);l.addView(text(c,"核对后再记下来喵",21));if(!hint.isEmpty())l.addView(text(c,hint,13));
        Spinner type=spinner(l,"收支类型",new String[]{"支出","收入"},entry.type.equals("income")?1:0);
        EditText amount=input(l,"金额（人民币元）",entry.cents>0?Book.money(entry.cents):"",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText date=input(l,"日期 YYYY-MM-DD",entry.date,InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);
        Spinner category=spinner(l,"分类",Book.CATEGORIES,Arrays.asList(Book.CATEGORIES).indexOf(entry.category));
        EditText note=input(l,"备注",entry.note,InputType.TYPE_CLASS_TEXT);note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        TextView error=text(c,"",13);error.setTextColor(Color.rgb(160,50,35));l.addView(error);
        LinearLayout buttons=new LinearLayout(c);Button cancel=new Button(c);cancel.setText("取消");Button save=new Button(c);save.setText("确认入账");buttons.addView(cancel,new LinearLayout.LayoutParams(0,dp(c,50),1));buttons.addView(save,new LinearLayout.LayoutParams(0,dp(c,50),1));l.addView(buttons);
        cancel.setOnClickListener(v->done.run());save.setOnClickListener(v->{try{Book.Entry e=new Book.Entry();e.id=entry.id;e.cents=Book.amount(amount.getText().toString().trim());e.date=date.getText().toString().trim();e.type=type.getSelectedItemPosition()==1?"income":"expense";e.category=(String)category.getSelectedItem();e.note=note.getText().toString().trim();Book.save(c,e);Toast.makeText(c,"记好啦喵！",Toast.LENGTH_SHORT).show();done.run();}catch(Exception ex){error.setText(ex.getMessage());}});
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.addView(l);return scroll;
    }
}
