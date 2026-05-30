import pytest
import json
from unittest.mock import patch
from app import app as flask_app
from shared.models import (
    ReweExpenditureOutput, BankDBEntry,
    Item, BankExpenditureEntry, BankCategoryEntry, ReweCategoryEntry,
    CategoryTypeEntry,
)


@pytest.fixture
def client():
    flask_app.config['TESTING'] = True
    with flask_app.test_client() as c:
        yield c


# ── Index ─────────────────────────────────────────────────────────────────────

def test_index_returns_200(client):
    response = client.get('/')
    assert response.status_code == 200


# ── REWE individually ────────────────────────────────────────────────────────

def test_rewe_individually_returns_json_array(client):
    entry = ReweExpenditureOutput(date="2024-01-01", name="Milk", amount=2, price=1.5, category="Dairy")
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=[entry]):
        resp = client.get('/get_rewe_expenditures_individually')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert isinstance(data, list)
    assert len(data) == 1
    item = data[0]
    assert item["name"] == "Milk"
    assert item["amount"] == 2
    assert item["price"] == pytest.approx(1.5)
    assert item["category"] == "Dairy"
    assert item["display_name"] == "Milk"


def test_rewe_individually_applies_name_reference(client):
    entry = ReweExpenditureOutput(date="2024-01-01", name="BIO MILCH", amount=1, price=1.5, category="", display_name="Milk")
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=[entry]):
        resp = client.get('/get_rewe_expenditures_individually')

    assert json.loads(resp.data)[0]["display_name"] == "Milk"


def test_rewe_individually_empty_db_returns_empty_array(client):
    with patch("shared.script.get_rewe_expenditures_from_db", return_value=[]):
        resp = client.get('/get_rewe_expenditures_individually')

    assert resp.status_code == 200
    assert json.loads(resp.data) == []


# ── REWE grouped routes ───────────────────────────────────────────────────────

def test_rewe_grouped_by_name_returns_200(client):
    mock = Item(amount=3, name=None, price=4.5, category="Dairy",
                display_name="Milk", alt_names={"Milk"})
    with patch("shared.script.get_rewe_expenditures_grouped_by_name", return_value=[mock]):
        resp = client.get('/get_rewe_expenditures_grouped_by_name')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert data[0]["display_name"] == "Milk"
    assert data[0]["price"] == pytest.approx(4.5)


def test_rewe_grouped_by_name_and_cat_returns_200(client):
    mock = Item(amount=1, name=None, price=1.5, category="Dairy",
                display_name="Milk", alt_names={"Milk"})
    with patch("shared.script.get_rewe_expenditures_grouped_by_name_and_cat", return_value=[mock]):
        resp = client.get('/get_rewe_expenditures_grouped_by_name_and_cat')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert data[0]["category"] == "Dairy"


def test_rewe_grouped_by_category_returns_correct_fields(client):
    mock = ReweCategoryEntry(category="Dairy", amount=5, price=7.5)
    with patch("shared.script.get_rewe_expenditures_grouped_by_category", return_value=[mock]):
        resp = client.get('/get_rewe_expenditures_grouped_by_category')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert data[0]["category"] == "Dairy"
    assert data[0]["amount"] == 5
    assert data[0]["price"] == pytest.approx(7.5)


# ── Bank individually ─────────────────────────────────────────────────────────

def test_bank_individually_returns_json_array(client):
    entry = BankDBEntry(key_helper=1, date="2024-01-01", source="DKB", recipient="Amazon", purpose="Cheap iPhone", price=50.0, category="Shopping")
    with patch("shared.script.get_general_expenditures_from_db", return_value=[entry]):
        resp = client.get('/get_bank_expenditures_individually')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert len(data) == 1
    assert data[0]["source"] == "DKB"
    assert data[0]["price"] == pytest.approx(50.0)
    assert data[0]["display_name"] == "Amazon"
    assert data[0]["purpose"] == "Cheap iPhone"


def test_bank_individually_applies_name_reference(client):
    entry = BankDBEntry(key_helper=1, date="2024-01-01", source="DKB", recipient="AMAZON.DE GMBH", purpose="Cheap iPhone", price=50.0, category="", display_name="Amazon")
    with patch("shared.script.get_general_expenditures_from_db", return_value=[entry]):
        resp = client.get('/get_bank_expenditures_individually')

    assert json.loads(resp.data)[0]["display_name"] == "Amazon"


# ── Bank grouped routes ───────────────────────────────────────────────────────

