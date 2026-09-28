package com.bankms.service.statement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StatementRenderingTest {

    @Test
    void csvCellsThatLookLikeFormulasAreNeutralised() {
        assertThat(CsvStatementRenderer.safe("=HYPERLINK(\"http://evil\")")).startsWith("'=");
        assertThat(CsvStatementRenderer.safe("+cmd")).startsWith("'+");
        assertThat(CsvStatementRenderer.safe("@SUM(A1)")).startsWith("'@");
        assertThat(CsvStatementRenderer.safe("House rent")).isEqualTo("House rent");
    }

    @Test
    void pdfAmountsUseIndianDigitGrouping() {
        assertThat(PdfStatementRenderer.amount(new BigDecimal("295704"))).isEqualTo("2,95,704.00");
        assertThat(PdfStatementRenderer.amount(new BigDecimal("12345678.9"))).isEqualTo("1,23,45,678.90");
        assertThat(PdfStatementRenderer.amount(new BigDecimal("999"))).isEqualTo("999.00");
    }
}
