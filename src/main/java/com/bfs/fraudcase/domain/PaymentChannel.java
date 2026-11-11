package com.bfs.fraudcase.domain;

/**
 * Payment channel.
 *
 * @author Venkateshwara Doijode
 */
public enum PaymentChannel {
    CARD_NOT_PRESENT,
    CARD_PRESENT,
    CONTACTLESS,
    ATM,
    ONLINE_BANKING,
    UPI
}
