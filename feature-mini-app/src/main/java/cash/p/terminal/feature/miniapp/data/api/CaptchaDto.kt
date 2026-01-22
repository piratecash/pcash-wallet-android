package cash.p.terminal.feature.miniapp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CaptchaResponse(
    @SerialName("captcha_id")
    val captchaId: String,
    @SerialName("image_base64")
    val imageBase64: String,
    @SerialName("expires_in")
    val expiresIn: Long
)

@Serializable
data class VerifyCaptchaRequest(
    val code: String
)

@Serializable
data class VerifyCaptchaResponse(
    val valid: Boolean
)

@Serializable
data class ErrorResponse(
    val message: String? = null
)
