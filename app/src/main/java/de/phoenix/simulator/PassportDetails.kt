package de.phoenix.simulator

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import org.json.JSONObject

/** The approved template keeps labels, rules and Phoenix watermark at their mockup coordinates. */
@Composable internal fun PassportDetails(profile:Profile,details:JSONObject,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val template=remember{BitmapFactory.decodeResource(context.resources,R.drawable.passport_details_template).asImageBitmap()}
    val residence=details.optString("residence").ifBlank{"—"}
    val title=details.optString("title").ifBlank{"—"}
    val can=profile.settings.optString("can").ifBlank{"—"}
    Canvas(modifier.testTag("passport-page-details").semantics {
        contentDescription="Reisepass zweite Innenseite · Hochformat · Wohnort: $residence · Größe: keine Angabe · Augenfarbe: keine Angabe · Doktorgrad: $title · Ordens- oder Künstlername: keine Angabe · CAN: $can"
    }) {
        val factor=minOf(size.width/1051f,size.height/1496f)
        translate((size.width-1051f*factor)/2,(size.height-1496f*factor)/2){scale(factor,factor,pivot=Offset.Zero) {
            drawImage(template,dstSize=IntSize(1051,1496))
            fun value(text:String,x:Float,y:Float,width:Float=670f,number:Boolean=false) {
                val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.rgb(15,20,19);textSize=if(number)44f else 38f
                    typeface=if(number)Typeface.MONOSPACE else Typeface.create("sans-serif",Typeface.NORMAL)
                }
                drawContext.canvas.nativeCanvas.drawText(TextUtils.ellipsize(text.uppercase().take(256),paint,width,TextUtils.TruncateAt.END).toString(),x,y,paint)
            }
            value(residence,172f,287f)
            value("—",172f,777f)
            value("—",172f,894f)
            value(title,172f,1009f)
            value("—",172f,1110f)
            value(can.toCharArray().joinToString(" "),505f,1217f,358f,true)
        }}
    }
}
