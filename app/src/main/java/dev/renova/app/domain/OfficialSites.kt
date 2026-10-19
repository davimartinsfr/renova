package dev.renova.app.domain

/** Sites oficiais de apps conhecidos: bloquear o app bloqueia também o site no navegador. */
object OfficialSites {
    private val BY_PACKAGE = mapOf(
        "com.instagram.android" to listOf("instagram.com"),
        "com.instagram.lite" to listOf("instagram.com"),
        "com.instagram.barcelona" to listOf("threads.net", "threads.com"),
        "com.facebook.katana" to listOf("facebook.com", "fb.com"),
        "com.facebook.lite" to listOf("facebook.com", "fb.com"),
        "com.facebook.orca" to listOf("messenger.com"),
        "com.zhiliaoapp.musically" to listOf("tiktok.com"),
        "com.zhiliaoapp.musically.go" to listOf("tiktok.com"),
        "com.ss.android.ugc.trill" to listOf("tiktok.com"),
        "com.twitter.android" to listOf("x.com", "twitter.com"),
        "com.reddit.frontpage" to listOf("reddit.com"),
        "com.google.android.youtube" to listOf("youtube.com", "youtu.be"),
        "com.google.android.apps.youtube.music" to listOf("music.youtube.com"),
        "com.kwai.video" to listOf("kwai.com"),
        "com.kwai.bulldog" to listOf("kwai.com"),
        "com.pinterest" to listOf("pinterest.com", "br.pinterest.com"),
        "com.snapchat.android" to listOf("snapchat.com"),
        "com.tumblr" to listOf("tumblr.com"),
        "com.linkedin.android" to listOf("linkedin.com"),
        "com.whatsapp" to listOf("web.whatsapp.com"),
        "com.whatsapp.w4b" to listOf("web.whatsapp.com"),
        "org.telegram.messenger" to listOf("web.telegram.org"),
        "com.discord" to listOf("discord.com"),
        "tv.twitch.android.app" to listOf("twitch.tv"),
        "com.netflix.mediaclient" to listOf("netflix.com"),
        "com.amazon.avod.thirdpartyclient" to listOf("primevideo.com"),
        "com.disney.disneyplus" to listOf("disneyplus.com"),
        "com.wbd.stream" to listOf("max.com"),
        "com.globo.globotv" to listOf("globoplay.globo.com"),
        "com.spotify.music" to listOf("open.spotify.com"),
        "com.roblox.client" to listOf("roblox.com"),
        "com.shopee.br" to listOf("shopee.com.br"),
        "com.mercadolibre" to listOf("mercadolivre.com.br"),
    )

    fun of(packageName: String): List<String> = BY_PACKAGE[packageName].orEmpty()
}
