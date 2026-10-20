package de.phoenix.simulator

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

internal val Navy=Color(0xff081840)
internal val Blue=Color(0xff4969ff)
internal val Muted=Color(0xff596b98)
internal val Pale=Color(0xffeff1ff)
internal val Border=Color(0xffd8e0f5)
internal val Page=Color(0xfffcfcff)
internal val Space=12.dp
internal val Inset=20.dp
@OptIn(ExperimentalTextApi::class)
internal val PhoenixFont=FontFamily(listOf(400,500,600,700,800).map { weight ->
    Font(R.font.manrope,weight=FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight)))
})
internal val PhoenixTypography=Typography().let { t -> t.copy(
    bodyLarge=t.bodyLarge.copy(fontFamily=PhoenixFont),bodyMedium=t.bodyMedium.copy(fontFamily=PhoenixFont),bodySmall=t.bodySmall.copy(fontFamily=PhoenixFont),
    labelLarge=t.labelLarge.copy(fontFamily=PhoenixFont),labelMedium=t.labelMedium.copy(fontFamily=PhoenixFont),labelSmall=t.labelSmall.copy(fontFamily=PhoenixFont),
    titleLarge=t.titleLarge.copy(fontFamily=PhoenixFont),titleMedium=t.titleMedium.copy(fontFamily=PhoenixFont),titleSmall=t.titleSmall.copy(fontFamily=PhoenixFont),
    headlineLarge=t.headlineLarge.copy(fontFamily=PhoenixFont),headlineMedium=t.headlineMedium.copy(fontFamily=PhoenixFont),headlineSmall=t.headlineSmall.copy(fontFamily=PhoenixFont)) }

@Composable internal fun Brand(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val image=remember { BitmapFactory.decodeResource(context.resources,R.drawable.phoenix_wordmark).asImageBitmap() }
    Canvas(modifier.semantics { contentDescription="Phoenix Logo" }) {
        val source=IntOffset((image.width*.098).toInt(),(image.height*.163).toInt())
        val dimensions=IntSize((image.width*.293).toInt(),(image.height*.677).toInt())
        val scale=minOf(size.width/dimensions.width,size.height/dimensions.height)
        val dest=IntSize((dimensions.width*scale).toInt(),(dimensions.height*scale).toInt())
        drawImage(image,source,dimensions,IntOffset(((size.width-dest.width)/2).toInt(),((size.height-dest.height)/2).toInt()),dest,filterQuality=FilterQuality.High)
    }
}

@Composable internal fun Glyph(kind:String,color:Color=Muted,modifier:Modifier=Modifier.size(23.dp)) {
    val resource=when(kind) {
        "Profile"->R.drawable.icon_house;"Simulation","play"->R.drawable.icon_play;"Über","info"->R.drawable.icon_info
        "layers"->R.drawable.icon_layers;"add"->R.drawable.icon_circle_plus;"back"->R.drawable.icon_arrow_left;"chevron"->R.drawable.icon_chevron_right
        "edit"->R.drawable.icon_pencil;"import"->R.drawable.icon_file_input;"save"->R.drawable.icon_save;"cloud"->R.drawable.icon_cloud
        "privacy"->R.drawable.icon_shield_check;"legal"->R.drawable.icon_scale;"file"->R.drawable.icon_file_text;"eid"->R.drawable.icon_id_card
        "epassport"->R.drawable.icon_globe;"phone"->R.drawable.icon_smartphone;"wifi"->R.drawable.icon_wifi;"stop"->R.drawable.icon_square;"copy"->R.drawable.icon_copy
        else->R.drawable.icon_settings
    }
    Icon(painterResource(resource),contentDescription=null,tint=color,modifier=modifier)
}

@Composable internal fun Chip(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        drawRoundRect(Color(0xffd1b975),cornerRadius=CornerRadius(4.dp.toPx()))
        val ink=Color(0xff7c714e);val w=size.width;val h=size.height
        drawRoundRect(ink,Offset(w*.3f,h*.25f),Size(w*.4f,h*.5f),CornerRadius(2.dp.toPx()),style=Stroke(1.dp.toPx()))
        for(y in listOf(.25f,.75f)){drawLine(ink,Offset(0f,h*y),Offset(w*.3f,h*y),1.dp.toPx());drawLine(ink,Offset(w*.7f,h*y),Offset(w,h*y),1.dp.toPx())}
        drawLine(ink,Offset(w*.5f,0f),Offset(w*.5f,h*.25f),1.dp.toPx());drawLine(ink,Offset(w*.5f,h*.75f),Offset(w*.5f,h),1.dp.toPx())
    }
}

