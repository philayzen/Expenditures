import pytest
from pytest import fixture, mark
from shared.database import core
from shared.database.constants import REWE_TABLE_NAME, GENERAL_EXPENDITURES_TABLE_NAME, CATEGORY_TYPES_TABLE_NAME
from shared.models import BankDBEntry, BankExpenditureEntry, ReweExpenditureEntry, Item, CategoryTypeEntry, ExpenseType
from unittest.mock import MagicMock, patch
import sqlite3
from pathlib import Path


def test_patch_renaming(db_name):
    with patch("shared.database.core.DB_NAME", db_name):
        assert core.DB_NAME == db_name


def test_init_db_creates_tables(db_name):
    with patch("shared.database.core.DB_NAME", db_name):
        core.init_db()
        conn = sqlite3.connect(db_name)
        try:
            tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
            structure_rewe = conn.execute(f"PRAGMA table_info({REWE_TABLE_NAME})").fetchall()
            structure_expenditures = conn.execute(f"PRAGMA table_info({GENERAL_EXPENDITURES_TABLE_NAME})").fetchall()
        finally:
            conn.close()
    assert REWE_TABLE_NAME in tables
    assert GENERAL_EXPENDITURES_TABLE_NAME in tables

    assert structure_rewe[0] == (0, 'date',         'TIMESTAMP', 1, None,   1)
    assert structure_rewe[1] == (1, 'amount',       'INTEGER',   1, None,   0)
    assert structure_rewe[2] == (2, 'name',         'TEXT',      1, None,   2)
    assert structure_rewe[3] == (3, 'price',        'REAL',      1, None,   0)
    assert structure_rewe[4] == (4, 'category',     'TEXT',      0, "''",   0)
    assert structure_rewe[5] == (5, 'display_name', 'TEXT',      0, "''",   0)

    assert structure_expenditures[0] == (0, 'key_helper',   'INTEGER',   1, None,  1)
    assert structure_expenditures[1] == (1, 'date',         'TIMESTAMP', 1, None,  2)
    assert structure_expenditures[2] == (2, 'source',       'TEXT',      1, None,  3)
    assert structure_expenditures[3] == (3, 'recipient',    'TEXT',      1, None,  4)
    assert structure_expenditures[4] == (4, 'purpose',      'TEXT',      0, None,  0)
    assert structure_expenditures[5] == (5, 'price',        'REAL',      1, None,  0)
    assert structure_expenditures[6] == (6, 'category',     'TEXT',      0, "''",  0)
    assert structure_expenditures[7] == (7, 'display_name', 'TEXT',      0, "''",  0)


def test_wrapper(db_name):
    def get_db_location(cursor):
        cursor.execute("PRAGMA database_list")
        res = cursor.fetchone()
        return res[-1]
    returned_location = core.database_connection(get_db_location)()
    assert db_name == Path(returned_location).name


@mark.parametrize("entries, expected_count", [
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "books")],
        1,
        id="single"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "books"),
         BankDBEntry(2, "2024-01-02", "Me", "Rewe",   30.0, "food")],
        2,
        id="multiple"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "books"),
         BankDBEntry(1, "2024-01-01", "Me", "Amazon", 99.0, "same_pk_ignored")],
        1,
        id="duplicate_ignored"
    ),
])
def test_insert_general_expenditure(entries, expected_count):
    core.insert_general_expenditure(entries)
    
    @core.database_connection
    def get_rows(cursor):
        rows = cursor.execute(
            f"SELECT key_helper, date, source, recipient, purpose, price "
            f"FROM {GENERAL_EXPENDITURES_TABLE_NAME} ORDER BY key_helper, date"
        ).fetchall()
        return rows
    rows = get_rows()

    assert len(rows) == expected_count
    e = entries[0]
    assert rows[0] == (e.key_helper, e.date, e.source, e.recipient, e.purpose, e.price)


