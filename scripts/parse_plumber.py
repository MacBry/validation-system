import pdfplumber
import sys

try:
    with pdfplumber.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf") as pdf:
        with open("pdf_plumber_out.txt", "w", encoding="utf-8") as f:
            for page in pdf.pages:
                 f.write(page.extract_text() + "\n====\n")
except Exception as e:
    print(e)
