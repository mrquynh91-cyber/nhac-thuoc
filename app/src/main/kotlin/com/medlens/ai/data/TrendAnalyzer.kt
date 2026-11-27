package com.medlens.ai.data

object TrendAnalyzer {
    fun normalizeIndicatorKey(rawName: String): String {
        val upper = rawName.trim().uppercase()
        return when {
            upper.contains("ALT") || upper.contains("GPT") -> "ALT (GPT)"
            upper.contains("AST") || upper.contains("GOT") -> "AST (GOT)"
            upper.contains("GGT") -> "GGT"
            upper.contains("TRIGLYCERIDE") -> "Triglyceride"
            upper.contains("LDL") -> "LDL-Cholesterol"
            upper.contains("HDL") -> "HDL-Cholesterol"
            upper.contains("CHOLESTEROL") -> "Cholesterol toàn phần"
            upper.contains("HBA1C") -> "HbA1c"
            upper.contains("GLUCOSE") || upper.contains("ĐƯỜNG HUYẾT") -> "Glucose (Đường huyết)"
            upper.contains("CREATININE") -> "Creatinine"
            upper.contains("URIC") -> "Acid Uric"
            else -> rawName.trim()
        }
    }

    fun buildTrendSeries(records: List<MedicalRecord>): List<IndicatorTrendSeries> {
        val grouped = linkedMapOf<String, MutableList<IndicatorTrendPoint>>()

        records.sortedBy { it.createdAt }.forEach { record ->
            record.labIndicators.forEach { indicator ->
                val num = indicator.parsedNumericValue() ?: return@forEach
                val key = normalizeIndicatorKey(indicator.name)
                val list = grouped.getOrPut(key) { mutableListOf() }
                list.add(
                    IndicatorTrendPoint(
                        recordId = record.id,
                        dateLabel = record.recordDate.ifBlank { "Lần ${list.size + 1}" },
                        timestamp = record.createdAt,
                        value = num,
                        rawValue = indicator.value,
                        unit = indicator.unit,
                        status = indicator.status
                    )
                )
            }
        }

        return grouped.entries
            .map { (name, points) ->
                val sortedPoints = points.sortedBy { it.timestamp }
                IndicatorTrendSeries(
                    indicatorName = name,
                    unit = sortedPoints.lastOrNull()?.unit.orEmpty(),
                    points = sortedPoints
                )
            }
            .sortedByDescending { it.points.size }
    }
}
