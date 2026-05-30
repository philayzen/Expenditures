from shared.database.constants import DB_NAME, REWE_TABLE_NAME, GENERAL_EXPENDITURES_TABLE_NAME, CATEGORY_TYPES_TABLE_NAME
import sqlite3
from shared.models import Item, ReweExpenditureOutput, NameReferenceOutput, BankDBEntry, BankExpenditureEntry, ReweExpenditureEntry, CategoryTypeEntry, ExpenseType
from shared.config import BANK_IGNORE

def database_connection(func):
    def wrapper(*args, **kwargs):
        connection = sqlite3.connect(DB_NAME)
        cursor = connection.cursor()
        try:
            result = func(cursor, *args, **kwargs)
            connection.commit()
            return result
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()
    return wrapper


@database_connection
def init_db(cursor: sqlite3.Cursor) -> None:
    cursor.execute(f'''
        CREATE TABLE IF NOT EXISTS {REWE_TABLE_NAME} (
            date TIMESTAMP NOT NULL,
            amount INTEGER NOT NULL,
            name TEXT NOT NULL,
            price REAL NOT NULL,
            category TEXT DEFAULT '',
            display_name TEXT DEFAULT '',
            primary key (date, name)
        )
    ''')
    cursor.execute(f'''
        CREATE TABLE IF NOT EXISTS {GENERAL_EXPENDITURES_TABLE_NAME} (
            key_helper INTEGER NOT NULL,
            date TIMESTAMP NOT NULL,
            source TEXT NOT NULL,
            recipient TEXT NOT NULL,
            purpose TEXT,
            price REAL NOT NULL,
            category TEXT DEFAULT '',
            display_name TEXT DEFAULT '',
            primary key (key_helper, date, source, recipient)
        )
    ''')
    try:
        cursor.execute(f"ALTER TABLE {REWE_TABLE_NAME} ADD COLUMN display_name TEXT DEFAULT ''")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute(f"ALTER TABLE {GENERAL_EXPENDITURES_TABLE_NAME} ADD COLUMN display_name TEXT DEFAULT ''")
    except sqlite3.OperationalError:
        pass
    cursor.execute(f'''
        CREATE TABLE IF NOT EXISTS {CATEGORY_TYPES_TABLE_NAME} (
            category TEXT NOT NULL,
            expense_type TEXT NOT NULL DEFAULT 'Unassigned',
            PRIMARY KEY (category),
            FOREIGN KEY (category) REFERENCES {GENERAL_EXPENDITURES_TABLE_NAME}(category)
        )
    ''')

