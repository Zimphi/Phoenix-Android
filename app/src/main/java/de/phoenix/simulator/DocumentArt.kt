package de.phoenix.simulator

import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

private val DocumentInk=Color(0xff26302f)
private fun DrawScope.documentText(value:String,x:Float,y:Float,font:Float,bold:Boolean=false,mono:Boolean=false,width:Float=720f-x-24f,color:Color=DocumentInk) {
    val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize=font;setColor(color.toArgb())
        typeface=Typeface.create(if(mono)"monospace" else "sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    }
    val text=TextUtils.ellipsize(value.take(256),paint,width.coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
    drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)
}
private fun DrawScope.documentField(label:String,value:String,x:Float,y:Float,width:Float=400f,font:Float=16f) {
    documentText(label,x,y,9f,width=width,color=Color(0xff4a5651))
    documentText(value.uppercase(),x,y+22f,font,mono=true,width=width)
}
private fun DrawScope.securityLines(width:Float,height:Float) {
    clipRect(0f,0f,width,height) {
    for(index in 0..25) {
        val path=Path().apply {
            moveTo(-20f,height*.22f+index*height*.024f)
            cubicTo(width*.3f,-height*.16f+index*height*.026f,width*.62f,height*1.16f-index*height*.014f,width+20f,height*.17f+index*height*.024f)
        }
        drawPath(path,Color(0xff7f9d94).copy(alpha=.16f),style=Stroke(.6f))
    }
    }
}

/** Fixed document coordinates keep the artwork proportional to the reference. */
internal fun DrawScope.passportDocument(country:String,code:String,surname:String,given:String,number:String,birth:String,sex:String,nationality:String,expiry:String,mrz:String) {
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xffedf0e6),Color(0xfff4eee0),Color(0xffeadfcb))),size=Size(720f,507f),cornerRadius=CornerRadius(8f))
    securityLines(720f,507f)
    drawRoundRect(Color(0xffc5cbbd),Offset(.8f,.8f),Size(718.4f,505.4f),CornerRadius(8f),style=Stroke(1.2f))
    drawCircle(DocumentInk,20f,Offset(45f,40f),style=Stroke(1.4f))
    drawOval(DocumentInk,Offset(34f,20f),Size(22f,40f),style=Stroke(.8f))
    drawLine(DocumentInk,Offset(25f,40f),Offset(65f,40f),.8f)
    documentText(country.uppercase(),80f,33f,16f,true,width=440f)
    documentText("SYNTHETISCHES TESTDOKUMENT",80f,49f,7f,width=430f)
    documentText("REISEPASS",554f,31f,18f,true,width=142f)
    documentText("PASSPORT / PASSEPORT",554f,47f,7f,width=142f)
    documentField("Typ / Type", "P",234f,79f,65f,18f)
    documentField("Kode / Code",code,335f,79f,70f,18f)
    documentField("Pass-Nr. / Passport No.",number,455f,79f,238f,20f)
    documentField("1. Name / Surname",surname,234f,137f,458f,21f)
    documentField("2. Vornamen / Given names",given,234f,193f,458f,20f)
    documentField("3. Geburtstag / Date of birth",birth,234f,254f,190f,19f)
    documentField("4. Geschlecht / Sex",sex,434f,254f,110f,19f)
    documentField("5. Staatsangehörigkeit / Nationality",nationality,550f,254f,145f,18f)
    documentField("8. Gültig bis / Date of expiry",expiry,234f,320f,265f,19f)
    documentText("TEST",550f,366f,38f,true,width=140f,color=Color(0xff54776b).copy(alpha=.18f))
    drawRect(Color(0xfff7f3e7).copy(alpha=.6f),Offset(12f,411f),Size(696f,84f))
    if(mrz.length==88) {
        documentText(mrz.take(44),27f,445f,25f,mono=true,width=666f)
        documentText(mrz.drop(44),27f,480f,25f,mono=true,width=666f)
    }
}

internal fun DrawScope.identityDocument(surname:String="MUSTERMANN",given:String="ERIKA",birth:String="12.08.83",nationality:String="D",expiry:String="01.08.31",place:String="BERLIN",can:String="123456",country:String="BUNDESREPUBLIK DEUTSCHLAND") {
    val outline=Path().apply{addRoundRect(RoundRect(0f,0f,856f,540f,CornerRadius(20f)))}
    clipPath(outline) {
        drawRect(Brush.linearGradient(listOf(Color(0xffeee9d9),Color(0xfff5f1e7),Color(0xffe0e8d9))),size=Size(856f,540f))
        drawOval(Brush.radialGradient(listOf(Color(0xffdab7b5).copy(alpha=.32f),Color.Transparent),Offset(554f,270f),410f),Offset(244f,-100f),Size(620f,690f))
        securityLines(856f,540f)
        for(index in 0..37) {
            val ribbon=Path().apply {
                moveTo(302f+index*4f,-20f)
                cubicTo(715f+index*3f,125f,217f+index*7f,332f,900f,470f+index*2.8f)
            }
            drawPath(ribbon,Color(0xffad878c).copy(alpha=.17f),style=Stroke(.65f))
        }
        for(index in 0..18)drawOval(Color(0xff89a28a).copy(alpha=.18f),Offset(693f-index*6f,166f-index*8f),Size(108f+index*12f,233f+index*16f),style=Stroke(.7f))
        drawRect(Color(0xfff7f5ec).copy(alpha=.25f),Offset(348f,101f),Size(483f,386f))
    }
    drawRoundRect(Color(0xffaab7a8),Offset(1f,1f),Size(854f,538f),CornerRadius(20f),style=Stroke(1.3f))
    drawRect(Color(0xff334b89),Offset(28f,21f),Size(86f,59f))
    for(i in 0..11) {
        val angle=i*Math.PI/6
        drawCircle(Color(0xffebd674),2f,Offset(71f+25f*kotlin.math.sin(angle).toFloat(),50f+20f*kotlin.math.cos(angle).toFloat()))
    }
    documentText("DE",56f,59f,17f,true,width=50f,color=Color.White)
    documentText(country.uppercase(),138f,37f,17f,true,width=505f,color=Color(0xff34546a))
    documentText("TESTPROFIL / SYNTHETIC IDENTITY DOCUMENT",138f,49f,6.5f,width=505f,color=Color(0xff34546a))
    documentText("PERSONALAUSWEIS",138f,70f,20f,true,width=470f,color=Color(0xff34546a))
    documentText("IDENTITY CARD / CARTE D’IDENTITÉ",138f,84f,8f,width=410f,color=Color(0xff34546a))
    documentText("M U S T E R",678f,51f,18f,mono=true,width=153f,color=Color(0xff46615e))
    translate(29f,111f){identityPortrait(305f,403f)}
    drawRect(Color(0xffa4b2aa).copy(alpha=.65f),Offset(29f,111f),Size(305f,403f),style=Stroke(.8f))
    documentField("Name / Surname / Nom",surname,361f,129f,456f,27f)
    documentField("Vornamen / Given names / Prénoms",given,361f,199f,456f,26f)
    documentField("Geburtstag / Date of birth",birth,361f,271f,224f,24f)
    documentField("Staatsangehörigkeit / Nationality",nationality,602f,271f,218f,24f)
    documentField("Geburtsort / Place of birth / Lieu de naissance",place,361f,337f,456f,25f)
    documentField("Gültig bis / Date of expiry",expiry,361f,407f,260f,24f)
    documentText(can,649f,462f,34f,mono=true,width=173f)
    documentText("SYNTHETISCHES TESTDOKUMENT",361f,510f,9f,width=456f,color=Color(0xff657b70))
}
