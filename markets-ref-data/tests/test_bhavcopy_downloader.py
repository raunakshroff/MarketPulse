import os
import pytest
from unittest.mock import patch, mock_open, MagicMock
from src.jobs.bhavcopy_downloader import execute
from src.config import DATA_DIR
import datetime

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
    mock_client_instance.download_file.return_value = b"csv,data,here"
    
    # Execute job
    execute()
    
    # Assert directory was created
    mock_makedirs.assert_called_once_with(DATA_DIR, exist_ok=True)
    
    # Assert file was downloaded and saved
    expected_file_name = "sec_bhavdata_full_14112025.csv"
    expected_file_path = os.path.join(DATA_DIR, expected_file_name)
    
    mock_file.assert_called_once_with(expected_file_path, "wb")
    mock_file().write.assert_called_once_with(b"csv,data,here")
