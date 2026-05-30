import json
from enum import Enum


class ExpenseType(Enum):
    ESSENTIAL    = {"value": "Essential", "position": 0}
    SUBSCRIPTION = {"value": "Subscription", "position": 1}
    ROUTINE      = {"value": "Routine", "position": 2}
    PERIODIC     = {"value": "Periodic", "position": 3}
    DURABLE      = {"value": "Durable", "position": 4}
    ONE_TIME     = {"value": "One-time", "position": 5}
    SAVINGS      = {"value": "Savings", "position": 6}
    UNASSIGNED   = {"value": "Unassigned", "position": 7}


class Category:
    FOOD= "Food"
    CHEESE = "Cheese"
    SNACKS = "Snacks"
    CEREAL_BAR = "Cereal Bar"

class SetEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, set):
            return list(obj)
        return super().default(obj)

class Jsonable:
    def toJSON(self):
        return json.dumps(
            self,
            default=lambda o: o.__dict__, 
            sort_keys=True,
            indent=4)
    # def toJSON(self):
    #     pass

class BankDBEntry:
    key_helper: str
    date: str
    source: str
    recipient: str
    price: str
    purpose: str
    category: str

    def __init__(self, key_helper: str, date: str, source: str, recipient: str, price: str, purpose: str, category: str = None, display_name: str = None):
        self.key_helper = key_helper
        self.date = date
        self.source = source
        if recipient is not None and type(recipient) is str and len(recipient) > 0:
            self.recipient = recipient
        else:
            self.recipient = source
        self.price = price
        self.purpose = purpose
        self.category = category
        self.display_name = display_name


class Item(Jsonable):
    amount: int
    name: str
    alt_names: set[str]
    price: float
    category: str
    display_name: str

    def __init__(self, amount: int, name: str, price: float, category: str=None, display_name: str=None, alt_names: set[str]=None):
        self.amount = amount
        self.name = name
        self.alt_names = alt_names
        self.price = price
        self.category = category
        self.display_name = display_name

    # def toJSON(self):
    #     return {
    #         "amount": self.amount,
    #         "name": self.name,
    #         "price": self.price,
    #         "category": self.category,
    #         "display_name": self.display_name
    #     }

# class BankExpenditureOutput:
#     key_helper: int
#     date: str
#     source: str
#     recipient: str
#     purpose: str
#     price: float
#     category: str

#     def __init__(self, key_helper, date: str, source: str, recipient: str, purpose: str, price: float, category: str = ""):
#         self.key_helper
#         self.date = date
#         self.source = source
#         self.recipient = recipient
#         self.purpose = purpose
#         self.price = price
#         self.category = category

class ReweExpenditureOutput:
    date: str
    name: str
    amount: int
    price: float
    category: str

    def __init__(self, date: str, name: str, amount: int, price: float, category: str = "", display_name: str = None):
        self.date = date
        self.name = name
        self.amount = amount
        self.price = price
        self.category = category
        self.display_name = display_name

class ReweExpenditureEntry(Jsonable):
    date: str
    name: str
    alt_names: set[str]
    amount: int
    price: float
    category: str
    display_name: str

    def __init__(self, date: str, name: str, amount: int, price: float, category: str, display_name: str,  alt_names: set[str]=None):
        self.date = date
        self.name = name
        self.alt_names = alt_names
        self.amount = amount
        self.price = price
        self.category = category
        self.display_name = display_name

    # def toJSON(self):
    #     return {
    #         "date": self.date,
    #         "name": self.name,
    #         "amount": self.amount,
    #         "price": self.price,
    #         "category": self.category,
    #         "display_name": self.display_name
    #     }

class NameReferenceOutput(Jsonable):
    name: str
    display_name: str

    def __init__(self, name: str, display_name: str):
        self.name = name
        self.display_name = display_name

        

class BankExpenditureEntry(Jsonable):
    key_helper: int
    date: str
    source: str
    recipient: str
    purpose: str
    alt_names: list[str]
    price: float
    category: str
    display_name: str

    def __init__(self, key_helper: int, date: str, source: str, recipient: str, price: float, purpose: str, category: str, display_name: str, alt_names: list[str]=None):
        self.key_helper = key_helper
        self.date = date
        self.source = source
        self.recipient = recipient
        self.purpose = purpose
        self.alt_names = alt_names
        self.price = price
        self.category = category
        self.display_name = display_name

    # def toJSON(self):
    #     return {
    #         "date": self.date,
    #         "source": self.source,
    #         "recipient": self.recipient,
    #         "price": self.price,
    #         "category": self.category,
    #         "display_name": self.display_name
    #     }

class BankCategoryEntry(Jsonable):
    cateogry: str
    price: float

    def __init__(self, category: str, price: float):
        self.category = category
        self.price = price

    # def toJSON(self):
    #     return {
    #         "category": self.cateogry,
    #         "price": self.price
    #     }

class ReweCategoryEntry(Jsonable):
    names: list[str]
    cateogry: str
    amount: int
    price: float

    def __init__(self, category: str, amount: int, price: float):
        self.category = category
        self.amount = amount
        self.price = price


class CategoryTypeEntry(Jsonable):
    category: str
    expense_type: str

    def __init__(self, category: str, expense_type: str):
        self.category = category
        self.expense_type = expense_type

    # def toJSON(self):
    #     return {
    #         "category": self.cateogry,
    #         "amount": self.amount,
    #         "price": self.price
    #     }