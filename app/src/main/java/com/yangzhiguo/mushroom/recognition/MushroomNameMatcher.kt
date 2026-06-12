package com.yangzhiguo.mushroom.recognition

/**
 * 名称归一化 + 索引匹配（设计文档 §3.3）。
 *
 * 归一化规则（按顺序）：
 *  1. trim + lowercase
 *  2. 去除括号及括号内内容（处理 "Amanita muscaria (L.) Lam." 这类学名）
 *  3. 按空白分词
 *  4. 丢弃 sp. / sp / cf. / cf / aff. / aff 这类"未确定 / 近似"标记
 *  5. 用单空格拼接
 *
 * 匹配策略（设计文档 §3.3）：
 *  - 取候选 list 的前 3 个
 *  - 归一化后查内存 map
 *  - 第一个命中的返回；都 miss 返回 null
 */
object MushroomNameMatcher {

    private val QUALIFIER_TOKENS = setOf("sp.", "sp", "cf.", "cf", "aff.", "aff")
    private val PARENTHETICAL = Regex("\\s*\\([^)]*\\)\\s*")
    private val WHITESPACE = Regex("\\s+")

    /**
     * 把任意学名字符串归一化为索引键。失败（空字符串 / 全是 qualifier）也合法，
     * 返回值仅用作查 map；空键永远不会命中。
     */
    fun normalize(name: String): String {
        val trimmed = name.trim().lowercase()
        val noParen = trimmed.replace(PARENTHETICAL, " ").trim()
        val tokens = noParen.split(WHITESPACE)
            .filter { it.isNotBlank() }
            .filterNot { it in QUALIFIER_TOKENS }
        return tokens.joinToString(" ")
    }

    /**
     * 在 [index] 中查找第一个能命中的候选。
     *
     * @param candidates 大模型返回的候选（按置信度降序）
     * @param index 归一化名 → LocalMushroom 的内存 map
     * @return 第一个命中；前 3 个全 miss 返回 null
     */
    fun firstMatch(
        candidates: List<Candidate>,
        index: Map<String, LocalMushroom>,
    ): LocalMushroom? {
        return candidates.asSequence()
            .take(3)
            .mapNotNull { c ->
                val key = normalize(c.scientificName)
                if (key.isEmpty()) null else index[key]
            }
            .firstOrNull()
    }
}
