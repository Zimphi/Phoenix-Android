package de.phoenix.simulator

import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

private val DocumentInk=Color(0xff172330)
private fun DrawScope.documentText(value:String,x:Float,y:Float,font:Float,bold:Boolean=false,mono:Boolean=false,width:Float=720f-x-24f,color:Color=DocumentInk,fit:Boolean=false) {
    val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize=font;setColor(color.toArgb())
        typeface=Typeface.create(if(mono)"monospace" else "sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    }
    if(fit&&paint.measureText(value)>width)paint.textSize=font*width.coerceAtLeast(1f)/paint.measureText(value)
    val text=TextUtils.ellipsize(value.take(256),paint,width.coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
    drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)
}
private fun DrawScope.documentField(label:String,value:String,x:Float,y:Float,width:Float=400f,font:Float=16f,valueOffset:Float=32f,labelFont:Float=14f) {
    documentText(label,x,y,labelFont,width=width,color=Color(0xff4a5651),fit=true)
    documentText(value.ifBlank{"—"}.uppercase(),x,y+valueOffset,font,bold=true,width=width)
}
/** Fixed document coordinates keep the artwork proportional to the reference. */
internal fun DrawScope.passportDocument(country:String,code:String,surname:String,given:String,number:String,birth:String,sex:String,nationality:String,expiry:String,mrz:String,watermark:ImageBitmap?=null,paper:ImageBitmap?=null) {
    val outline=Path().apply{addRoundRect(RoundRect(0f,0f,720f,550f,CornerRadius(10f)))}
    clipPath(outline) {
        if(paper!=null)drawImage(paper,dstSize=IntSize(720,550),filterQuality=FilterQuality.High)
        else drawRect(Color(0xfffaf8ef),size=Size(720f,550f))
    }
    drawRoundRect(Color(0xffc5cbbd),Offset(.8f,.8f),Size(718.4f,548.4f),CornerRadius(10f),style=Stroke(1f))
    if(watermark!=null)phoenixBird(watermark,Offset(29f,17f),Size(52f,55f),.85f)
    documentText(country.uppercase(),105f,45f,25f,true,width=407f,color=Color(0xff223b57))
    documentText("REISEPASS",548f,39f,21f,true,width=154f,color=Color(0xff223b57))
    documentText("P A S S P O R T",548f,57f,9f,width=154f,color=Color(0xff223b57))
    documentField("Typ / Type","P",264f,91f,70f,19f,valueOffset=26f,labelFont=12f)
    documentField("Kode / Code",code,365f,91f,105f,19f,valueOffset=26f,labelFont=12f)
    documentField("Pass-Nr. / Passport No.",number,508f,91f,190f,19f,valueOffset=26f,labelFont=12f)
    documentField("1. Name / Surname",surname,264f,139f,433f,23f,valueOffset=26f,labelFont=12f)
    documentField("2. Vornamen / Given names",given,264f,184f,433f,23f,valueOffset=26f,labelFont=12f)
    documentField("3. Geburtsdatum / Date of birth",birth,264f,236f,178f,21f,valueOffset=26f,labelFont=12f)
    documentField("4. Geschlecht / Sex",sex,447f,236f,100f,21f,valueOffset=26f,labelFont=12f)
    documentField("5. Staatsangehörigkeit",nationality,554f,236f,144f,21f,valueOffset=26f,labelFont=12f)
    documentField("6. Geburtsort / Place of birth","—",264f,286f,433f,21f,valueOffset=26f,labelFont=12f)
    documentField("7. Ausgestellt am / Date of issue","—",264f,335f,214f,20f,valueOffset=26f,labelFont=12f)
    documentField("8. Gültig bis / Date of expiry",expiry,491f,335f,207f,20f,valueOffset=26f,labelFont=12f)
    documentField("9. Behörde / Authority","—",264f,384f,433f,19f,valueOffset=26f,labelFont=12f)
    documentText("SYNTHETISCHES TESTDOKUMENT",25f,416f,7f,width=225f,color=Color(0xff5c6864))
    if(mrz.length==88) {
        documentText(mrz.take(44),27f,478f,24.5f,mono=true,width=666f)
        documentText(mrz.drop(44),27f,510f,24.5f,mono=true,width=666f)
    }
}

internal fun DrawScope.identityDocument(surname:String="MUSTERMANN",given:String="ERIKA",birth:String="12.08.83",nationality:String="D",expiry:String="01.08.31",place:String="BERLIN",can:String="123456",country:String="BUNDESREPUBLIK DEUTSCHLAND",birthName:String="",portrait:ImageBitmap?=null,watermark:ImageBitmap?=null,paper:ImageBitmap?=null,number:String="TESTDOKUMENT") {
    val outline=Path().apply{addRoundRect(RoundRect(0f,0f,856f,540f,CornerRadius(20f)))}
    clipPath(outline) {
        if(paper!=null)drawImage(paper,dstSize=IntSize(856,540),filterQuality=FilterQuality.High)
        else drawRect(Color(0xfffaf8ef),size=Size(856f,540f))
    }
    drawRoundRect(Color(0xffaab7a8),Offset(1f,1f),Size(854f,538f),CornerRadius(20f),style=Stroke(1.3f))
    drawRect(Color(0xff334b89),Offset(26f,22f),Size(102f,64f))
    for(i in 0..11) {
        val angle=i*Math.PI/6
        drawCircle(Color(0xffebd674),2.4f,Offset(77f+25f*kotlin.math.sin(angle).toFloat(),54f+22f*kotlin.math.cos(angle).toFloat()))
    }
    documentText(country.uppercase(),149f,42f,25f,true,width=530f,color=Color(0xff223b57))
    documentText("P E R S O N A L A U S W E I S",149f,70f,17f,width=450f,color=Color(0xff223b57))
    documentText(number,685f,42f,20f,mono=true,width=149f,color=Color(0xff46615e))
    translate(26f,101f){identityPortrait(302f,388f,portrait)}
    drawRect(Color(0xffa4b2aa).copy(alpha=.45f),Offset(26f,101f),Size(302f,388f),style=Stroke(.7f))
    documentField("Name/Surname/Nom",surname,350f,114f,476f,27f)
    val hasBirthName=birthName.isNotBlank()&&birthName!="—"
    if(hasBirthName)documentField("Geburtsname/Name at birth/Nom de naissance",birthName,350f,174f,476f,25f)
    val givenY=if(hasBirthName)248f else 180f
    documentField("Vornamen/Given names/Prénoms",given,350f,givenY,476f,27f)
    documentField("Geburtstag/Date of birth",birth,350f,givenY+62f,204f,25f)
    documentField("Staatsangehörigkeit/Nationality",nationality,568f,givenY+62f,258f,25f)
    documentField("Geburtsort/Place of birth",place,350f,givenY+124f,476f,26f)
    documentField("Gültig bis/Date of expiry",expiry,350f,givenY+198f,258f,25f)
    documentText(can,666f,486f,31f,mono=true,width=161f)
    documentText("SYNTHETISCHES TESTDOKUMENT",26f,520f,9f,width=700f,color=Color(0xff657b70))
}

internal fun DrawScope.identityBack(groups:org.json.JSONObject,paper:ImageBitmap,countries:List<CountryCode>,mrz:String) {
    val outline=Path().apply{addRoundRect(RoundRect(0f,0f,856f,540f,CornerRadius(20f)))}
    clipPath(outline){drawImage(paper,dstSize=IntSize(856,540),filterQuality=FilterQuality.High)}
    drawRoundRect(Color(0xffaab7a8),Offset(1f,1f),Size(854f,538f),CornerRadius(20f),style=Stroke(1f))
    documentField("Augenfarbe/Colour of eyes","—",28f,38f,326f,27f)
    documentField("Größe/Height","—",28f,106f,326f,27f)
    documentField("Datum/Date",passportDate(groupValue("eid","DG15",groups.optString("DG15"))),28f,174f,326f,27f)
    documentField("Behörde/Authority","—",28f,242f,326f,27f)
    documentText("Anschrift/Address/Adresse",386f,38f,15f,true,width=442f)
    val raw=groups.optString("DG17")
    val places=structuredPlaces(runCatching{org.json.JSONObject(raw)}.getOrNull())
    val address=places.firstOrNull()?.let{place->
        listOf(place.optString("street").takeUnless{it=="null"}?:"",listOf(place.optString("zipcode").takeUnless{it=="null"}?:"",place.optString("city")).filter{it.isNotBlank()}.joinToString(" "),countries.firstOrNull{it.hasCode(place.optString("country"))}?.name?:place.optString("country"))
    }?:listOf(groupValue("eid","DG17",raw))
    address.forEachIndexed{index,line->documentText(line.uppercase(),386f,72f+index*35f,27f,width=442f)}
    documentField("Ordens- oder Künstlername",groupValue("eid","DG6",groups.optString("DG6")).takeUnless{it=="Keine Angabe"}?:"—",386f,213f,442f,25f)
    identityMrzLines(mrz)
    documentText("SYNTHETISCHES TESTDOKUMENT",28f,523f,9f,width=800f)
}

private fun DrawScope.identityMrzLines(mrz:String) {
    if(!mrz.matches(Regex("[A-Z0-9<]{30}\\n[A-Z0-9<]{30}\\n[A-Z0-9<]{30}"))){documentText("MRZ wird aus vollständigen Profildaten berechnet",28f,446f,13f,width=800f);return}
    mrz.lines().forEachIndexed{index,line->
        val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textSize=34f;typeface=Typeface.MONOSPACE;color=DocumentInk.toArgb();textScaleX=800f/measureText(line)}
        drawContext.canvas.nativeCanvas.drawText(line,28f,416f+index*38f,paint)
    }
}
private fun DrawScope.identityPaper(paper:ImageBitmap,cool:Boolean=false,horizontalOffset:Int=0) {
    clipPath(Path().apply{addRoundRect(RoundRect(0f,0f,856f,540f,CornerRadius(20f)))}) {
        drawRect(Color.White,size=Size(856f,540f))
        if(horizontalOffset>0)drawImage(paper,srcSize=IntSize(paper.width*horizontalOffset/856,paper.height),dstSize=IntSize(horizontalOffset,540))
        drawImage(paper,dstOffset=androidx.compose.ui.unit.IntOffset(horizontalOffset,0),dstSize=IntSize(856,540),alpha=if(cool).35f else 1f)
        if(cool){drawRect(Color(0xffe4f7ff).copy(alpha=.3f),size=Size(856f,540f));for(i in -54..120)drawLine(Color(0xff5fc1ec).copy(alpha=.17f),Offset(i*12f,0f),Offset(i*12f+540f,540f),.7f)}
    }
    drawRoundRect(Color(0xffabc5d2),Offset(1f,1f),Size(854f,538f),CornerRadius(20f),style=Stroke(1f))
}
internal fun DrawScope.unionDocument(profile:Profile,groups:org.json.JSONObject,paper:ImageBitmap,logo:ImageBitmap,back:Boolean) {
    identityPaper(paper,true)
    val cyan=Color(0xff079dce)
    if(back) {
        documentField("GEBURTSORT / PLACE OF BIRTH",profileField(profile,groups,"birth_place"),494f,75f,330f,25f)
        documentField("AUSSTELLENDE BEHÖRDE","—",494f,176f,330f,25f)
        documentText("KEIN REISEDOKUMENT",130f,444f,38f,true,width=700f,color=Color(0xff236887))
        documentText("NOT VALID FOR TRAVEL",268f,477f,20f,width=540f,color=Color(0xff236887))
    }else {
        phoenixBird(logo,Offset(22f,18f),Size(72f,92f))
        documentText("BUNDESREPUBLIK DEUTSCHLAND",117f,39f,21f,true,width=525f,color=cyan)
        documentText("eID-KARTE",117f,78f,21f,true,width=420f,color=cyan)
        documentText(profile.documentNumber,650f,42f,24f,mono=true,width=180f)
        documentText("eID",35f,315f,158f,true,width=297f,color=Color(0xff00689c))
        documentField("NAMEN / Vornamen",profileField(profile,groups,"surname"),353f,137f,467f,26f)
        documentText(profileField(profile,groups,"given_names").uppercase(),353f,203f,26f,width=467f)
        documentField("GEBURTSDATUM",passportDate(profileField(profile,groups,"date_of_birth")),353f,265f,245f,25f)
        documentField("GÜLTIG BIS",passportDate(profileField(profile,groups,"date_of_expiry")),620f,265f,212f,25f)
        documentText("eID-KARTE FÜR UNIONSBÜRGERINNEN /",353f,365f,16f,width=472f)
        documentText("UNIONSBÜRGER UND ANGEHÖRIGE DES",353f,391f,16f,width=472f)
        documentText("EUROPÄISCHEN WIRTSCHAFTSRAUMS",353f,417f,16f,width=472f)
        documentText(profile.settings.optString("can"),650f,488f,31f,mono=true,width=180f)
    }
    documentText("SYNTHETISCHES TESTDOKUMENT",28f,523f,9f,width=800f)
}
internal fun DrawScope.permitDocument(profile:Profile,groups:org.json.JSONObject,paper:ImageBitmap,portrait:ImageBitmap?,mrz:String,back:Boolean,countries:List<CountryCode>) {
    identityPaper(paper,horizontalOffset=if(back)0 else 148)
    if(back) {
        documentField("Ausstellungsdatum",passportDate(groupValue("eid","DG15",groups.optString("DG15"))),28f,47f,350f,25f)
        documentField("Ausstellende Behörde","—",28f,116f,350f,25f)
        val places=structuredPlaces(runCatching{org.json.JSONObject(groups.optString("DG17"))}.getOrNull())
        val place=places.firstOrNull()
        documentField("Anschrift",place?.optString("street")?:groupValue("eid","DG17",groups.optString("DG17")),494f,47f,334f,23f)
        documentText(listOf(place?.optString("zipcode")?:"",place?.optString("city")?:"").joinToString(" "),494f,108f,24f,width=334f)
        documentText(countries.firstOrNull{it.hasCode(place?.optString("country")?:"")}?.name?:place?.optString("country")?:"",494f,140f,20f,width=334f)
        documentField("Aufenthaltstitel I",groupValue("eid","DG19",groups.optString("DG19")),28f,218f,790f,25f)
        documentField("Aufenthaltstitel II",groupValue("eid","DG20",groups.optString("DG20")),28f,290f,790f,25f)
        identityMrzLines(mrz)
    }else {
        documentText("D",28f,49f,37f,true,width=60f,color=Color(0xff356188))
        documentText("AUFENTHALTSTITEL",238f,45f,26f,true,width=400f)
        documentText(profile.documentNumber,650f,44f,24f,mono=true,width=180f)
        translate(26f,96f){identityPortrait(270f,388f,portrait)}
        documentField("Namen / Vornamen",profileField(profile,groups,"surname"),323f,112f,499f,26f)
        documentText(profileField(profile,groups,"given_names").uppercase(),323f,179f,26f,width=499f)
        documentField("Geschlecht",groups.optString("DG11"),323f,220f,110f,24f)
        documentField("Staatsangehörigkeit",profileField(profile,groups,"nationality"),451f,220f,190f,24f)
        documentField("Geburtsdatum",passportDate(profileField(profile,groups,"date_of_birth")),659f,220f,169f,23f)
        documentField("Art des Titels",groupValue("eid","DG19",groups.optString("DG19")),323f,291f,499f,24f)
        documentField("Karte gültig bis",passportDate(profileField(profile,groups,"date_of_expiry")),323f,359f,499f,24f)
        documentField("Anmerkungen",groupValue("eid","DG20",groups.optString("DG20")),323f,427f,499f,22f)
        documentText(profile.settings.optString("can"),650f,496f,30f,mono=true,width=180f)
    }
    documentText("SYNTHETISCHES TESTDOKUMENT",28f,523f,9f,width=800f)
}
