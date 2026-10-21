package de.phoenix.simulator

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

internal data class CountryCode(val name:String,val alpha2:String,val alpha3:String,val icao:String,val mrz:String) {
    fun matches(query:String)=listOf(name,alpha2,alpha3,icao,mrz).any{it.contains(query,ignoreCase=true)}
    fun hasCode(code:String)=code.equals(mrz,ignoreCase=true)||code.equals(alpha3,ignoreCase=true)||code.trimEnd('<').equals(icao,ignoreCase=true)
}
internal object CountryCodes {
    fun load(context:Context):List<CountryCode> {
        val data=context.assets.open("icao_countries_DE.json").use{JSONArray(ProfileStore.read(it))}
        return (0 until data.length()).map{index->
            val row=data.getJSONObject(index)
            CountryCode(row.getString("name"),row.getString("iso_alpha2"),row.getString("iso_alpha3"),row.getString("icao_9303_code"),row.getString("mrz_value"))
        }+CountryCode("Utopia (synthetisch)","","","UTO","UTO")
    }
}
@Composable internal fun CountryField(label:String,code:String,countries:List<CountryCode>,modifier:Modifier=Modifier,onClick:()->Unit) {
    OutlinedButton(onClick,modifier.heightIn(min=60.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Muted),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label,fontSize=10.sp,color=Muted)
            Text(countries.firstOrNull{it.hasCode(code)}?.name?:code,fontSize=13.sp,color=Navy,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Glyph("chevron",Muted,Modifier.size(16.dp))
    }
}
@Composable internal fun CountryPicker(countries:List<CountryCode>,onDismiss:()->Unit,onSelect:(CountryCode)->Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    var query by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Land auswählen")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query,{query=it.take(120)},label={Text("Land oder Code suchen")},singleLine=true,modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                items(countries.filter{it.matches(query.trim())},key={it.mrz}){country->
                    Row(Modifier.fillMaxWidth().clickable{keyboard?.hide();onSelect(country)}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(country.name,Modifier.weight(1f),fontSize=14.sp,color=Navy,fontWeight=FontWeight.SemiBold)
                        Text(country.icao,fontSize=12.sp,color=Muted)
                    }
                }
            }
        }
    },confirmButton={TextButton(onDismiss){Text("Abbrechen")}})
}
