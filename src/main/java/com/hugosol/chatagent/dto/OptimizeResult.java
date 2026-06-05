package com.hugosol.chatagent.dto;

public record OptimizeResult(double[] weights, double finalLoss, int iterations, long durationMs) {

    public OptimizeResult {
        weights = weights.clone();
    }

    @Override
    public double[] weights() {
        return weights.clone();
    }
}
