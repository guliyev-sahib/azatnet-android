package com.v2ray.ang.handler

import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.util.Utils

/**
 * Built-in AzatNet endpoint when [MmkvManager.decodeAllServerList] is empty.
 * Profile is built from a standard VLESS share link (same path as QR/import).
 */
object AzatNetDefaultServer {

    const val GUID = "8ad39014-ff27-4172-8211-db3b8bb9fd19"

    private const val ADDRESS = "vpn.bilgibox.xyz"
    private const val PORT = "443"
    private const val FLOW = ""
    private const val WS_PATH = "/vpn"
    private const val WS_HOST = "vpn.bilgibox.xyz"
    private const val SNI = "vpn.bilgibox.xyz"
    private const val FINGERPRINT = "chrome"

    /**
     * Standard VLESS URI parsed by [VlessFmt.parse].
     */
    fun createProfile(): ProfileItem {
        val shareLink = buildVlessShareLink()
        val parsed = VlessFmt.parse(shareLink)
        if (parsed != null) {
            parsed.description = AngConfigManager.generateDescription(parsed)
            return parsed
        }
        return createProfileManualFallback()
    }

    private fun buildVlessShareLink(): String {
        return buildString {
            append("vless://")
            append(GUID)
            append("@")
            append(ADDRESS)
            append(":")
            append(PORT)
            append("?encryption=none")
            append("&security=tls")
            append("&sni=").append(Utils.encodeURIComponent(SNI))
            append("&fp=").append(FINGERPRINT)
            append("&type=").append(NetworkType.WS.type)
            append("&headerType=none")
            append("&path=").append(Utils.encodeURIComponent(WS_PATH))
            append("&host=").append(Utils.encodeURIComponent(WS_HOST))
            append("&flow=").append(Utils.encodeURIComponent(FLOW))
            append("#").append(Utils.encodeURIComponent("AzatNet"))
        }
    }

    private fun createProfileManualFallback(): ProfileItem {
        return ProfileItem.create(EConfigType.VLESS).apply {
            remarks = "AzatNet"
            server = ADDRESS
            serverPort = PORT
            password = GUID
            method = "none"
            flow = FLOW
            network = NetworkType.WS.type
            headerType = "none"
            security = "tls"
            sni = SNI
            fingerPrint = FINGERPRINT
            path = WS_PATH
            host = WS_HOST
            insecure = false
            description = AngConfigManager.generateDescription(this)
        }
    }
}
