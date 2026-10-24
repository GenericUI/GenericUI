package com.nexustools.genericui.component.form

/**
 * 24-hour (`HH:MM` or `HH:MM:SS`) time picker form control (`<input type="time">`).
 */
class TimePicker(
    isoTime: String = "12:00"
) : TextField(value = isoTime, placeholder = "HH:MM")
