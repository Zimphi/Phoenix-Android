package de.phoenix.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ProfileSettingsSection(title:String,icon:String,tag:String,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical=4.dp).testTag(tag),shape=RoundedCornerShape(14.dp),color=Color.White,border=BorderStroke(1.dp,Border)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Glyph(icon,Blue,Modifier.size(20.dp))
                Text(title,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Navy)
            }
            content()
        }
    }
}
