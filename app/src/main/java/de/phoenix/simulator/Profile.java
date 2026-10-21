package de.phoenix.simulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Editable test-data draft; never passed off as a personalized card. */
final class Profile {
    final String id;
    final String kind;
    String name;
    String nativeName;
    String portraitSex="";
    String pkiInfrastructure="";
    String persosimSource="";
    String personalizationGeneration="";
    String documentNumber="";
    boolean signedFixture;
    boolean passportCatalog;
    boolean simulatable=true;
    String simulationError="";
    JSONObject ri;
    JSONObject baselineGroups;
    JSONObject groups;
    JSONObject settings;
    Profile(String id, String kind, String name, JSONObject groups) {
        this.id = id; this.kind = kind; this.name = name; this.nativeName=name; this.groups = groups;
        this.documentNumber=String.format(java.util.Locale.ROOT,"T%08X",id.hashCode());
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
        profile.ri=value.optJSONObject("ri");
        profile.baselineGroups=value.optJSONObject("baseline_groups");
        if(profile.baselineGroups!=null) {
            if(profile.baselineGroups.length()>22)throw new JSONException("Zu viele Ausgangs-Datengruppen");
            for(java.util.Iterator<String> it=profile.baselineGroups.keys();it.hasNext();) {
                String key=it.next();if(!key.matches("DG([1-9]|1[0-9]|2[0-2])")||profile.baselineGroups.getString(key).length()>65536)throw new JSONException("Ungültige Ausgangs-Datengruppe");
            }
        }
        profile.persosimSource=value.optString("persosim_source","");
        if(!profile.persosimSource.isEmpty()&&(!kind.equals("eid")||!profile.persosimSource.matches("[a-zA-Z0-9_-]{1,64}")))throw new JSONException("Ungültiges PersoSim-Ausgangsprofil");
        profile.nativeName=value.optString("native_name",name);
        if(profile.nativeName.isEmpty()||profile.nativeName.length()>120)throw new JSONException("Ungültiger interner Profilname");
        profile.pkiInfrastructure=value.optString("pki_infrastructure","profile");
        if(profile.pkiInfrastructure.isEmpty())profile.pkiInfrastructure="profile";
        if(!java.util.Arrays.asList("","profile","bsi","phoenix").contains(profile.pkiInfrastructure)||kind.equals("epassport")&&profile.pkiInfrastructure.equals("bsi"))throw new JSONException("Unbekannte PKI-Testinfrastruktur");
        profile.personalizationGeneration=value.optString("personalization_generation","");
        if(!profile.personalizationGeneration.isEmpty()&&!java.util.UUID.fromString(profile.personalizationGeneration).toString().equals(profile.personalizationGeneration))throw new JSONException("Ungültiger Personalisierungsstand");
        profile.documentNumber=value.optString("document_number",profile.documentNumber);
        if(!profile.documentNumber.matches("[A-Z0-9]{1,9}"))throw new JSONException("Ungültige Dokumentnummer");
        profile.portraitSex=value.optString("portrait_sex","");
        if(!profile.portraitSex.matches("M|F|"))throw new JSONException("Ungültige Porträtdarstellung");
        if(value.has("settings")) {
            JSONObject settings=value.getJSONObject("settings");
            boolean transport=settings.optBoolean("transport_pin",false),eidActive=settings.optBoolean("eid_active",true);
            if(kind.equals("epassport") && (transport || !eidActive)) throw new JSONException("eID-Einstellungen sind nur für eID verfügbar");
            boolean noPin=kind.equals("epassport")&&settings.getString("pin").isEmpty()&&settings.getString("puk").isEmpty();
            if(!settings.getString("can").matches("[0-9]{6}") || (!noPin&&!settings.getString("pin").matches(transport?"[0-9]{5}":"[0-9]{6}")) || (!noPin&&!settings.getString("puk").matches("[0-9]{10}"))) throw new JSONException("Ungültige Test-Zugangsdaten");
            int pinRetries=settings.optInt("pin_retries",3),pukUses=settings.optInt("puk_uses",10);
            if(pinRetries<0 || pinRetries>3 || pukUses<0 || pukUses>10) throw new JSONException("PIN-Versuche: 0–3; PUK-Nutzungen: 0–10");
            profile.settings=new JSONObject().put("can",settings.getString("can")).put("pin",settings.getString("pin")).put("puk",settings.getString("puk")).put("pin_retries",pinRetries).put("puk_uses",pukUses).put("transport_pin",transport).put("eid_active",eidActive);
        }
        profile.updateName();
        return profile;
    }
    String groupText(String key) {
        String raw=groups.optString(key,"");
        try {return raw.trim().startsWith("{")?new JSONObject(raw).optString("Text",""):raw;}
        catch(JSONException error){return "";}
    }
    void updateName() {
        if(kind.equals("eid")) {String surname=groupText("DG5").trim();name=surname.isEmpty()?"eID-Profil":surname.substring(0,Math.min(surname.length(),120));}
    }
    static String newSerial() {return String.format(java.util.Locale.ROOT,"T%08X",new java.security.SecureRandom().nextInt());}
    JSONObject json() throws JSONException {
        JSONObject value=coreDraft().put("groups",groups).put("name",name);
        if(!portraitSex.isEmpty())value.put("portrait_sex",portraitSex);
        if(kind.equals("eid"))value.put("document_number",documentNumber).put("native_name",nativeName);
        return value;
    }
    String documentType() {
        String raw=groups.optString("DG1","ID");
        try { if(raw.startsWith("{")) return new JSONObject(raw).optString("Text","ID"); }
        catch(JSONException ignored) { return raw; }
        return raw;
    }
    JSONObject coreDraft() throws JSONException {
        JSONObject effective=new JSONObject(groups.toString());
        if(kind.equals("eid")&&!java.util.Arrays.asList("AR","AS","AF").contains(documentType())) {
            effective.remove("DG19");effective.remove("DG20");
        }
        JSONObject draft=new JSONObject().put("format","phoenix-android-draft-v1").put("id",id).put("kind",kind).put("name",nativeName).put("groups",effective).put("settings",settings);
        if(ri!=null&&kind.equals("eid"))draft.put("ri",ri);
        if(baselineGroups!=null&&kind.equals("eid"))draft.put("baseline_groups",baselineGroups);
        if(!persosimSource.isEmpty())draft.put("persosim_source",persosimSource);
        if(!pkiInfrastructure.isEmpty())draft.put("pki_infrastructure",pkiInfrastructure);
        if(!personalizationGeneration.isEmpty())draft.put("personalization_generation",personalizationGeneration);
        return draft;
    }
    JSONObject editableCopy() throws JSONException {
        JSONObject copy=new JSONObject(json().toString());
        if(signedFixture)copy.put("persosim_source",id);
        copy.remove("native_name");copy.remove("document_number");copy.remove("ri");copy.remove("baseline_groups");copy.remove("pki_infrastructure");copy.remove("personalization_generation");
        copy.put("id",kind+"-copy-"+java.util.UUID.randomUUID());
        copy.put("name",name.substring(0,Math.min(name.length(),110))+" · Kopie");
        if(kind.equals("epassport"))copy.getJSONObject("settings").put("pin","123456").put("puk","1234567890");
        return copy;
    }
    String label() { return kind.equals("eid") ? "eID" : "ePassport"; }
    static JSONArray defaults() throws JSONException {
        JSONArray list=new JSONArray();
        JSONObject mrz=new JSONObject().put("surname","MUSTERMANN").put("given_names","ERIKA").put("issuing_state","D<<").put("nationality","D<<").put("document_number","TEST00001").put("date_of_birth","640812").put("date_of_expiry","301020").put("sex","F").put("optional_data","");
        list.put(new Profile("passport-erika","epassport","Erika Mustermann",new JSONObject().put("DG1",mrz.toString()).put("DG2","synthetic:erika")).json());
        list.put(new Profile("eid-max","eid","Max Mustermann",new JSONObject().put("DG4","MAX").put("DG5","MUSTERMANN").put("DG8","19900101").put("DG18","02761100000000")).json());
        return list;
    }
}
