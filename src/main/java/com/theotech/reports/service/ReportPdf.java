package com.theotech.reports.service;

import com.theotech.catalog.dto.ProductResponse;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.sales.dto.SaleResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Draws the sales report as an A4 PDF: logo + shop name, period, four figures, the tables. */
final class ReportPdf {

    private static final Color BRAND = new Color(0x4f, 0x46, 0xe5);
    private static final Color INK = new Color(0x1e, 0x29, 0x3b);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color HEAD_BG = new Color(0xee, 0xf2, 0xff);
    private static final Color ZEBRA = new Color(0xf8, 0xfa, 0xfc);
    private static final Color LINE = new Color(0xe2, 0xe8, 0xf0);
    private static final Color GREEN = new Color(0x15, 0x80, 0x3d);
    private static final Color RED = new Color(0xb9, 0x1c, 0x1c);
    private static final Color AMBER = new Color(0xb4, 0x53, 0x09);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportPdf() {
    }

    record Shop(String name, String address, String phone, byte[] logo) {
    }

    static byte[] render(Shop shop, String periodLabel, SalesReportResponse r, List<ProductResponse> lowStock, ZoneId zone) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.addTitle(shop.name() + " - sales report");
            doc.addAuthor(shop.name());
            doc.open();

            header(doc, shop, periodLabel, r, zone);
            figures(doc, r);

