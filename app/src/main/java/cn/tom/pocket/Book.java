package cn.tom.pocket;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The UI and foreground service live in one process; all read-modify-write operations share this lock. */
final class Book {
    static final String[] CATEGORIES={"餐饮","交通","购物","居住","娱乐","医疗","学习","工资","奖金","其他收入","其他支出"};
    static final class Entry {
        String id=UUID.randomUUID().toString(),date=LocalDate.now().toString(),note="",category="其他支出",type="expense";
        long cents;
        JSONObject json() throws Exception {JSONObject j=new JSONObject();j.put("id",id);j.put("date",date);j.put("note",note);j.put("category",category);j.put("type",type);j.put("cents",cents);return j;}
    }
    static synchronized List<Entry> read(Context c) throws Exception {
        JSONArray a=new JSONArray(c.getSharedPreferences("tom-native-book",0).getString("entries","[]"));List<Entry> result=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);Entry e=new Entry();e.id=j.getString("id");e.date=j.getString("date");e.note=j.getString("note");e.category=j.getString("category");e.type=j.getString("type");e.cents=j.getLong("cents");validate(e);result.add(e);}return result;
    }
    private static void store(Context c,List<Entry> list)throws Exception{JSONArray a=new JSONArray();for(Entry e:list)a.put(e.json());if(!c.getSharedPreferences("tom-native-book",0).edit().putString("entries",a.toString()).commit())throw new Exception("保存失败，可能存储空间不足。请重试。");}
    static synchronized void save(Context c,Entry entry)throws Exception{validate(entry);List<Entry> list=read(c);boolean found=false;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(entry.id)){list.set(i,entry);found=true;break;}if(!found)list.add(entry);store(c,list);}
    static synchronized void delete(Context c,String id)throws Exception{List<Entry> list=read(c);list.removeIf(e->e.id.equals(id));store(c,list);}
    static long amount(String s)throws Exception{if(!s.matches("\\d+(\\.\\d{1,2})?"))throw new Exception("金额请填写正数，最多两位小数。");try{long value=new BigDecimal(s).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();if(value<1||value>9999999999L)throw new Exception("金额范围为0.01至99,999,999.99元。");return value;}catch(ArithmeticException ex){throw new Exception("金额超出范围。");}}
    static void validate(Entry e)throws Exception{LocalDate d;try{d=LocalDate.parse(e.date);}catch(Exception ex){throw new Exception("日期请填写有效的 YYYY-MM-DD。");}if(d.getYear()<2000||d.getYear()>2100||!d.toString().equals(e.date))throw new Exception("日期范围为2000至2100年。");if(e.cents<1||e.cents>9999999999L)throw new Exception("金额超出范围。");if(!e.type.equals("income")&&!e.type.equals("expense"))throw new Exception("请选择收入或支出。");if(!java.util.Arrays.asList(CATEGORIES).contains(e.category))throw new Exception("请选择分类。");if(e.note.trim().isEmpty()||e.note.length()>200)throw new Exception("备注需要1至200字。");}
    static String money(long n){return String.format(java.util.Locale.CHINA,"%.2f",n/100.0);}
    static Entry parse(String text)throws Exception{
        Entry e=new Entry();e.note=text.trim();String rest=e.note;
        if(rest.matches(".*(美元|美金|欧元|港币|日元|英镑|[$€£]).*"))throw new Exception("当前账本仅使用人民币，请换算后手动填写。");
        Matcher date=Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}").matcher(rest);
        if(date.find()){String value=date.group();String[] parts=value.split("-");try{e.date=LocalDate.of(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])).toString();}catch(Exception ex){throw new Exception("日期无效，请手动填写。");}rest=rest.replace(value,"");if(Pattern.compile("\\d{4}-").matcher(rest).find())throw new Exception("请一次记一笔账。");}
        else if(rest.contains("前天")){e.date=LocalDate.now().minusDays(2).toString();rest=rest.replace("前天","");}
        else if(rest.contains("昨天")){e.date=LocalDate.now().minusDays(1).toString();rest=rest.replace("昨天","");}
        else if(Pattern.compile("\\d+月|\\d+[日号]|上周|上个月|去年|明天|后天|周[一二三四五六日天]").matcher(rest).find())throw new Exception("请在确认表单中手动选择日期。");
        if(Pattern.compile("退款|退回|报销|转账|借|还款|欠|余额|预算|打算|计划|准备|如果|没花|没有花|不记|不要记|[-−]\\s*\\d").matcher(rest).find())throw new Exception("收支方向或是否已发生还不明确，请手动填写。");
        Matcher m=Pattern.compile("\\d+(?:\\.\\d+)?").matcher(rest);String number=null;while(m.find()){if(number!=null)throw new Exception("识别到了多个数字，请一次记一笔，或手动填写。");number=m.group();}
        if(number==null){Matcher chinese=Pattern.compile("([零〇一二两三四五六七八九十百千万点]+)(?:块|元)").matcher(rest);if(chinese.find()){number=chineseNumber(chinese.group(1));if(chinese.find())throw new Exception("识别到了多个金额，请手动填写。");}}
        if(number==null)throw new Exception("没有识别到明确金额，可以在表单中填写。");e.cents=amount(number);
        boolean income=Pattern.compile("工资|薪水|奖金|收入|到账|赚了|收款|收到").matcher(rest).find();
        boolean expense=Pattern.compile("花|支出|消费|买|付|饭|餐|咖啡|奶茶|打车|地铁|公交|房租|水电|购物|电影|医院|看病|药|学费|书").matcher(rest).find();
        if(income&&Pattern.compile("支出|花了|消费|买了|付了").matcher(rest).find())throw new Exception("同时识别到收入和支出，请手动选择。");
        if(!income&&!expense)throw new Exception("请手动选择这笔是收入还是支出。");
        if(income){e.type="income";e.category=rest.matches(".*(工资|薪水).*")?"工资":rest.contains("奖金")?"奖金":"其他收入";}
        else{String[][] rules={{"餐饮","饭|餐|咖啡|奶茶|吃|外卖"},{"交通","打车|地铁|公交|车费|加油|高铁"},{"居住","房租|水电|物业"},{"医疗","医院|看病|药"},{"学习","学费|书|课程"},{"娱乐","电影|游戏|门票"},{"购物","买|购物|衣服|鞋"}};for(String[] rule:rules)if(Pattern.compile(rule[1]).matcher(rest).find()){e.category=rule[0];break;}}
        validate(e);return e;
    }
    static String chineseNumber(String text)throws Exception{
        String[] parts=text.split("点",-1);if(parts.length>2)throw new Exception("金额不明确，请手动填写。");long total=0,section=0,digit=0;String chars="零一二三四五六七八九";
        for(char ch:parts[0].toCharArray()){int n=chars.indexOf(ch);if(ch=='两')n=2;if(ch=='〇')n=0;if(n>=0){digit=n;continue;}int unit=ch=='十'?10:ch=='百'?100:ch=='千'?1000:ch=='万'?10000:0;if(unit==0)throw new Exception("金额不明确。");if(unit==10000){section+=digit;total+=section*unit;section=0;}else section+=(digit==0?1:digit)*unit;digit=0;}
        String result=Long.toString(total+section+digit);if(parts.length==2){if(parts[1].isEmpty()||parts[1].length()>2)throw new Exception("小数金额请手动填写，最多两位。");StringBuilder decimals=new StringBuilder();for(char ch:parts[1].toCharArray()){int n=chars.indexOf(ch);if(ch=='〇')n=0;if(n<0)throw new Exception("小数金额不明确。");decimals.append(n);}result+="."+decimals;}return result;
    }
}
