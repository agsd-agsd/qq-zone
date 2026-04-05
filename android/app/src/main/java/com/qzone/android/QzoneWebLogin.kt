package com.qzone.android

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import java.util.TreeMap

const val QZONE_WEB_LOGIN_URL =
    "https://xui.ptlogin2.qq.com/cgi-bin/xlogin?" +
        "proxy_url=https://qzs.qq.com/qzone/v6/portal/proxy.html&" +
        "daid=5&hide_title_bar=1&low_login=0&qlogin_auto_login=1&no_verifyimg=1&" +
        "link_target=blank&appid=549000912&style=22&target=self&" +
        "s_url=https://qzs.qq.com/qzone/v5/loginsucc.html?para=izone&" +
        "pt_qr_app=%E6%89%8B%E6%9C%BAQQ%E7%A9%BA%E9%97%B4&" +
        "pt_qr_link=https://z.qzone.com/download.html&" +
        "self_regurl=https://qzs.qq.com/qzone/v6/reg/index.html&" +
        "pt_qr_help_link=https://z.qzone.com/download.html&pt_no_auth=0"

private const val WEB_LOGIN_LOG_TAG = "QzoneWebLogin"

private val FIXED_COOKIE_URLS = listOf(
    "https://qzs.qq.com",
    "https://qzone.qq.com",
    "https://user.qzone.qq.com",
    "https://h5.qzone.qq.com",
    "https://photo.qzone.qq.com",
    "https://i.qq.com",
    "https://qq.com",
    "https://www.qq.com",
    "https://ptlogin2.qq.com",
    "https://ssl.ptlogin2.qq.com",
    "https://xui.ptlogin2.qq.com",
)

data class CollectedCookies(
    val header: String,
    val keys: List<String>,
    val sampledUrls: List<String>,
    val hitUrls: List<String>,
)

fun collectQzoneCookies(
    cookieManager: CookieManager,
    successUrl: String = "",
    observedUrls: Collection<String> = emptyList(),
): CollectedCookies {
    val cookies = TreeMap<String, String>()
    val sampledUrls = buildCookieCandidateUrls(successUrl, observedUrls)
    val hitUrls = mutableListOf<String>()

    for (url in sampledUrls) {
        val raw = cookieManager.getCookie(url).orEmpty()
        if (raw.isBlank()) {
            continue
        }

        hitUrls += url
        for (part in raw.split(";")) {
            val piece = part.trim()
            if (piece.isBlank()) {
                continue
            }
            val separator = piece.indexOf('=')
            if (separator <= 0 || separator >= piece.length - 1) {
                continue
            }

            val name = piece.substring(0, separator).trim()
            val value = piece.substring(separator + 1).trim()
            if (name.isNotBlank() && value.isNotBlank()) {
                cookies[name] = value
            }
        }
    }

    return CollectedCookies(
        header = cookies.entries.joinToString(separator = "; ") { (name, value) -> "$name=$value" },
        keys = cookies.keys.toList(),
        sampledUrls = sampledUrls,
        hitUrls = hitUrls,
    )
}

fun hasRequiredQzoneCookies(cookieHeader: String): Boolean {
    val cookies = parseCookieHeader(cookieHeader)
    val hasQQ = firstCookieValue(cookies, "p_uin", "uin", "ptui_loginuin").isNotBlank()
    val hasSKey = firstCookieValue(cookies, "p_skey", "skey").isNotBlank()
    return hasQQ && hasSKey
}

fun isQzoneLoginSuccessUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) {
        return false
    }

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
    val host = uri.host?.trim()?.trim('.')?.lowercase().orEmpty()
    val path = uri.path?.trim()?.lowercase().orEmpty()

    if (host != "qzs.qq.com") {
        return false
    }

    return path == "/qzone/v5/loginsucc.html"
}

fun logCollectedCookies(attempt: Int, snapshot: CollectedCookies) {
    val keys = snapshot.keys.ifEmpty { listOf("(none)") }.joinToString(",")
    val hits = snapshot.hitUrls.ifEmpty { listOf("(none)") }.joinToString(" | ")
    Log.d(
        WEB_LOGIN_LOG_TAG,
        "cookie attempt=$attempt keys=$keys sampled=${snapshot.sampledUrls.size} hits=$hits",
    )
}

private fun buildCookieCandidateUrls(successUrl: String, observedUrls: Collection<String>): List<String> {
    val urls = linkedSetOf<String>()
    FIXED_COOKIE_URLS.forEach(urls::add)
    addCookieUrlCandidates(urls, successUrl)
    observedUrls.forEach { addCookieUrlCandidates(urls, it) }
    return urls.toList()
}

private fun addCookieUrlCandidates(target: LinkedHashSet<String>, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) {
        return
    }

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        target += trimmed
    }

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return
    val host = uri.host?.trim()?.trim('.') ?: return
    if (host.isBlank()) {
        return
    }

    val scheme = when (uri.scheme) {
        "http", "https" -> uri.scheme.orEmpty()
        else -> "https"
    }

    target += "$scheme://$host"

    val parts = host.split('.').filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val registrableDomain = parts.takeLast(2).joinToString(".")
        target += "$scheme://$registrableDomain"
    }
}

private fun parseCookieHeader(raw: String): Map<String, String> {
    val cookies = linkedMapOf<String, String>()
    val normalized = raw.replace("\r", ";").replace("\n", ";")
    for (part in normalized.split(";")) {
        val piece = part.trim()
        if (piece.isBlank()) {
            continue
        }

        val separator = piece.indexOf('=')
        if (separator <= 0 || separator >= piece.length - 1) {
            continue
        }

        val name = piece.substring(0, separator).trim()
        val value = piece.substring(separator + 1).trim()
        if (name.isNotBlank() && value.isNotBlank()) {
            cookies[name] = value
        }
    }

    return cookies
}

private fun firstCookieValue(cookies: Map<String, String>, vararg names: String): String {
    for (name in names) {
        val value = cookies[name].orEmpty().trim()
        if (value.isNotBlank()) {
            return value
        }
    }
    return ""
}
