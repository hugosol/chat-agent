package com.hugosol.chatagent.dto;

import java.util.List;

public record ImportResult(int totalRows, int successCount, List<ImportError> errors) {
}
