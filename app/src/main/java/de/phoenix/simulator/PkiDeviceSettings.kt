package de.phoenix.simulator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PkiDeviceSettings(identity:JSONObject,mobileAuth:MobileAuth,onProfiles:()->Unit,onAccountChanged:()->Unit) {
    val context=LocalContext.current
    val device=remember{PkiDevice(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf("")}
    var qrBusy by remember{mutableStateOf(false)}
    var connected by remember{mutableStateOf(device.connected())}
    var hasLocalKey by remember{mutableStateOf(device.hasLocalKey())}
    var screen by remember{mutableStateOf(if(connected)"success" else "welcome")}
    var code by remember{mutableStateOf("")}
    val busy=qrBusy||mobileAuth.busy
    val supportsPasskeys=android.os.Build.VERSION.SDK_INT>=28
    LaunchedEffect(mobileAuth.completed){connected=device.connected();hasLocalKey=device.hasLocalKey();onAccountChanged()}
    LaunchedEffect(mobileAuth.phase,connected){screen=when{
        connected->"success"
        mobileAuth.phase=="mail"->"code"
        mobileAuth.phase in listOf("passkey","login","enroll")->mobileAuth.phase
        else->"welcome"
    }}
    fun perform(action:()->String){
        qrBusy=true;message=""
        scope.launch{
            val result=withContext(Dispatchers.IO){runCatching(action)}
            message=result.getOrElse{"Verbindung nicht abgeschlossen. Bitte erneut versuchen."}
            connected=device.connected();hasLocalKey=device.hasLocalKey();onAccountChanged();qrBusy=false
        }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        if(result.contents!=null)perform{device.enroll(result.contents);device.status()}
    }
    Column(Modifier.fillMaxWidth().testTag("cloud-registration"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(screen !in listOf("welcome","success"))TextButton(onClick={if(mobileAuth.phase!="idle")mobileAuth.reset();screen="welcome";code=""},enabled=!busy){Text("‹ Zurück",color=Muted)}
        Image(painterResource(R.drawable.phoenix_cloud_logo),"Phoenix Cloud – ePassport und eID Simulator",
            Modifier.align(Alignment.CenterHorizontally)
                .size(if(screen=="welcome"||screen=="login")190.dp else 118.dp))
        if(screen in listOf("email","code","passkey"))CloudRegistrationProgress(when(screen){"email"->1;"code"->2;else->3})
        when(screen) {
            "welcome"->{
                CloudTitle("Dein Phoenix.\nÜberall verbunden.")
                CloudText("Verbinde die App mit deinem Phoenix-Konto. Deine lokalen Profile bleiben auch ohne Anmeldung verfügbar.")
                Action("Mit Passkey anmelden",enabled=!busy&&supportsPasskeys){screen="login";mobileAuth.login()}
                Action(if(mobileAuth.hasPending())"Registrierung fortsetzen" else "Konto erstellen",false,enabled=!busy&&supportsPasskeys){if(mobileAuth.hasPending())mobileAuth.resumePending() else screen="email"}
                if(!supportsPasskeys)CloudText("Passkeys benötigen Android 9 oder neuer. Du kannst die App weiterhin per QR-Code verbinden.")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    HorizontalDivider(Modifier.weight(1f));Text("oder",fontSize=11.sp,color=Muted);HorizontalDivider(Modifier.weight(1f))
                }
                TextButton(onClick={scanner.launch(ScanOptions().setCaptureActivity(QrCaptureActivity::class.java).setOrientationLocked(true)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setPrompt("Geräte-QR-Code aus den Phoenix-Web-Einstellungen scannen"))},
                    enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Mit QR-Code anmelden")}
                TextButton(onClick=onProfiles,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Ohne Cloud-Konto lokal weiterarbeiten",fontSize=11.sp,color=Muted)}
                if(BuildConfig.DEBUG)TextButton(onClick={mobileAuth.selectEnvironment(if(mobileAuth.environment=="preview")"production" else "preview")},enabled=!busy&&!mobileAuth.hasPending()){
                    Text("Umgebung: ${if(mobileAuth.environment=="preview")"Preview" else "Produktion"}",fontSize=11.sp,color=Muted)
                }
            }
            "email"->{
                CloudTitle("Beginnen wir mit\ndeiner E-Mail.")
                CloudText("Wir schicken dir einen Bestätigungslink und einen Einmalcode. Du brauchst kein Passwort.")
                OutlinedTextField(mobileAuth.email,mobileAuth::editEmail,label={Text("E-Mail-Adresse")},singleLine=true,enabled=!busy,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),shape=RoundedCornerShape(11.dp),modifier=Modifier.fillMaxWidth().testTag("cloud-email"))
                Action("Bestätigungsmail senden",enabled=!busy&&mobileAuth.email.isNotBlank()){mobileAuth.signup()}
                CloudNote("Ein vorhandenes Konto? Melde dich mit deinem Passkey an.")
                CloudText("Mit der Registrierung akzeptierst du die Nutzungsbedingungen. Informationen findest du im Datenschutz.")
            }
            "code"->{
                Glyph("cloud",Blue,Modifier.size(44.dp))
                CloudTitle("Post für dich.")
                CloudText("Öffne den Link aus unserer E-Mail auf diesem Smartphone oder gib den Einmalcode ein.")
                Text(mobileAuth.email,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Navy)
                OutlinedTextField(code,{code=it.filter{digit->digit in '0'..'9'}.take(10)},label={Text("Einmalcode")},singleLine=true,enabled=!busy,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(11.dp),modifier=Modifier.fillMaxWidth().testTag("cloud-otp"))
                Action("E-Mail bestätigen",enabled=!busy&&code.length in 6..10){mobileAuth.confirmCode(code);code=""}
                TextButton(onClick={mobileAuth.changeEmail();screen="email";code=""},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("E-Mail-Adresse korrigieren")}
                if(mobileAuth.recovered)TextButton(onClick={mobileAuth.resumeRegistration()},enabled=!busy){Text("Bestätigte Registrierung fortsetzen")}
                CloudNote("Die E-Mail ist auf deinem Computer geöffnet? Übertrage einfach den Code hierher.")
            }
            "passkey"->{
                CloudTitle("Dein Schlüssel\nzu Phoenix Cloud.")
                CloudText("Erstelle einen Passkey. Bei der Anmeldung bestätigst du mit Fingerabdruck, Gesicht oder deiner Gerätesperre.")
                CloudNote("${mobileAuth.email}\nE-Mail-Adresse bestätigt")
                Action("Passkey erstellen",enabled=!busy){mobileAuth.createPasskey()}
                CloudNote("Deine Biometrie bleibt auf dem Gerät.\nPhoenix erhält weder deinen Fingerabdruck noch deine Geräte-PIN.")
                CloudText("Android öffnet dafür einen Systemdialog.")
                TextButton(onClick={mobileAuth.login()},enabled=!busy){Text("Passkey bereits erstellt? Anmelden")}
            }
            "login"->{
                CloudTitle("Schön, dass du\nwieder da bist.")
                CloudText("Nutze deinen vorhandenen Phoenix-Passkey. Android zeigt dir die verfügbaren Konten.")
                Action("Mit Passkey anmelden",enabled=!busy){mobileAuth.login()}
                CloudNote("Dein Passkey liegt auf einem anderen Gerät? Wähle die von Android angebotene geräteübergreifende Anmeldung.")
            }
            "enroll"->{
                CloudTitle("Fast geschafft.")
                CloudText("Dein Passkey wurde bestätigt. Schließe jetzt die Verbindung dieser App ab.")
                Action("Geräteverbindung abschließen",enabled=!busy){mobileAuth.retryEnrollment()}
            }
            "success"->{
                CloudTitle("Willkommen\nbei Phoenix Cloud.")
                CloudText("Dein Konto ist eingerichtet und diese App verbunden.")
                AccountIdentity(identity,device.environment())
                Action("Zu meinen Profilen",enabled=!busy,onClick=onProfiles)
                Action("Verbindung prüfen",false,enabled=!busy){perform{device.status()}}
                TextButton(onClick={perform{device.disconnect()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Abmelden")}
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().semantics{contentDescription="Anmeldung wird verarbeitet"})
        val info=message.ifBlank{mobileAuth.message}
        if(info.isNotBlank())Text(info,fontSize=13.sp,color=Navy,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        if(hasLocalKey&&!connected&&screen=="welcome")TextButton(onClick={perform{device.forget()}},enabled=!busy){Text("Lokalen Zugang zurücksetzen")}
        if(mobileAuth.phase!="idle"&&!connected)TextButton(onClick={mobileAuth.reset();screen="welcome";code=""},enabled=!busy){Text("Später fortsetzen")}
    }
}

@Composable private fun CloudTitle(value:String){Text(value,fontSize=25.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp,color=Navy)}
@Composable private fun CloudText(value:String){Text(value,fontSize=13.sp,lineHeight=22.sp,color=Muted)}
@Composable private fun CloudNote(value:String){Surface(color=Pale,shape=RoundedCornerShape(12.dp)){Text(value,fontSize=12.sp,lineHeight=20.sp,color=Muted,modifier=Modifier.fillMaxWidth().padding(15.dp))}}
@Composable private fun CloudRegistrationProgress(step:Int){
    Row(Modifier.fillMaxWidth().semantics{contentDescription="Schritt $step von 3"},horizontalArrangement=Arrangement.spacedBy(6.dp)){
        repeat(3){index->Surface(color=if(index<step)Blue else Border,shape=RoundedCornerShape(4.dp),modifier=Modifier.weight(1f).height(4.dp)){} }
    }
}
