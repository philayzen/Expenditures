import re
import sqlite3
import pymupdf
import os
from datetime import datetime
import pandas as pd
import numpy as np
from shared.database.core import (init_db,
                                  get_general_expenditures_from_db,
                                  get_rewe_expenditures_from_db)
from shared.models import (Item,
                        BankDBEntry,
                        ReweExpenditureOutput,
                        BankExpenditureEntry,
                        BankCategoryEntry, ReweCategoryEntry)

def get_general_expenditures_grouped_by_name(since=None, until=None) -> list[BankExpenditureEntry]:
    expenditures: list[BankDBEntry] = get_general_expenditures_from_db(since=since, until=until)
    exp_dic: dict[str, BankExpenditureEntry] = {}
    for expenditure in expenditures:
        name_ref = expenditure.display_name or expenditure.recipient
        if name_ref not in exp_dic:
            exp_dic[name_ref] = BankExpenditureEntry(
                key_helper=None,
                date=None, 
                source=None, 
                recipient=None, 
                purpose=expenditure.purpose,
                alt_names={expenditure.recipient},
                price=expenditure.price, 
                category=expenditure.category, 
                display_name=name_ref if name_ref else "")
        else:
            exp_dic[name_ref].price += expenditure.price
            exp_dic[name_ref].alt_names.add(expenditure.recipient)
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: x[1].price, reverse=True)]
    return exp_list


def get_general_expenditures_grouped_by_name_and_cat(since=None, until=None) -> list[BankExpenditureEntry]:
    expenditures: list[BankDBEntry] = get_general_expenditures_from_db(since=since, until=until)
    exp_dic: dict[tuple[str,str], BankExpenditureEntry] = {}
    for expenditure in expenditures:
        name_ref = expenditure.display_name or expenditure.recipient
        key = (name_ref, expenditure.category)
        if key not in exp_dic:
            exp_dic[key] = BankExpenditureEntry(
                key_helper=expenditure.key_helper,
                date=expenditure.date, 
                source=expenditure.source, 
                recipient=None, 
                purpose=expenditure.purpose,
                alt_names={expenditure.recipient},
                price=expenditure.price, 
                category=expenditure.category, 
                display_name=name_ref if name_ref else "")
        else:
            exp_dic[key].price += expenditure.price
            exp_dic[key].alt_names.add(expenditure.recipient)
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: x[1].price, reverse=True)]
    return exp_list


def get_general_expenditures_grouped_by_category(since=None, until=None) -> list[BankCategoryEntry]:
    expenditures: list[BankDBEntry] = get_general_expenditures_from_db(since=since, until=until)
    exp_dic: dict[str, BankCategoryEntry] = {}
    for expenditure in expenditures:
        if expenditure.category not in exp_dic:
            exp_dic[expenditure.category] = BankCategoryEntry(price=expenditure.price, category=expenditure.category)
        else:
            exp_dic[expenditure.category].price += expenditure.price
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: x[1].price, reverse=True)]
    return exp_list


def get_rewe_expenditures_grouped_by_name(since=None, until=None) -> list[Item]:
    expenditures: list[ReweExpenditureOutput] = get_rewe_expenditures_from_db(since=since, until=until)
    exp_dic: dict[tuple[str], Item] = {}
    for expenditure in expenditures:
        name_ref = expenditure.display_name or expenditure.name
        if name_ref not in exp_dic:
            exp_dic[name_ref] = Item(alt_names={expenditure.name}, amount=expenditure.amount, name=None, price=expenditure.price, category=expenditure.category, display_name=name_ref)
        else:
            exp_dic[name_ref].amount += expenditure.amount
            exp_dic[name_ref].price += expenditure.price
            exp_dic[name_ref].alt_names.add(expenditure.name)
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: x[1].price, reverse=True)]
    return exp_list

def get_rewe_expenditures_grouped_by_name_and_cat(since=None, until=None) -> list[Item]:
    expenditures: list[ReweExpenditureOutput] = get_rewe_expenditures_from_db(since=since, until=until)
    exp_dic: dict[tuple[str], Item] = {}
    for expenditure in expenditures:
        name_ref = expenditure.display_name or expenditure.name
        key = (name_ref, expenditure.category)
        if key not in exp_dic:
            exp_dic[key] = Item(alt_names={expenditure.name}, amount=expenditure.amount, name=None, price=expenditure.price, category=expenditure.category, display_name=name_ref)
        else:
            exp_dic[key].amount += expenditure.amount
            exp_dic[key].price += expenditure.price
            exp_dic[key].alt_names.add(expenditure.name)
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: x[1].price, reverse=True)]
    return exp_list

def get_rewe_expenditures_grouped_by_category(since=None, until=None):
    expenditures: list[ReweExpenditureOutput] = get_rewe_expenditures_from_db(since=since, until=until)
    exp_dic: dict[str, ReweCategoryEntry] = {}
    for expenditure in expenditures:
        if (expenditure.category) not in exp_dic:
            exp_dic[expenditure.category] = ReweCategoryEntry(
                amount=expenditure.amount,
                price=expenditure.price, 
                category=expenditure.category
            )
        else:
            exp_dic[expenditure.category].amount += expenditure.amount
            exp_dic[expenditure.category].price += expenditure.price
    exp_list = [item for _, item in sorted(exp_dic.items(), key=lambda x: round(x[1].price,2), reverse=True)]
    return exp_list


if __name__ == "__main__":
    init_db()
    # retrieve_rewe_pdf_data()
    # APRIL = datetime(2026, 4, 1).isoformat()
    # MAY = datetime(2026, 5, 1).isoformat()
    # save_dkb_data()
    # exp = get_rewe_expenditures_grouped_by_name()
    # exp.sort(key=lambda x: x[1][1], reverse=True)
    # for e in exp:
    #     print(e)
    # change_category("BANANE BIO", Category.Food)
    # change_category("BIO SUESSR-BUTT.", Category.Food)
    # change_category("PIZZA MARGHERITA", Category.Food)
    # change_category("PINIENKERNE NAT.'", Category.Food)
    # change_category("PECORINO ROMANO", Category.Food)