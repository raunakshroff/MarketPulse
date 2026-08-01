package com.marketpulse.refdata.service;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes the Bhavcopy download job: fetch, filter to equities, and persist to disk. */
@Service
public class BhavcopyService {

    private static final Logger log = LoggerFactory.getLogger(BhavcopyService.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final NseHttpClient nseHttpClient;
    private final NseProperties properties;

    public BhavcopyService(NseHttpClient nseHttpClient, NseProperties properties) {
        this.nseHttpClient = nseHttpClient;
        this.properties = properties;
    }

    public DownloadResult downloadBhavcopy(LocalDate date) {
        String fileName = fileNameFor(date);
        String downloadUrl = properties.getArchiveUrl() + fileName;
        Path dataDir = Path.of(properties.getDataDir());
        Path filePath = dataDir.resolve(fileName);

        log.info("Starting download for: {}", date);

        try {
            Files.createDirectories(dataDir);
            byte[] content = nseHttpClient.downloadFile(downloadUrl);
            byte[] filtered = EquityCsvFilter.filterEquityRows(content);
            Files.write(filePath, filtered);
            long rowCount = countDataRows(filtered);
            log.info("Success! Saved Bhavcopy to {}", filePath);
            return DownloadResult.success(date, filePath.toString(), rowCount);
        } catch (NseHttpException e) {
            if (e.getStatusCode() == 404) {
                log.warn("File not found (404). {} might be a market holiday or the file isn't published yet.", date);
                return DownloadResult.notFound(date);
            }
            log.error("HTTP error occurred: {}", e.getMessage());
            return DownloadResult.failure(date, e.getMessage());
        } catch (IOException e) {
            log.error("An unexpected error occurred: {}", e.getMessage());
            return DownloadResult.failure(date, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("An unexpected error occurred: {}", e.getMessage());
            return DownloadResult.failure(date, e.getMessage());
        }
    }

    /** Reads back the previously saved, EQ-filtered Bhavcopy for a given date. */
    public List<EquityRecord> getEquityRecords(LocalDate date) throws IOException {
        Path filePath = Path.of(properties.getDataDir()).resolve(fileNameFor(date));
        if (!Files.exists(filePath)) {
            throw new NoSuchFileException(filePath.toString());
        }

        List<EquityRecord> records = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (CSVParser parser = CSVParser.parse(filePath, java.nio.charset.StandardCharsets.UTF_8, format)) {
            for (CSVRecord record : parser) {
                records.add(toEquityRecord(record));
            }
        }
        return records;
    }

    private EquityRecord toEquityRecord(CSVRecord record) {
        return new EquityRecord(
                record.get("SYMBOL"),
                record.get("SERIES"),
                record.get("DATE1"),
                new BigDecimal(record.get("PREV_CLOSE")),
                new BigDecimal(record.get("OPEN_PRICE")),
                new BigDecimal(record.get("HIGH_PRICE")),
                new BigDecimal(record.get("LOW_PRICE")),
                new BigDecimal(record.get("LAST_PRICE")),
                new BigDecimal(record.get("CLOSE_PRICE")),
                new BigDecimal(record.get("AVG_PRICE")),
                Long.parseLong(record.get("TTL_TRD_QNTY")),
                new BigDecimal(record.get("TURNOVER_LACS")),
                Long.parseLong(record.get("NO_OF_TRADES")),
                Long.parseLong(record.get("DELIV_QTY")),
                new BigDecimal(record.get("DELIV_PER")));
    }

    private String fileNameFor(LocalDate date) {
        return "sec_bhavdata_full_" + date.format(FILE_DATE_FORMAT) + ".csv";
    }

    private long countDataRows(byte[] csvContent) {
        return new String(csvContent, java.nio.charset.StandardCharsets.UTF_8).lines().count() - 1;
    }
}
