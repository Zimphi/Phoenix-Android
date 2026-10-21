package de.phoenix.simulator

import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.testTag
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
    val image=remember { DocumentImages.load(context,R.drawable.phoenix_wordmark) }
    Canvas(modifier.semantics { contentDescription="Phoenix Logo" }) {
        phoenixBird(image)
    }
}

internal fun DrawScope.phoenixBird(image:ImageBitmap,origin:Offset=Offset.Zero,bounds:Size=size,alpha:Float=1f) {
    val source=IntOffset((image.width*.098).toInt(),(image.height*.163).toInt())
    val dimensions=IntSize((image.width*.293).toInt(),(image.height*.677).toInt())
    val factor=minOf(bounds.width/dimensions.width,bounds.height/dimensions.height)
    val dest=IntSize((dimensions.width*factor).toInt(),(dimensions.height*factor).toInt())
    drawImage(image,source,dimensions,IntOffset((origin.x+(bounds.width-dest.width)/2).toInt(),(origin.y+(bounds.height-dest.height)/2).toInt()),dest,alpha=alpha,filterQuality=FilterQuality.High)
}

@Composable internal fun portraitBitmap(sex:String):ImageBitmap? {
    val context=LocalContext.current
    val resource=when(sex){"M"->R.drawable.portrait_male;"F"->R.drawable.portrait_female;else->null}
    return remember(resource){resource?.let{DocumentImages.load(context,it)}}
}

@Composable internal fun AccountAvatar(modifier:Modifier=Modifier) {
    Canvas(modifier.clip(androidx.compose.foundation.shape.CircleShape).semantics{contentDescription="Benutzeravatar"}){identityPortrait()}
}

