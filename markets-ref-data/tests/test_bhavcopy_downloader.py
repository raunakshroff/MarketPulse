import os
import pytest
from unittest.mock import patch, mock_open, MagicMock
from src.jobs.bhavcopy_downloader import execute, filter_equity_rows
from src.config import DATA_DIR
import datetime

RAW_CSV = (
    b"SYMBOL, SERIES, DATE1, PREV_CLOSE\n"
    b"1018GS2026, GS, 10-Jul-2026, 103.50\n"
    b"20MICRONS, EQ, 10-Jul-2026, 193.94\n"
    b"21STCENMGM, BE, 10-Jul-2026, 31.35\n"
    b"360ONE, EQ, 10-Jul-2026, 1100.20\n"
)

@patch('src.jobs.bhavcopy_downloader.NSEHttpClient')
@patch('src.jobs.bhavcopy_downloader.datetime')
@patch('builtins.open', new_callable=mock_open)
@patch('os.makedirs')
def test_execute_success(mock_makedirs, mock_file, mock_datetime, mock_http_client_class):
    """Test successful execution of the bhavcopy downloader job."""
    # Mock datetime to a fixed date
    mock_now = datetime.datetime(2025, 11, 14)
    mock_datetime.datetime.now.return_value = mock_now

    # Mock HTTP client
    mock_client_instance = mock_http_client_class.return_value
    mock_client_instance.download_file.return_value = RAW_CSV

    # Execute job
    execute()

    # Assert directory was created
    mock_makedirs.assert_called_once_with(DATA_DIR, exist_ok=True)

    # Assert file was downloaded, filtered to EQ rows, and saved
    expected_file_name = "sec_bhavdata_full_14112025.csv"
    expected_file_path = os.path.join(DATA_DIR, expected_file_name)

    mock_file.assert_called_once_with(expected_file_path, "wb")
    mock_file().write.assert_called_once_with(filter_equity_rows(RAW_CSV))


def test_filter_equity_rows_keeps_only_eq_series():
    """Test that non-EQ series rows (e.g. GS, BE) are dropped and EQ rows are kept."""
    filtered = filter_equity_rows(RAW_CSV).decode("utf-8")
    lines = filtered.splitlines()

    assert lines[0] == "SYMBOL,SERIES,DATE1,PREV_CLOSE"
    assert lines[1] == "20MICRONS,EQ,10-Jul-2026,193.94"
    assert lines[2] == "360ONE,EQ,10-Jul-2026,1100.20"
    assert len(lines) == 3
    assert "GS" not in filtered
    assert "BE" not in filtered
