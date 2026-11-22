package org.example.backtrackweb.controller;

import org.example.backtrackweb.service.DashboardService;
import org.example.backtrackweb.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * The dashboard: every number comes from PostgreSQL.
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final ReportService reportService;

    public DashboardController(DashboardService dashboardService, ReportService reportService) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("lowStockProducts", dashboardService.lowStockProducts());
        model.addAttribute("recentSales", dashboardService.recentSales());

        LocalDate today = LocalDate.now();
        var weekReport = reportService.salesReport(today.minusDays(6), today);
        model.addAttribute("weekDaily", weekReport.daily());
        model.addAttribute("weekTopProducts", weekReport.topProducts().stream().limit(5).toList());
        return "dashboard";
    }
}
