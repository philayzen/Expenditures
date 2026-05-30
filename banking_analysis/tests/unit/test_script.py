import pytest
from unittest.mock import patch
from shared import script
from shared.models import (
    BankDBEntry, ReweExpenditureOutput,
)

# ── get_general_expenditures_grouped_by_name ─────────────────────────────────

@pytest.mark.parametrize("entries, expected_count, expected_name, expected_price, expected_alts", [
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone", category="Shopping")],
        1, "AMAZON", 50.0, {"AMAZON"},
        id="single_no_ref"
    ),
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone", category="Shopping"),
         BankDBEntry(key_helper=1, date="2024-01-02", source="Me", recipient="AMAZON", price=30.0, purpose="Cheap Samsung", category="Shopping")],
        1, "AMAZON", 80.0, {"AMAZON"},
        id="same_name_accumulates_price"
    ),
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON.DE", price=50.0, purpose="Cheap iPhone", category="Shopping", display_name="Amazon")],
        1, "Amazon", 50.0, {"AMAZON.DE"},
        id="display_name_resolves_group_name"
    ),
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON",    price=50.0, purpose="Cheap iPhone",  category="Shopping", display_name="Amazon"),
         BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON.DE", price=30.0, purpose="Cheap Samsung", category="Shopping", display_name="Amazon")],
        1, "Amazon", 80.0, {"AMAZON", "AMAZON.DE"},
        id="two_originals_merged_via_shared_display_name"
    ),
])
def test_get_general_expenditures_grouped_by_name(
    entries, expected_count, expected_name, expected_price, expected_alts
):
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_name()

    assert len(result) == expected_count
    assert result[0].display_name == expected_name
    assert result[0].price == pytest.approx(expected_price)
    assert result[0].alt_names == expected_alts


def test_get_general_expenditures_grouped_by_name_sorted_desc():
    entries = [
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="REWE",   price=30.0, purpose="...",   category=""),
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=80.0, purpose="Shoes", category=""),
    ]
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_name()

    assert result[0].display_name == "AMAZON"
    assert result[1].display_name == "REWE"


def test_get_general_expenditures_grouped_by_name_different_names_stay_separate():
    entries = [
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone", category=""),
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="REWE",   price=30.0, purpose="...",           category=""),
    ]
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_name()

    assert len(result) == 2


# ── get_general_expenditures_grouped_by_name_and_cat ────────────────────────

@pytest.mark.parametrize("entries, expected_count", [
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone",  category="Shopping"),
         BankDBEntry(key_helper=1, date="2024-01-02", source="Me", recipient="AMAZON", price=30.0, purpose="Cheap Samsung", category="Shopping")],
        1,
        id="same_name_same_cat_merges"
    ),
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone",  category="Shopping"),
         BankDBEntry(key_helper=1, date="2024-01-02", source="Me", recipient="AMAZON", price=30.0, purpose="Cheap Samsung", category="Online")],
        2,
        id="same_name_different_cat_stays_separate"
    ),
    pytest.param(
        [BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone",  category="Shopping"),
         BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="REWE",   price=30.0, purpose="Cheap Samsung", category="Shopping")],
        2,
        id="different_names_same_cat_stay_separate"
    ),
])
def test_get_general_expenditures_grouped_by_name_and_cat(entries, expected_count):
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_name_and_cat()

    assert len(result) == expected_count


def test_get_general_expenditures_grouped_by_name_and_cat_accumulates_price():
    entries = [
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="AMAZON", price=50.0, purpose="Cheap iPhone",  category="Shopping"),
        BankDBEntry(key_helper=1, date="2024-01-02", source="Me", recipient="AMAZON", price=30.0, purpose="Cheap Samsung", category="Shopping"),
    ]
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_name_and_cat()

    assert result[0].price == pytest.approx(80.0)
    assert result[0].category == "Shopping"


# ── get_general_expenditures_grouped_by_category ────────────────────────────

@pytest.mark.parametrize("entries, expected_cats, expected_prices", [
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "AMAZON", purpose="Cheap iPhone", price=50.0, category="Shopping"),
         BankDBEntry(1, "2024-01-01", "Me", "REWE", purpose="...", price=30.0, category="Shopping")],
        ["Shopping"], [80.0],
        id="same_category_accumulates"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "AMAZON", purpose="Cheap iPhone", price=50.0, category="Shopping"),
         BankDBEntry(1, "2024-01-01", "Me", "REWE", purpose="...", price=30.0, category="Groceries")],
        ["Shopping", "Groceries"], [50.0, 30.0],
        id="different_categories_sorted_desc"
    ),
])
def test_get_general_expenditures_grouped_by_category(entries, expected_cats, expected_prices):
    with patch("shared.script.get_general_expenditures_from_db", return_value=entries):
        result = script.get_general_expenditures_grouped_by_category()

    assert len(result) == len(expected_cats)
    for item, exp_cat, exp_price in zip(result, expected_cats, expected_prices):
        assert item.category == exp_cat
        assert item.price == pytest.approx(exp_price)


