package com.digicash.app.qr.model;

import com.google.gson.annotations.SerializedName;

/**
 * Wire-format data embedded in a DIGICASH Receiver Identity QR code.
 *
 * receiverPublicKeyId carries the receiver's full Base64-encoded (X.509)
 * public key, consistent with PaymentQrData.senderPublicKeyId, so any
 * component that needs to address or later verify this receiver has the
 * key material available directly from this QR.
 *
 * This payload is NOT signed: its authenticity relies on the in-person
 * QR display/scan channel itself - a documented limitation of this
 * prototype, not an oversight.
 */
public class ReceiverIdentityQrData {

    /** Fixed discriminator so arbitrary/foreign QR content is never mistaken for an identity QR. */
    public static final String TYPE_DISCRIMINATOR = "DIGICASH_IDENTITY_V1";

    @SerializedName("type")
    private String type = TYPE_DISCRIMINATOR;

    @SerializedName("protocolVersion")
    private int protocolVersion;

    @SerializedName("receiverId")
    private String receiverId;

    @SerializedName("receiverPublicKeyId")
    private String receiverPublicKeyId;

    public ReceiverIdentityQrData(int protocolVersion, String receiverId, String receiverPublicKeyId) {
        this.type = TYPE_DISCRIMINATOR;
        this.protocolVersion = protocolVersion;
        this.receiverId = receiverId;
        this.receiverPublicKeyId = receiverPublicKeyId;
    }

    public String getType() {
        return type;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getReceiverPublicKeyId() {
        return receiverPublicKeyId;
    }
}
