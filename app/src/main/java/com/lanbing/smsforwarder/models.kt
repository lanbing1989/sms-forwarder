package com.lanbing.smsforwarder

/**
 * Shared model types for channels and keyword rules.
 *
 * Note:
 * - For SMS channels, `target` stores the destination phone number,
 *   and `simSubscriptionId` stores the subscriptionId (subId) to use for sending.
 *   If simSubscriptionId is null, the default SMS manager will be used.
 */
enum class ChannelType { WECHAT, DINGTALK, GENERIC_WEBHOOK, SMS }

data class Channel(
    val id: String,
    val name: String,
    val type: ChannelType,
    val target: String,           // webhook URL for webhooks, phone number for SMS
    val simSubscriptionId: Int? = null // subscriptionId for dual-SIM selection (nullable, null => default)
)

data class KeywordConfig(
    val id: String,
    val keyword: String, // empty string means match-all
    val channelId: String
)