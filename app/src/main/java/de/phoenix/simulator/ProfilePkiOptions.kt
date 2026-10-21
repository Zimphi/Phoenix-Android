package de.phoenix.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ProfilePkiOptions(kind:String,infrastructure:String,signedFixture:Boolean,cloudVerified:Boolean,onSelect:(String)->Unit) {
    val selected=infrastructure.ifEmpty{if(kind=="eid")"bsi" else "profile"}
    ProfileSettingsSection("PKI-Testinfrastruktur","cloud","pki-settings") {
    val options=(if(kind=="eid")listOf("bsi" to "BSI-Testinfrastruktur")else emptyList())+listOf("profile" to "Profil-Testinfrastruktur","phoenix" to "Phoenix-Testinfrastruktur","own" to "Eigene Testinfrastruktur")
    options.forEach{(id,label)->
        val enabled=id!="own"&&(id!="phoenix"||cloudVerified)
        Row(Modifier.fillMaxWidth().testTag("pki-$id").selectable(selected=selected==id,enabled=enabled,role=Role.RadioButton,onClick={onSelect(id)}).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected=selected==id,onClick=null,enabled=enabled)
            Column(Modifier.padding(start=8.dp)) {
                Text(label,color=if(enabled)Navy else Muted,fontSize=14.sp)
                Text(when(id){"profile"->"Bereits vorhandene Test-PKI dieses Profils";"bsi"->"PersoSim-Testmaterial · signierte Datengruppen müssen dazu passen";"phoenix"->if(cloudVerified)"Cloud verbunden · Zertifikatsausstellung noch nicht angebunden" else "Erfolgreiche Cloud-Anbindung erforderlich";else->"Noch nicht verfügbar"},fontSize=11.sp,color=Muted)
            }
        }
    }
    }
}
