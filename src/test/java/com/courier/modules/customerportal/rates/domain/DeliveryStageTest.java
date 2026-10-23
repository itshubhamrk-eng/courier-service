package com.courier.modules.customerportal.rates.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.courier.modules.customerportal.rates.domain.DeliveryStage.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStageTest {

    @Test
    @DisplayName("reads Delhivery and Shiprocket wording into one vocabulary")
    void reads() {
        assertThat(of("Manifested")).isEqualTo(AWAITING_PICKUP);
        assertThat(of("NEW")).isEqualTo(AWAITING_PICKUP);
        assertThat(of("Pickup Scheduled")).isEqualTo(AWAITING_PICKUP);
        assertThat(of("Not Picked")).isEqualTo(AWAITING_PICKUP);
        assertThat(of("Picked Up")).isEqualTo(PICKED_UP);
        assertThat(of("In Transit")).isEqualTo(IN_TRANSIT);
        assertThat(of("Reached at Destination Hub")).isEqualTo(IN_TRANSIT);
        assertThat(of("Out For Delivery")).isEqualTo(OUT_FOR_DELIVERY);
        assertThat(of("Undelivered")).isEqualTo(OUT_FOR_DELIVERY);
        assertThat(of("Delivered")).isEqualTo(DELIVERED);
        assertThat(of("RTO Initiated")).isEqualTo(RETURNED);
        assertThat(of("RTO Delivered")).isEqualTo(RETURNED);
        assertThat(of("Canceled")).isEqualTo(CANCELLED);
        assertThat(of("")).isNull();
        assertThat(of("Lost")).isNull();
    }

    @Test
    @DisplayName("delivered and cancelled are finished; a return only once it is back")
    void finished() {
        assertThat(DeliveryStage.finished(DELIVERED, "Delivered")).isTrue();
        assertThat(DeliveryStage.finished(CANCELLED, "Cancelled")).isTrue();
        assertThat(DeliveryStage.finished(RETURNED, "RTO In Transit")).isFalse();
        assertThat(DeliveryStage.finished(RETURNED, "RTO Delivered")).isTrue();
        assertThat(DeliveryStage.finished(IN_TRANSIT, "In Transit")).isFalse();
    }
}
