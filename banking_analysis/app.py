from flask import Flask, render_template, request
import shared.script as script 
from shared.database.core import (update_bank_categories_in_db,
                                update_rewe_categories_in_db,
                                update_display_names, update_display_name_by_name,
                                get_original_names,
                                get_category_types, update_category_types,
                                sync_bank_category_types)
from shared.models import (BankExpenditureEntry, BankDBEntry,
                           ReweExpenditureOutput, ReweExpenditureEntry, Item, SetEncoder,
                           ExpenseType)
from shared.database.constants import REWE_TABLE_NAME, GENERAL_EXPENDITURES_TABLE_NAME
from datetime import datetime
import json

app = Flask(__name__)

def to_json(entries: list):
    return json.dumps([e.__dict__ for e in entries], cls=SetEncoder)

@app.route('/')
def index():
        # active_tab = request.args.get('tab', 'rewe')
    return render_template('content.html')

def get_frame_from_request(request):
    since = request.args.get('since')
    until = request.args.get('until')
    try:
        since = datetime.fromisoformat(since).isoformat() if since else None
        until = datetime.fromisoformat(until).isoformat() if until else None
    except ValueError:
        return {"error": "Invalid date format"}, 400
    return since, until

# def serialise(items: list[script.Item]):
#      serialised = [{"amount": item.amount, "name": item.name, "price": item.price, "category": item.category} for item in items]
#      return serialised

@app.route('/get_rewe_expenditures_individually', methods=['GET'])
def get_rewe_expenditures_individually() -> tuple[list[dict], int]:
    since, until = get_frame_from_request(request)
    db_output: list[ReweExpenditureOutput] = script.get_rewe_expenditures_from_db(since, until)
    entries = [
        ReweExpenditureEntry(date=e.date, name=e.name, amount=e.amount, price=e.price,
                             category=e.category, display_name=e.display_name or e.name)
        for e in db_output
    ]
    return to_json(entries), 200

@app.route('/get_rewe_expenditures_grouped_by_name', methods=['GET'])
def get_rewe_expenditures_grouped_by_name():    
    since, until = get_frame_from_request(request)
    entries = script.get_rewe_expenditures_grouped_by_name(since, until)
    # get_name_references = script.get_name_references([entry["name"] for entry in entries])
    # for entry in entries:
    #     entry["alias"] = next((ref[1] for ref in get_name_references if ref[0] == entry["name"]), "")
    return to_json(entries), 200

@app.route('/get_rewe_expenditures_grouped_by_name_and_cat', methods=['GET'])
def get_rewe_expenditures_grouped_by_name_and_cat():    
    since, until = get_frame_from_request(request)
    entries = script.get_rewe_expenditures_grouped_by_name_and_cat(since, until)
    # get_name_references = script.get_name_references([entry["name"] for entry in entries])
    # for entry in entries:
    #     entry["alias"] = next((ref[1] for ref in get_name_references if ref[0] == entry["name"]), "")
    return to_json(entries), 200

@app.route('/get_rewe_expenditures_grouped_by_category', methods=['GET'])
def get_rewe_expenditures_grouped_by_category():    
    since, until = get_frame_from_request(request)
    return to_json(script.get_rewe_expenditures_grouped_by_category(since, until)), 200

# @app.route('/update_rewe_categories', methods=['POST'])
# def update_categories():
#     data = request.get_json()
#     script.update_categories_in_db(REWE_TABLE_NAME, data)
#     return {"status": "success"}, 200

@app.route('/update_categories', methods=['POST'])
def update_categories():
    body = request.get_json()
    data = body["data"]
    transformed_data = []
    for entry in data:
        names = get_original_names(entry["name"])
        for name in names:
            transformed_data.append({"name": name, "category": entry["category"]})
    if body["target"] == "rewe":
        update_rewe_categories_in_db(transformed_data)
    else:
        update_bank_categories_in_db(transformed_data)
        sync_bank_category_types()
    return {"status": "success"}, 200

@app.route('/get_category_types', methods=['GET'])
def get_category_types_route():
    return to_json(get_category_types()), 200

@app.route('/get_expense_types', methods=['GET'])
def get_expense_types_route():
    return json.dumps([t.value for t in ExpenseType]), 200

@app.route('/update_category_types', methods=['POST'])
def update_category_types_route():
    data = request.get_json()["data"]
    update_category_types([(item["category"], item["type"]) for item in data])
    return {"status": "success"}, 200

@app.route('/update_display_names', methods=["POST"])
def update_display_names_route():
    data = request.get_json()
    new_name = data["new_name"]
    if "entries" in data:
        entries = []
        for e in data["entries"]:
            if "key_helper" in e or "source" in e:
                entries.append(BankExpenditureEntry(**e))
            elif "date" in e and "amount" in e:
                entries.append(ReweExpenditureEntry(**e))
            else:
                entries.append(Item(**e))
        update_display_names(entries, new_name)
    else:
        original_names = data.get("original_name", [])
        if isinstance(original_names, str):
            original_names = [original_names]
        for name in original_names:
            update_display_name_by_name(name, new_name)
    return {"status": "success"}, 200


@app.route("/get_bank_expenditures_individually", methods=["GET"])
def get_bank_expenditures():
    since, until = get_frame_from_request(request)
    db_output: list[BankDBEntry] = script.get_general_expenditures_from_db(since, until)
    entries = [
        BankExpenditureEntry(key_helper=e.key_helper, date=e.date, source=e.source,
                             recipient=e.recipient, purpose=e.purpose, price=e.price,
                             category=e.category, display_name=e.display_name or e.recipient)
        for e in db_output
    ]
    return to_json(entries), 200

@app.route('/get_bank_expenditures_grouped_by_name', methods=['GET'])
def get_bank_expenditures_grouped_by_name():    
    since, until = get_frame_from_request(request)
    return to_json(script.get_general_expenditures_grouped_by_name(since, until)), 200


@app.route('/get_bank_expenditures_grouped_by_name_and_cat', methods=['GET'])
def get_bank_expenditures_grouped_by_name_and_cat():    
    since, until = get_frame_from_request(request)
    entries = script.get_general_expenditures_grouped_by_name_and_cat(since, until)
    return to_json(entries), 200

@app.route('/get_bank_expenditures_grouped_by_category', methods=['GET'])
def get_bank_expenditures_grouped_by_category():    
    since, until = get_frame_from_request(request)
    return to_json(script.get_general_expenditures_grouped_by_category(since, until)), 200

# @app.route('/rewe')
# def rewe():
#     return render_template('rewe.html')

if __name__ == '__main__':
    script.init_db()
    app.run(debug=True, use_reloader=False)
