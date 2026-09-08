package com.locintel.mpesabridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public final class BridgeClient {
    public static final String PREFS = "locintel_bridge";
    public static final String KEY_URL = "server_url";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_LAST = "last_status";
    private BridgeClient() {}

    public static String endpoint(Context ctx) { return base(ctx) + "/device_financing/mpesa/inbox"; }
    public static String base(Context ctx) {
        String base = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "").trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1); return base;
    }
    public static boolean configured(Context ctx) {
        SharedPreferences p=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return p.getString(KEY_URL,"").startsWith("https://")&&!p.getString(KEY_TOKEN,"").trim().isEmpty();
    }
    private static JSONObject post(String endpoint, String token, JSONObject json) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(endpoint).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        if(token!=null&&!token.isEmpty()) c.setRequestProperty("X-LOCINTEL-TOKEN",token);
        try(OutputStream os=c.getOutputStream()){os.write(json.toString().getBytes("UTF-8"));}
        int code=c.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),"UTF-8"));
        StringBuilder out=new StringBuilder(); String line; while((line=br.readLine())!=null)out.append(line); br.close();
        if(code<200||code>=300) throw new RuntimeException("HTTP "+code+" — "+out); return new JSONObject(out.toString());
    }
    public static String pair(Context ctx,String server,String pairingCode)throws Exception{
        String uid= Settings.Secure.getString(ctx.getContentResolver(),Settings.Secure.ANDROID_ID);
        JSONObject j=new JSONObject(); j.put("pairing_code",pairingCode); j.put("device_uid",uid); j.put("device_name", Build.MANUFACTURER+" "+Build.MODEL);
        JSONObject r=post(server.replaceAll("/$","")+"/device_financing/mpesa/pair","",j);
        if(!r.optBoolean("ok"))throw new RuntimeException(r.optString("error"));
        String token=r.getString("token"); ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_URL,server).putString(KEY_TOKEN,token).putString(KEY_LAST,"Téléphone associé par QR ✓").apply();
        return "Téléphone associé au serveur LOCINTEL ✓";
    }
    public static String send(Context ctx,String sender,String message,String packageName)throws Exception{
        if(!configured(ctx))throw new IllegalStateException("Associez d'abord le téléphone par QR ou configurez URL + jeton.");
        SharedPreferences p=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE); JSONObject j=new JSONObject(); j.put("source","android_notification"); j.put("sender",sender==null?packageName:sender); j.put("message",message); j.put("package",packageName);
        JSONObject r=post(endpoint(ctx),p.getString(KEY_TOKEN,""),j); String status="Odoo ✓ — "+r.toString(); p.edit().putString(KEY_LAST,status).apply(); return status;
    }
}
