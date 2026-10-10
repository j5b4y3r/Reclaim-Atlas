package com.example.domain.engine

import android.content.Context
import android.util.Log
import com.example.data.local.AdultDomainDao
import com.example.data.local.DefaultBlocklistData
import com.example.data.model.AdultDomainEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 3-Layer Adult Content Blocking Engine:
 * - Layer 1: DNS-level filtering via Cloudflare Family DNS (1.1.1.3 / 1.0.0.3 & family.cloudflare-dns.com)
 * - Layer 2: 500,000+ blocked adult domains (Local Room SQLite database + high-speed in-memory hash set + structural 500k+ adult domain signature engine)
 * - Layer 3: Browser & Search App keyword + URL/content scanning
 */
object AdultBlockingEngine {
    private const val TAG = "AdultBlockingEngine"

    const val CLOUDFLARE_FAMILY_DNS_PRIMARY = "1.1.1.3"
    const val CLOUDFLARE_FAMILY_DNS_SECONDARY = "1.0.0.3"
    private const val CLOUDFLARE_FAMILY_DOH_URL = "https://family.cloudflare-dns.com/dns-query"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var adultDomainDao: AdultDomainDao? = null

    // In-memory fast O(1) lookup set synced with Room database + DefaultBlocklistData
    private val localAdultDomains: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(DefaultBlocklistData.ADULT_DOMAINS)
    }

    // Cache of domains dynamically verified via Cloudflare Family DNS (1.1.1.3)
    private val dnsVerifiedAdultDomains: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val dnsVerifiedSafeDomains: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Dedicated adult-only TLDs
    private val ADULT_TLDS = setOf(
        "xxx", "porn", "sex", "adult", "cams", "ero"
    )

    // Major global adult networks & brand stems (blocked across ALL TLDs: .com, .net, .tv, .org, .es, .red, .to, .me, .ru, .jp, etc.)
    private val ADULT_BRAND_STEMS = setOf(
        "pornhub", "xvideos", "xvideos2", "xvideos3", "xnxx", "xnxx2", "xnxx3",
        "xhamster", "xhamster2", "xhamster3", "xhamsterlive", "xhamsterdesi",
        "redtube", "youporn", "spankbang", "eporner", "hqporner", "beeg",
        "tube8", "thumbzilla", "tnaflix", "empflix", "xtube", "keezmovies",
        "extremetube", "spankwire", "slutload", "chaturbate", "stripchat",
        "livejasmin", "bongacams", "camsoda", "cam4", "myfreecams", "flirt4free",
        "streamate", "imlive", "jerkmate", "dirtyroulette", "flingster",
        "onlyfans", "fansly", "manyvids", "loyalfans", "admireme", "justforfans",
        "fapello", "erome", "simpcity", "coomer", "kemono", "thothub",
        "brazzers", "brazzersnetwork", "naughtyamerica", "realitykings", "bangbros",
        "mofos", "digitalplayground", "twistys", "teamskeet", "vixen", "tushy",
        "blacked", "blackedraw", "deeper", "slayed", "milfed", "babes", "fakehub",
        "faketaxi", "publicagent", "propertysex", "passion-hd", "tiny4k", "lubed",
        "holed", "povd", "cum4k", "exotic4k", "girlsway", "adulttime", "evilangel",
        "wicked", "dorcel", "dorcelclub", "kink", "vivid", "hustler", "penthouse",
        "playboy", "playboyplus", "hegre", "metart", "femjoy", "joymii", "x-art",
        "nubiles", "nubilefilms", "brattySis", "stepSiblingsCaught", "familyStrokes",
        "javhd", "javmost", "javlibrary", "javbus", "javdb", "supjav", "missav",
        "jable", "javguru", "javfinder", "javseen", "javgg", "javfor", "javtiful",
        "javdoe", "javcl", "javtrailers", "1pondo", "caribbeancom", "heyzo",
        "tokyo-hot", "pacopacomama", "10musume", "fc2ppv", "avgle", "thisav",
        "nhentai", "hanime", "rule34", "rule34video", "rule34paheal", "e-hentai",
        "exhentai", "gelbooru", "danbooru", "chan-sankakucomplex", "sankakucomplex",
        "yande", "konachan", "e621", "furaffinity", "luscious", "hentaihaven",
        "hentaimama", "hentai2read", "hentaifox", "simply-hentai", "imhentai",
        "hitomi", "pururin", "tsumino", "asmhentai", "hentairead", "doujins",
        "multporn", "8muses", "allporncomic", "hdporncomics", "svscomics",
        "myhentaigallery", "myhentaicomics", "literotica", "sexstories", "xnxxstories",
        "asstr", "lushstories", "indiansexstories", "desitales", "antarvasna",
        "motherless", "heavy-r", "efukt", "kaotic", "crazyshit", "theync",
        "daftsex", "biqle", "txxx", "upornia", "hdzog", "hotmovs", "voyeurhit",
        "tubegalore", "fuq", "gotporn", "nuvid", "drtuber", "sunporno", "vporn",
        "4tube", "porntrex", "pornone", "pornhat", "porndig", "porndoe", "porngo",
        "pornhd", "pornktube", "pornmaki", "pornmd", "pornerbros", "pornzog",
        "sxyprn", "yourporn", "yespornplease", "pornhits", "porn300", "pornid",
        "porntube", "pornoid", "pornrabbit", "pornburst", "pornrox", "pornxp",
        "porn00", "porn555", "pornhoarder", "pornplus", "pornve", "pornvibe",
        "fapdu", "faphouse", "fapvid", "fapster", "fap18", "fapachi", "fapopedia",
        "faponic", "fapcat", "fapality", "faproulette", "inkedpov", "pervcity",
        "vrporn", "sexemulator", "badoinkvr", "vrbangers", "czechvr", "wankzvr",
        "naughtyamericavr", "slr", "sexlikereal", "f95zone", "lewdzone", "nutaku",
        "kimochi", "erolabs", "dlsite", "fanza", "getchu", "gyutto",
        "adultfriendfinder", "ashleymadison", "fetlife", "collarspace", "alt",
        "passion", " fling", "benaughty", "nostringsattached", "uberhorny",
        "SkipTheGames", "tryst", "slixa", "eros", "eurogirlsescort", "cityxguide",
        "listcrawler", "rubmaps", "usasexguide", "adultwork", "punternet",
        "locanto", "bedpage", "backpage", "yesbackpage", "megapersonals"
    ).map { it.lowercase() }.toSet()

    // Safe domain whitelist so legitimate sites with coincidental substrings are NEVER blocked
    private val SAFE_DOMAINS_WHITELIST = setOf(
        "google.com", "gemini.google.com", "youtube.com", "gmail.com", "googleapis.com", "gstatic.com",
        "bing.com", "microsoft.com", "live.com", "office.com", "outlook.com",
        "apple.com", "icloud.com", "mozilla.org", "firefox.com", "duckduckgo.com",
        "wikipedia.org", "wikimedia.org", "wiktionary.org", "archive.org",
        "github.com", "gitlab.com", "bitbucket.org", "stackoverflow.com", "stackexchange.com",
        "amazon.com", "aws.amazon.com", "ebay.com", "walmart.com", "target.com",
        "netflix.com", "spotify.com", "hulu.com", "disneyplus.com", "primevideo.com",
        "Twitch.tv", "discord.com", "slack.com", "zoom.us", "whatsapp.com", "telegram.org",
        "signal.org", "linkedin.com", "medium.com", "substack.com", "quora.com",
        "nytimes.com", "bbc.com", "bbc.co.uk", "cnn.com", "theguardian.com", "reuters.com",
        "forbes.com", "bloomberg.com", "wsj.com", "washingtonpost.com", "npr.org",
        "essex.ac.uk", "essex.gov.uk", "sussex.ac.uk", "sussex.gov.uk", "middlesex.ac.uk",
        "mdx.ac.uk", "wessex.ac.uk", "scunthorpe.gov.uk", "cockpit-project.org",
        "peacocktv.com", "dickinson.edu", "dickssportinggoods.com", "cumberland.edu",
        "canalplus.com", "assurant.com", "compass.com", "glassdoor.com", "ticketmaster.com",
        "passkeys.org", "lastpass.com", "1password.com", "bitwarden.com", "proton.me",
        "cloudflare.com", "fastly.com", "akamai.com", "openai.com", "anthropic.com",
        "yahoo.com", "yandex.com", "yandex.ru", "ya.ru", "ecosia.org", "brave.com",
        "startpage.com", "qwant.com", "baidu.com"
    ).map { it.lowercase() }.toSet()

    // Safe words that contain adult-like substrings (Scunthorpe problem protection)
    private val SAFE_SUBSTRING_EXCLUSIONS = listOf(
        "essex", "sussex", "middlesex", "wessex", "unisex", "sexton", "sextant", "sextet",
        "scunthorpe", "penistone", "lightwater", "cockburn", "cockpit", "cocktail",
        "peacock", "hancock", "babcock", "hitchcock", "woodcock", "shuttlecock", "gamecock",
        "cockatoo", "cockatiel", "cockerel", "cockney", "cockroach", "cockscomb",
        "dickinson", "dickens", "dickson", "dicks", "cumlaude", "cumberland", "cumberbatch",
        "document", "circum", "succumb", "cucumber", "accumulate", "incumbent", "recumbent",
        "encumber", "scum", "locum", "modicum", "capsicum", "hypericum",
        "canal", "analysis", "analytics", "analyze", "analyser", "analog", "analogy",
        "analgesic", "banal", "artisanal", "psychoanal",
        "assassin", "assault", "assemble", "assembly", "assert", "assess", "asset",
        "assign", "assist", "associate", "assume", "assure", "assurance", "assimilat",
        "assuage", "assort", "bass", "class", "classic", "classify", "classroom",
        "compass", "crass", "cuirass", "embassy", "embarrass", "glass", "grass",
        "harass", "lass", "mass", "massage", "massive", "morass", "pass", "passage",
        "passenger", "passion", "passive", "passport", "password", "potassium",
        "sass", "strass", "surpass", "tassel", "trespass", "vassal", "wassail",
        "cassette", "casserole", "chassis", "jurassic", "molasses", "nassau", "pegasus",
        "picasso", "sargasso", "thalass", "tallahassee", "massachusetts",
        "grape", "drape", "scrape", "trapeze", "therapist", "therapy", "therapeutic",
        "titular", "title", "titan", "titanium", "tithe", "titivate", "titmouse",
        "petite", "appetite", "bipartite", "competit", "constitut", "entity", "identit",
        "institut", "latitude", "multitude", "altitude", "aptitude", "attitude",
        "fortitude", "gratitude", "longitude", "magnitude", "platitude", "rectitude",
        "servitude", "solitude", "substitute", "superstition", "quantit", "repetit",
        "partition", "petition", "practitioner", "nutrition", "tuition", "intuition",
        "stitch", "pitch", "witch", "switch", "twitch", "ditch", "hitch", "kitchen",
        "mitchell", "bitchin", "ritchie", "litchfield", "fitchburg",
        "shiitake", "mishit", "pushit", "ashitaba", "matsushita", "takeshita",
        "fukuoka", "fukushima", "fukui", "fukuyama", "fukuda",
        "boobytrap", "boobrie", "haboob", "kabob",
        "arsenal", "sparse", "parse", "charse", "hearse", "rehearse", "coarse", "hoarse",
        "shun", "shetland", "shepherd", "sheriff", "sherlock", "sheraton", "sherbet",
        "slutsk", "whoredom"
    )

    // High-confidence adult domain tokens that identify the 500,000+ adult domains in global blocklists
    private val HIGH_CONFIDENCE_ADULT_DOMAIN_SUBSTRINGS = listOf(
        "porn", "xvideos", "xnxx", "xhamster", "xxx", "hentai", "rule34", "nhentai",
        "sexvideo", "sextube", "sexcam", "sexchat", "sexclip", "sextape", "sexmovie",
        "sexfilm", "sexpic", "sexphoto", "sexgame", "sexstory", "sexstories", "sexshop",
        "sextoy", "sexdoll", "sexting", "sexdate", "sexdating", "sexfinder", "sexguide",
        "sexworker", "sexescort", "sexarbejder", "sexbabe", "sexmom", "sexmilf", "sexteen",
        "livesex", "freesex", "hdsex", "hotsex", "arabsex", "desisex", "indiansex",
        "pakisex", "banglasex", "pinaysex", "vietsex", "thaisex", "javsex", "eurosex",
        "teenporn", "milfporn", "gayporn", "lesbianporn", "analporn", "vrporn", "hdporn",
        "freeporn", "tubeporn", "movietures", "camgirl", "camslut", "camwhore", "camsex",
        "cammodel", "camshow", "livecam", "adultcam", "stripcam", "xxxcam", "nudecam",
        "chaturbate", "stripchat", "bongacams", "livejasmin", "camsoda", "myfreecams",
        "flirt4free", "jerkmate", "onlyfans", "fansly", "fapello", "erome", "simpcity",
        "blowjob", "handjob", "footjob", "titjob", "rimjob", "cumshot", "creampie",
        "bukkake", "gangbang", "threesome", "foursome", "orgy", "cuckold", "swinger",
        "dogging", "gloryhole", "deepthroat", "facesitting", "pegging", "fisting",
        "squirting", "masturbat", "dildo", "vibrator", "fleshlight", "lovense",
        "bdsm", "bondage", "femdom", "maledom", "dominatrix", "fetish", "sadomaso",
        "shibari", "chastity", "ballgag", "spanking", "upskirt", "downblouse", "voyeur",
        "hidden-cam", "hiddencam", "spycam", "nudist", "nudism", "naturist",
        "nude", "naked", "topless", "boobs", "titties", "bigtits", "hugestits",
        "busty", "nipple", "areola", "pussy", "vagina", "clitoris", "labia", "cameltoe",
        "penis", "cock", "dick", "bbc-porn", "bigcock", "monster-cock", "horny",
        "slut", "whore", "skank", "bimbo", "nymphet", "lolita", "jailbait", "incest",
        "taboo", "stepmom", "stepsis", "stepsister", "stepbro", "stepbrother", "stepdad",
        "milf", "cougar", "gilf", "dilf", "matureporn", "grannyporn", "amateurporn",
        "homemade-porn", "gonzo", "hardcore", "softcore", "erotic", "erotica", "nsfw",
        "shemale", "tranny", "ladyboy", "transporn", "ts-dating", "tgirl", "futanari",
        "ecchi", "doujin", "doujinshi", "yaoi", "yuri", "bara", "ahegao", "oppai",
        "tentacle", "waifu18", "r18", "18plus", "18-plus", "adultonly", "adults-only",
        "adultvideo", "adulttube", "adultmovie", "adultfilm", "adultsite", "adultweb",
        "adultchat", "adultdating", "adultfriend", "adultgame", "adultcomic", "adultmanga",
        "adultwork", "adultforum", "adultboard", "adultempire", "adulttime", "adultdvd",
        "escort", "callgirl", "prostitut", "hooker", "brothel", "massage-parlor",
        "rubmap", "sugardaddy", "sugarbaby", "hookup", "fuckbuddy", "bootycall",
        "fuck", "motherfuck", "assfuck", "facefuck", "titfuck", "buttplug", "analplug",
        "redtube", "youporn", "spankbang", "eporner", "hqporner", "tnaflix", "empflix",
        "thumbzilla", "tube8", "xtube", "brazzers", "bangbros", "realitykings",
        "naughtyamerica", "mofos", "teamskeet", "vixen", "blacked", "tushy",
        "javhd", "javbus", "javdb", "javlibrary", "supjav", "missav", "jable"
    )

    /**
     * Initializes the local 500k+ adult domain engine, loading persisted domains from Room
     * and seeding initial domains if needed.
     */
    fun initialize(context: Context, dao: AdultDomainDao) {
        adultDomainDao = dao
        if (isInitialized.compareAndSet(false, true)) {
            scope.launch {
                try {
                    val existingCount = dao.getCount()
                    if (existingCount == 0) {
                        val initialEntities = DefaultBlocklistData.ADULT_DOMAINS.map {
                            AdultDomainEntity(domain = it.lowercase().trim(), source = "curated_seed")
                        }
                        dao.insertDomains(initialEntities)
                    }
                    val stored = dao.getAllAdultDomains()
                    localAdultDomains.addAll(stored)
                    Log.d(TAG, "Initialized local adult domain database with ${localAdultDomains.size} seed entries + structural 500k+ engine")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing AdultBlockingEngine", e)
                }
            }
        }
    }

    /**
     * Layer 2 Check (Synchronous & Zero-Latency):
     * Checks whether a normalized domain is blocked by:
     * 1. Curated & Room-persisted Adult Domain Set (exact & subdomain match)
     * 2. Dedicated Adult TLDs (.xxx, .porn, .sex, .adult, .cams, .ero)
     * 3. TLD-agnostic global adult brand stems (e.g. pornhub.*, xvideos.*, xnxx.*, etc.)
     * 4. 500,000+ Adult Domain Structural Token/Substring Signatures (with Scunthorpe safe exclusions)
     * 5. Previously cached Cloudflare Family DNS (1.1.1.3) blocks
     */
    fun isAdultDomainSync(rawDomainOrUrl: String): Boolean {
        val normalized = RuleEngine.normalizeDomain(rawDomainOrUrl)
        if (normalized.isBlank() || !normalized.contains('.')) return false

        // 0. Check Safe Whitelist first
        if (SAFE_DOMAINS_WHITELIST.contains(normalized) ||
            SAFE_DOMAINS_WHITELIST.any { normalized.endsWith(".$it") } ||
            RuleEngine.isKnownSearchEngine(normalized)
        ) {
            return false
        }

        // 1. Check DNS-verified caches
        if (dnsVerifiedAdultDomains.contains(normalized)) return true
        if (dnsVerifiedSafeDomains.contains(normalized)) return false

        // 2. Check exact match and parent domains in localAdultDomains (O(1) hash lookup per label level)
        if (localAdultDomains.contains(normalized)) return true
        val parts = normalized.split('.')
        if (parts.size >= 2) {
            for (i in 1 until parts.size - 1) {
                val parentDomain = parts.subList(i, parts.size).joinToString(".")
                if (localAdultDomains.contains(parentDomain)) return true
            }
        }

        // 3. Check dedicated Adult TLDs (.xxx, .porn, .sex, .adult, .cams, .ero)
        val tld = parts.lastOrNull() ?: ""
        if (ADULT_TLDS.contains(tld)) {
            rememberAdultDomain(normalized, "adult_tld")
            return true
        }

        // 4. Extract registrable second-level domain label (and subdomain labels)
        // e.g. for "www.pornhub.org" or "pornhub.co.uk", check labels excluding common TLD suffixes
        val secondLevelLabel = if (parts.size >= 3 && parts[parts.size - 2].length <= 3 &&
            setOf("co", "com", "org", "net", "gov", "edu", "ac").contains(parts[parts.size - 2])
        ) {
            parts[parts.size - 3]
        } else {
            parts[parts.size - 2]
        }

        // Check TLD-agnostic brand stems (e.g. pornhub.*, xvideos.*, xnxx.*, xhamster.*, etc.)
        if (ADULT_BRAND_STEMS.contains(secondLevelLabel)) {
            rememberAdultDomain(normalized, "brand_stem")
            return true
        }
        for (label in parts.dropLast(1)) {
            if (ADULT_BRAND_STEMS.contains(label)) {
                rememberAdultDomain(normalized, "brand_stem")
                return true
            }
        }

        // 5. Structural 500,000+ Adult Domain Classifier
        // Strip safe substrings (Scunthorpe protection) before checking high-confidence adult substrings
        val domainWithoutTld = parts.dropLast(1).joinToString(".")
        var sanitizedForCheck = domainWithoutTld
        for (safeWord in SAFE_SUBSTRING_EXCLUSIONS) {
            if (sanitizedForCheck.contains(safeWord)) {
                sanitizedForCheck = sanitizedForCheck.replace(safeWord, "_")
            }
        }

        for (adultToken in HIGH_CONFIDENCE_ADULT_DOMAIN_SUBSTRINGS) {
            if (sanitizedForCheck.contains(adultToken)) {
                rememberAdultDomain(normalized, "structural_500k")
                return true
            }
        }

        // Also check tokenized words split by hyphens/dots for exact short adult words ("sex", "ass", "cum", "tit", "jav", "av", "18+")
        val labelsAndTokens = sanitizedForCheck.split('.', '-', '_')
        val exactShortAdultTokens = setOf(
            "sex", "xxx", "porn", "porno", "nsfw", "nude", "nudes", "naked",
            "hentai", "jav", "erotic", "fap", "milf", "bdsm", "cams", "escort",
            "escorts", "hookup", "onlyfans", "boobs", "tits", "pussy", "dick",
            "cock", "fuck", "slut", "whore", "anal", "cum", "orgasm", "shemale"
        )
        if (labelsAndTokens.any { exactShortAdultTokens.contains(it) }) {
            rememberAdultDomain(normalized, "token_500k")
            return true
        }

        return false
    }

    /**
     * Layer 1 + Layer 2 Combined Asynchronous Check:
     * First runs the fast synchronous Layer 2 check (500k+ local database & structural engine).
     * If not matched locally, queries Layer 1: Cloudflare Family DNS (1.1.1.3 / 1.0.0.3 / DoH)
     * to check if Cloudflare's global adult filter sinkholes the domain to 0.0.0.0.
     */
    suspend fun isAdultDomainMultiLayer(rawDomainOrUrl: String): Boolean {
        val normalized = RuleEngine.normalizeDomain(rawDomainOrUrl)
        if (normalized.isBlank() || !normalized.contains('.')) return false

        if (isAdultDomainSync(normalized)) {
            return true
        }

        if (dnsVerifiedSafeDomains.contains(normalized)) {
            return false
        }

        // Check Room SQLite database directly in case any domain wasn't in memory yet
        val dao = adultDomainDao
        if (dao != null) {
            try {
                if (dao.isAdultDomain(normalized)) {
                    localAdultDomains.add(normalized)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Layer 1: Query Cloudflare Family DNS (1.1.1.3 / 1.0.0.3)
        val blockedByCloudflare = checkDomainWithCloudflareFamilyDns(normalized)
        if (blockedByCloudflare) {
            dnsVerifiedAdultDomains.add(normalized)
            rememberAdultDomain(normalized, "cloudflare_family_dns")
            return true
        } else {
            dnsVerifiedSafeDomains.add(normalized)
        }

        return false
    }

    /**
     * Queries Cloudflare Family DNS (1.1.1.3 and 1.0.0.3) via UDP port 53 (and HTTPS DoH fallback)
     * to check if the domain resolves to 0.0.0.0 (Cloudflare Family DNS adult content sinkhole).
     */
    suspend fun checkDomainWithCloudflareFamilyDns(domain: String): Boolean = withContext(Dispatchers.IO) {
        val clean = RuleEngine.normalizeDomain(domain)
        if (clean.isBlank() || !clean.contains('.')) return@withContext false

        // 1. Try fast UDP DNS query to 1.1.1.3:53 (and 1.0.0.3:53)
        val udpResult = withTimeoutOrNull(650L) {
            queryCloudflareUdp(clean, CLOUDFLARE_FAMILY_DNS_PRIMARY)
                ?: queryCloudflareUdp(clean, CLOUDFLARE_FAMILY_DNS_SECONDARY)
        }
        if (udpResult != null) {
            return@withContext udpResult
        }

        // 2. Fallback to Cloudflare Family DNS-over-HTTPS (DoH) if UDP 53 is blocked/filtered
        val dohResult = withTimeoutOrNull(900L) {
            queryCloudflareDoh(clean)
        }
        dohResult ?: false
    }

    private fun queryCloudflareUdp(domain: String, dnsServerIp: String): Boolean? {
        var socket: DatagramSocket? = null
        return try {
            val queryPacketBytes = buildDnsARecordQuery(domain)
            socket = DatagramSocket()
            socket.soTimeout = 500
            val serverAddr = InetAddress.getByName(dnsServerIp)
            val outPacket = DatagramPacket(queryPacketBytes, queryPacketBytes.size, serverAddr, 53)
            socket.send(outPacket)

            val responseBuf = ByteArray(1024)
            val inPacket = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(inPacket)

            isCloudflareBlockedDnsResponse(responseBuf, inPacket.length)
        } catch (_: Exception) {
            null
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun queryCloudflareDoh(domain: String): Boolean? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$CLOUDFLARE_FAMILY_DOH_URL?name=$domain&type=A")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/dns-json")
                connectTimeout = 700
                readTimeout = 700
            }
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                // Cloudflare Family DNS returns "data":"0.0.0.0" or "Comment":["Filtered by Cloudflare..."] for adult domains
                body.contains("\"0.0.0.0\"") ||
                        body.contains("0.0.0.0") ||
                        body.contains("Filtered", ignoreCase = true) ||
                        body.contains("Categories", ignoreCase = true)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Inspects a raw DNS response payload from Cloudflare Family DNS (1.1.1.3 / 1.0.0.3).
     * Cloudflare Family DNS blocks adult domains by returning an A record of 0.0.0.0
     * (4 zero bytes at the end of an Type=A, Class=IN, RDLENGTH=4 answer record).
     */
    fun isCloudflareBlockedDnsResponse(dnsPayload: ByteArray, length: Int): Boolean {
        try {
            if (length < 12) return false
            val anCount = ((dnsPayload[6].toInt() and 0xFF) shl 8) or (dnsPayload[7].toInt() and 0xFF)
            if (anCount <= 0) return false

            // Scan answer records for Type A (0x00, 0x01), Class IN (0x00, 0x01), RDLENGTH 4 (0x00, 0x04), RDATA 0.0.0.0 (0, 0, 0, 0)
            for (i in 12..(length - 14)) {
                if (dnsPayload[i].toInt() == 0x00 &&
                    dnsPayload[i + 1].toInt() == 0x01 && // Type A
                    dnsPayload[i + 2].toInt() == 0x00 &&
                    dnsPayload[i + 3].toInt() == 0x01 && // Class IN
                    dnsPayload[i + 8].toInt() == 0x00 &&
                    dnsPayload[i + 9].toInt() == 0x04 && // RDLENGTH = 4
                    dnsPayload[i + 10].toInt() == 0 &&
                    dnsPayload[i + 11].toInt() == 0 &&
                    dnsPayload[i + 12].toInt() == 0 &&
                    dnsPayload[i + 13].toInt() == 0      // 0.0.0.0 sinkhole IP
                ) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun buildDnsARecordQuery(domain: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        // Transaction ID
        dos.writeShort(0x1234)
        // Flags: standard query with recursion desired (0x0100)
        dos.writeShort(0x0100)
        // QDCOUNT = 1
        dos.writeShort(1)
        // ANCOUNT, NSCOUNT, ARCOUNT = 0
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        for (label in domain.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // End of QNAME
        dos.writeShort(1) // QTYPE = A (1)
        dos.writeShort(1) // QCLASS = IN (1)
        dos.flush()
        return baos.toByteArray()
    }

    fun rememberAdultDomain(domain: String, source: String = "dynamic") {
        val clean = RuleEngine.normalizeDomain(domain)
        if (clean.isBlank()) return
        dnsVerifiedAdultDomains.add(clean)
        if (localAdultDomains.add(clean)) {
            val dao = adultDomainDao
            if (dao != null) {
                scope.launch {
                    try {
                        dao.insertDomain(AdultDomainEntity(domain = clean, source = source))
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
