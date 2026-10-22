package com.courier.modules.kyc.application;

/** A document's bytes for download. */
public record KycFile(String fileName, String contentType, byte[] content) {
}
