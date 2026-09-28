package com.bankms.service.statement;

import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class CsvStatementRenderer {

    private static final String[] HEADER = {
            "Date", "Reference", "Description", "Type", "Debit", "Credit", "Balance", "Counterparty"};

    public byte[] render(StatementData data) {
        StringWriter out = new StringWriter();
        out.write('﻿'); // BOM so Excel opens UTF-8 (the rupee sign, Indian names) correctly
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).get())) {
            for (Transaction t : data.rows()) {
                boolean debit = t.getDirection() == TransactionDirection.DEBIT;
                printer.printRecord(
                        t.getValueDate(),
                        t.getReferenceNumber(),
                        safe(t.getDescription()),
                        t.getType(),
                        debit ? t.getAmount() : "",
                        debit ? "" : t.getAmount(),
                        t.getBalanceAfter(),
                        safe(t.getCounterpartyName()));
            }
            printer.println();
            printer.printRecord("Opening balance", "", "", "", "", "", data.openingBalance(), "");
            printer.printRecord("Total debits", "", "", "", data.totalDebits(), "", "", "");
            printer.printRecord("Total credits", "", "", "", "", data.totalCredits(), "", "");
            printer.printRecord("Closing balance", "", "", "", "", "", data.closingBalance(), "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CSV/formula injection guard: a description such as "=HYPERLINK(...)" typed into a transfer
     * remark would otherwise execute as a formula when the customer opens the file in Excel.
     */
    static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value
                : value;
    }
}
