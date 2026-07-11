import logging
from src.utils.logger import setup_logger

def test_setup_logger():
    logger = setup_logger("test_logger")
    
    # Assert logger name
    assert logger.name == "test_logger"
    
    # Assert level is set to INFO
    assert logger.level == logging.INFO
    
    # Assert handler is added
    assert len(logger.handlers) == 1
    assert isinstance(logger.handlers[0], logging.StreamHandler)
    
    # Setup logger again to ensure no duplicate handlers are added
    logger2 = setup_logger("test_logger")
    assert len(logger2.handlers) == 1
