import fitz
doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
with open("pdf_lines.txt", "w", encoding="utf-8") as f:
    for page in doc:
        blocks = page.get_text("dict")["blocks"]
        for b in blocks:
            if "lines" in b:
                for l in b["lines"]:
                    text = "".join(s["text"] for s in l["spans"])
                    f.write(text + "\n")
