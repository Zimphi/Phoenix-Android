package de.phoenix.simulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Editable test-data draft; never passed off as a personalized card. */
final class Profile {
    final String id;
    final String kind;
    String name;
    JSONObject groups;
    JSONObject settings;
    Profile(String id, String kind, String name, JSONObject groups) {
        this.id = id; this.kind = kind; this.name = name; this.groups = groups;
        try { this.settings=new JSONObject().put("can","123456").put("pin","123456").put("puk","1234567890"); }
        catch(JSONException error) { throw new IllegalStateException(error); }
    }
    static Profile parse(JSONObject value) throws JSONException {
        if (!"phoenix-android-draft-v1".equals(value.getString("format"))) throw new JSONException("Kein Phoenix-Profilentwurf");
        String id=value.getString("id"), kind=value.getString("kind"), name=value.getString("name").trim();
        if (!id.matches("[a-zA-Z0-9_-]{1,64}") || !(kind.equals("eid") || kind.equals("epassport")) || name.isEmpty() || name.length()>120) throw new JSONException("Ungültige Profildaten");
        JSONObject groups=value.getJSONObject("groups");
        for (java.util.Iterator<String> it=groups.keys(); it.hasNext();) {
            String key=it.next();
            if (!key.matches("DG([1-9]|1[0-9]|2[0-2])") || groups.getString(key).length()>65536) throw new JSONException("Ungültige Datengruppe");
        }
        if (groups.length()>22) throw new JSONException("Zu viele Datengruppen");
        Profile profile=new Profile(id,kind,name,groups);
        if(value.has("settings")) {
            JSONObject settings=value.getJSONObject("settings");
            if(!settings.getString("can").matches("[0-9]{6}") || !settings.getString("pin").matches("[0-9]{6}") || !settings.getString("puk").matches("[0-9]{10}")) throw new JSONException("Ungültige Test-Zugangsdaten");
            profile.settings=new JSONObject().put("can",settings.getString("can")).put("pin",settings.getString("pin")).put("puk",settings.getString("puk"));
        }
        return profile;
    }
    JSONObject json() throws JSONException {
        return new JSONObject().put("format","phoenix-android-draft-v1").put("id",id).put("kind",kind).put("name",name).put("groups",groups).put("settings",settings);
    }
    String label() { return kind.equals("eid") ? "eID" : "ePassport"; }
    static JSONArray defaults() throws JSONException {
        JSONArray list=new JSONArray();
        JSONObject mrz=new JSONObject().put("surname","MUSTERMANN").put("given_names","ERIKA").put("issuing_state","D<<").put("nationality","D<<").put("document_number","TEST00001").put("date_of_birth","640812").put("date_of_expiry","301020").put("sex","F").put("optional_data","");
        list.put(new Profile("passport-erika","epassport","Erika Mustermann",new JSONObject().put("DG1",mrz.toString()).put("DG2","synthetic:erika")).json());
        list.put(new Profile("eid-max","eid","Max Mustermann",new JSONObject().put("DG4","MAX").put("DG5","MUSTERMANN").put("DG8","19900101")).json());
        return list;
    }
}
