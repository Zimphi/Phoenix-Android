package de.phoenix.simulator;

import org.json.JSONObject;

/** JNI transport only. All card behavior belongs to the separately licensed Rust cores. */
final class NativeCore {
    static final boolean available;
    static { boolean loaded; try { System.loadLibrary("phoenix_android"); loaded=true; } catch(UnsatisfiedLinkError error) { loaded=false; } available=loaded; }
    private static native String exchange(String request);
    private static native byte[] challenge();
    private static native String initializeNative(android.content.Context context, byte[] publicKey, byte[] proof, String root);
    private static boolean initialized;
    static synchronized void initialize(android.content.Context context, byte[] publicKey, byte[] proof) throws Exception {
        if(!available) throw new IllegalStateException("Native Core-Bibliothek fehlt");
        if(initialized) return;
        java.io.File directory=new java.io.File(context.getFilesDir(),"native");
        if(!directory.isDirectory()&&!directory.mkdirs()) throw new java.io.IOException("Lokaler Core-Speicher nicht verfügbar");
        JSONObject response=new JSONObject(initializeNative(context,publicKey,proof,directory.getAbsolutePath()));
        if(response.has("error")) throw new IllegalStateException(response.getString("error"));
        initialized=true;
    }
    static byte[] bindingChallenge(){return challenge();}
    static synchronized JSONObject call(JSONObject request) throws Exception {
        if(!available) throw new IllegalStateException("Native Core-Bibliothek fehlt. Simulation ist nicht verfügbar.");
        JSONObject response=new JSONObject(exchange(request.toString()));
        if(response.has("error")) throw new IllegalStateException(response.getString("error"));
        return response;
    }
}
