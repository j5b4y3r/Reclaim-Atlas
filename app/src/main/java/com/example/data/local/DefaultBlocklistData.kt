package com.example.data.local

import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleAction
import com.example.data.model.RuleType

object DefaultBlocklistData {

    // Curated known adult content and adult network/redirect domains (seeded into Room adult_domains table)
    val ADULT_DOMAINS = listOf(
        "pornhub.com", "pornhubpremium.com", "xvideos.com", "xvideos.es", "xvideos2.com",
        "xnxx.com", "xnxx.tv", "xnxx2.com", "redtube.com", "youporn.com",
        "xhamster.com", "xhamster.desi", "xhamsterlive.com", "xhamster2.com",
        "cam4.com", "chaturbate.com", "onlyfans.com", "fansly.com", "manyvids.com",
        "stripchat.com", "livejasmin.com", "bongacams.com", "spankbang.com",
        "daftsex.com", "biqle.ru", "eporner.com", "beeg.com", "tube8.com",
        "thumbzilla.com", "tnaflix.com", "empflix.com", "hqporner.com",
        "sxyprn.com", "javhd.com", "javmost.com", "javbus.com", "javdb.com",
        "javlibrary.com", "supjav.com", "missav.com", "jable.tv", "dmm.co.jp",
        "erome.com", "fapello.com", "simpcity.su", "coomer.su", "kemono.su",
        "camsoda.com", "myfreecams.com", "adultfriendfinder.com", "flirt4free.com",
        "streamate.com", "imlive.com", "jerkmate.com", "dirtyroulette.com",
        "flingster.com", "brazzers.com", "naughtyamerica.com", "realitykings.com",
        "bangbros.com", "mofos.com", "digitalplayground.com", "twistys.com",
        "teamskeet.com", "vixen.com", "tushy.com", "blacked.com", "deeper.com",
        "slayed.com", "babes.com", "faketaxi.com", "fakehub.com", "adulttime.com",
        "evilangel.com", "kink.com", "playboy.com", "penthouse.com", "hustler.com",
        "hegre.com", "metart.com", "femjoy.com", "x-art.com", "nubiles.net",
        "nhentai.net", "nhentai.to", "hanime.tv", "rule34.xxx", "rule34.paheal.net",
        "rule34video.com", "e-hentai.org", "exhentai.org", "gelbooru.com",
        "danbooru.donmai.us", "sankakucomplex.com", "e621.net", "furaffinity.net",
        "luscious.net", "hentaihaven.xxx", "hitomi.la", "tsumino.com", "multporn.net",
        "8muses.com", "literotica.com", "sexstories.com", "asstr.org",
        "motherless.com", "heavy-r.com", "efukt.com", "txxx.com", "upornia.com",
        "hdzog.com", "hotmovs.com", "voyeurhit.com", "tubegalore.com", "fuq.com",
        "gotporn.com", "nuvid.com", "drtuber.com", "sunporno.com", "vporn.com",
        "4tube.com", "porntrex.com", "pornone.com", "pornhat.com", "porndig.com",
        "porndoe.com", "porngo.com", "pornhd.com", "pornktube.com", "pornmaki.com",
        "pornmd.com", "pornerbros.com", "pornzog.com", "yourporn.sexy",
        "yespornplease.com", "pornhits.com", "porn300.com", "porntube.com",
        "xtube.com", "keezmovies.com", "extremetube.com", "spankwire.com",
        "slutload.com", "fapdu.com", "faphouse.com", "vrporn.com", "badoinkvr.com",
        "vrbangers.com", "sexlikereal.com", "f95zone.to", "nutaku.net", "dlsite.com",
        "ashleymadison.com", "fetlife.com", "skipthegames.com", "tryst.link",
        "slixa.com", "eros.com", "eurogirlsescort.com", "cityxguide.com",
        "listcrawler.eu", "rubmaps.ch", "adultwork.com"
    )

    // Curated gambling domains
    val GAMBLING_DOMAINS = listOf(
        "bet365.com", "pokerstars.com", "draftkings.com", "fanduel.com",
        "bovada.lv", "stake.com", "roobet.com", "rollbit.com",
        "williamhill.com", "888casino.com", "betway.com", "unibet.com"
    )

    // Curated short video domains and app packages
    val SHORT_VIDEO_TARGETS = listOf(
        "tiktok.com", "m.tiktok.com", "v.douyin.com",
        "youtube.com/shorts", "instagram.com/reels"
    )