@mark.parametrize("date, items, expected_count", [
    pytest.param(
        "2024-01-01",
        [Item(2, "Milk", 1.5, "", "Milk")],
        1,
        id="single_item"
    ),
    pytest.param(
        "2024-01-01",
        [Item(2, "Milk", 1.5, "", "Milk"), Item(1, "Bread", 2.0, "", "Bread")],
        2,
        id="multiple_items"
    ),
    pytest.param(
        "2024-01-01",
        [Item(2, "Milk", 1.5, "", "Milk"), Item(1, "Milk", 1.5, "", "Milk")],
        1,
        id="duplicate_items"
    ),
])
def test_insert_rewe_expenditure(date, items, expected_count):
    core.insert_rewe_expenditure(date, items)
    @core.database_connection
    def get_rows(cursor):
        return cursor.execute(
            f"SELECT date, name, amount, price FROM {REWE_TABLE_NAME}"
        ).fetchall()
    rows = get_rows()
    assert len(rows) == expected_count
    added = set()
    for item in items:
        if item.name in added:
            assert (date, item.name, item.amount, item.price) not in rows
        else:
            assert (date, item.name, item.amount, item.price) in rows
        added.add(item.name)


def test_insert_rewe_expenditure_same_date_skips_all_items():
    core.insert_rewe_expenditure("2024-01-01", [Item(2, "Milk", 1.5, "", "Milk")])
    core.insert_rewe_expenditure("2024-01-01", [Item(1, "Bread", 2.0, "", "Bread")])
    
    @core.database_connection
    def get_rows(cursor):
        return cursor.execute(f"SELECT name FROM {REWE_TABLE_NAME}").fetchall()
    assert get_rows() == [("Milk",)]


# == update_display_names ==

@mark.parametrize("entry, expected_display_name, other_unaffected", [
    pytest.param(
        BankExpenditureEntry(key_helper=1, date="2024-01-01", source="Me", recipient="PayPal",
                             price=80.0, purpose="sub", category="TV", display_name="PayPal"),
        "PayPal Premium",
        False,
        id="bank_single_entry_by_pk"
    ),
    pytest.param(
        BankExpenditureEntry(key_helper=None, date=None, source=None, recipient=None,
                             price=160.0, purpose=None, category="TV", display_name="PayPal",
                             alt_names={"PayPal"}),
        "PayPal Premium",
        True,
        id="bank_grouped_by_alt_names_respects_category"
    ),
])
def test_update_display_names_bank(entry, expected_display_name, other_unaffected):
    core.insert_general_expenditure([
        BankDBEntry(key_helper=1, date="2024-01-01", source="Me", recipient="PayPal", price=80.0, purpose="sub", category="TV"),
        BankDBEntry(key_helper=2, date="2024-01-02", source="Me", recipient="PayPal", price=80.0, purpose="sub", category=""),
    ])
    core.update_display_names([entry], "PayPal Premium")
    @core.database_connection
    def get_rows(cursor):
        return cursor.execute(
            f"SELECT display_name FROM {GENERAL_EXPENDITURES_TABLE_NAME} ORDER BY key_helper"
        ).fetchall()
    rows = get_rows()
    assert rows[0][0] == expected_display_name
    if other_unaffected:
        assert rows[1][0] == ''


@mark.parametrize("entry, expected_display_name, other_unaffected", [
    pytest.param(
        ReweExpenditureEntry(date="2024-01-01", name="BIO MILCH A", amount=2, price=1.5,
                             category="Dairy", display_name="BIO MILCH A"),
        "Milk",
        True,
        id="rewe_single_entry_by_pk"
    ),
    pytest.param(
        Item(amount=3, name=None, price=3.5, category="Dairy",
             display_name="Milk", alt_names={"BIO MILCH A", "BIO MILCH B"}),
        "Milk",
        False,
        id="rewe_grouped_by_alt_names"
    ),
])
def test_update_display_names_rewe(entry, expected_display_name, other_unaffected):
    core.insert_rewe_expenditure("2024-01-01", [Item(amount=2, name="BIO MILCH A", price=1.5, category="Dairy")])
    core.insert_rewe_expenditure("2024-01-02", [Item(amount=1, name="BIO MILCH B", price=2.0, category="Dairy")])
    core.update_display_names([entry], "Milk")
    @core.database_connection
    def get_rows(cursor):
        return {row[0] for row in cursor.execute(f"SELECT display_name FROM {REWE_TABLE_NAME}").fetchall()}
    rows = get_rows()
    assert expected_display_name in rows
    if other_unaffected:
        assert '' in rows


