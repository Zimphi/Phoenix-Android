package de.phoenix.simulator;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.concurrent.*;

public final class CardService extends HostApduService {
    private static boolean active;
    private static long generation;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static synchronized void deactivate() { active=false; generation++; }
    static synchronized boolean active() { return active; }
    static synchronized void activate() { active=true; generation++; }
    @Override public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        final long session;
        synchronized(CardService.class) {
            if(!active) return new byte[]{0x69,(byte)0x85};
            session=generation;
        }
        if(apdu==null || apdu.length<4 || apdu.length>65544) return new byte[]{0x67,0};
        byte[] command=apdu.clone();
        worker.execute(()->{
            synchronized(CardService.class) { if(session!=generation || !active) return; }
            byte[] response;
            boolean failed=false;
            try { response=unhex(NativeCore.call(new JSONObject().put("op","apdu").put("command",hex(command))).getString("response")); }
            catch(Exception error) { response=new byte[]{0x6f,0}; failed=true; }
            synchronized(CardService.class) {
                if(session==generation && active) { sendResponseApdu(response); if(failed)deactivate(); }
            }
        });
        return null;
    }
    @Override public void onDeactivated(int reason) {
        // Preserve explicit arming, but never reuse cryptographic state across RF sessions.
        synchronized(CardService.class) { generation++; }
        worker.execute(()->{try { NativeCore.call(new JSONObject().put("op","reset")); } catch(Exception error) { deactivate(); }});
    }
    static String hex(byte[] input) { StringBuilder text=new StringBuilder(input.length*2); for(byte b:input) text.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return text.toString(); }
    static byte[] unhex(String text) {
        if(text.length()<4 || text.length()>131088 || text.length()%2!=0) throw new IllegalArgumentException("Ungültige Core-Antwort");
        byte[] result=new byte[text.length()/2];
        for(int i=0;i<result.length;i++) { int high=Character.digit(text.charAt(i*2),16),low=Character.digit(text.charAt(i*2+1),16); if(high<0 || low<0) throw new IllegalArgumentException("Ungültige Core-Antwort"); result[i]=(byte)(high*16+low); }
        return result;
    }
}
