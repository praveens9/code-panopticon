package com.repo.analyzer.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class CsvReporter {

    public void generate(List<AnalysisData> data, Path outputPath) {
        StringBuilder csv = new StringBuilder();
        // Header
        csv.append(
                "Class Name,Churn,Recent Churn,Coupled Peers,Methods,Avg Fields (Cohesion),LCOM4,Total CC,Max CC,Fan-Out,Afferent Coupling,Instability,LOC,Risk Score,Verdict,Brain Method\n");

        // Rows
        for (AnalysisData d : data) {
            csv.append(String.format("%s,%d,%d,%d,%.0f,%.2f,%.0f,%.0f,%.0f,%.0f,%.0f,%.2f,%.0f,%.2f,%s,%s\n",
                    escape(d.className()),
                    d.churn(),
                    d.recentChurn(),
                    d.coupledPeers(),
                    d.methodCount(),
                    d.avgFields(),
                    d.lcom4(),
                    d.totalCC(),
                    d.maxCC(),
                    d.fanOut(),
                    d.afferentCoupling(),
                    d.instability(),
                    d.loc(),
                    d.riskScore(),
                    d.verdict(),
                    d.verdict(),
                    escape(String.join(";", d.brainMethods()))));
        }

        try {
            Files.writeString(outputPath, csv.toString());
            System.out.println("CSV Report generated at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String escape(String s) {
        if (s == null)
            return "";
        // Simple CSV escaping: if contains comma, wrap in quotes
        if (s.contains(",")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