# == get_name_references ==

@mark.parametrize("inserted, lookup, expected", [
    pytest.param(
        [("AMAZON", "Amazon")],
        ["AMAZON"],
        {("AMAZON", "Amazon")},
        id="single_match"
    ),
    pytest.param(
        [("AMAZON", "Amazon"), ("REWE", "Rewe")],
        ["AMAZON", "REWE"],
        {("AMAZON", "Amazon"), ("REWE", "Rewe")},
        id="multiple_matches"
    ),
    pytest.param(
        [("AMAZON", "Amazon"), ("AMAZON PRIME", "Amazon")],
        ["AMAZON", "AMAZON PRIME"],
        {("AMAZON", "Amazon"), ("AMAZON PRIME", "Amazon")},
        id="multiple_matches_same_display_name"
    ),
    pytest.param(
        [("AMAZON", "Amazon")],
        ["UNKNOWN"],
        set(),
        id="no_match"
    ),
])
def test_get_name_references(inserted, lookup, expected):
    @core.database_connection
    def establish_entries(cursor):
        for i, name in enumerate(lookup):
            cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (KEY_HELPER, DATE, SOURCE, RECIPIENT, PRICE) VALUES (?, '2024-01-01', 'Me', ?, 1.0)", (i, name,))
    
    @core.database_connection
    def insert_refs(cursor):
        for name, display_name in inserted:
            cursor.execute(f"UPDATE {GENERAL_EXPENDITURES_TABLE_NAME} SET display_name=? WHERE recipient=?", (display_name, name))
    establish_entries()
    insert_refs()
    
    results = core.get_name_references(lookup)
    print(results)
    assert {(r.name, r.display_name) for r in results} == expected


# == get_original_names ==

@mark.parametrize("inserted, reference, expected", [
    pytest.param(
        [("AMAZON", "Amazon")],
        "Amazon",
        ["AMAZON"],
        id="single_original"
    ),
    pytest.param(
        [("AMAZON1", "Amazon"), ("AMAZON2", "Amazon")],
        "Amazon",
        ["AMAZON1", "AMAZON2"],
        id="multiple_originals"
    ),
    pytest.param(
        [],
        "Original name",
        ["Original name"],
        id="no_match_returns_reference_itself"
    ),
])
def test_get_original_names(inserted, reference, expected):
    @core.database_connection
    def establish_entries(cursor):
        for i, entry in enumerate(inserted):
            cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (KEY_HELPER, DATE, SOURCE, RECIPIENT, PRICE, DISPLAY_NAME) VALUES (?, '2024-01-01', 'Me', ?, 1.0, ?)", (i, entry[0], entry[1],))
    
    @core.database_connection
    def insert_refs(cursor):
        for name, display_name in inserted:
            cursor.execute(f"UPDATE {GENERAL_EXPENDITURES_TABLE_NAME} SET display_name=? WHERE recipient=?", (display_name, name))
    establish_entries()
    insert_refs()
    results = core.get_original_names(reference)
    assert sorted(results) == sorted(expected)


_BANK_ENTRIES = [
    BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0,  "books"),
    BankDBEntry(2, "2024-02-01", "Me", "Rewe", 30.0,  "food"),
    BankDBEntry(3, "2024-03-01", "Me", "ignored1", 100.0, "transfer"),
    BankDBEntry(4, "2024-03-01", "Me", "ignored2", 100.0, "transfer"),
]

@mark.parametrize("since, until, ignore, expected_recipients", [
    pytest.param(None,         None,         [],                    {"Amazon", "Rewe", "ignored1", "ignored2"}, id="all"),
    pytest.param("2024-02-01", None,         [],                    {"Rewe", "ignored1", "ignored2"},           id="since"),
    pytest.param(None,         "2024-01-31", [],                    {"Amazon"},                              id="until"),
    pytest.param("2024-01-01", "2024-02-28", [],                    {"Amazon", "Rewe"},                     id="range"),
    pytest.param(None,         None,         ["ignored1"],          {"Amazon", "Rewe", "ignored2"},                     id="bank_ignore"),
    pytest.param(None,         None,         ["ignored1", "ignored2"],{"Amazon", "Rewe"},                     id="bank_ignore_both"),
])
def test_get_general_expenditures_from_db(since, until, ignore, expected_recipients):
    core.insert_general_expenditure(_BANK_ENTRIES)
    with patch("shared.database.core.BANK_IGNORE", ignore):
        results = core.get_general_expenditures_from_db(since=since, until=until)
    assert {r.recipient for r in results} == expected_recipients


