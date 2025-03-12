package com.example.serviceapp

import androidx.annotation.RawRes

data class MusicData(
    val name:String,
    val author:String,
    @RawRes val songId: Int,
    val coverage:Int
){
    constructor():this("","",0,0)
}

val songsList= listOf(
    MusicData(
        "BURN IT DOWN",
        "Linkin Park",
        R.raw.music1,
        R.drawable.banner1
    ),
    MusicData(
        "Where is my mind",
        "Pixies",
        R.raw.song2,
        R.drawable.banner2
    ),
    MusicData(
        "Ocean Eyes",
        "Billie Eilish",
        R.raw.song3,
        R.drawable.banner3
    ),
    MusicData(
        "Numb",
        "Linkin Park",
        R.raw.song4,
        R.drawable.banner4

    )
)