package de.phoenix.simulator

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Only immutable packaged artwork is shared; profile photos and keys never enter this cache. */
internal object DocumentImages {
    private val images=object:LruCache<Int,ImageBitmap>(32*1024) {
        override fun sizeOf(key:Int,value:ImageBitmap)=value.width*value.height*4/1024
    }
    @Synchronized fun load(context:Context,resource:Int):ImageBitmap {
        images.get(resource)?.let{return it}
        val options=BitmapFactory.Options().apply {if(resource==R.drawable.portrait_male||resource==R.drawable.portrait_female)inSampleSize=2}
        val image=checkNotNull(BitmapFactory.decodeResource(context.resources,resource,options)).asImageBitmap()
        images.put(resource,image)
        return image
    }
    fun warm(context:Context) {
        listOf(R.drawable.phoenix_wordmark,R.drawable.document_paper,R.drawable.document_back_paper,
            R.drawable.portrait_male,R.drawable.portrait_female,R.drawable.passport_details_template).forEach{load(context,it)}
    }
}
