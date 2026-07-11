import pytest
from unittest.mock import patch, MagicMock
import requests
from src.utils.http_client import NSEHttpClient
from src.config import NSE_BASE_URL

@patch('requests.Session.get')
def test_http_client_initialization(mock_get):
    """Test if session initializes correctly and hits the base URL."""
    # Setup mock response
    mock_response = MagicMock()
    mock_get.return_value = mock_response

    client = NSEHttpClient()
    
    # Check if session initialized with a call to base URL
    mock_get.assert_called_once_with(NSE_BASE_URL, timeout=10)
    
    # Verify default headers were injected
    assert 'User-Agent' in client.session.headers

@patch('requests.Session.get')
def test_download_file_success(mock_get):
    """Test successful file download."""
    mock_response = MagicMock()
    mock_response.content = b"file_content"
    mock_response.raise_for_status.return_value = None
    mock_get.return_value = mock_response
    
    client = NSEHttpClient()
    mock_get.reset_mock() # Reset after initial __init__ call
    
    content = client.download_file("https://dummyurl.com/file.csv")
    
    mock_get.assert_called_once_with("https://dummyurl.com/file.csv", timeout=10)
    assert content == b"file_content"

@patch('requests.Session.get')
def test_download_file_failure(mock_get):
    """Test file download failure raises an exception."""
    # First call for init succeeds
    init_response = MagicMock()
    
    # Second call for download fails
    download_response = MagicMock()
    download_response.raise_for_status.side_effect = requests.exceptions.HTTPError("404 Error")
    
    mock_get.side_effect = [init_response, download_response]
    
    client = NSEHttpClient()
    
    with pytest.raises(requests.exceptions.HTTPError):
        client.download_file("https://dummyurl.com/file.csv")
