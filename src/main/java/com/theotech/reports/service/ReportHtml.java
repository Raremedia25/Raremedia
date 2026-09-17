package com.theotech.reports.service;

import com.theotech.catalog.dto.ProductResponse;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.sales.dto.SaleResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders the sales report as a self-contained HTML e-mail (inline styles: mail clients ignore stylesheets). */
final class ReportHtml {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String TD = "padding:6px 8px;border-bottom:1px solid #e5e7eb;";
    private static final String TH = "padding:6px 8px;border-bottom:2px solid #cbd5e1;text-align:left;background:#f1f5f9;";
    private static final String NUM = "text-align:right;white-space:nowrap;";

    private ReportHtml() {
    }

    static String render(String company, String periodLabel, SalesReportResponse r, List<ProductResponse> lowStock, ZoneId zone) {
        StringBuilder h = new StringBuilder(4096);
        h.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#111827;max-width:720px\">");
        h.append("<h2 style=\"margin:0 0 4px\">").append(esc(company)).append("</h2>");
        h.append("<div style=\"color:#6b7280;margin-bottom:16px\">Sales report &middot; ").append(esc(periodLabel))
         .append(" &middot; ").append(esc(range(r.from(), r.to(), zone))).append("</div>");

        h.append("<table cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%;margin-bottom:18px\"><tr>");
        box(h, "Items sold", String.valueOf(r.totalQuantity()), "#1d4ed8");
        box(h, "Total sales", money(r.totalSales()), "#15803d");
        box(h, "Paid", money(r.totalPaid()), "#15803d");
        box(h, "Not paid", money(r.totalUnpaid()), r.totalUnpaid().signum() > 0 ? "#b91c1c" : "#6b7280");
        h.append("</tr></table>");

        h.append("<h3 style=\"margin:18px 0 6px\">Sales per product</h3>");
        if (r.rows().isEmpty()) {
            h.append("<p style=\"color:#6b7280\">No sales in this period.</p>");
        } else {
            h.append("<table cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%\">")
             .append("<tr><th style=\"").append(TH).append("\">Product</th><th style=\"").append(TH).append("\">Category</th>")
             .append("<th style=\"").append(TH).append(NUM).append("\">Sold</th><th style=\"").append(TH).append(NUM).append("\">Sales</th>")
             .append("<th style=\"").append(TH).append(NUM).append("\">Not paid</th><th style=\"").append(TH).append(NUM).append("\">Remaining</th></tr>");
            for (SalesReportResponse.Row row : r.rows()) {
                h.append("<tr><td style=\"").append(TD).append("\"><b>").append(esc(row.productName())).append("</b></td>")
                 .append("<td style=\"").append(TD).append("color:#6b7280\">").append(esc(row.categoryName() == null ? "—" : row.categoryName())).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("\">").append(row.quantitySold()).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("\">").append(money(row.totalSales())).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append(row.unpaidSales().signum() > 0 ? "color:#b91c1c" : "color:#9ca3af").append("\">")
                 .append(row.unpaidSales().signum() > 0 ? money(row.unpaidSales()) : "—").append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("\">").append(row.remainingStock() == null ? "deleted" : row.remainingStock()).append("</td></tr>");
            }
            h.append("<tr><td style=\"").append(TD).append("\" colspan=\"2\"><b>TOTAL</b></td>")
             .append("<td style=\"").append(TD).append(NUM).append("\"><b>").append(r.totalQuantity()).append("</b></td>")
             .append("<td style=\"").append(TD).append(NUM).append("\"><b>").append(money(r.totalSales())).append("</b></td>")
             .append("<td style=\"").append(TD).append(NUM).append("\"><b>").append(r.totalUnpaid().signum() > 0 ? money(r.totalUnpaid()) : "—").append("</b></td>")
             .append("<td style=\"").append(TD).append("\"></td></tr></table>");
        }

        h.append("<h3 style=\"margin:18px 0 6px\">Not paid</h3>");
        if (r.unpaidSales().isEmpty()) {
            h.append("<p style=\"color:#6b7280\">Every sale in this period is paid.</p>");
        } else {
            h.append("<table cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%\">")
             .append("<tr><th style=\"").append(TH).append("\">Receipt</th><th style=\"").append(TH).append("\">Date</th>")
             .append("<th style=\"").append(TH).append("\">Customer</th><th style=\"").append(TH).append("\">Product</th>")
             .append("<th style=\"").append(TH).append(NUM).append("\">Qty</th><th style=\"").append(TH).append(NUM).append("\">Amount</th></tr>");
            for (SaleResponse s : r.unpaidSales()) {
                h.append("<tr><td style=\"").append(TD).append("\">").append(esc(s.receiptNo())).append("</td>")
                 .append("<td style=\"").append(TD).append("white-space:nowrap\">").append(DATE_TIME.format(s.soldAt().atZone(zone))).append("</td>")
                 .append("<td style=\"").append(TD).append("\"><b>").append(esc(s.customerName() == null ? "—" : s.customerName())).append("</b></td>")
                 .append("<td style=\"").append(TD).append("\">").append(esc(s.productName())).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("\">").append(s.quantity()).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("color:#b91c1c\">").append(money(s.total())).append("</td></tr>");
            }
            h.append("</table>");
        }

        h.append("<h3 style=\"margin:18px 0 6px\">Low stock</h3>");
        if (lowStock.isEmpty()) {
            h.append("<p style=\"color:#6b7280\">No product is running low.</p>");
        } else {
            h.append("<table cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;width:100%\">")
             .append("<tr><th style=\"").append(TH).append("\">Product</th><th style=\"").append(TH).append("\">Category</th>")
             .append("<th style=\"").append(TH).append(NUM).append("\">Remaining</th><th style=\"").append(TH).append("\">Status</th></tr>");
            for (ProductResponse p : lowStock) {
                boolean out = p.availableStock() <= 0;
                h.append("<tr><td style=\"").append(TD).append("\"><b>").append(esc(p.name())).append("</b></td>")
                 .append("<td style=\"").append(TD).append("color:#6b7280\">").append(esc(p.categoryName())).append("</td>")
                 .append("<td style=\"").append(TD).append(NUM).append("\">").append(p.availableStock()).append("</td>")
                 .append("<td style=\"").append(TD).append(out ? "color:#b91c1c" : "color:#b45309").append("\"><b>")
                 .append(out ? "OUT OF STOCK" : "LOW STOCK").append("</b></td></tr>");
            }
            h.append("</table>");
        }

        h.append("<p style=\"color:#9ca3af;font-size:12px;margin-top:24px\">Sent automatically by ").append(esc(company))
         .append(" sales &amp; stock tracker.</p></div>");
        return h.toString();
    }

    private static void box(StringBuilder h, String label, String value, String color) {
        h.append("<td style=\"padding:0 6px 0 0;width:25%\"><div style=\"border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px\">")
         .append("<div style=\"font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:#6b7280\">").append(esc(label)).append("</div>")
         .append("<div style=\"font-size:20px;font-weight:bold;color:").append(color).append("\">").append(esc(value)).append("</div></div></td>");
    }

    static String range(Instant from, Instant to, ZoneId zone) {
        String start = from == null || from.equals(Instant.EPOCH) ? "beginning" : DATE.format(from.atZone(zone));
        // `to` is exclusive (start of the next day); show the last day included
        String end = to == null ? "today" : DATE.format(to.atZone(zone).minusSeconds(1));
        return start + " – " + end;
    }

    static String money(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.UK);
        return "RWF " + f.format(v.setScale(0, RoundingMode.HALF_UP));
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
