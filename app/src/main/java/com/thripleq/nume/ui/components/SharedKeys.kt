package com.thripleq.nume.ui.components

/**
 * 共享元素键的统一来源：发起端（列表行）与目标端（详情页）必须取到**完全相同**的键，
 * 否则不会识别为同一个元素。集中定义，避免两端手写字符串漂移。
 */
object SharedKeys {
    fun artistAvatar(id: String): String = "nume:artist-avatar:$id"
}
