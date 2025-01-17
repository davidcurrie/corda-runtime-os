package net.corda.membership.client.dto

enum class RegistrationStatusDto {
    NEW,
    PENDING_MEMBER_VERIFICATION,
    PENDING_APPROVAL_FLOW,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    DECLINED,
    APPROVED
}
