REWE_FOLDER = 'REWE/'
DKB_FOLDER = 'DKB/'
VB_FOLDER = 'VB/'
ING_FOLDER = 'ING/'
from datetime import datetime
import os
import re
from shared.models import Item, BankDBEntry
import pymupdf
import pandas as pd
import numpy as np
from shared.database.core import insert_general_expenditure, insert_rewe_expenditure, get_bank_most_common_categories, get_rewe_most_common_categories
import logging

logger = logging.getLogger()

def read_csv(file_path: str, header_line: int, betrag_column: str, recipient_column: str, date_column: str, encoding: str=None) -> pd.DataFrame:
    """
        Reads a csv file and transforms it into a pandas DataFrame with the following transformations:
        - The "Betrag (€)" column is converted to a float by formatting the string accordingly.
        - A new column "key_helper" is added, which is used to create unique key.

    Args:
        file_path: path to the csv file to read
    Returns:
        A pandas DataFrame containing the data from the csv file, with the following transformations:
        - The "Betrag (€)" column is converted to a float, with "." as thousand separator and "," as decimal separator
        - A new column "key_helper" is added, which is used to differentiate between multiple expenditures with the same recipient and date. It is incremented for each expenditure with the same recipient and date.
    """
    if encoding is None:
        # df = pd.read_csv(file_path, thousands=".", decimal=",", header=0, skiprows=list(range(header_line-1)), sep=";")
        df = pd.read_csv(file_path, header=0, skiprows=list(range(header_line)), sep=";")
    else:
        df = pd.read_csv(file_path, encoding=encoding, header=0, skiprows=list(range(header_line-1)), sep=";")
    df[betrag_column] = df[betrag_column].str.replace(".", "", regex=False).str.replace(",", ".", regex=False).astype(float)
    try:
        df[date_column] = df[date_column].map(lambda d: datetime.strptime(d, "%d.%m.%Y").strftime("%Y-%m-%d"))
    except Exception:
        df[date_column] = df[date_column].map(lambda d: datetime.strptime(d, "%d.%m.%y").strftime("%Y-%m-%d"))
    try:
        for date in df[date_column]:
            datetime.strptime(date, "%Y-%m-%d")
    except Exception as e: #just to show intent
        raise e
    df["key_helper"] = pd.Series(np.ones(len(df)))
    for i in range(1, len(df)):
        if df.loc[i, recipient_column] == df.loc[i-1, recipient_column] and df.loc[i, date_column] == df.loc[i-1, date_column]:
            df.loc[i, "key_helper"] = df.loc[i-1, "key_helper"] + 1
    return df

def transform_data(data: list[tuple[str,...]], source: str, betrag_column: str, recipient_column: str, date_column: str, purpose_column: str) -> list[BankDBEntry]:
    return [BankDBEntry(
                key_helper=d["key_helper"],
                date=d[date_column],
                source=source,
                recipient=d[recipient_column],
                price=d[betrag_column],
                purpose=d[purpose_column]
            )
            for _, d in data.iterrows()]

def save_dkb_data() -> None:
    for file in os.listdir(DKB_FOLDER):
        if file.endswith('.csv'):
            file_path = os.path.join(DKB_FOLDER, file)
            data = read_csv(file_path, header_line=4, betrag_column="Betrag (€)", recipient_column="Zahlungsempfänger*in", date_column="Buchungsdatum")
            insert_banking_data(transform_data(data, 
                                                      source="DKB",
                                                      betrag_column="Betrag (€)", 
                                                      recipient_column="Zahlungsempfänger*in", 
                                                      date_column="Buchungsdatum", 
                                                      purpose_column="Verwendungszweck"
                                                      ))


def save_ingdiba_data() -> None:
    for file in os.listdir(ING_FOLDER):
        if file.endswith('.csv'):
            file_path = os.path.join(ING_FOLDER, file)
            data = read_csv(file_path, encoding="mbcs", header_line=12, betrag_column="Betrag", recipient_column="Auftraggeber/Empfänger", date_column="Buchung")
            insert_banking_data(transform_data(data, 
                                                      source="ING",
                                                      betrag_column="Betrag", 
                                                      recipient_column="Auftraggeber/Empfänger", 
                                                      date_column="Buchung", 
                                                      purpose_column="Verwendungszweck"
                                                      ))

def save_vb_data() -> None:
    for file in os.listdir(VB_FOLDER):
        if file.endswith('.csv'):
            file_path = os.path.join(VB_FOLDER, file)
            data = read_csv(file_path, header_line=0, betrag_column="Betrag", recipient_column="Name Zahlungsbeteiligter", date_column="Buchungstag")
            insert_banking_data(transform_data(data, 
                                                      source="VB",
                                                      betrag_column="Betrag", 
                                                      recipient_column="Name Zahlungsbeteiligter", 
                                                      date_column="Buchungstag", 
                                                      purpose_column="Verwendungszweck"
                                                      ))

def insert_banking_data(entries: list[BankDBEntry]) -> None:
    cats = get_bank_most_common_categories([d.recipient for d in entries])
    for entry in entries:
        if entry.recipient in cats:
            entry.category = cats[entry.recipient]
    insert_general_expenditure(entries)

def read_pdf(file_path: str) -> str:
    doc = pymupdf.open(file_path)
    text = ''
    for page in doc:
        text += page.get_text()
    return text


def retrieve_rewe_pdf_data() -> None:
    dir = os.listdir(REWE_FOLDER)
    for file in dir:
        if file.endswith('.pdf'):
            items: list[Item] = []
            file_path = os.path.join(REWE_FOLDER, file)
            text = read_pdf(file_path)
            lines = text.split("\n")
            date_line = [l for l in lines if re.search(r" *TSE-Start: *", l) is not None][0].strip()
            date = re.split(r" +", date_line)[1]
            try:
                start = [i for i,l in enumerate(lines) if re.match(r" *EUR *", l)][0]+1
            except:
                try:
                    start = [i for i,l in enumerate(lines) if re.match(r".*\d*,\d* [AB] *", l)][0]
                except IndexError:
                    logger.warning(f"Can't process start of file {file}. Format not implemented")
                    continue
            try:
                end = [i for i,l in enumerate(lines) if re.match(r" *-+ *", l)][0]
            except IndexError:
                logger.warning(f"Can't process end of file {file}. Format not implemented")
                continue
            for line in lines[start:end]:

                if line[-2:] == " B":
                    line = line[:-2]
                    line_array = re.split(r" +", line)
                    total_price = float(line_array[-1].replace(",", "."))
                    name = " ".join(line_array[:-1])
                    if total_price < 0:
                        items[-1].price -= abs(total_price)
                    else:
                        items.append(Item(amount = 1, name = name,price = total_price))
                elif re.match(r".*Stk x.*", line):
                    amount, price_str = [_.strip() for _ in re.split(r" *Stk x *", line)]
                    price = float(price_str.replace(",", "."))
                    items[-1].amount = int(amount)
                    items[-1].price = price
            cats = get_rewe_most_common_categories([d.name for d in items])
            for item in items:
                if item.name in cats:
                    item.category = cats[item.name]
            insert_rewe_expenditure(date, items)



if __name__=="__main__":
    save_vb_data()
    save_ingdiba_data()