            section(doc, "Sales per product");
            if (r.rows().isEmpty()) {
                note(doc, "No sales in this period.");
            } else {
                PdfPTable t = table(new float[]{4f, 2.5f, 1f, 1.8f, 1.8f, 1.4f},
                        "Product", "Category", "Sold", "Sales", "Not paid", "Remaining");
                int i = 0;
                for (SalesReportResponse.Row row : r.rows()) {
                    Color bg = i++ % 2 == 0 ? Color.WHITE : ZEBRA;
                    t.addCell(cell(row.productName(), bold(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(row.categoryName() == null ? "-" : row.categoryName(), normal(9, MUTED), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(String.valueOf(row.quantitySold()), normal(9), Element.ALIGN_RIGHT, bg));
                    t.addCell(cell(money(row.totalSales()), normal(9), Element.ALIGN_RIGHT, bg));
                    boolean owed = row.unpaidSales().signum() > 0;
                    t.addCell(cell(owed ? money(row.unpaidSales()) : "-", normal(9, owed ? RED : MUTED), Element.ALIGN_RIGHT, bg));
                    t.addCell(cell(row.remainingStock() == null ? "deleted" : String.valueOf(row.remainingStock()), normal(9), Element.ALIGN_RIGHT, bg));
                }
                t.addCell(cell("TOTAL", bold(9), Element.ALIGN_LEFT, HEAD_BG));
                t.addCell(cell("", bold(9), Element.ALIGN_LEFT, HEAD_BG));
                t.addCell(cell(String.valueOf(r.totalQuantity()), bold(9), Element.ALIGN_RIGHT, HEAD_BG));
                t.addCell(cell(money(r.totalSales()), bold(9), Element.ALIGN_RIGHT, HEAD_BG));
                t.addCell(cell(r.totalUnpaid().signum() > 0 ? money(r.totalUnpaid()) : "-", bold(9, RED), Element.ALIGN_RIGHT, HEAD_BG));
                t.addCell(cell("", bold(9), Element.ALIGN_LEFT, HEAD_BG));
                doc.add(t);
            }

            section(doc, "Not paid");
            if (r.unpaidSales().isEmpty()) {
                note(doc, "Every sale in this period is paid.");
            } else {
                PdfPTable t = table(new float[]{1.2f, 2.2f, 3f, 3.5f, .9f, 1.8f},
                        "Receipt", "Date", "Customer", "Product", "Qty", "Amount");
                int i = 0;
                for (SaleResponse s : r.unpaidSales()) {
                    Color bg = i++ % 2 == 0 ? Color.WHITE : ZEBRA;
                    t.addCell(cell(s.receiptNo(), normal(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(DATE_TIME.format(s.soldAt().atZone(zone)), normal(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(s.customerName() == null ? "-" : s.customerName(), bold(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(s.productName(), normal(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(String.valueOf(s.quantity()), normal(9), Element.ALIGN_RIGHT, bg));
                    t.addCell(cell(money(s.total()), bold(9, RED), Element.ALIGN_RIGHT, bg));
                }
                doc.add(t);
            }

            section(doc, "Expenses");
            if (r.expensesByCategory().isEmpty()) {
                note(doc, "No expenses recorded in this period.");
            } else {
                PdfPTable t = table(new float[]{5f, 1.5f, 2.5f}, "Category", "Entries", "Amount");
                int i = 0;
                for (var c : r.expensesByCategory()) {
                    Color bg = i++ % 2 == 0 ? Color.WHITE : ZEBRA;
                    t.addCell(cell(c.category(), bold(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(String.valueOf(c.count()), normal(9), Element.ALIGN_RIGHT, bg));
                    t.addCell(cell(money(c.total()), normal(9), Element.ALIGN_RIGHT, bg));
                }
                t.addCell(cell("TOTAL", bold(9), Element.ALIGN_LEFT, HEAD_BG));
                t.addCell(cell("", bold(9), Element.ALIGN_LEFT, HEAD_BG));
                t.addCell(cell(money(r.totalExpenses()), bold(9), Element.ALIGN_RIGHT, HEAD_BG));
                doc.add(t);
            }

            section(doc, "Low stock");
            if (lowStock.isEmpty()) {
                note(doc, "No product is running low.");
            } else {
                PdfPTable t = table(new float[]{4f, 3f, 1.5f, 2f}, "Product", "Category", "Remaining", "Status");
                int i = 0;
                for (ProductResponse p : lowStock) {
                    Color bg = i++ % 2 == 0 ? Color.WHITE : ZEBRA;
                    boolean soldOut = p.availableStock() <= 0;
                    t.addCell(cell(p.name(), bold(9), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(p.categoryName(), normal(9, MUTED), Element.ALIGN_LEFT, bg));
                    t.addCell(cell(String.valueOf(p.availableStock()), normal(9), Element.ALIGN_RIGHT, bg));
                    t.addCell(cell(soldOut ? "OUT OF STOCK" : "LOW STOCK", bold(9, soldOut ? RED : AMBER), Element.ALIGN_LEFT, bg));
                }
                doc.add(t);
            }

            Paragraph foot = new Paragraph("Generated by " + shop.name() + " sales & stock tracker", normal(7, MUTED));
            foot.setSpacingBefore(18);
            doc.add(foot);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render the PDF report", e);
        }
    }

    // ---- building blocks -----------------------------------------------------------------------

    private static void header(Document doc, Shop shop, String periodLabel, SalesReportResponse r, ZoneId zone) throws DocumentException {
        PdfPTable head = new PdfPTable(new float[]{1.2f, 4f});
        head.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(0);
        logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (shop.logo() != null) {
            try {
                Image img = Image.getInstance(shop.logo());
                img.scaleToFit(90, 54);
                logoCell.addElement(img);
            } catch (Exception ignored) {
                // an unreadable logo must never break the report; the name still prints
            }
        }
        head.addCell(logoCell);

        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setPadding(0);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        text.addElement(new Paragraph(shop.name(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND)));
        String contact = String.join("  ·  ", nonBlank(shop.address()), nonBlank(shop.phone()));
        if (!contact.isBlank()) text.addElement(new Paragraph(contact.replace("  ·    ·  ", "  ·  ").trim(), normal(9, MUTED)));
        Paragraph sub = new Paragraph("Sales report  ·  " + ReportHtml.range(r.from(), r.to(), zone) +
                (periodLabel == null || periodLabel.isBlank() ? "" : "  ·  " + periodLabel), normal(10, INK));
        sub.setSpacingBefore(4);
        text.addElement(sub);
        head.addCell(text);
        doc.add(head);

        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell line = new PdfPCell(new Phrase(""));
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderColor(BRAND);
        line.setBorderWidth(1.5f);
        line.setFixedHeight(6);
        rule.addCell(line);
        rule.setSpacingAfter(10);
        doc.add(rule);
    }

    private static void figures(Document doc, SalesReportResponse r) throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        figure(t, "ITEMS SOLD", String.valueOf(r.totalQuantity()), BRAND);
        figure(t, "TOTAL SALES", money(r.totalSales()), GREEN);
        figure(t, "PAID", money(r.totalPaid()), GREEN);
        figure(t, "NOT PAID", money(r.totalUnpaid()), r.totalUnpaid().signum() > 0 ? RED : MUTED);
        doc.add(t);

        PdfPTable t2 = new PdfPTable(4);
        t2.setWidthPercentage(100);
        t2.setSpacingAfter(8);
        figure(t2, "EXPENSES", money(r.totalExpenses()), r.totalExpenses().signum() > 0 ? AMBER : MUTED);
        figure(t2, "SALES MINUS EXPENSES", money(r.netAfterExpenses()), r.netAfterExpenses().signum() >= 0 ? GREEN : RED);
        PdfPCell blank = new PdfPCell(new Phrase(""));
        blank.setBorder(Rectangle.NO_BORDER);
        t2.addCell(blank);
        t2.addCell(blank);
        doc.add(t2);
    }

    private static void figure(PdfPTable t, String label, String value, Color color) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setPadding(8);
        c.addElement(new Paragraph(label, normal(7, MUTED)));
        c.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, color)));
        t.addCell(c);
    }

    private static void section(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK));
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    private static void note(Document doc, String text) throws DocumentException {
        doc.add(new Paragraph(text, normal(9, MUTED)));
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (int i = 0; i < headers.length; i++) {
            boolean numeric = headers[i].equals("Sold") || headers[i].equals("Sales") || headers[i].equals("Not paid")
                    || headers[i].equals("Remaining") || headers[i].equals("Qty") || headers[i].equals("Amount")
                    || headers[i].equals("Entries");
            t.addCell(cell(headers[i].toUpperCase(), bold(7, MUTED), numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, HEAD_BG));
        }
        return t;
    }

    private static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(bg);
        c.setBorderColor(LINE);
        c.setBorderWidth(.5f);
        c.setPadding(5);
        return c;
    }

    private static Font normal(float size) {
        return normal(size, INK);
    }

    private static Font normal(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, color);
    }

    private static Font bold(float size) {
        return bold(size, INK);
    }

    private static Font bold(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, color);
    }

    private static String money(BigDecimal v) {
        return ReportHtml.money(v);
    }

    private static String nonBlank(String s) {
        return s == null ? "" : s.trim();
    }
}