@database_connection
def get_general_expenditures_from_db(cursor: sqlite3.Cursor, since: str=None, until=None) -> list[BankDBEntry]:
    where_clauses = []
    params = []

    if BANK_IGNORE:
        placeholders = ",".join("?" * len(BANK_IGNORE))
        where_clauses.append(f"recipient NOT IN ({placeholders})")
        params.extend(BANK_IGNORE)

    if since is not None:
        where_clauses.append("date >= ?")
        params.append(since)

    if until is not None:
        where_clauses.append("date <= ?")
        params.append(until)

    where = (" WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    query = f"SELECT key_helper, date, source, recipient, price, purpose, category, display_name FROM {GENERAL_EXPENDITURES_TABLE_NAME}{where} order by date desc"
    cursor.execute(query, params)
    expenditures = [BankDBEntry(*row) for row in cursor.fetchall()]
    for e in expenditures:
        e.price = -e.price  
    return expenditures

@database_connection
def get_rewe_expenditures_from_db(cursor: sqlite3.Cursor, since: str=None, until: str=None) -> list[ReweExpenditureOutput]:
    where_clauses = []
    if since is not None:
        where_clauses.append(f"date >= '{since}'")
    if until is not None:
        where_clauses.append(f"date <= '{until}'") 
    where = " WHERE " + " AND ".join(where_clauses) if len(where_clauses)>0 else ""
    cursor.execute(f"SELECT date, name, amount, price, category, display_name FROM {REWE_TABLE_NAME} {where} order by date desc")
    expenditures = [ReweExpenditureOutput(*row) for row in cursor.fetchall()]
    return expenditures

@database_connection
def insert_general_expenditure(cursor: sqlite3.Cursor, data: list[BankDBEntry]) -> None:
    for row in data:
        try:
            cursor.execute(f'''
                INSERT OR IGNORE INTO {GENERAL_EXPENDITURES_TABLE_NAME} (key_helper, date, source, recipient, purpose, price, category)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', (row.key_helper, row.date, row.source, row.recipient, row.purpose, row.price, row.category))
        except Exception as e:
            raise e

@database_connection
def insert_rewe_expenditure(cursor: sqlite3.Cursor, date: str, items: list[Item]) -> None:
    cursor.execute(f"SELECT COUNT(*) FROM {REWE_TABLE_NAME} WHERE date = ?", (date,))
    
    if cursor.fetchone()[0] != 0:
        return
    
    for item in items:
        cursor.execute(f'''
            INSERT OR IGNORE INTO {REWE_TABLE_NAME} (date, amount, name, price, category)
            VALUES (?, ?, ?, ?, ?)
        ''', (date, item.amount, item.name, item.price, item.category))

@database_connection
def _update_bank_entry_display_name(cursor: sqlite3.Cursor, entry: BankExpenditureEntry, new_name: str) -> None:
    if entry.recipient is not None:
        cursor.execute(
            f"UPDATE {GENERAL_EXPENDITURES_TABLE_NAME} SET display_name=? "
            f"WHERE key_helper=? AND date=? AND source=? AND recipient=?",
            (new_name, entry.key_helper, entry.date, entry.source, entry.recipient)
        )
    elif entry.alt_names:
        placeholders = ",".join("?" * len(entry.alt_names))
        cursor.execute(
            f"UPDATE {GENERAL_EXPENDITURES_TABLE_NAME} SET display_name=? "
            f"WHERE recipient IN ({placeholders}) AND category=?",
            (new_name, *entry.alt_names, entry.category)
        )

@database_connection
def _update_rewe_display_name(cursor: sqlite3.Cursor, entry: ReweExpenditureEntry, new_name: str) -> None:
    if entry.name is not None:
        cursor.execute(
            f"UPDATE {REWE_TABLE_NAME} SET display_name=? WHERE name=?",
            (new_name, entry.name)
        )
    elif entry.alt_names:
        placeholders = ",".join("?" * len(entry.alt_names))
        cursor.execute(
            f"UPDATE {REWE_TABLE_NAME} SET display_name=? "
            f"WHERE name IN ({placeholders})",
            (new_name, *entry.alt_names)
        )

def update_display_names(entries: list[BankExpenditureEntry | ReweExpenditureEntry | Item], new_name: str) -> None:
    for entry in entries:
        if isinstance(entry, BankExpenditureEntry):
            _update_bank_entry_display_name(entry, new_name)
        else:
            _update_rewe_display_name(entry, new_name)

@database_connection
def update_display_name_by_name(cursor: sqlite3.Cursor, name: str, new_display_name: str) -> None:
    cursor.execute(f"UPDATE {GENERAL_EXPENDITURES_TABLE_NAME} SET display_name=? WHERE recipient=?", (new_display_name, name))
    cursor.execute(f"UPDATE {REWE_TABLE_NAME} SET display_name=? WHERE name=?", (new_display_name, name))

@database_connection
def get_name_references(cursor: sqlite3.Cursor, names: list[str]) -> list[NameReferenceOutput]:
    
    try:
        results = []
        placeholders = ",".join("?" * len(names))
        cursor.execute(f"SELECT name, display_name FROM {REWE_TABLE_NAME} WHERE name IN ({placeholders}) and display_name <> ''", names)
        results += cursor.fetchall()
        cursor.execute(f"SELECT recipient, display_name FROM {GENERAL_EXPENDITURES_TABLE_NAME} WHERE recipient IN ({placeholders}) and display_name <> ''", names)
        results += cursor.fetchall()
    except Exception as e:
        print(f"Error occurred while fetching name references: {e}")
        raise e
    references = [NameReferenceOutput(*row) for row in results]
    return references

@database_connection
def get_original_names(cursor: sqlite3.Cursor, reference: str) -> list[str]:
    try:
        results = []
        cursor.execute(f"SELECT name FROM {REWE_TABLE_NAME} WHERE display_name == ?", (reference,))
        results += cursor.fetchall()
        cursor.execute(f"SELECT recipient FROM {GENERAL_EXPENDITURES_TABLE_NAME} WHERE display_name == ?", (reference,))
        results += cursor.fetchall()
    except Exception as e:
        print(f"Error occurred while fetching name references: {e}")
        raise e
    if len(results) == 0:
        return [reference]
    else:
        return [row[0] for row in results]

@database_connection
def update_rewe_categories_in_db(cursor: sqlite3.Cursor, data: list[dict]) -> None:
    for item in data:
        cursor.execute(f'''
            UPDATE {REWE_TABLE_NAME}
            SET category = ?
            WHERE name = ?
        ''', (item["category"], item["name"]))

@database_connection
def update_bank_categories_in_db(cursor: sqlite3.Cursor, data: list[dict]) -> None:
    for item in data:
        cursor.execute(f'''
            UPDATE {GENERAL_EXPENDITURES_TABLE_NAME}
            SET category = ?
            WHERE recipient = ?
        ''', (item["category"], item["name"]))

@database_connection
def get_rewe_most_common_categories(cursor: sqlite3.Cursor, names: list[str]) -> dict[str,str]:
    placeholders = ",".join("?" * len(names))
    rows = cursor.execute(f'''
        SELECT name, category FROM {REWE_TABLE_NAME}
        WHERE name IN ({placeholders})
        GROUP BY name, category
        ORDER BY count(*) DESC
    ''', names)
    return_dict = {}
    for name, cat in rows:
        if name not in return_dict:
            return_dict[name] = cat
    return return_dict

@database_connection
def get_bank_most_common_categories(cursor: sqlite3.Cursor, names:list[str]) -> dict[str, str]:
    placeholders = ",".join("?" * len(names))
    rows = cursor.execute(f'''
        SELECT recipient, category FROM {GENERAL_EXPENDITURES_TABLE_NAME}
        WHERE recipient IN ({placeholders})
        GROUP BY recipient, category
        ORDER BY count(*) DESC
    ''', names)
    return_dict = {}
    for name, cat in rows:
        if name not in return_dict:
            return_dict[name] = cat
    return return_dict

# @database_connection
# def change_category(cursor: sqlite3.Cursor, name: str, category: str) -> None:
#     cursor.execute(f'''
#         UPDATE {REWE_TABLE_NAME}
#         SET category = ?
#         WHERE name = ?
#     ''', (category, name))

@database_connection
def get_category_types(cursor: sqlite3.Cursor) -> list[CategoryTypeEntry]:
    cursor.execute(f"SELECT * FROM {CATEGORY_TYPES_TABLE_NAME}")
    return [CategoryTypeEntry(*row) for row in cursor.fetchall()]

@database_connection
def update_category_types(cursor: sqlite3.Cursor, data: list[tuple[str, str]]) -> None:
    for category, expense_type in data:
        cursor.execute(f'''
            INSERT OR REPLACE INTO {CATEGORY_TYPES_TABLE_NAME} (category, expense_type)
            VALUES (?, ?)
        ''', (category, expense_type))

@database_connection
def get_distinct_bank_categories(cursor: sqlite3.Cursor) -> list[str]:
    cursor.execute(f"SELECT DISTINCT category FROM {GENERAL_EXPENDITURES_TABLE_NAME} WHERE category != ''")
    return [row[0] for row in cursor.fetchall()]

@database_connection
def sync_bank_category_types(cursor: sqlite3.Cursor) -> None:
    # I think calling function above won't work as db connection will interfere 
    cursor.execute(f"SELECT DISTINCT category FROM {GENERAL_EXPENDITURES_TABLE_NAME} WHERE category != ''")
    current_cats = {row[0] for row in cursor.fetchall()}

    cursor.execute(f"SELECT category FROM {CATEGORY_TYPES_TABLE_NAME}")
    existing_cats = {row[0] for row in cursor.fetchall()}

    for cat in existing_cats - current_cats:
        cursor.execute(f"DELETE FROM {CATEGORY_TYPES_TABLE_NAME} WHERE category = ?", (cat,))

    for cat in current_cats - existing_cats:
        cursor.execute(
            f"INSERT INTO {CATEGORY_TYPES_TABLE_NAME} (category, expense_type) VALUES (?, ?)",
            (cat, ExpenseType.UNASSIGNED.value["value"])
        )

