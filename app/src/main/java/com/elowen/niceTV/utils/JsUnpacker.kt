package com.elowen.niceTV.utils

import android.util.Log
import java.util.regex.Pattern

/**
 * JsUnpacker: 用于从 HTML 中提取 Dean Edwards Packer 混淆的 JavaScript
 * 并对其进行解混淆的工具类。
 * [深度还原自 Java 老祖版本]
 */
object JsUnpacker {

    // 严格遵循老祖定义的模式，但增加对 script 标签属性的兼容性
    private val PACKER_EXTRACT_PATTERN = Pattern.compile(
        "eval\\s*\\(\\s*function\\s*\\(p,a,c,k,e,[rd]\\)[\\s\\S]*?return p\\}\\s*\\(.*?\\.split\\('\\|'\\)\\)\\)",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    private val PACKER_PARSE_PATTERN = Pattern.compile(
        "\\}\\s*\\('((?:[^'\\\\]|\\\\.)*)',(\\d+),(\\d+),'((?:[^'\\\\]|\\\\.)*)'\\.split\\('\\|'\\)",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    fun unpackHtmlScript(htmlContent: String): String {
        val packedJs = extractPackedScript(htmlContent)
        if (packedJs.startsWith("Error")) {
            return packedJs
        }
        return unpack(packedJs)
    }

    private fun extractPackedScript(htmlContent: String): String {
        val matcher = PACKER_EXTRACT_PATTERN.matcher(htmlContent)
        return if (matcher.find()) {
            matcher.group() ?: ""
        } else {
            "Error: Could not find the packed script (eval(function...) pattern) in the HTML content."
        }
    }

    private fun unpack(packedJs: String): String {
        try {
            val matcher = PACKER_PARSE_PATTERN.matcher(packedJs)
            if (!matcher.find()) {
                return "Error: Could not parse packer format. (Failed to find P, A, C, K parameters)"
            }

            // 提取参数 (P, A, C, K)
            val payloadRaw = matcher.group(1) ?: ""
            val radix = matcher.group(2)?.toInt() ?: 10
            // val count = matcher.group(3) // C 不使用
            val keywordsRaw = matcher.group(4) ?: ""

            // 1. 处理 Payload 中的转义字符 (严格同步老祖)
            val payload = payloadRaw
                .replace("\\'", "'")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")

            // 2. 构建字典 (K 数组) - 使用 -1 保持空字符串
            val keywords = keywordsRaw.split("|") // Kotlin split literal 默认行为类似 Java split regex -1

            // 3. 替换 Payload 中的编码
            val wordPattern = Pattern.compile("\\b\\w+\\b")
            val wordMatcher = wordPattern.matcher(payload)

            val decoded = StringBuilder()
            var lastAppendPosition = 0

            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                // 添加非单词部分
                decoded.append(payload, lastAppendPosition, wordMatcher.start())

                var replacement = word
                try {
                    // 将单词视作 radix 进制的数字
                    val index = Integer.parseInt(word, radix)

                    // 如果下标在字典范围内，且字典对应值不为空，则替换
                    if (index < keywords.size) {
                        val valStr = keywords[index]
                        if (valStr.isNotEmpty()) {
                            replacement = valStr
                        }
                    }
                } catch (e: NumberFormatException) {
                    // 如果不是数字编码，保留原样
                }

                decoded.append(replacement)
                lastAppendPosition = wordMatcher.end()
            }

            // 追加剩余部分
            decoded.append(payload.substring(lastAppendPosition))
            return decoded.toString()

        } catch (e: Exception) {
            return "Error during JS unpacking: ${e.message}"
        }
    }

    private val LINKS_PATTERN = Pattern.compile("var\\s+links\\s*=\\s*\\{.*?\\};", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    fun getHighestPriorityHlsUrl(jsContent: String, baseUrl: String): Array<String> {
        val linksMatcher = LINKS_PATTERN.matcher(jsContent)
        if (!linksMatcher.find()) {
            return arrayOf("Error: Could not find the 'var links={...};' block in the content.")
        }
        val linksBlock = linksMatcher.group(0) ?: ""
        val priorities = arrayOf("hls4", "hls2", "hls3")
        val result = mutableListOf<String>()

        for (priority in priorities) {
            val pattern = Pattern.compile("['\"]$priority['\"]\\s*:\\s*['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(linksBlock)
            if (matcher.find()) {
                val link = matcher.group(1)
                if (!link.isNullOrEmpty()) {
                    if (priority == "hls4" && link.startsWith("/")) {
                        val base = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                        result.add(base + link)
                    } else {
                        result.add(link)
                    }
                }
            }
        }

        return if (result.isEmpty()) {
            arrayOf("Error: Could not find any valid HLS link (hls4, hls3, hls2).")
        } else {
            result.toTypedArray()
        }
    }

    fun getFullHls4UrlFromHtml(htmlContent: String): Array<String> {
        val jsContent = unpackHtmlScript(htmlContent)
        
        // 如果解包失败，尝试直接捕获 var url = '...' (这是主站最近的一些变动，老祖可能还没遇到但我们要加上)
        if (jsContent.startsWith("Error")) {
            val urlPattern = Pattern.compile("var\\s+url\\s*=\\s*['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE)
            val urlMatcher = urlPattern.matcher(htmlContent)
            if (urlMatcher.find()) {
                val directUrl = urlMatcher.group(1) ?: ""
                if (directUrl.isNotEmpty()) {
                    return arrayOf(directUrl)
                }
            }
            return arrayOf(jsContent)
        }
        
        // Log.d("JsUnpacker", "解包后的JS内容: $jsContent")
        return getHighestPriorityHlsUrl(jsContent, "https://fc2stream.tv")
    }
}
