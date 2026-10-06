package com.thripleq.cirro.core.net

/**
 * Parsed response of a libnetease service call. `body` is the raw JSON
 * response payload; `code` is the API business code (200 on success, 520 on
 * transport error); `err` is 0 on success, 1 on transport error, 2 when the
 * body has no `code` field.
 */
class ApiResult(
    @JvmField val code: Int,
    @JvmField val err: Int,
    @JvmField val body: ByteArray,
)