package com.thripleq.nume.core.repo

/** 详情类请求失败（传输错误 / 空响应 / 非法 JSON）。UI 据此展示失败态并允许重试。 */
class RequestFailedException(message: String = "request failed") : Exception(message)
