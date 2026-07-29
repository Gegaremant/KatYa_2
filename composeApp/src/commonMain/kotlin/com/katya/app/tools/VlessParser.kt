package com.katya.app.tools

import io.ktor.http.Url
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object VlessParser {
    fun generateXrayConfig(vlessUri: String, localSocksPort: Int = 10808, localHttpPort: Int = 10809): String {
        val url = Url(vlessUri)
        if (url.protocol.name != "vless") throw IllegalArgumentException("Not a vless URI")

        val uuid = url.user ?: throw IllegalArgumentException("Missing UUID")
        val host = url.host
        val port = url.port

        val params = url.parameters
        val type = params["type"] ?: "tcp"
        val security = params["security"] ?: "none"
        val sni = params["sni"] ?: ""
        val pbk = params["pbk"] ?: ""
        val sid = params["sid"] ?: ""
        val fp = params["fp"] ?: "chrome"

        return buildJsonObject {
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("port", localSocksPort)
                        put("listen", "127.0.0.1")
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            put("udp", true)
                        }
                    },
                )
                add(
                    buildJsonObject {
                        put("port", localHttpPort)
                        put("listen", "127.0.0.1")
                        put("protocol", "http")
                    },
                )
            }
            putJsonArray("outbounds") {
                add(
                    buildJsonObject {
                        put("protocol", "vless")
                        putJsonObject("settings") {
                            putJsonArray("vnext") {
                                add(
                                    buildJsonObject {
                                        put("address", host)
                                        put("port", port)
                                        putJsonArray("users") {
                                            add(
                                                buildJsonObject {
                                                    put("id", uuid)
                                                    put("encryption", "none")
                                                    put("flow", params["flow"] ?: "")
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        putJsonObject("streamSettings") {
                            put("network", type)
                            put("security", security)
                            if (security == "reality") {
                                putJsonObject("realitySettings") {
                                    put("serverName", sni)
                                    put("publicKey", pbk)
                                    put("shortId", sid)
                                    put("fingerprint", fp)
                                }
                            } else if (security == "tls") {
                                putJsonObject("tlsSettings") {
                                    put("serverName", sni)
                                    put("fingerprint", fp)
                                }
                            }

                            if (type == "ws") {
                                putJsonObject("wsSettings") {
                                    put("path", params["path"] ?: "/")
                                    putJsonObject("headers") {
                                        put("Host", params["host"] ?: sni)
                                    }
                                }
                            } else if (type == "grpc") {
                                putJsonObject("grpcSettings") {
                                    put("serviceName", params["serviceName"] ?: "")
                                    put("multiMode", params["mode"] == "multi")
                                }
                            }
                        }
                    },
                )
            }
        }.toString()
    }
}