def test_get_general_expenditures_price_negated():
    core.insert_general_expenditure([BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "books")])
    with patch("shared.database.core.BANK_IGNORE", []):
        results = core.get_general_expenditures_from_db()
    assert results[0].price == -50.0


# == get_rewe_expenditures_from_db ==

_REWE_ENTRIES = [
    ("2024-01-01", [Item(2, "Milk",  1.5, "", "Milk")]),
    ("2024-02-01", [Item(1, "Bread", 2.0, "", "Bread")]),
    ("2024-03-01", [Item(3, "Eggs",  3.5, "", "Eggs")]),
]

@mark.parametrize("since, until, expected_names", [
    pytest.param(None,         None,         {"Milk", "Bread", "Eggs"}, id="all"),
    pytest.param("2024-02-01", None,         {"Bread", "Eggs"},         id="since"),
    pytest.param(None,         "2024-01-31", {"Milk"},                  id="until"),
    pytest.param("2024-01-01", "2024-02-28", {"Milk", "Bread"},         id="range"),
])
def test_get_rewe_expenditures_from_db(since, until, expected_names):
    for date, items in _REWE_ENTRIES:
        core.insert_rewe_expenditure(date, items)
    results = core.get_rewe_expenditures_from_db(since=since, until=until)
    assert {r.name for r in results} == expected_names


# == update_rewe_categories_in_db ==

@mark.parametrize("name, category", [
    pytest.param("Milk",  "Dairy",  id="dairy"),
    pytest.param("Bread", "Bakery", id="bakery"),
])
def test_update_rewe_categories(db_name, name, category):
    core.insert_rewe_expenditure("2024-01-01", [Item(2, name, 1.5, "", name)])
    core.update_rewe_categories_in_db([{"name": name, "category": category}])
    conn = sqlite3.connect(db_name)
    try:
        row = conn.execute(f"SELECT category FROM {REWE_TABLE_NAME} WHERE name = ?", (name,)).fetchone()
    finally:
        conn.close()
    assert row[0] == category


# == update_bank_categories_in_db ==

@mark.parametrize("recipient, category", [
    pytest.param("Amazon", "Shopping",  id="amazon"),
    pytest.param("Rewe",   "Groceries", id="rewe"),
])
def test_update_bank_categories(db_name, recipient, category):
    core.insert_general_expenditure([BankDBEntry(1, "2024-01-01", "Me", recipient, 50.0, "test")])
    core.update_bank_categories_in_db([{"name": recipient, "category": category}])
    
    @core.database_connection
    def get_row(cursor):
        return cursor.execute(
            f"SELECT category FROM {GENERAL_EXPENDITURES_TABLE_NAME} WHERE recipient = ?", (recipient,)
        ).fetchone()
    row = get_row()
    assert row[0] == category


# == get_rewe_most_common_categories ==

@mark.parametrize("inserted, lookup, expected", [
    pytest.param(
        [("2024-01-01", [Item(1, "Milk",  1.5, "Milk")])],
        ["Milk"],
        {"Milk": "Milk"},
        id="single_match"
    ),
    pytest.param(
        [("2024-01-01", [Item(1, "Milk", 1.5, "Milk"), Item(1, "Bread", 2.0, "Bread")])],
        ["Milk", "Bread"],
        {"Milk": "Milk", "Bread": "Bread"},
        id="multiple_matches"
    ),
    pytest.param(
        [("2024-01-01", [Item(1, "Milk", 1.5, "Milk")])],
        ["Bread"],
        {},
         id="no_match"
    ),
    pytest.param(
        [("2024-01-01", [Item(4, "Milk", 1.5, ""), Item(3, "Bread", 2.0, "")]),
         ("2024-01-02", [Item(1, "Milk", 1.5, "Milk"), Item(4, "Bread", 2.0, "")]),
         ("2024-01-03", [Item(4, "Milk", 1.5, "Milk"), Item(5, "Bread", 2.0, "Bread")])],
        ["Milk", "Bread"],
        {"Milk": "Milk", "Bread": ""},
        id="multiple_entries"
    ),
])
def test_get_rewe_most_common_categories(inserted, lookup, expected):
    for date, items in inserted:
        core.insert_rewe_expenditure(date, items)
    # core.update_rewe_categories_in_db(updates)
    result = core.get_rewe_most_common_categories(lookup)
    assert result == expected


