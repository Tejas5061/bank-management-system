package com.bankms.service.ledger;

import com.bankms.entity.TransactionChannel;

/** Descriptive fields of a ledger entry; the money fields are passed separately. */
public record PostingDetails(
        TransactionChannel channel,
        String description,
        String counterpartyAccount,
        String counterpartyName,
        String counterpartyIfsc,
        Long initiatedBy) {

    public static PostingDetails system(String description) {
        return new PostingDetails(TransactionChannel.SYSTEM, description, null, null, null, null);
    }

    public static PostingDetails branch(String description, Long tellerUserId) {
        return new PostingDetails(TransactionChannel.BRANCH, description, null, null, null, tellerUserId);
    }

    public static PostingDetails online(String description, Long userId, String counterpartyAccount,
                                        String counterpartyName, String counterpartyIfsc) {
        return new PostingDetails(TransactionChannel.ONLINE, description, counterpartyAccount,
                counterpartyName, counterpartyIfsc, userId);
    }

    public PostingDetails withCounterparty(String account, String name, String ifsc) {
        return new PostingDetails(channel, description, account, name, ifsc, initiatedBy);
    }
}
