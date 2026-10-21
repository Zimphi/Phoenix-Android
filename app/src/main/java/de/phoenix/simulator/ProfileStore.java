package de.phoenix.simulator;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class ProfileStore {
    private final AtomicFile file;
    final List<Profile> profiles = new ArrayList<>();
    ProfileStore(Context context) throws Exception {
        file=new AtomicFile(new File(context.getFilesDir(),"profile-drafts.json"));
        JSONArray list;
        if (file.getBaseFile().exists()) {
            try(InputStream input=file.openRead()) { list=new JSONArray(read(input,16*1024*1024)); }
        } else list=Profile.defaults();
        if(list.length()>200)throw new IOException("Zu viele gespeicherte Profile");
        for(int i=0;i<list.length();i++) profiles.add(Profile.parse(list.getJSONObject(i)));
        boolean changed=false;
        JSONObject supplied=NativeCore.call(new JSONObject().put("op","passport_catalog"));
        Set<String> retired=new HashSet<>();
        JSONArray retiredIds=supplied.getJSONArray("retired_ids");
        for(int i=0;i<retiredIds.length();i++)retired.add(retiredIds.getString(i));
        JSONArray removed=new JSONArray();
        for(Profile profile:profiles)if(profile.kind.equals("epassport")&&retired.contains(profile.id))removed.put(profile.json());
        if(removed.length()>0) {
            AtomicFile archive=new AtomicFile(new File(context.getFilesDir(),"retired-passport-drafts.json"));
            JSONArray previous;
            if(archive.getBaseFile().exists())try(InputStream input=archive.openRead()){previous=new JSONArray(read(input,16*1024*1024));}
            else previous=new JSONArray();
            Set<String> recorded=new HashSet<>();
            for(int i=0;i<previous.length();i++)recorded.add(previous.getJSONObject(i).toString());
            for(int i=0;i<removed.length();i++)if(recorded.add(removed.getJSONObject(i).toString()))previous.put(removed.getJSONObject(i));
            write(archive,previous);
            profiles.removeIf(profile->profile.kind.equals("epassport")&&retired.contains(profile.id));
            changed=true;
        }
        JSONArray passports=supplied.getJSONArray("profiles");
        for(int i=0;i<passports.length();i++) {
            JSONObject draft=passports.getJSONObject(i);
            Profile found=null;
            for(Profile profile:profiles)if(profile.id.equals(draft.getString("id")))found=profile;
            if(found!=null&&!original(found,draft)) {
                Profile preserved=Profile.parse(found.json().put("id","own-"+UUID.randomUUID()));
                profiles.set(profiles.indexOf(found),preserved);found=null;changed=true;
            }
            if(found==null){if(profiles.size()>=200)throw new IOException("Maximal 100 Profile");found=Profile.parse(draft);profiles.add(found);changed=true;}
            found.passportCatalog=true;found.simulatable=draft.getBoolean("simulatable");found.simulationError=draft.optString("simulation_error","");
        }
        JSONArray catalog;
        try(InputStream input=context.getAssets().open("persosim-profiles.json")) { catalog=new JSONArray(read(input)); }
        for(int i=0;i<catalog.length();i++) {
            JSONObject draft=catalog.getJSONObject(i);
            boolean present=false;
            for(Profile profile:profiles) if(profile.id.equals(draft.getString("id"))) { profile.signedFixture=true; present=true; }
            if(!present && profiles.size()<200) {Profile profile=Profile.parse(draft);profile.signedFixture=true;profiles.add(profile);changed=true;}
        }
        if(changed)save();
    }
    private static boolean original(Profile profile,JSONObject draft) throws JSONException {
        JSONObject groups=draft.getJSONObject("groups"),settings=draft.getJSONObject("settings");
        if(profile.groups.length()!=groups.length())return false;
        for(Iterator<String> it=groups.keys();it.hasNext();) {String key=it.next();if(!groups.getString(key).equals(profile.groups.optString(key)))return false;}
        for(String key:new String[]{"can","pin","puk"})if(!settings.getString(key).equals(profile.settings.getString(key)))return false;
        return true;
    }
    int ownCount(){int count=0;for(Profile profile:profiles)if(!profile.signedFixture&&!profile.passportCatalog)count++;return count;}
    static String read(InputStream input) throws IOException {
        return read(input,1024*1024);
    }
    private static String read(InputStream input,int limit) throws IOException {
        if(input==null) throw new IOException("Datei nicht lesbar");
        ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int size;
        while((size=input.read(buffer))!=-1) {
            if(output.size()+size>limit) throw new IOException("Profil überschreitet 1 MiB");
            output.write(buffer,0,size);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
    void save() throws Exception {
        JSONArray list=new JSONArray(); for(Profile profile:profiles) list.put(profile.json());
        write(file,list);
    }
    private static void write(AtomicFile file,JSONArray list) throws Exception {
        byte[] bytes=list.toString(2).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>16*1024*1024) throw new IOException("Profilspeicher überschreitet 16 MiB");
        FileOutputStream stream=file.startWrite();
        try { stream.write(bytes); file.finishWrite(stream); }
        catch(Exception error) { file.failWrite(stream); throw error; }
    }
    static byte[] exportDraft(Profile profile) throws Exception {
        JSONObject draft=profile.json();
        Profile.parse(draft);
        byte[] bytes=draft.toString(2).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>1024*1024)throw new IOException("Profil überschreitet 1 MiB");
        return bytes;
    }
    Profile importDraft(String source) throws Exception {
        Profile imported=Profile.parse(new JSONObject(source));
        if(ownCount()>=100) throw new IOException("Maximal 100 eigene Profile");
        for(Profile profile:profiles) if(profile.id.equals(imported.id)) throw new IOException("Profil-ID bereits vorhanden; bestehendes Profil bleibt erhalten");
        profiles.add(imported);
        try { save(); } catch(Exception error) { profiles.remove(imported); throw error; }
        return imported;
    }
}