def test_get_rewe_most_common_categories_returns_most_common():

    @core.database_connection
    def insert_entries(cursor):
        cursor.execute(f"INSERT INTO {REWE_TABLE_NAME} (date, amount, name, price, category) VALUES ('2024-01-01', 1, 'Milk', 1.5, 'Dairy')")
        cursor.execute(f"INSERT INTO {REWE_TABLE_NAME} (date, amount, name, price, category) VALUES ('2024-01-02', 1, 'Milk', 1.5, 'Dairy')")
        cursor.execute(f"INSERT INTO {REWE_TABLE_NAME} (date, amount, name, price, category) VALUES ('2024-01-03', 1, 'Milk', 1.5, 'Snacks')")
    insert_entries()
    result = core.get_rewe_most_common_categories(["Milk"])
    assert result == {"Milk": "Dairy"}


# == get_bank_most_common_categories ==

@mark.parametrize("inserted, lookup, expected", [
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping")],
        ["Amazon"],
        {"Amazon": "Shopping"},
        id="single_match"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping"),
         BankDBEntry(2, "2024-01-01", "Me", "Rewe",   30.0, "", "food")],
        ["Amazon", "Rewe"],
        {"Amazon": "Shopping", "Rewe": "food"},
        id="multiple_matches"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping")],
        ["Rewe"],
        {},
        id="no_match"
    ),
    pytest.param(
        [BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping"),
         BankDBEntry(2, "2024-01-01", "Me", "Rewe",   30.0, "", "food")],
        ["Amazon"],
        {"Amazon": "Shopping"},
        id="filtered_by_lookup"
    ),
])
def test_get_bank_most_common_categories(inserted, lookup, expected):
    core.insert_general_expenditure(inserted)
    @core.database_connection
    def get_all(cursor):
        return cursor.execute(f"Select recipient, category from {GENERAL_EXPENDITURES_TABLE_NAME}").fetchall()

    print(get_all())
    result = core.get_bank_most_common_categories(lookup)
    assert result == expected


def test_get_bank_most_common_categories_returns_most_common():

    @core.database_connection
    def insert_entries(cursor):
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-01', 'Me', 'Amazon', 'books', 50.0, 'Shopping')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (2, '2024-01-02', 'Me', 'Amazon', 'books', 50.0, 'Shopping')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (3, '2024-01-03', 'Me', 'Amazon', 'books', 50.0, 'Online')")

    insert_entries()
    result = core.get_bank_most_common_categories(["Amazon"])
    assert result == {"Amazon": "Shopping"}


def test_get_bank_most_common_categories_multiple_entries():

    @core.database_connection
    def insert_entries(cursor):
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-01', 'Me', 'Amazon', 'books', 50.0, '')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-02', 'Me', 'Amazon', 'books', 50.0, 'books')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (2, '2024-01-02', 'Me', 'Amazon', 'books', 50.0, 'Shopping')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-03', 'Me', 'Amazon', 'books', 50.0, 'Shopping')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-01', 'Me', 'Rewe', 'food', 30.0, '')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (1, '2024-01-02', 'Me', 'Rewe', 'food', 30.0, '')")
        cursor.execute(f"INSERT INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category) VALUES (2, '2024-01-02', 'Me', 'Rewe', 'food', 30.0, 'Groceries')")

    insert_entries()
    result = core.get_bank_most_common_categories(["Amazon", "Rewe"])
    assert result == {"Amazon": "Shopping", "Rewe": ""}


# == get_category_types ==

def test_get_category_types_empty():
    assert core.get_category_types() == []


