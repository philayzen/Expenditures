import pytest
from unittest.mock import patch, MagicMock
from pathlib import Path
from shared import data_import

_FIXTURE_PDF_DIR = str(Path(__file__).parent.parent / "fixtures" / "rewe_pdfs")


_SIMPLE_RECEIPT = (
    "TSE-Start: 01.01.2024 10:00:00\n"
    "EUR\n"
    "Milch 1,50 B\n"
    "Brot 2,00 B\n"
    "------\n"
)

_QUANTITY_RECEIPT = (
    "TSE-Start: 05.03.2024 09:30:00\n"
    "EUR\n"
    "Eier 3,49 B\n"
    "6 Stk x 0,58\n"
    "------\n"
)

_DISCOUNT_RECEIPT = (
    "TSE-Start: 01.01.2024 10:00:00\n"
    "EUR\n"
    "Joghurt 0,89 B\n"
    "Rabatt -0,10 B\n"
    "------\n"
)


@pytest.mark.parametrize("receipt_text, expected_date, expected_items", [
    pytest.param(
        _SIMPLE_RECEIPT,
        "01.01.2024",
        [("Milch", 1, 1.50), ("Brot", 1, 2.00)],
        id="simple_items"
    ),
    pytest.param(
        _QUANTITY_RECEIPT,
        "05.03.2024",
        [("Eier", 6, 0.58)],
        id="quantity_line_updates_amount_and_price"
    ),
    pytest.param(
        _DISCOUNT_RECEIPT,
        "01.01.2024",
        [("Joghurt", 1, 0.79)],
        id="negative_price_subtracted_from_previous_item"
    ),
])
def test_retrieve_rewe_pdf_data(receipt_text, expected_date, expected_items):
    mock_insert = MagicMock()
    with patch("os.listdir", return_value=["receipt.pdf"]), \
         patch("shared.data_import.read_pdf", return_value=receipt_text), \
         patch("shared.data_import.insert_rewe_expenditure", mock_insert), \
         patch("shared.data_import.get_rewe_most_common_categories", return_value={}):
        data_import.retrieve_rewe_pdf_data()

    mock_insert.assert_called_once()
    date, items = mock_insert.call_args.args
    assert date == expected_date
    assert len(items) == len(expected_items)
    for item, (name, amount, price) in zip(items, expected_items):
        assert item.name == name
        assert item.amount == amount
        assert item.price == pytest.approx(price)


def test_retrieve_rewe_pdf_data_no_pdf_files():
    mock_insert = MagicMock()
    with patch("os.listdir", return_value=["notes.txt", "receipt.csv"]), \
         patch("shared.data_import.insert_rewe_expenditure", mock_insert):
        data_import.retrieve_rewe_pdf_data()

    mock_insert.assert_not_called()


def test_retrieve_rewe_pdf_data_fixture_pdf():
    mock_insert = MagicMock()
    with patch("shared.data_import.REWE_FOLDER", _FIXTURE_PDF_DIR), \
         patch("shared.data_import.insert_rewe_expenditure", mock_insert), \
         patch("shared.data_import.get_rewe_most_common_categories", return_value={"GNOCCHI": "Food", "BIO MILCH 3,6%": "Milch"}):
        data_import.retrieve_rewe_pdf_data()

    mock_insert.assert_called_once()
    date, items = mock_insert.call_args.args
    assert date == "2026-02-02T14:29:13.000"
    assert len(items) == 13

    expected = [
        ("BIO BERGKAESE ST",  1, 2.69),
        ("CHOC & CHOC",       1, 2.89),
        ("GNOCCHI",           1, 3.69),
        ("GEMUESEMAULTASCH",  1, 2.29),
        ("BIO CASHEWKERNE",   1, 3.79),
        ("PIENENKERNE",       1, 3.99),
        ("PAKCHOI MINI",      1, 1.99),
        ("KAROTTE REWE PP",   1, 1.15),
        ("PAPRIKA ROT",       1, 1.57),
        ("KRAEUTER",          1, 1.69),
        ("BIO MILCH 3,6%",    2, 2.29),  # amount/price updated by "2 Stk x 2,29"
        ("BIO SCHLAGSAHNE",   1, 1.19),
        ("DINKEL FLAKES",     1, 2.49),
    ]
    for item, (name, amount, price) in zip(items, expected):
        assert item.name   == name
        assert item.amount == amount
        assert item.price  == pytest.approx(price)
