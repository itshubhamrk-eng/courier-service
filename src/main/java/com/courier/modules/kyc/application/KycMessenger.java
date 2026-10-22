package com.courier.modules.kyc.application;

import java.util.UUID;

/** Delivers one-time codes. Returns false when the channel is not set up (nothing was sent). */
public interface KycMessenger {

    boolean emailCode(UUID companyId, String email, String name, String code);

    boolean smsCode(UUID companyId, String mobile, String code);
}
