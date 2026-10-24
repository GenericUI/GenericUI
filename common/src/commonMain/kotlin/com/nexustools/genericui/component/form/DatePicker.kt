package com.nexustools.genericui.component.form

/**
 * ISO-8601 (`YYYY-MM-DD`) date picker form control (`<input type="date">`).
 */
class DatePicker(
    isoDate: String = "2026-01-01"
) : TextField(value = isoDate, placeholder = "YYYY-MM-DD")