def test_bank_grouped_by_name_returns_200(client):
    mock = BankExpenditureEntry(
        key_helper=1,
        date="2024-01-01", source="DKB", recipient=None, purpose="cheap iPhone",
        price=50.0, category="Shopping", display_name="Amazon", alt_names={"AMAZON"},
    )
    with patch("shared.script.get_general_expenditures_grouped_by_name", return_value=[mock]):
        resp = client.get('/get_bank_expenditures_grouped_by_name')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert data[0]["display_name"] == "Amazon"


def test_bank_grouped_by_name_and_cat_returns_200(client):
    mock = BankExpenditureEntry(
        key_helper=1,
        date="2024-01-01", source="DKB", recipient=None, purpose="cheap iPhone",
        price=50.0, category="Shopping", display_name="Amazon", alt_names={"AMAZON"},
    )
    with patch("shared.script.get_general_expenditures_grouped_by_name_and_cat", return_value=[mock]):
        resp = client.get('/get_bank_expenditures_grouped_by_name_and_cat')

    assert resp.status_code == 200
    assert json.loads(resp.data)[0]["category"] == "Shopping"


def test_bank_grouped_by_category_returns_correct_fields(client):
    mock = BankCategoryEntry(category="Shopping", price=50.0)
    with patch("shared.script.get_general_expenditures_grouped_by_category", return_value=[mock]):
        resp = client.get('/get_bank_expenditures_grouped_by_category')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert data[0]["category"] == "Shopping"
    assert data[0]["price"] == pytest.approx(50.0)


# ── Date forwarding ───────────────────────────────────────────────────────────

@pytest.mark.parametrize("since, until", [
    ("2024-01-01", "2024-12-31"),
    ("2024-01-01", None),
    (None, "2024-12-31"),
    (None, None),
])
def test_date_params_accepted(client, since, until):
    with patch("shared.script.get_rewe_expenditures_grouped_by_name", return_value=[]):
        params = {k: v for k, v in {"since": since, "until": until}.items() if v}
        resp = client.get('/get_rewe_expenditures_grouped_by_name', query_string=params)
    assert resp.status_code == 200


def test_since_until_forwarded_as_iso_to_script(client):
    with patch("shared.script.get_rewe_expenditures_grouped_by_name",
               return_value=[]) as mock_fn:
        client.get('/get_rewe_expenditures_grouped_by_name',
                   query_string={"since": "2024-01-01", "until": "2024-12-31"})

    mock_fn.assert_called_once_with("2024-01-01T00:00:00", "2024-12-31T00:00:00")


def test_none_dates_forwarded_when_params_absent(client):
    with patch("shared.script.get_rewe_expenditures_grouped_by_name",
               return_value=[]) as mock_fn:
        client.get('/get_rewe_expenditures_grouped_by_name')

    mock_fn.assert_called_once_with(None, None)


# ── update_categories ─────────────────────────────────────────────────────────

def test_update_categories_rewe_calls_correct_db_function(client):
    body = {"target": "rewe", "data": [{"name": "Milk", "category": "Dairy"}]}
    with patch("app.get_original_names", return_value=["Milk"]), \
         patch("app.update_rewe_categories_in_db") as mock_update, \
         patch("app.update_bank_categories_in_db") as mock_bank:
        resp = client.post('/update_categories',
                           data=json.dumps(body),
                           content_type='application/json')

    assert resp.status_code == 200
    mock_update.assert_called_once_with([{"name": "Milk", "category": "Dairy"}])
    mock_bank.assert_not_called()


def test_update_categories_bank_calls_correct_db_function(client):
    body = {"target": "bank", "data": [{"name": "Amazon", "category": "Shopping"}]}
    with patch("app.get_original_names", return_value=["Amazon"]), \
         patch("app.update_bank_categories_in_db") as mock_update, \
         patch("app.update_rewe_categories_in_db") as mock_rewe, \
         patch("app.sync_bank_category_types") as mock_sync:
        resp = client.post('/update_categories',
                           data=json.dumps(body),
                           content_type='application/json')

    assert resp.status_code == 200
    mock_update.assert_called_once_with([{"name": "Amazon", "category": "Shopping"}])
    mock_rewe.assert_not_called()
    mock_sync.assert_called_once()


