// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

internal object NfcTestAssessment {
    const val ORIGINAL="c31d12dbb8359da86292b6add372f529ea2fd9664efce2492a60ba493f436445"
    const val PATCHED="3ea4de76d589620ada64ebe9e0a3b9e2c15fd19ffba410e8b515d3d07dcf3482"

    fun assess(model:String,firmware:String,hash:String?,radio:Boolean,correlated:Boolean,
               authenticatedChallenge:Boolean,authenticatedSelect:Boolean,selectReachedHce:Boolean,outerStatus:Int):String {
        if(!radio || !correlated || !authenticatedChallenge)return "unconfirmed"
        if(authenticatedSelect && selectReachedHce)return "not_observed"
        if(!selectReachedHce && outerStatus==0x6e00 && model=="SM-G991B" && firmware=="G991BXXSJHZC2" && hash==ORIGINAL)
            return "known_signature"
        return "unconfirmed"
    }
}
