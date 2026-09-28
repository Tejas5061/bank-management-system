package com.bankms.service.statement;

public record StatementFile(String filename, String contentType, byte[] content) {
}
