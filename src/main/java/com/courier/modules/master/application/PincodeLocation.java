package com.courier.modules.master.application;

import java.util.UUID;

/** Geography ids behind a pincode's primary Area; any may be null when the chain is incomplete. */
public record PincodeLocation(UUID pincodeId, UUID areaId, UUID cityId, UUID districtId,
                              boolean serviceable, boolean codAvailable, boolean odaApplicable) {
}
