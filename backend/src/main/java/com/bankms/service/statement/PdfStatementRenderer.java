package com.bankms.service.statement;

import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.util.Money;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Account statement as a PDF (OpenPDF). The standard Helvetica font has no rupee glyph, so amounts
 * are printed as plain numbers under an "INR" heading.
 */
@Component
public class PdfStatementRenderer {

    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color RULE = new Color(0xE5, 0xE7, 0xEB);
    private static final Color HEADER_FILL = new Color(0x0F, 0x3D, 0x3E);
    private static final Color STRIPE = new Color(0xF7, 0xF8, 0xFA);
    private static final Color DEBIT = new Color(0xB4, 0x23, 0x18);
    private static final Color CREDIT = new Color(0x06, 0x76, 0x47);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.NORMAL, INK);
    private final Font label = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, MUTED);
    private final Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, INK);
    private final Font body = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, INK);
    private final Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
    private final Font debit = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, DEBIT);
    private final Font credit = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, CREDIT);

    public byte[] render(StatementData data, ZoneId zone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();

        Paragraph heading = new Paragraph(data.bankName() + "  -  Account Statement", title);
        heading.setSpacingAfter(4);
        document.add(heading);
        Paragraph period = new Paragraph("Period: %s to %s    Generated: %s".formatted(
                DATE.format(data.from()), DATE.format(data.to()), STAMP.format(data.generatedAt().atZone(zone))), label);
        period.setSpacingAfter(14);
        document.add(period);

        PdfPTable details = new PdfPTable(new float[]{1, 1, 1});
        details.setWidthPercentage(100);
        details.addCell(field("Account holder", data.holderName()));
        details.addCell(field("Customer ID", data.customerNumber()));
        details.addCell(field("Account number", data.accountNumber()));
        details.addCell(field("Account type", data.accountType()));
        details.addCell(field("Branch", data.branchName()));
        details.addCell(field("IFSC", data.ifsc()));
        details.setSpacingAfter(10);
        document.add(details);

        PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
        summary.setWidthPercentage(100);
        summary.addCell(field("Opening balance (INR)", amount(data.openingBalance())));
        summary.addCell(field("Total debits (INR)", amount(data.totalDebits())));
        summary.addCell(field("Total credits (INR)", amount(data.totalCredits())));
        summary.addCell(field("Closing balance (INR)", amount(data.closingBalance())));
        summary.setSpacingAfter(14);
        document.add(summary);

        PdfPTable table = new PdfPTable(new float[]{1.1f, 2.4f, 2.7f, 1.2f, 1.2f, 1.3f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String column : new String[]{"Date", "Reference", "Description", "Debit", "Credit", "Balance"}) {
            PdfPCell cell = new PdfPCell(new Phrase(column, head));
            cell.setBackgroundColor(HEADER_FILL);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(6);
            if (!column.equals("Date") && !column.equals("Reference") && !column.equals("Description")) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            table.addCell(cell);
        }
        int index = 0;
        for (Transaction t : data.rows()) {
            Color fill = index++ % 2 == 0 ? Color.WHITE : STRIPE;
            boolean isDebit = t.getDirection() == TransactionDirection.DEBIT;
            table.addCell(row(DATE.format(t.getValueDate()), body, fill, Element.ALIGN_LEFT));
            table.addCell(row(t.getReferenceNumber(), body, fill, Element.ALIGN_LEFT));
            table.addCell(row(describe(t), body, fill, Element.ALIGN_LEFT));
            table.addCell(row(isDebit ? amount(t.getAmount()) : "", debit, fill, Element.ALIGN_RIGHT));
            table.addCell(row(isDebit ? "" : amount(t.getAmount()), credit, fill, Element.ALIGN_RIGHT));
            table.addCell(row(amount(t.getBalanceAfter()), body, fill, Element.ALIGN_RIGHT));
        }
        if (data.rows().isEmpty()) {
            PdfPCell empty = row("No transactions in this period.", label, Color.WHITE, Element.ALIGN_CENTER);
            empty.setColspan(6);
            empty.setPadding(14);
            table.addCell(empty);
        }
        document.add(table);

        Paragraph footer = new Paragraph(
                "This is a computer-generated statement and does not require a signature. "
                        + "Please report any discrepancy to your branch within 30 days.", label);
        footer.setSpacingBefore(16);
        document.add(footer);
        document.close();
        return out.toByteArray();
    }

    private PdfPCell field(String name, String text) {
        Phrase phrase = new Phrase();
        phrase.add(new Phrase(name + "\n", label));
        phrase.add(new Phrase(text == null ? "-" : text, value));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPaddingBottom(7);
        cell.setPaddingTop(4);
        return cell;
    }

    private static PdfPCell row(String text, Font font, Color fill, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setBackgroundColor(fill);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static String describe(Transaction t) {
        String description = t.getDescription() == null ? t.getType().name() : t.getDescription();
        return t.getCounterpartyName() != null && !description.contains(t.getCounterpartyName())
                ? description + " - " + t.getCounterpartyName()
                : description;
    }

    /** Plain number with Indian grouping (the PDF font has no rupee glyph). */
    static String amount(BigDecimal value) {
        return value == null ? "" : Money.group(value);
    }
}
