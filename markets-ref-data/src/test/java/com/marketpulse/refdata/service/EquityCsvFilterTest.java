package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquityCsvFilterTest {

    private static final byte[] RAW_CSV = ("""
            SYMBOL, SERIES, DATE1, PREV_CLOSE
            1018GS2026, GS, 10-Jul-2026, 103.50
            20MICRONS, EQ, 10-Jul-2026, 193.94
            21STCENMGM, BE, 10-Jul-2026, 31.35
            360ONE, EQ, 10-Jul-2026, 1100.20
            """).getBytes(StandardCharsets.UTF_8);

    @Test
    void keepsOnlyEquitySeriesRows() {
        byte[] filtered = EquityCsvFilter.filterEquityRows(RAW_CSV);
        String result = new String(filtered, StandardCharsets.UTF_8);
        List<String> lines = result.lines().toList();

        assertThat(lines.get(0)).isEqualTo("SYMBOL,SERIES,DATE1,PREV_CLOSE");
        assertThat(lines.get(1)).isEqualTo("20MICRONS,EQ,10-Jul-2026,193.94");
        assertThat(lines.get(2)).isEqualTo("360ONE,EQ,10-Jul-2026,1100.20");
        assertThat(lines).hasSize(3);
        assertThat(result).doesNotContain("GS").doesNotContain("BE");
    }
}