@Composable internal fun Startup() {
    Box(Modifier.fillMaxSize().background(Page).testTag("phoenix-startup"),contentAlignment=Alignment.Center) {
        // Match Android 12's 288dp icon canvas and the 20% inset in phoenix_splash.xml.
        Image(painterResource(R.drawable.phoenix_favicon),contentDescription="Phoenix Logo",
            modifier=Modifier.size(288.dp).padding(57.6.dp).testTag("phoenix-launch-logo"))
        Column(Modifier.offset(y=174.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Phoenix",color=Navy,fontSize=38.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1).sp)
            Text("eID & ePassport Simulator",color=Muted,fontSize=14.sp)
        }
    }
}

internal fun DrawScope.identityPortrait(width:Float=size.width,height:Float=size.height,portrait:ImageBitmap?=null) {
    if(portrait!=null) {
        drawRect(Brush.verticalGradient(listOf(Color(0xffe5e7e0),Color(0xffced6d0))),size=Size(width,height))
        val factor=maxOf(width/portrait.width,height/portrait.height)
        val crop=IntSize((width/factor).toInt(),(height/factor).toInt())
        drawImage(portrait,IntOffset((portrait.width-crop.width)/2,(portrait.height-crop.height)/2),crop,dstSize=IntSize(width.toInt(),height.toInt()),filterQuality=FilterQuality.High)
        return
    }
    scale(width/100f,height/128f,pivot=Offset.Zero) {
        drawRect(Brush.verticalGradient(listOf(Color(0xffe7e8e2),Color(0xffc8d3d1))),size=Size(100f,128f))
        val head=Path().apply {
            moveTo(50f,16f)
            cubicTo(36f,16f,29f,26f,30f,43f)
            cubicTo(25f,40f,26f,55f,32f,57f)
            cubicTo(34f,69f,42f,76f,50f,77f)
            cubicTo(58f,76f,66f,69f,68f,57f)
            cubicTo(74f,55f,75f,40f,70f,43f)
            cubicTo(71f,26f,64f,16f,50f,16f);close()
        }
        val bust=Path().apply {
            moveTo(40f,71f);lineTo(40f,82f)
            cubicTo(36f,87f,23f,89f,14f,94f)
            cubicTo(5f,99f,3f,112f,2f,128f)
            lineTo(98f,128f)
            cubicTo(97f,112f,95f,99f,86f,94f)
            cubicTo(77f,89f,64f,87f,60f,82f)
            lineTo(60f,71f);close()
        }
        val silhouette=Brush.linearGradient(listOf(Color(0xff687c83),Color(0xff3b515f)),Offset(20f,22f),Offset(78f,128f))
        drawPath(bust,silhouette)
        drawPath(head,silhouette)
        val collar=Path().apply {moveTo(38f,84f);lineTo(50f,96f);lineTo(62f,84f)}
        drawPath(collar,Color(0xffa4b3b4).copy(alpha=.6f),style=Stroke(.65f,cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}

@Composable internal fun Glyph(kind:String,color:Color=Muted,modifier:Modifier=Modifier.size(23.dp)) {
    val resource=when(kind) {
        "Profile"->R.drawable.icon_house_filled;"Simulation"->R.drawable.icon_scan;"play"->R.drawable.icon_play_filled;"Über","info"->R.drawable.icon_info
        "person"->R.drawable.icon_user_round;"calendar"->R.drawable.icon_calendar;"place"->R.drawable.icon_map_pin;"layers"->R.drawable.icon_data_groups;"add"->R.drawable.icon_circle_plus;"back"->R.drawable.icon_arrow_left;"chevron"->R.drawable.icon_chevron_right
        "edit"->R.drawable.icon_pencil;"import"->R.drawable.icon_file_input;"export"->R.drawable.icon_file_output;"save"->R.drawable.icon_save;"cloud"->R.drawable.icon_cloud
        "privacy"->R.drawable.icon_shield_check;"legal"->R.drawable.icon_scale;"file"->R.drawable.icon_file_text;"eid"->R.drawable.icon_id_card
        "epassport"->R.drawable.icon_globe;"phone"->R.drawable.icon_smartphone;"contactless"->R.drawable.icon_contactless;"ifd"->R.drawable.icon_ifd;"wifi"->R.drawable.icon_wifi;"stop"->R.drawable.icon_square;"copy"->R.drawable.icon_copy
        else->R.drawable.icon_settings
    }
    Icon(painterResource(resource),contentDescription=null,tint=color,modifier=modifier)
}

@Composable internal fun Art(kind:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val logo=if(kind=="nfc"||kind=="eid")remember { DocumentImages.load(context,R.drawable.phoenix_wordmark) }else null
    val portrait=if(kind=="eid")portraitBitmap("F")else null
    Canvas(modifier) {
        val factor=minOf(size.width,size.height)/160f
        translate((size.width-160f*factor)/2,(size.height-160f*factor)/2) {scale(factor,factor,pivot=Offset.Zero) {
        val color=Color(0xff9dafec);val stroke=Stroke(1.4f)
        when(kind) {
            "epassport" -> rotate(9f,Offset(102f,85f)) {
                val gold=Color(0xffe3c783)
                drawRoundRect(Color(0xff9cadd5).copy(alpha=.35f),Offset(57f,23f),Size(84f,130f),CornerRadius(5f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xff0c203b),Color(0xff20395b))),Offset(52f,17f),Size(84f,130f),CornerRadius(5f))
                drawLine(Color(0xff405673),Offset(57f,22f),Offset(57f,142f),1f)
                drawRoundRect(gold.copy(alpha=.22f),Offset(61f,25f),Size(67f,114f),CornerRadius(2f),style=Stroke(.6f))
                drawCircle(gold,21f,Offset(95f,76f),style=Stroke(1.2f))
                drawOval(gold,Offset(85f,55f),Size(20f,42f),style=Stroke(1f))
                drawOval(gold,Offset(74f,69f),Size(42f,14f),style=Stroke(1f))
                drawLine(gold,Offset(95f,55f),Offset(95f,97f),1f)
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    setColor(gold.toArgb());textAlign=android.graphics.Paint.Align.CENTER
                    typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
                    textSize=10f
                }
                drawContext.canvas.nativeCanvas.drawText("UTOPIA",95f,42f,paint)
                paint.textSize=10f
                drawContext.canvas.nativeCanvas.drawText("REISEPASS",95f,115f,paint)
                drawLine(gold.copy(alpha=.6f),Offset(82f,125f),Offset(108f,125f),.7f)
            }
            "eid" -> rotate(-4f,Offset(100f,83f)) {
                drawRoundRect(Navy.copy(alpha=.09f),Offset(16f,42f),Size(140f,88f),CornerRadius(5f))
                translate(12f,37f){scale(140f/856f,88f/540f,pivot=Offset.Zero){identityDocument(portrait=portrait,watermark=logo)}}
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

@Composable internal fun Action(label:String,primary:Boolean=true,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit) {
    val icon=when {label.contains("importieren")->"import";label.contains("exportieren")->"export";label.contains("speichern")->"save";label.contains("kopieren")->"copy";label.contains("starten")->"play";label.contains("beenden")->"stop";label.contains("Profil")||label.contains("hinzufügen")->"add";else->null}
    val accent=if(icon=="stop"||label=="Abmelden"||label.startsWith("Wird abgemeldet"))Color(0xffdc4657)else Blue
    val content:@Composable RowScope.()->Unit={
        if(icon!=null){Glyph(icon,if(primary)Color.White else accent,Modifier.size(20.dp));Spacer(Modifier.width(9.dp))}
        Text(label.removePrefix("＋  "),fontSize=14.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
    }
    val shape=RoundedCornerShape(12.dp)
    if(primary) Button(onClick,modifier.fillMaxWidth().heightIn(min=48.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xff5278ff),Color(0xff4664ff)))),enabled=enabled,shape=shape,colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent),contentPadding=PaddingValues(12.dp),content=content)
    else OutlinedButton(onClick,modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled,shape=shape,colors=ButtonDefaults.outlinedButtonColors(contentColor=accent),border=androidx.compose.foundation.BorderStroke(1.dp,accent.copy(alpha=.75f)),contentPadding=PaddingValues(12.dp),content=content)
}

@Composable internal fun Navigation(active:String,onNavigate:(String)->Unit) {
    Column(Modifier.background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color=Border.copy(alpha=.45f))
        Row(Modifier.fillMaxWidth().height(75.dp).padding(top=10.dp)) {
            for(name in listOf("Profile","Simulation","Über")) {
                val chosen=name==active
                Column(Modifier.weight(1f).fillMaxHeight().testTag("nav-$name").semantics{selected=chosen}.clickable(role=Role.Tab,onClick={onNavigate(name)}),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Glyph(name,if(chosen)Navy else Muted)
                    Spacer(Modifier.height(4.dp));Text(name,fontSize=11.sp,fontWeight=if(chosen)FontWeight.Bold else FontWeight.Medium,color=if(chosen)Navy else Muted)
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
    }
}
