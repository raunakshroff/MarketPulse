package com.marketpulse.refdata.service;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.EquityPrice;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import com.marketpulse.refdata.repository.EquitySymbolRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes the Bhavcopy download job: fetch, filter to equities, and persist to TimescaleDB. */
@Service
public class BhavcopyService {

    private static final Logger log = LoggerFactory.getLogger(BhavcopyService.class);
    private static final DateTimeFormatter ARCHIVE_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter WIRE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final NseHttpClient nseHttpClient;
    private final NseProperties properties;
    private final EquityPriceRepository equityPriceRepository;
    private final EquitySymbolRepository equitySymbolRepository;

    public BhavcopyService(
            NseHttpClient nseHttpClient,
            NseProperties properties,
            EquityPriceRepository equityPriceRepository,
            EquitySymbolRepository equitySymbolRepository) {
        this.nseHttpClient = nseHttpClient;
        this.properties = properties;
        this.equityPriceRepository = equityPriceRepository;
        this.equitySymbolRepository = equitySymbolRepository;
    }

    @Transactional
    public DownloadResult downloadBhavcopy(LocalDate date) {
        String fileName = fileNameFor(date);
        String downloadUrl = properties.getArchiveUrl() + fileName;

        log.info("Starting download for: {}", date);

        try {
            byte[] content = nseHttpClient.downloadFile(downloadUrl);
            byte[] filtered = EquityCsvFilter.filterEquityRows(content);
            List<EquityPrice> rows = parseToEntities(filtered, date);

            equitySymbolRepository.upsertAll(rows.stream().map(EquityPrice::getSymbol).distinct().toList(), date);
            equityPriceRepository.upsertAll(rows);

            log.info("Success! Saved {} equity rows for {} to equity_price", rows.size(), date);
            return DownloadResult.success(date, "equity_price", rows.size());
        } catch (NseHttpException e) {
            if (e.getStatusCode() == 404) {
                log.warn("File not found (404). {} might be a market holiday or the file isn't published yet.", date);
                return DownloadResult.notFound(date);
            }
            log.error("HTTP error occurred: {}", e.getMessage());
            return DownloadResult.failure(date, e.getMessage());
        } catch (DateMismatchException e) {
            log.error("{}", e.getMessage());
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

    /** Reads back the previously saved, EQ-filtered Bhavcopy rows for a given date. */
    public List<EquityRecord> getEquityRecords(LocalDate date) {
        return equityPriceRepository.findByTradeDateOrderBySymbolAsc(date).stream()
                .map(BhavcopyService::toEquityRecord)
                .toList();
    }

    private List<EquityPrice> parseToEntities(byte[] filtered, LocalDate date) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (CSVParser parser = CSVParser.parse(new String(filtered, StandardCharsets.UTF_8), format)) {
            List<EquityPrice> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                LocalDate contentDate = LocalDate.parse(record.get("DATE1"), WIRE_DATE_FORMAT);
                if (!contentDate.equals(date)) {
                    throw new DateMismatchException(
                            "Bhavcopy content date mismatch: requested " + date
                                    + " but the downloaded file contains rows dated " + contentDate
                                    + ". Refusing to persist.");
                }
                rows.add(new EquityPrice(
                        date,
                        record.get("SYMBOL"),
                        record.get("SERIES"),
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
                        new BigDecimal(record.get("DELIV_PER"))));
            }
            return rows;
        }
    }

    private static EquityRecord toEquityRecord(EquityPrice entity) {
        return new EquityRecord(
                entity.getSymbol(),
                entity.getSeries(),
                entity.getTradeDate().format(WIRE_DATE_FORMAT),
                entity.getPrevClose(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getLastPrice(),
                entity.getClosePrice(),
                entity.getAvgPrice(),
                entity.getTtlTradedQty(),
                entity.getTurnoverLacs(),
                entity.getNoOfTrades(),
                entity.getDelivQty(),
                entity.getDelivPer());
    }

    private String fileNameFor(LocalDate date) {
        return "sec_bhavdata_full_" + date.format(ARCHIVE_FILE_DATE_FORMAT) + ".csv";
    }

    /** Raised when the downloaded CSV's DATE1 column disagrees with the date we asked for. */
    private static class DateMismatchException extends RuntimeException {
        DateMismatchException(String message) {
            super(message);
        }
    }
}
