// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal object NfcUploadReport {
    private val verdicts=setOf("not_observed","known_signature","inconclusive")
    private fun uuid(text:String)=text.also{require(UUID.fromString(it).toString()==it)}
    private fun time(text:String)=text.also{require(it.length<=40);Instant.parse(it)}
    fun clean(raw:String):JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size<=1048576){"Diagnoselog ist zu groß"}
        val source=JSONObject(raw)
        val allowed=setOf("schema","test_id","started_at","finished_at","timezone","master","peer","directions","verdict","patch_required","scope","probe_header","probe_length","reason")
        require(source.keys().asSequence().all{it in allowed}&&source.getString("schema")=="phoenix-nfc-suite-v1"){"Unbekanntes Diagnoseformat"}
        val result=JSONObject().put("schema","phoenix-nfc-suite-v1")
            .put("master",NfcTestMetadata.clean(source.getJSONObject("master")))
            .put("peer",NfcTestMetadata.clean(source.getJSONObject("peer")))
        if(source.has("test_id"))result.put("test_id",uuid(source.getString("test_id")))
        for(key in listOf("started_at","finished_at"))if(source.has(key))result.put(key,time(source.getString(key)))
        if(source.has("timezone"))result.put("timezone",ZoneId.of(source.getString("timezone")).id)
        val verdict=source.getString("verdict");require(verdict in verdicts)
        result.put("verdict",verdict).put("patch_required",JSONObject.NULL)
        if(source.has("reason"))result.put("reason","incomplete")
        if(source.has("probe_header")){require(source.getString("probe_header")=="0CA4040C"&&source.getInt("probe_length")==35);result.put("probe_header","0CA4040C").put("probe_length",35)}
        val directions=source.getJSONArray("directions");require(directions.length()<=6)
        result.put("directions",JSONArray().apply{for(index in 0 until directions.length()){
            val row=directions.getJSONObject(index);val kind=row.getString("kind");require(kind in listOf("epassport","eid","samsung"))
            val direction=row.getInt("direction");require(direction in 0..1)
            val state=row.getString("verdict");require(state in verdicts)
            val model=row.getString("card_model");require(model.length<=512&&model.none{it.isISOControl()})
            put(JSONObject().put("kind",kind).put("direction",direction).put("round",uuid(row.getString("round")))
                .put("finished_at",time(row.getString("finished_at"))).put("card_model",model).put("verdict",state)
                .put("reader",NfcTestSession.cleanReader(row.getJSONObject("reader")))
                .put("hce",NfcTestSession.cleanCard(row.getJSONObject("hce"))))
        }})
        return result
    }
}