    val SHORT_VIDEO_PACKAGES = listOf(
        "com.zhiliaoapp.musically", // TikTok
        "com.zhiliaoapp.musically.go", // TikTok Lite
        "com.ss.android.ugc.trill", // TikTok (Asia/Global)
        "com.google.android.youtube", // YouTube (Shorts mode)
        "app.revanced.android.youtube", // ReVanced YouTube
        "app.rvx.android.youtube", // ReVanced Extended YouTube
        "com.vanced.android.youtube", // Vanced YouTube
        "org.schabi.newpipe", // NewPipe
        "org.schabi.newpipe.debug",
        "org.polymorphicshade.newpipe",
        "com.github.libretube", // LibreTube
        "com.pure_tubers.video_music_stream",
        "free.tube.premium.advanced.tuber",
        "com.instagram.android", // Instagram Reels
        "com.instagram.lite",
        "com.facebook.katana", // Facebook Reels
        "com.facebook.lite"
    )

    // WhatsApp packages
    val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    // Social media domains & packages
    val SOCIAL_MEDIA_DOMAINS = listOf(
        "twitter.com", "x.com", "instagram.com", "facebook.com",
        "reddit.com", "threads.net", "snapchat.com", "pinterest.com"
    )

    val SOCIAL_MEDIA_PACKAGES = listOf(
        "com.twitter.android", "com.instagram.android",
        "com.facebook.katana", "com.reddit.frontpage",
        "com.snapchat.android", "com.pinterest"
    )

