package org.example.backtrackweb.controller;

import org.example.backtrackweb.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Reports: sales/revenue, inventory, low stock and customers.
 * Every report can be filtered by date and exported to CSV (opens in Excel).
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public String reports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }

        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("salesReport", reportService.salesReport(start, end));
        model.addAttribute("inventoryReport", reportService.inventoryReport());
        model.addAttribute("customerReport", reportService.customerReport(start, end));
        return "reports/index";
    }

    @GetMapping("/sales.csv")
    public ResponseEntity<byte[]> salesCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = reportService.salesCsv(reportService.salesReport(from, to));
        return csvResponse(csv, "sales-report-" + from + "-to-" + to + ".csv");
    }

    @GetMapping("/inventory.csv")
    public ResponseEntity<byte[]> inventoryCsv() {
        String csv = reportService.inventoryCsv(reportService.inventoryReport());
        return csvResponse(csv, "inventory-report-" + LocalDate.now() + ".csv");
    }

    @GetMapping("/customers.csv")
    public ResponseEntity<byte[]> customersCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = reportService.customerCsv(reportService.customerReport(from, to));
        return csvResponse(csv, "customer-report-" + from + "-to-" + to + ".csv");
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