# ── get_rewe_expenditures_grouped_by_name ────────────────────────────────────

@pytest.mark.parametrize("entries, exp_count, exp_name, exp_amount, exp_price", [
    pytest.param(
        [ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=2, price=1.5, category="Dairy")],
        1, "Milk", 2, 1.5,
        id="single_item"
    ),
    pytest.param(
        [ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=2, price=1.5, category="Dairy"),
         ReweExpenditureOutput(date="2024-01-02", name="Milk", amount=1, price=1.5, category="Dairy")],
        1, "Milk", 3, 3.0,
        id="same_name_accumulates_amount_and_price"
    ),
    pytest.param(
        [ReweExpenditureOutput(date="2024-01-01", name="BIO MILCH", amount=1, price=1.5, category="Dairy", display_name="Milk")],
        1, "Milk", 1, 1.5,
        id="display_name_resolves_group_name"
    ),
])
def test_get_rewe_expenditures_grouped_by_name(
    entries, exp_count, exp_name, exp_amount, exp_price
):
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_name()

    assert len(result) == exp_count
    assert result[0].display_name == exp_name
    assert result[0].amount == exp_amount
    assert result[0].price == pytest.approx(exp_price)


def test_get_rewe_expenditures_grouped_by_name_two_originals_share_display_name():
    entries = [
        ReweExpenditureOutput(date="2024-01-01", name="BIO MILCH A", amount=1, price=1.5, category="", display_name="Milk"),
        ReweExpenditureOutput(date="2024-01-02", name="BIO MILCH B", amount=2, price=2.0, category="", display_name="Milk"),
    ]
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_name()

    assert len(result) == 1
    assert result[0].display_name == "Milk"
    assert result[0].amount == 3
    assert result[0].price == pytest.approx(3.5)
    assert result[0].alt_names == {"BIO MILCH A", "BIO MILCH B"}


def test_get_rewe_expenditures_grouped_by_name_sorted_desc():
    entries = [
        ReweExpenditureOutput(date="2024-01-01", name="Bread", amount=1, price=1.5, category=""),
        ReweExpenditureOutput(date="2024-01-01", name="Milk",  amount=1, price=3.0, category=""),
    ]
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_name()

    assert result[0].display_name == "Milk"
    assert result[1].display_name == "Bread"


# ── get_rewe_expenditures_grouped_by_name_and_cat ────────────────────────────

@pytest.mark.parametrize("entries, expected_count", [
    pytest.param(
        [ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=1, price=1.5, category="Dairy"),
         ReweExpenditureOutput(date="2024-01-02", name="Milk", amount=1, price=1.5, category="Dairy")],
        1,
        id="same_name_same_cat_merges"
    ),
    pytest.param(
        [ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=1, price=1.5, category="Dairy"),
         ReweExpenditureOutput(date="2024-01-02", name="Milk", amount=1, price=1.5, category="Snacks")],
        2,
        id="same_name_different_cat_stays_separate"
    ),
])
def test_get_rewe_expenditures_grouped_by_name_and_cat(entries, expected_count):
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_name_and_cat()

    assert len(result) == expected_count


def test_get_rewe_expenditures_grouped_by_name_and_cat_accumulates():
    entries = [
        ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=2, price=1.5, category="Dairy"),
        ReweExpenditureOutput(date="2024-01-02", name="Milk", amount=3, price=1.5, category="Dairy"),
    ]
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_name_and_cat()

    assert result[0].amount == 5
    assert result[0].price == pytest.approx(3.0)


# ── get_rewe_expenditures_grouped_by_category ────────────────────────────────

@pytest.mark.parametrize("entries, exp_cats, exp_amounts, exp_prices", [
    pytest.param(
        [ReweExpenditureOutput("2024-01-01", "Milk",    2, 1.5, "Dairy"),
         ReweExpenditureOutput("2024-01-01", "Yoghurt", 3, 0.9, "Dairy")],
        ["Dairy"], [5], [2.4],
        id="same_category_accumulates_amount_and_price"
    ),
    pytest.param(
        [ReweExpenditureOutput("2024-01-01", "Milk",  1, 2.0, "Dairy"),
         ReweExpenditureOutput("2024-01-01", "Bread", 1, 1.5, "Bakery")],
        ["Dairy", "Bakery"], [1, 1], [2.0, 1.5],
        id="different_categories_sorted_desc_by_price"
    ),
])
def test_get_rewe_expenditures_grouped_by_category(entries, exp_cats, exp_amounts, exp_prices):
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=entries):
        result = script.get_rewe_expenditures_grouped_by_category()

    assert len(result) == len(exp_cats)
    for item, cat, amount, price in zip(result, exp_cats, exp_amounts, exp_prices):
        assert item.category == cat
        assert item.amount == amount
        assert item.price == pytest.approx(price)