def test_update_categories_expands_via_get_original_names(client):
    # display_name "Amazon" maps to two raw DB names — both should be updated
    body = {"target": "rewe", "data": [{"name": "Amazon", "category": "Shopping"}]}
    with patch("app.get_original_names", return_value=["AMAZON.DE", "AMAZON GMBH"]), \
         patch("app.update_rewe_categories_in_db") as mock_update:
        client.post('/update_categories',
                    data=json.dumps(body),
                    content_type='application/json')

    transformed = mock_update.call_args[0][0]
    assert {"name": "AMAZON.DE",   "category": "Shopping"} in transformed
    assert {"name": "AMAZON GMBH", "category": "Shopping"} in transformed


def test_update_categories_returns_success_json(client):
    body = {"target": "rewe", "data": []}
    with patch("app.update_rewe_categories_in_db"):
        resp = client.post('/update_categories',
                           data=json.dumps(body),
                           content_type='application/json')

    assert json.loads(resp.data) == {"status": "success"}


# ── update_display_names ──────────────────────────────────────────────────────

def test_update_display_names_single_original_name(client):
    body = {"original_name": "AMAZON.DE", "new_name": "Amazon"}
    with patch("app.update_display_name_by_name") as mock_update:
        resp = client.post('/update_display_names',
                           data=json.dumps(body),
                           content_type='application/json')

    assert resp.status_code == 200
    mock_update.assert_called_once_with("AMAZON.DE", "Amazon")


def test_update_display_names_list_of_original_names(client):
    body = {"original_name": ["AMAZON.DE", "AMAZON GMBH"], "new_name": "Amazon"}
    with patch("app.update_display_name_by_name") as mock_update:
        resp = client.post('/update_display_names',
                           data=json.dumps(body),
                           content_type='application/json')

    assert resp.status_code == 200
    assert mock_update.call_count == 2
    mock_update.assert_any_call("AMAZON.DE",   "Amazon")
    mock_update.assert_any_call("AMAZON GMBH", "Amazon")


def test_update_display_names_returns_success_json(client):
    body = {"original_name": "X", "new_name": "Y"}
    with patch("app.update_display_name_by_name"):
        resp = client.post('/update_display_names',
                           data=json.dumps(body),
                           content_type='application/json')

    assert json.loads(resp.data) == {"status": "success"}


# ── get_category_types ────────────────────────────────────────────────────────

def test_get_category_types_returns_json_array(client):
    mock_types = [CategoryTypeEntry("Food", "Essential"), CategoryTypeEntry("Shopping", "Routine")]
    with patch("app.get_category_types", return_value=mock_types):
        resp = client.get('/get_category_types')

    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert len(data) == 2
    assert {"category": "Food", "expense_type": "Essential"} in data
    assert {"category": "Shopping", "expense_type": "Routine"} in data


def test_get_category_types_empty(client):
    with patch("app.get_category_types", return_value=[]):
        resp = client.get('/get_category_types')

    assert resp.status_code == 200
    assert json.loads(resp.data) == []


# ── get_expense_types ─────────────────────────────────────────────────────────

def test_get_expense_types_returns_all_enum_values(client):
    resp = client.get('/get_expense_types')
    assert resp.status_code == 200
    data = json.loads(resp.data)
    assert set([d["value"] for d in data]) == {
        "Essential", "Subscription", "Routine", "Periodic",
        "Durable", "One-time", "Savings", "Unassigned",
    }


def test_get_expense_types_returns_strings(client):
    resp = client.get('/get_expense_types')
    data = json.loads(resp.data)
    assert all(isinstance(v, dict) for v in data)
    assert all(isinstance(v["position"], int) and isinstance(v["value"], str) for v in data)


# ── update_category_types ─────────────────────────────────────────────────────

def test_update_category_types_calls_db(client):
    body = {"data": [{"category": "Food", "type": "Essential"}]}
    with patch("app.update_category_types") as mock_update:
        resp = client.post('/update_category_types',
                           data=json.dumps(body),
                           content_type='application/json')

    assert resp.status_code == 200
    mock_update.assert_called_once_with([("Food", "Essential")])


def test_update_category_types_multiple_entries(client):
    body = {"data": [
        {"category": "Food",     "type": "Essential"},
        {"category": "Netflix",  "type": "Subscription"},
    ]}
    with patch("app.update_category_types") as mock_update:
        client.post('/update_category_types',
                    data=json.dumps(body),
                    content_type='application/json')

    mock_update.assert_called_once_with([
        ("Food",    "Essential"),
        ("Netflix", "Subscription"),
    ])


def test_update_category_types_returns_success(client):
    body = {"data": []}
    with patch("app.update_category_types"):
        resp = client.post('/update_category_types',
                           data=json.dumps(body),
                           content_type='application/json')

    assert json.loads(resp.data) == {"status": "success"}