    // Supported browsers that expose standard URL bar for real-time inspection
    val SUPPORTED_BROWSERS = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta"
    )

    // Common and uncommon unsupported browsers & media downloader browsers across all Android OEMs
    val UNSUPPORTED_BROWSERS = listOf(
        // Video downloader and tool browsers
        "com.snaptube.premium",
        "com.snaptube.loader",
        "snap.tube.app",
        "com.nemo.vidmate",
        "devian.tubemate.home",
        "devian.tubemate.v3",
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.adm.lite",
        "com.dv.adm",
        "com.dv.adm.pay",
        // Alternative & Privacy browsers
        "com.opera.browser",
        "com.opera.browser.afin",
        "com.opera.mini.native",
        "com.opera.mini.native.beta",
        "com.opera.touch",
        "com.opera.gx",
        "com.opera.cryptobrowser",
        "com.duckduckgo.mobile.android",
        "org.torproject.torbrowser",
        "org.torproject.torbrowser_alpha",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",
        "com.UCMobile.intl",
        "com.ucmobile.intl",
        "com.UCMobile",
        "com.uc.browser.en",
        "com.uc.iflow",
        "com.cloudmosa.puffinFree",
        "com.cloudmosa.puffin",
        "com.yandex.browser",
        "com.yandex.browser.lite",
        "com.yandex.browser.beta",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.dev",
        "com.microsoft.emmx.canary",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "com.alohamobile.browser",
        "com.alohamobile.browser.lite",
        // OEM built-in browsers (Xiaomi, Oppo, Realme, Vivo, Huawei, Honor, Transsion, etc.)
        "com.android.browser",
        "com.mi.globalbrowser",
        "com.mi.globalbrowser.mini",
        "com.miui.browser",
        "com.heytap.browser",
        "com.coloros.browser",
        "com.nearme.browser",
        "com.oppo.browser",
        "com.vivo.browser",
        "com.huawei.browser",
        "com.hihonor.browser",
        "com.transsion.phoenix",
        "com.talpa.hibrowser",
        "com.afmobigroup.amanbrowser",
        // Lightweight & WebView browsers
        "mobi.mgeek.TunnyBrowser",
        "com.mx.browser",
        "com.mx.browser.tablet",
        "com.ksmobile.cb",
        "com.mycompany.app.soulbrowser",
        "mark.via.gp",
        "mark.via",
        "com.chimbori.hermitcrab",
        "net.onecook.browser",
        "com.naver.whale",
        "com.ecosia.android",
        "com.qwant.liberty",
        "com.startpage.app",
        "us.spotco.fennec_dos",
        "org.cromite.cromite",
        "org.bromite.bromite",
        "io.github.forkmaintainers.iceraven",
        "net.waterfox.android.release",
        "acr.browser.barebones",
        "acr.browser.lightning",
        "com.stoutner.privacybrowser.standard",
        "com.stoutner.privacybrowser.free",
        "de.baumann.browser"
    )

    // Google App packages (Search, Discover, Assistant, Custom Tabs, Lens)
    val GOOGLE_SEARCH_PACKAGES = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",
        "com.google.android.apps.gsa",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.apps.gsa.staticplugins.opa",
        "com.google.android.apps.lens",
        "com.google.ar.lens",
        "com.google.android.apps.turbo"
    )

    // Google Lens & Visual Search packages
    val GOOGLE_LENS_PACKAGES = listOf(
        "com.google.ar.lens",
        "com.google.android.apps.lens",
        "com.samsung.android.bixbyvision.framework",
        "com.samsung.android.visionintelligence"
    )

    // All browser packages combined
    val BROWSER_PACKAGES = SUPPORTED_BROWSERS + UNSUPPORTED_BROWSERS

    // Keywords associated with adult searches to detect in search queries (Layer 3 Browser & Search App Scanning)
    val ADULT_KEYWORDS = listOf(
        "porn", "porno", "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "spankbang", "eporner", "hqporner", "beeg", "tnaflix", "thumbzilla", "tube8",
        "chaturbate", "stripchat", "bongacams", "livejasmin", "camsoda", "myfreecams",
        "onlyfans", "fansly", "fapello", "erome", "brazzers", "bangbros", "naughtyamerica",
        "realitykings", "vixen", "blacked", "tushy", "javhd", "missav", "jable",
        "nhentai", "rule34", "hanime", "e-hentai", "exhentai", "gelbooru", "literotica",
        "xxx", "nude", "nudes", "naked", "sex", "sex video", "sex videos", "sextape",
        "erotic", "erotica", "nsfw", "hentai", "camgirl", "blowjob", "handjob",
        "cumshot", "creampie", "bukkake", "gangbang", "threesome", "milf", "hardcore",
        "fetish", "bdsm", "bondage", "femdom", "stripclub", "escort", "adult game",
        "adult video", "leak video", "leaked nude", "boobs", "tits", "pussy", "dick",
        "cock", "fuck", "fucking", "slut", "whore", "shemale", "ladyboy", "futanari",
        "ecchi", "doujin", "doujinshi", "ahegao", "masturbate", "masturbation", "orgasm",
        "dildo", "vibrator", "upskirt", "voyeur", "deepthroat"
    )

    fun createInitialRules(): List<BlockRule> {
        val rules = mutableListOf<BlockRule>()

        // Adult content rule
        rules.add(
            BlockRule(
                name = "Adult Websites & Content",
                type = RuleType.CATEGORY,
                target = BlockCategory.ADULT.name,
                category = BlockCategory.ADULT,
                enabled = false,
                priority = 100,
                action = RuleAction.BLOCK
            )
        )

        // Specific adult domains
        for (domain in ADULT_DOMAINS) {
            rules.add(
                BlockRule(
                    name = "Adult: $domain",
                    type = RuleType.DOMAIN,
                    target = domain,
                    category = BlockCategory.ADULT,
                    enabled = false,
                    priority = 90
                )
            )
        }

        // Gambling category rule
        rules.add(
            BlockRule(
                name = "Online Gambling",
                type = RuleType.CATEGORY,
                target = BlockCategory.GAMBLING.name,
                category = BlockCategory.GAMBLING,
                enabled = false,
                priority = 80
            )
        )

        for (domain in GAMBLING_DOMAINS) {
            rules.add(
                BlockRule(
                    name = "Gambling: $domain",
                    type = RuleType.DOMAIN,
                    target = domain,
                    category = BlockCategory.GAMBLING,
                    enabled = false,
                    priority = 75
                )
            )
        }

        // Short-form video rules
        rules.add(
            BlockRule(
                name = "Short-form Video Feeds",
                type = RuleType.SHORTS,
                target = "SHORTS_GLOBAL",
                category = BlockCategory.SHORT_VIDEO,
                enabled = false,
                priority = 95
            )
        )

        // Social media rules
        rules.add(
            BlockRule(
                name = "Social Media Feeds",
                type = RuleType.CATEGORY,
                target = BlockCategory.SOCIAL_MEDIA.name,
                category = BlockCategory.SOCIAL_MEDIA,
                enabled = false,
                priority = 70
            )
        )

        for (domain in SOCIAL_MEDIA_DOMAINS) {
            rules.add(
                BlockRule(
                    name = "Social: $domain",
                    type = RuleType.DOMAIN,
                    target = domain,
                    category = BlockCategory.SOCIAL_MEDIA,
                    enabled = false,
                    priority = 60
                )
            )
        }

        return rules
    }
}
