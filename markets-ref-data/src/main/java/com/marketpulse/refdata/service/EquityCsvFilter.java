package com.marketpulse.refdata.service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/** Filters a raw Bhavcopy CSV down to Equity (SERIES == EQ) rows only. */
public final class EquityCsvFilter {

    private static final String SERIES_COLUMN = "SERIES";
    private static final String EQUITY_SERIES = "EQ";

    private EquityCsvFilter() {
    }

    public static byte[] filterEquityRows(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        CSVFormat parseFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(text, parseFormat)) {
            List<String> headers = parser.getHeaderNames();

            StringWriter writer = new StringWriter();
            CSVFormat printFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(headers.toArray(new String[0]))
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, printFormat)) {
                for (CSVRecord record : parser) {
                    if (EQUITY_SERIES.equals(record.get(SERIES_COLUMN).trim())) {
                        List<String> values = new ArrayList<>(headers.size());
                        for (String header : headers) {
                            values.add(record.get(header).trim());
                        }
                        printer.printRecord(values);
                    }
                }
            }
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
