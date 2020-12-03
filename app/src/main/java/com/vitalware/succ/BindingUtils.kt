package com.vitalware.succ

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.preference.PreferenceManager


@BindingAdapter("hymnPackTextSet")
fun TextView.setPackText(item: HymnPack) {
    text = item.packName
}

@BindingAdapter("hymnPackSizeSet")
fun TextView.setPackSize(item: HymnPack) {
    text = item.packSize.toString()
}

@BindingAdapter("hymnPackIdSet")
fun TextView.setPackId(item: HymnPack) {
    text = item.packId
}

@BindingAdapter("verseTextSet")
fun TextView.setVerseText(item: Verse) {
    text = item.verseText
    textSize = if (!item.isChorus){
        setTextColor(ContextCompat.getColor(context, R.color.secondary_text_default_material_light_succ))
        24f
    }
    else{
        setTextColor(ContextCompat.getColor(context, R.color.colorBlue))
        18f
    }
}

@BindingAdapter("verseIdSet")
fun TextView.setVerseId(item: Verse) {
    text = item.verseId
}

@BindingAdapter("hymnTitleSet")
fun TextView.setHymnTitle(item: HymnProfile) {
    text = item.title
}

@BindingAdapter("hymnalTextSet")
fun TextView.setHymnalName(item: HymnProfile) {
    text = item.hymnal
}

@BindingAdapter("hymnNumberSet")
fun TextView.setHymnNumber(item: HymnProfile) {
    text = item.hymnNumber
}

@BindingAdapter("hymnPageSet")
fun TextView.setHymnPage(item: HymnProfile) {
    text = item.hymnPage
}

@BindingAdapter("hymnIdSet")
fun TextView.setHymnId(item: HymnProfile) {
    text = item.hymnId
}

@BindingAdapter("packIdForHymnSet")
fun TextView.setHymnPackId(item: SearchHymnProfile) {
    text = item.packId
}

@BindingAdapter("hymnSearchTitleSet")
fun TextView.setHymnTitle(item: SearchHymnProfile) {
    text = item.title
}

@BindingAdapter("hymnalSearchTextSet")
fun TextView.setHymnalName(item: SearchHymnProfile) {
    text = item.hymnal
}

@BindingAdapter("hymnSearchNumberSet")
fun TextView.setHymnNumber(item: SearchHymnProfile) {
    text = item.hymnNumber
}

@BindingAdapter("hymnSearchPageSet")
fun TextView.setHymnPage(item: SearchHymnProfile) {
    text = item.hymnPage
}

@BindingAdapter("hymnSearchIdSet")
fun TextView.setHymnId(item: SearchHymnProfile) {
    text = item.hymnId
}

@BindingAdapter("hymnMassTypeSet")
fun TextView.setHymnType(item: MassHymn) {
    text = item.type
}

@BindingAdapter("hymnMassTitleSet")
fun TextView.setMassHymnTitle(item: MassHymn) {
    text = item.title
}

@BindingAdapter("hymnalMassTextSet")
fun TextView.setMassHymnalName(item: MassHymn) {
    text = item.hymnal
}

@BindingAdapter("hymnMassNumberSet")
fun TextView.setMassHymnNumber(item: MassHymn) {
    text = item.hymnNumber
}

@BindingAdapter("hymnMassPageSet")
fun TextView.setMassHymnPage(item: MassHymn) {
    text = item.hymnPage
}

@BindingAdapter("hymnMassIdSet")
fun TextView.setMassHymnId(item: MassHymn) {
    text = item.hymnId
}

@BindingAdapter("verseOptionVisibility")
fun TextView.setVerseOptionVisibility(@Suppress("UNUSED_PARAMETER") item: Verse) {
    PreferenceManager.getDefaultSharedPreferences(context).apply {
        when(getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)){
            1 -> {visibility = View.GONE}
            2 -> {visibility = View.GONE}
        }
    }
}
