package com.theotech.reports.service;

import com.theotech.catalog.dto.ProductResponse;
import com.theotech.expenses.dto.ExpenseCategoryTotal;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.sales.dto.SaleResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the sales report as an .xlsx workbook: one sheet per table, every table with a bold shaded header
 * row, thin borders on every cell, money as numbers with thousands separators, frozen header, fixed widths
 * (no auto-size: that needs a font system the server may not have).
 */
final class ReportExcel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportExcel() {
    }

    /** All the cell styles of one workbook (styles are per workbook in POI). */
    private record Styles(CellStyle title, CellStyle subtitle, CellStyle header, CellStyle headerRight, CellStyle text,
                          CellStyle bold, CellStyle integer, CellStyle money, CellStyle moneyBold, CellStyle integerBold,
                          CellStyle red, CellStyle moneyRed) {
    }

    static byte[] render(String company, String periodLabel, SalesReportResponse r, List<ProductResponse> lowStock, ZoneId zone) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = styles(wb);
            String period = ReportHtml.range(r.from(), r.to(), zone);

            // ---- Summary ------------------------------------------------------------------------
            Sheet summary = wb.createSheet("Summary");
            int row = titleBlock(summary, s, company, "Sales report  ·  " + period + (periodLabel == null || periodLabel.isBlank() ? "" : "  ·  " + periodLabel), 2);
            header(summary, row++, s, new String[]{"Figure", "Value"}, new boolean[]{false, true});
            row = kv(summary, row, s, "Items sold", r.totalQuantity());
            row = kv(summary, row, s, "Total sales (RWF)", r.totalSales(), s.money());
            row = kv(summary, row, s, "Paid (RWF)", r.totalPaid(), s.money());
            row = kv(summary, row, s, "Not paid (RWF)", r.totalUnpaid(), r.totalUnpaid().signum() > 0 ? s.moneyRed() : s.money());
            row = kv(summary, row, s, "Expenses (RWF)", r.totalExpenses(), s.money());
            row = kv(summary, row, s, "Sales minus expenses (RWF)", r.netAfterExpenses(), r.netAfterExpenses().signum() >= 0 ? s.moneyBold() : s.moneyRed());
            kv(summary, row, s, "Products still to be paid for", r.unpaidSales().size());
            widths(summary, 34, 20);

            // ---- Sales per product ----------------------------------------------------------------
            Sheet sales = wb.createSheet("Sales per product");
            row = titleBlock(sales, s, company, "Sales per product  ·  " + period, 6);
            header(sales, row++, s, new String[]{"Product", "Category", "Sold", "Sales (RWF)", "Not paid (RWF)", "Remaining stock"},
                    new boolean[]{false, false, true, true, true, true});
            int first = row;
            for (SalesReportResponse.Row p : r.rows()) {
                Row x = sales.createRow(row++);
                text(x, 0, p.productName(), s.text());
                text(x, 1, p.categoryName() == null ? "-" : p.categoryName(), s.text());
                number(x, 2, p.quantitySold(), s.integer());
                number(x, 3, p.totalSales(), s.money());
                number(x, 4, p.unpaidSales(), p.unpaidSales().signum() > 0 ? s.moneyRed() : s.money());
                if (p.remainingStock() == null) text(x, 5, "deleted", s.text()); else number(x, 5, p.remainingStock(), s.integer());
            }
            if (r.rows().isEmpty()) { Row x = sales.createRow(row++); text(x, 0, "No sales in this period", s.text()); for (int c = 1; c < 6; c++) text(x, c, "", s.text()); }
            Row total = sales.createRow(row);
            text(total, 0, "TOTAL", s.bold());
            text(total, 1, "", s.bold());
            number(total, 2, r.totalQuantity(), s.integerBold());
            number(total, 3, r.totalSales(), s.moneyBold());
            number(total, 4, r.totalUnpaid(), s.moneyBold());
            text(total, 5, "", s.bold());
            sales.createFreezePane(0, first);
            widths(sales, 32, 22, 10, 16, 16, 16);

            // ---- Not paid -------------------------------------------------------------------------
            Sheet unpaid = wb.createSheet("Not paid");
            row = titleBlock(unpaid, s, company, "Sales not yet paid  ·  " + period, 6);
            header(unpaid, row++, s, new String[]{"Receipt", "Date", "Customer", "Product", "Qty", "Amount (RWF)"},
                    new boolean[]{false, false, false, false, true, true});
            first = row;
            for (SaleResponse u : r.unpaidSales()) {
                Row x = unpaid.createRow(row++);
                text(x, 0, u.receiptNo(), s.text());
                text(x, 1, DATE_TIME.format(u.soldAt().atZone(zone)), s.text());
                text(x, 2, u.customerName() == null ? "-" : u.customerName(), s.bold());
                text(x, 3, u.productName(), s.text());
                number(x, 4, u.quantity(), s.integer());
                number(x, 5, u.total(), s.moneyRed());
            }
            if (r.unpaidSales().isEmpty()) { Row x = unpaid.createRow(row++); text(x, 0, "Every sale in this period is paid", s.text()); for (int c = 1; c < 6; c++) text(x, c, "", s.text()); }
            unpaid.createFreezePane(0, first);
            widths(unpaid, 12, 18, 26, 30, 8, 16);

            // ---- Expenses -------------------------------------------------------------------------
            Sheet expenses = wb.createSheet("Expenses");
            row = titleBlock(expenses, s, company, "Expenses by category  ·  " + period, 3);
            header(expenses, row++, s, new String[]{"Category", "Entries", "Amount (RWF)"}, new boolean[]{false, true, true});
            first = row;
            for (ExpenseCategoryTotal c : r.expensesByCategory()) {
                Row x = expenses.createRow(row++);
                text(x, 0, c.category(), s.text());
                number(x, 1, c.count(), s.integer());
                number(x, 2, c.total(), s.money());
            }
            if (r.expensesByCategory().isEmpty()) { Row x = expenses.createRow(row++); text(x, 0, "No expenses in this period", s.text()); text(x, 1, "", s.text()); text(x, 2, "", s.text()); }
            Row etotal = expenses.createRow(row);
            text(etotal, 0, "TOTAL", s.bold());
            text(etotal, 1, "", s.bold());
            number(etotal, 2, r.totalExpenses(), s.moneyBold());
            expenses.createFreezePane(0, first);
            widths(expenses, 30, 10, 16);

            // ---- Low stock ------------------------------------------------------------------------
            Sheet low = wb.createSheet("Low stock");
            row = titleBlock(low, s, company, "Products running low", 4);
            header(low, row++, s, new String[]{"Product", "Category", "Remaining", "Status"}, new boolean[]{false, false, true, false});
            first = row;
            for (ProductResponse p : lowStock) {
                Row x = low.createRow(row++);
                boolean soldOut = p.availableStock() <= 0;
                text(x, 0, p.name(), s.text());
                text(x, 1, p.categoryName(), s.text());
                number(x, 2, p.availableStock(), s.integer());
                text(x, 3, soldOut ? "OUT OF STOCK" : "LOW STOCK", soldOut ? s.red() : s.bold());
            }
            if (lowStock.isEmpty()) { Row x = low.createRow(row++); text(x, 0, "No product is running low", s.text()); for (int c = 1; c < 4; c++) text(x, c, "", s.text()); }
            low.createFreezePane(0, first);
            widths(low, 32, 22, 12, 16);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the Excel report", e);
        }
    }

    // ---- building blocks -----------------------------------------------------------------------

    private static Styles styles(XSSFWorkbook wb) {
        Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.INDIGO.getIndex());
        Font subtitleFont = wb.createFont(); subtitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        Font boldFont = wb.createFont(); boldFont.setBold(true);
        Font redFont = wb.createFont(); redFont.setBold(true); redFont.setColor(IndexedColors.DARK_RED.getIndex());
        Font headerFont = wb.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle title = wb.createCellStyle(); title.setFont(titleFont);
        CellStyle subtitle = wb.createCellStyle(); subtitle.setFont(subtitleFont);

        CellStyle header = bordered(wb);
        header.setFont(headerFont);
        header.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x4f, (byte) 0x46, (byte) 0xe5}, null));
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.LEFT);
        CellStyle headerRight = wb.createCellStyle(); headerRight.cloneStyleFrom(header); headerRight.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle text = bordered(wb);
        CellStyle bold = bordered(wb); bold.setFont(boldFont);
        CellStyle red = bordered(wb); red.setFont(redFont);

        short intFmt = wb.createDataFormat().getFormat("#,##0");
        CellStyle integer = bordered(wb); integer.setDataFormat(intFmt); integer.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle integerBold = bordered(wb); integerBold.setDataFormat(intFmt); integerBold.setAlignment(HorizontalAlignment.RIGHT); integerBold.setFont(boldFont);
        CellStyle money = bordered(wb); money.setDataFormat(intFmt); money.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle moneyBold = bordered(wb); moneyBold.setDataFormat(intFmt); moneyBold.setAlignment(HorizontalAlignment.RIGHT); moneyBold.setFont(boldFont);
        CellStyle moneyRed = bordered(wb); moneyRed.setDataFormat(intFmt); moneyRed.setAlignment(HorizontalAlignment.RIGHT); moneyRed.setFont(redFont);

        return new Styles(title, subtitle, header, headerRight, text, bold, integer, money, moneyBold, integerBold, red, moneyRed);
    }

    private static CellStyle bordered(XSSFWorkbook wb) {
        CellStyle c = wb.createCellStyle();
        c.setBorderTop(BorderStyle.THIN);
        c.setBorderBottom(BorderStyle.THIN);
        c.setBorderLeft(BorderStyle.THIN);
        c.setBorderRight(BorderStyle.THIN);
        short grey = IndexedColors.GREY_40_PERCENT.getIndex();
        c.setTopBorderColor(grey); c.setBottomBorderColor(grey); c.setLeftBorderColor(grey); c.setRightBorderColor(grey);
        c.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        return c;
    }

    /** Company name and subtitle across the table width; returns the row index where the table starts. */
    private static int titleBlock(Sheet sheet, Styles s, String company, String subtitle, int columns) {
        Row r0 = sheet.createRow(0);
        Cell c0 = r0.createCell(0); c0.setCellValue(company); c0.setCellStyle(s.title());
        Row r1 = sheet.createRow(1);
        Cell c1 = r1.createCell(0); c1.setCellValue(subtitle); c1.setCellStyle(s.subtitle());
        if (columns > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns - 1));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columns - 1));
        }
        return 3;
    }

    private static void header(Sheet sheet, int rowIndex, Styles s, String[] labels, boolean[] numeric) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);
        for (int i = 0; i < labels.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(labels[i]);
            c.setCellStyle(numeric[i] ? s.headerRight() : s.header());
        }
    }

    private static int kv(Sheet sheet, int rowIndex, Styles s, String label, long value) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, s.text());
        number(row, 1, value, s.integer());
        return rowIndex + 1;
    }

    private static int kv(Sheet sheet, int rowIndex, Styles s, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, s.text());
        number(row, 1, value, style);
        return rowIndex + 1;
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void number(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0d : value.doubleValue());
        c.setCellStyle(style);
    }

    /** Column widths in characters. */
    private static void widths(Sheet sheet, int... chars) {
        for (int i = 0; i < chars.length; i++) sheet.setColumnWidth(i, chars[i] * 256);
    }
}
