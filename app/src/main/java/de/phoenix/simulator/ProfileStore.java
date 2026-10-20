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
            try(InputStream input=file.openRead()) { list=new JSONArray(read(input)); }
        } else list=Profile.defaults();
        for(int i=0;i<list.length();i++) profiles.add(Profile.parse(list.getJSONObject(i)));
        boolean present=false;
        for(Profile profile:profiles) if(profile.id.equals("eid-persosim03")) present=true;
        if(!present && profiles.size()<100) {
            try(InputStream input=context.getAssets().open("persosim03-draft.json")) {
                importDraft(read(input));
            }
        }
    }
    static String read(InputStream input) throws IOException {
        if(input==null) throw new IOException("Datei nicht lesbar");
        ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int size;
        while((size=input.read(buffer))!=-1) {
            if(output.size()+size>1024*1024) throw new IOException("Profil überschreitet 1 MiB");
            output.write(buffer,0,size);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
    void save() throws Exception {
        JSONArray list=new JSONArray(); for(Profile profile:profiles) list.put(profile.json());
        byte[] bytes=list.toString(2).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>1024*1024) throw new IOException("Profilspeicher überschreitet 1 MiB");
        FileOutputStream stream=file.startWrite();
        try { stream.write(bytes); file.finishWrite(stream); }
        catch(Exception error) { file.failWrite(stream); throw error; }
    }
    Profile importDraft(String source) throws Exception {
        Profile imported=Profile.parse(new JSONObject(source));
        if(profiles.size()>=100) throw new IOException("Maximal 100 Profile");
        for(Profile profile:profiles) if(profile.id.equals(imported.id)) throw new IOException("Profil-ID bereits vorhanden; bestehendes Profil bleibt erhalten");
        profiles.add(imported);
        try { save(); } catch(Exception error) { profiles.remove(imported); throw error; }
        return imported;
    }
}
