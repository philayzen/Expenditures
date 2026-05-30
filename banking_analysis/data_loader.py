from shared.data_import import save_ingdiba_data, save_vb_data, save_dkb_data, retrieve_rewe_pdf_data

if __name__=="__main__":
    retrieve_rewe_pdf_data()
    save_vb_data()
    save_dkb_data()
    save_ingdiba_data()


### LEGACY DATE CONVERSION

# import sqlite3
# from datetime import datetime

# conn = sqlite3.connect("expenditures.db")
# cursor = conn.cursor()

# # Fetch all rows
# cursor.execute("SELECT key_helper, date, source, recipient FROM general_expenditures")
# rows = cursor.fetchall()

# # Convert and update each row
# for key, date_str, source, recipient in rows:
#     try:
#         converted = datetime.strptime(date_str, "%y-%m-%d").strftime("%Y-%m-%d")
#         cursor.execute(
#             "UPDATE general_expenditures SET date = ? WHERE key_helper = ? and date = ? and source = ? and recipient = ?",
#             (converted, key, date_str, source, recipient)
#         )
#         print(f"success for row {key}, {date_str}, {source}, {recipient}. New value: {converted}")
#     except (ValueError, TypeError):
#         print(f"Skipping row {key}, {date_str}, {source}, {recipient}. unexpected value: {date_str}")

# conn.commit()
# conn.close()
# print("Done.")