def test_get_category_types_returns_all():
    @core.database_connection
    def insert(cursor):
        cursor.execute(f"INSERT INTO {CATEGORY_TYPES_TABLE_NAME} VALUES ('Food', 'Essential')")
        cursor.execute(f"INSERT INTO {CATEGORY_TYPES_TABLE_NAME} VALUES ('Shopping', 'Routine')")
    insert()
    results = core.get_category_types()
    assert len(results) == 2
    assert {(r.category, r.expense_type) for r in results} == {
        ("Food", "Essential"), ("Shopping", "Routine")
    }


# == update_category_types ==

def test_update_category_types_inserts():
    core.update_category_types([("Food", "Essential")])
    @core.database_connection
    def get(cursor):
        return cursor.execute(f"SELECT * FROM {CATEGORY_TYPES_TABLE_NAME}").fetchall()
    assert get() == [("Food", "Essential")]


def test_update_category_types_replaces_on_duplicate():
    core.update_category_types([("Food", "Essential")])
    core.update_category_types([("Food", "Routine")])
    @core.database_connection
    def get(cursor):
        return cursor.execute(f"SELECT * FROM {CATEGORY_TYPES_TABLE_NAME}").fetchall()
    assert get() == [("Food", "Routine")]


def test_update_category_types_multiple():
    core.update_category_types([("Food", "Essential"), ("Netflix", "Subscription")])
    results = core.get_category_types()
    assert {(r.category, r.expense_type) for r in results} == {
        ("Food", "Essential"), ("Netflix", "Subscription")
    }


# == get_distinct_bank_categories ==

def test_get_distinct_bank_categories_empty():
    assert core.get_distinct_bank_categories() == []


def test_get_distinct_bank_categories_excludes_empty_string():
    core.insert_general_expenditure([
        BankDBEntry(1, "2024-01-01", "Me", "Amazon",  50.0, "", "Shopping"),
        BankDBEntry(2, "2024-01-01", "Me", "Rewe",    30.0, "", ""),
    ])
    assert set(core.get_distinct_bank_categories()) == {"Shopping"}


def test_get_distinct_bank_categories_deduplicates():
    core.insert_general_expenditure([
        BankDBEntry(1, "2024-01-01", "Me", "Amazon",  50.0, "", "Shopping"),
        BankDBEntry(2, "2024-01-01", "Me", "Netflix", 10.0, "", "Shopping"),
    ])
    assert core.get_distinct_bank_categories() == ["Shopping"]


def test_get_distinct_bank_categories_multiple():
    core.insert_general_expenditure([
        BankDBEntry(1, "2024-01-01", "Me", "Amazon",  50.0, "", "Shopping"),
        BankDBEntry(2, "2024-01-01", "Me", "Netflix", 10.0, "", "Subscription"),
        BankDBEntry(3, "2024-01-01", "Me", "Rewe",    30.0, "", "Groceries"),
    ])
    assert set(core.get_distinct_bank_categories()) == {"Shopping", "Subscription", "Groceries"}


# == sync_bank_category_types ==

def test_sync_bank_category_types_inserts_new_with_unassigned():
    core.insert_general_expenditure([BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping")])
    core.sync_bank_category_types()
    results = core.get_category_types()
    assert len(results) == 1
    assert results[0].category == "Shopping"
    assert results[0].expense_type == ExpenseType.UNASSIGNED.value["value"]


def test_sync_bank_category_types_removes_orphans():
    @core.database_connection
    def insert_type(cursor):
        cursor.execute(f"INSERT INTO {CATEGORY_TYPES_TABLE_NAME} VALUES ('OldCat', 'Essential')")
    insert_type()
    core.sync_bank_category_types()
    assert core.get_category_types() == []


def test_sync_bank_category_types_preserves_existing_assignment():
    core.insert_general_expenditure([BankDBEntry(1, "2024-01-01", "Me", "Amazon", 50.0, "", "Shopping")])
    @core.database_connection
    def insert_type(cursor):
        cursor.execute(f"INSERT INTO {CATEGORY_TYPES_TABLE_NAME} VALUES ('Shopping', 'Essential')")
    insert_type()
    core.sync_bank_category_types()
    results = core.get_category_types()
    assert len(results) == 1
    assert results[0].expense_type == "Essential"


def test_sync_bank_category_types_handles_empty_db():
    core.sync_bank_category_types()
    assert core.get_category_types() == []
