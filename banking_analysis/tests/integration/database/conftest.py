from pytest import fixture
from shared.database.core import init_db
from unittest.mock import patch
import os

@fixture
def namespace(worker_id):
    return f"test_{worker_id}"

@fixture
def db_name(namespace):
    return f"{namespace}.db"

@fixture(autouse=True)
def configure_db(db_name):
    try:
        with patch("shared.database.core.DB_NAME", db_name):
            init_db()
            yield
    finally:
        os.remove(db_name)