@Composable internal fun Art(kind:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val logo=if(kind=="nfc")remember { BitmapFactory.decodeResource(context.resources,R.drawable.phoenix_wordmark).asImageBitmap() }else null
    Canvas(modifier) {
        val factor=minOf(size.width,size.height)/160f
        translate((size.width-160f*factor)/2,(size.height-160f*factor)/2) {scale(factor,factor,pivot=Offset.Zero) {
        val color=Color(0xff9dafec);val stroke=Stroke(1.4f)
        when(kind) {
            "epassport" -> rotate(-18f,Offset(101f,86f)) {
                drawCircle(color.copy(alpha=.10f),65f,Offset(101f,86f))
                drawCircle(color,65f,Offset(101f,86f),style=stroke)
                for(width in listOf(50f,101f))drawOval(color,Offset(101-width/2,21f),Size(width,130f),style=stroke)
                for(height in listOf(44f,94f))drawOval(color,Offset(36f,86-height/2),Size(130f,height),style=stroke)
                drawLine(color,Offset(101f,21f),Offset(101f,151f),1.4f);drawLine(color,Offset(36f,86f),Offset(166f,86f),1.4f)
            }
            "eid" -> rotate(-7f,Offset(100f,83f)) {
                drawRoundRect(color.copy(alpha=.16f),Offset(33f,43f),Size(126f,84f),CornerRadius(8f))
                drawRoundRect(Color(0xffe8edff),Offset(28f,36f),Size(126f,84f),CornerRadius(8f))
                drawRoundRect(color,Offset(28f,36f),Size(126f,84f),CornerRadius(8f),style=stroke)
                drawLine(color,Offset(43f,49f),Offset(73f,49f),2f,StrokeCap.Round)
                drawCircle(color.copy(alpha=.65f),10f,Offset(58f,73f))
                drawRoundRect(color.copy(alpha=.65f),Offset(43f,86f),Size(30f,22f),CornerRadius(9f,9f))
                for((i,width) in listOf(48f,40f,30f).withIndex())drawLine(color,Offset(88f,61f+i*14),Offset(88f+width,61f+i*14),2.5f,StrokeCap.Round)
                drawLine(color.copy(alpha=.4f),Offset(86f,105f),Offset(136f,105f),1.2f)
            }
            else -> {
                drawCircle(Brush.radialGradient(listOf(Color(0xffe9edff),Color(0xfff3f5ff)),Offset(83f,81f),72f),72f,Offset(83f,81f))
                rotate(16f,Offset(48f,86f)) {
                    drawRoundRect(Color(0xffb7c6f4),Offset(20f,30f),Size(62f,119f),CornerRadius(10f))
                    drawRoundRect(Color.White,Offset(16f,25f),Size(62f,119f),CornerRadius(10f))
                    drawRoundRect(Blue.copy(alpha=.8f),Offset(16f,25f),Size(62f,119f),CornerRadius(10f),style=Stroke(2.2f))
                    drawRoundRect(Brush.linearGradient(listOf(Color(0xfffafbff),Color(0xffdfe6ff))),Offset(21f,30f),Size(52f,108f),CornerRadius(7f))
                    drawLine(Blue.copy(alpha=.8f),Offset(38f,32f),Offset(55f,32f),2f,StrokeCap.Round)
                    if(logo!=null)drawImage(logo,IntOffset((logo.width*.098).toInt(),(logo.height*.163).toInt()),IntSize((logo.width*.293).toInt(),(logo.height*.677).toInt()),IntOffset(33,65),IntSize(27,34),filterQuality=FilterQuality.High)
                }
                rotate(15f,Offset(123f,104f)) {
                    drawRoundRect(Color(0xffc5d2fa),Offset(96f,85f),Size(61f,53f),CornerRadius(8f))
                    drawRoundRect(Color.White,Offset(93f,78f),Size(61f,53f),CornerRadius(8f))
                    drawRoundRect(color,Offset(93f,78f),Size(61f,53f),CornerRadius(8f),style=Stroke(2f))
                    drawRoundRect(Color(0xffedf0ff),Offset(99f,84f),Size(49f,36f),CornerRadius(5f))
                    drawOval(Color(0xffdce3ff),Offset(109f,87f),Size(28f,29f))
                }
                for(i in 0..2)drawArc(Blue.copy(alpha=.85f),-49f,98f,false,Offset(60f-i*5,26f-i*5),Size(32f+i*13,45f+i*11),style=Stroke(2.1f,cap=StrokeCap.Round))
            }
        }
    }}}
}

@Composable internal fun Action(label:String,primary:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit) {
    val icon=when {label.contains("importieren")->"import";label.contains("speichern")->"save";label.contains("kopieren")->"copy";label.contains("starten")->"play";label.contains("beenden")->"stop";label.contains("Profil")||label.contains("hinzufügen")->"add";else->null}
    val accent=if(icon=="stop")Color(0xffdc4657)else Blue
    val content:@Composable RowScope.()->Unit={
        if(icon!=null){Glyph(icon,if(primary)Color.White else accent,Modifier.size(20.dp));Spacer(Modifier.width(9.dp))}
        Text(label.removePrefix("＋  "),fontSize=14.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
    }
    val shape=RoundedCornerShape(12.dp)
    if(primary) Button(onClick,modifier.fillMaxWidth().heightIn(min=48.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xff5278ff),Color(0xff4664ff)))),shape=shape,colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent),contentPadding=PaddingValues(12.dp),content=content)
    else OutlinedButton(onClick,modifier.fillMaxWidth().heightIn(min=48.dp),shape=shape,colors=ButtonDefaults.outlinedButtonColors(contentColor=accent),border=androidx.compose.foundation.BorderStroke(1.dp,accent.copy(alpha=.75f)),contentPadding=PaddingValues(12.dp),content=content)
}

@Composable internal fun Navigation(active:String,onNavigate:(String)->Unit) {
    Column(Modifier.background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color=Border.copy(alpha=.45f))
        Row(Modifier.fillMaxWidth().height(65.dp)) {
            for(name in listOf("Profile","Simulation","Über")) {
                val chosen=name==active
                Column(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Tab,onClick={onNavigate(name)}),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Glyph(name,if(chosen)Blue else Muted)
                    Spacer(Modifier.height(4.dp));Text(name,fontSize=11.sp,color=if(chosen)Blue else Muted)
                    Spacer(Modifier.height(5.dp));Box(Modifier.size(23.dp,2.dp).background(if(chosen)Blue else Color.Transparent,RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}
