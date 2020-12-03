package com.vitalware.succ

data class VoiceBundle(
    var soprano: Boolean = false,
    var alto: Boolean = false,
    var tenor: Boolean = false,
    var bass: Boolean = false,
    var choir: Boolean = false
)