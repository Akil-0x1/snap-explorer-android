package local.snapexplorer.mobile;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared phone display preferences, independent of imported user data. */
public final class AppStyle {
    public final int background,surface,ink,muted,accent;
    public final boolean light;
    public AppStyle(Context context){
        String mode=context.getSharedPreferences("display",0).getString("theme","system");
        light="light".equals(mode)||("system".equals(mode)&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES);
        background=Color.parseColor(light?"#F5F7F8":"#10191D");surface=Color.parseColor(light?"#FFFFFF":"#1E2A30");ink=Color.parseColor(light?"#17252B":"#EAF0F3");muted=Color.parseColor(light?"#63757E":"#9BAEB8");accent=Color.parseColor(light?"#147D70":"#70D6BF");
    }
    public static void apply(Activity activity){
        AppStyle style=new AppStyle(activity);activity.setTheme(style.light?android.R.style.Theme_Material_Light_NoActionBar:android.R.style.Theme_Material_NoActionBar);
    }
    public static int barFlags(Context context){return new AppStyle(context).light?android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;}
    public static void systemBars(Activity activity){AppStyle style=new AppStyle(activity);activity.getWindow().setStatusBarColor(style.background);activity.getWindow().setNavigationBarColor(style.background);activity.getWindow().getDecorView().setSystemUiVisibility(barFlags(activity));}
    public static String date(Context context,long epoch,String raw){
        if(epoch==0)return raw;
        String timezone=context.getSharedPreferences("display",0).getString("timezone","UTC");
        try{return DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss z",Locale.getDefault()).withZone(ZoneId.of(timezone)).format(Instant.ofEpochMilli(epoch));}
        catch(Exception ignored){return raw;}
    }
}
