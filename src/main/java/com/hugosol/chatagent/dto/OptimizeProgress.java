package com.hugosol.chatagent.dto;

public record OptimizeProgress(
        String status,
        int epoch,
        int batch,
        int totalBatches,
        double currentLoss,
        OptimizeResult result,
        String reason) {

    public static OptimizeProgress pending() {
        return new OptimizeProgress("PENDING", 0, 0, 0, 0.0, null, null);
    }

    public static OptimizeProgress running(int epoch, int batch, int totalBatches, double currentLoss) {
        return new OptimizeProgress("RUNNING", epoch, batch, totalBatches, currentLoss, null, null);
    }

    public static OptimizeProgress completed(OptimizeResult result) {
        return new OptimizeProgress("COMPLETED", 0, 0, 0, result.finalLoss(), result, null);
    }

    public static OptimizeProgress failed(String reason) {
        return new OptimizeProgress("FAILED", 0, 0, 0, 0.0, null, reason);
    }

    public static OptimizeProgress skipped(String reason) {
        return new OptimizeProgress("SKIPPED", 0, 0, 0, 0.0, null, reason);
    }
}
