import fitz
import sys

try:
    doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
    with open("pdf_pymupdf_blocks.txt", "w", encoding="utf-8") as f:
        for page in doc:
            for block in page.get_text("blocks"):
                f.write(str(block) + "\n")
        print("Success")
except Exception as e:
    print(e)
